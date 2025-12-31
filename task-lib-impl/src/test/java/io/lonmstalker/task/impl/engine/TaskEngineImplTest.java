package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskContextMerger;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.error.TaskExecutionException;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSubmissionStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.impl.store.memory.InMemoryTaskStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskEngineImpl")
class TaskEngineImplTest {

    private static final @NonNull TaskState STATE_NEW = TaskState.of("NEW");
    private static final @NonNull TaskState STATE_PROCESSING = TaskState.of("PROCESSING");
    private static final @NonNull TaskState STATE_DONE = TaskState.of("DONE");

    @Nested
    @DisplayName("Submission")
    class Submission {

        @Test
        @DisplayName("shouldMergeContext_WhenDuplicateSubmission")
        void shouldMergeContext_WhenDuplicateSubmission() {
            InMemoryTaskStore store = new InMemoryTaskStore();
            TaskContextCodec<List<String>> codec = new CsvListCodec();

            TaskDefinition<List<String>> definition = TaskDefinition.<List<String>>builder()
                .type(TaskType.of("notify"))
                .handler(context -> TaskResult.success())
                .contextCodec(codec)
                .contextMerger(new UniqueListMerger())
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .build();

            try (TaskEngine engine = TaskEngineBuilder.builder()
                .store(store)
                .dispatcher(new DirectTaskDispatcher())
                .registerDefinition(definition)
                .build()) {

                TaskKey key = TaskKey.of("task-1");

                TaskSubmissionResult first = engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    List.of("user-a"),
                    List.of()
                ));

                TaskSubmissionResult second = engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    List.of("user-b"),
                    List.of()
                ));

                List<String> merged = codec.decode(second.snapshot().payload());

                assertThat(first.snapshot().id()).isEqualTo(second.snapshot().id());
                assertThat(second.status()).isEqualTo(TaskSubmissionStatus.UPDATED);
                assertThat(merged).containsExactlyInAnyOrder("user-a", "user-b");
            }
        }

        @Test
        @DisplayName("shouldUpdateState_WhenExternalStateArrives")
        void shouldUpdateState_WhenExternalStateArrives() {
            InMemoryTaskStore store = new InMemoryTaskStore();

            TaskDefinition<String> definition = TaskDefinition.<String>builder()
                .type(TaskType.of("sync"))
                .handler(context -> TaskResult.success())
                .contextCodec(new StringCodec())
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_PROCESSING, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .build();

            try (TaskEngine engine = TaskEngineBuilder.builder()
                .store(store)
                .dispatcher(new DirectTaskDispatcher())
                .registerDefinition(definition)
                .build()) {

                TaskKey key = TaskKey.of("task-2");

                engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    "ctx",
                    List.of()
                ));

                TaskSubmissionResult second = engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_PROCESSING,
                    "ctx",
                    List.of()
                ));

                assertThat(second.snapshot().state()).isEqualTo(STATE_PROCESSING);
            }
        }
    }

    @Nested
    @DisplayName("Retries")
    class Retries {

        @Test
        @DisplayName("shouldRetryAndFail_WhenAttemptsExhausted")
        void shouldRetryAndFail_WhenAttemptsExhausted() {
            InMemoryTaskStore store = new InMemoryTaskStore();

            TaskDefinition<String> definition = TaskDefinition.<String>builder()
                .type(TaskType.of("retry"))
                .handler(context -> TaskResult.failure(new TaskExecutionException("boom")))
                .contextCodec(new StringCodec())
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.fixedDelay(2, Duration.ZERO))
                .build();

            try (TaskEngine engine = TaskEngineBuilder.builder()
                .store(store)
                .dispatcher(new DirectTaskDispatcher())
                .registerDefinition(definition)
                .build()) {

                TaskKey key = TaskKey.of("task-3");

                engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    "ctx",
                    List.of()
                ));

                TaskEngineImpl impl = (TaskEngineImpl) engine;
                impl.pollOnce();
                impl.pollOnce();

                TaskRecord record = store.findByKey(key);
                assertThat(record).isNotNull();
                assertThat(record.status()).isEqualTo(TaskStatus.FAILED);
                assertThat(record.attempt()).isEqualTo(2);
            }
        }
    }

    private static final class DirectTaskDispatcher implements TaskDispatcher {

        @Override
        public void dispatch(
            @NonNull Runnable task
        ) {
            task.run();
        }

        @Override
        public int parallelism() {
            return 1;
        }

        @Override
        public void close() {
        }
    }

    private static final class StringCodec implements TaskContextCodec<String> {

        @Override
        public @NonNull TaskPayload encode(
            @NonNull String context
        ) {
            return new TaskPayload(context.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        @Override
        public @NonNull String decode(
            @NonNull TaskPayload payload
        ) {
            return new String(payload.data(), StandardCharsets.UTF_8);
        }
    }

    private static final class CsvListCodec implements TaskContextCodec<List<String>> {

        @Override
        public @NonNull TaskPayload encode(
            @NonNull List<String> context
        ) {
            String joined = String.join(";", context);
            return new TaskPayload(joined.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        @Override
        public @NonNull List<String> decode(
            @NonNull TaskPayload payload
        ) {
            String decoded = new String(payload.data(), StandardCharsets.UTF_8);
            if (decoded.isBlank()) {
                return List.of();
            }

            return List.of(decoded.split(";"));
        }
    }

    private static final class UniqueListMerger implements TaskContextMerger<List<String>> {

        @Override
        public @NonNull List<String> merge(
            @NonNull List<String> existing,
            @NonNull List<String> incoming
        ) {
            Set<String> merged = new HashSet<>(existing);
            merged.addAll(incoming);
            return new ArrayList<>(merged);
        }
    }
}
