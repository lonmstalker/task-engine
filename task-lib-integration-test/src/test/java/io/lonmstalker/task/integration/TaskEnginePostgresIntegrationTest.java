package io.lonmstalker.task.integration;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSubmissionStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.impl.dispatcher.ExecutorTaskDispatcher;
import io.lonmstalker.task.impl.engine.TaskEngineBuilder;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TaskEngine Postgres integration")
class TaskEnginePostgresIntegrationTest extends PostgresIntegrationTestBase {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    @Test
    @DisplayName("shouldPersistDuplicateContextsOnCompletion")
    void shouldPersistDuplicateContextsOnCompletion() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("notify"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing + ";" + incoming)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .contributesToChainContext(true)
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definition)
            .build()) {

            TaskKey key = TaskKey.of("dup-contexts");
            TaskSubmissionResult created = engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "base",
                List.of()
            ));
            engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "dup",
                List.of()
            ));

            IntegrationTestSupport.pollOnce(engine);

            List<TaskEventRecord> events = store.findEventsByTaskId(created.snapshot().id());
            assertThat(events).hasSize(1);

            List<TaskEventContextEntry> contexts = store.findContexts(events.get(0).id());
            assertThat(contexts).anyMatch(entry ->
                entry.kind() == TaskEventContextKind.REQUEST
                    && "base".equals(codec.decode(entry.payload()))
            );
            assertThat(contexts).anyMatch(entry ->
                entry.kind() == TaskEventContextKind.DUPLICATE
                    && "dup".equals(codec.decode(entry.payload()))
            );
        }
    }

    @Test
    @DisplayName("shouldRunDependentAfterDependencyCompletes")
    void shouldRunDependentAfterDependencyCompletes() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definitionA = TaskDefinition.<String>builder()
            .type(TaskType.of("parent"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        TaskDefinition<String> definitionB = TaskDefinition.<String>builder()
            .type(TaskType.of("child"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definitionA)
            .registerDefinition(definitionB)
            .build()) {

            TaskSubmissionResult createdA = engine.submit(new TaskRequest<>(
                TaskKey.of("parent-task"),
                definitionA.type(),
                STATE_NEW,
                "a",
                List.of()
            ));

            TaskLink linkA = new TaskLink(createdA.snapshot().id(), TaskLinkType.DEPENDS_ON);
            TaskKey childKey = TaskKey.of("child-task");
            engine.submit(new TaskRequest<>(
                childKey,
                definitionB.type(),
                STATE_NEW,
                "b",
                List.of(linkA)
            ));

            IntegrationTestSupport.pollOnce(engine);

            TaskSnapshot parent = engine.findByKey(TaskKey.of("parent-task"));
            TaskSnapshot child = engine.findByKey(childKey);

            assertThat(parent).isNotNull();
            assertThat(parent.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(child).isNotNull();
            assertThat(child.status()).isEqualTo(TaskStatus.PENDING);

            IntegrationTestSupport.pollOnce(engine);

            TaskSnapshot childDone = engine.findByKey(childKey);
            assertThat(childDone).isNotNull();
            assertThat(childDone.status()).isEqualTo(TaskStatus.COMPLETED);
        }
    }

    @Test
    @DisplayName("shouldAttachDependentRequestContextOnFailedEvent")
    void shouldAttachDependentRequestContextOnFailedEvent() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definitionA = TaskDefinition.<String>builder()
            .type(TaskType.of("fail-parent"))
            .handler(context -> TaskResult.failure(new IntegrationFixtures.RuntimeFailure()))
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        TaskDefinition<String> definitionB = TaskDefinition.<String>builder()
            .type(TaskType.of("child"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definitionA)
            .registerDefinition(definitionB)
            .build()) {

            TaskSubmissionResult createdA = engine.submit(new TaskRequest<>(
                TaskKey.of("fail-task"),
                definitionA.type(),
                STATE_NEW,
                "ctx-a",
                List.of()
            ));

            TaskLink linkA = new TaskLink(createdA.snapshot().id(), TaskLinkType.DEPENDS_ON);
            engine.submit(new TaskRequest<>(
                TaskKey.of("child-task"),
                definitionB.type(),
                STATE_NEW,
                "ctx-b",
                List.of(linkA)
            ));

            IntegrationTestSupport.pollOnce(engine);

            List<TaskEventRecord> events = store.findEventsByTaskId(createdA.snapshot().id());
            assertThat(events).hasSize(1);

            List<TaskEventContextEntry> contexts = store.findContexts(events.get(0).id());
            assertThat(contexts).anyMatch(entry ->
                entry.kind() == TaskEventContextKind.REQUEST
                    && "ctx-b".equals(codec.decode(entry.payload()))
            );
        }
    }

    @Test
    @DisplayName("shouldMergeConcurrentDuplicates")
    void shouldMergeConcurrentDuplicates() throws Exception {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<List<String>> codec = new IntegrationFixtures.CsvListCodec();

        TaskDefinition<List<String>> definition = TaskDefinition.<List<String>>builder()
            .type(TaskType.of("notify-bulk"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger(new IntegrationFixtures.UniqueListMerger())
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(ExecutorTaskDispatcher.fixedThreadPool(4, "integration-worker-%d"))
            .registerDefinition(definition)
            .build()) {

            TaskKey key = TaskKey.of("concurrent-dup");
            int threads = 6;

            ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
            try {
                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<TaskSubmissionResult>> futures = new ArrayList<>();
                List<String> expected = new ArrayList<>();

                for (int i = 0; i < threads; i++) {
                    String user = "user-" + i;
                    expected.add(user);
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        if (!start.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("Start latch timed out");
                        }
                        return engine.submit(new TaskRequest<>(
                            key,
                            definition.type(),
                            STATE_NEW,
                            List.of(user),
                            List.of()
                        ));
                    }));
                }

                assertThat(ready.await(2, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                for (Future<TaskSubmissionResult> future : futures) {
                    future.get(2, TimeUnit.SECONDS);
                }

                TaskSnapshot snapshot = engine.findByKey(key);
                assertThat(snapshot).isNotNull();
                List<String> merged = codec.decode(snapshot.payload());
                assertThat(merged).containsExactlyInAnyOrderElementsOf(expected);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("shouldCreateLateDuplicateEvent")
    void shouldCreateLateDuplicateEvent() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("dup-late"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definition)
            .build()) {

            TaskKey key = TaskKey.of("dup-late");
            TaskSubmissionResult created = engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "base",
                List.of()
            ));

            IntegrationTestSupport.pollOnce(engine);

            engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "late",
                List.of()
            ));

            List<TaskEventRecord> events = store.findEventsByTaskId(created.snapshot().id());
            assertThat(events).hasSize(2);

            TaskEventRecord lateEvent = events.stream()
                .filter(event -> store.findContexts(event.id()).stream()
                    .anyMatch(entry -> entry.kind() == TaskEventContextKind.DUPLICATE))
                .findFirst()
                .orElseThrow();

            List<TaskEventContextEntry> contexts = store.findContexts(lateEvent.id());
            assertThat(contexts).anyMatch(entry ->
                entry.kind() == TaskEventContextKind.DUPLICATE
                    && "late".equals(codec.decode(entry.payload()))
            );
            assertThat(contexts).noneMatch(entry -> entry.kind() == TaskEventContextKind.REQUEST);
        }
    }

    @Test
    @DisplayName("shouldKeepPayloadWhenDuplicateArrivesDuringProcessing")
    void shouldKeepPayloadWhenDuplicateArrivesDuringProcessing() throws Exception {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("blocking"))
            .handler(context -> {
                started.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) {
                        return TaskResult.failure(new IntegrationFixtures.RuntimeFailure());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return TaskResult.failure(new IntegrationFixtures.RuntimeFailure());
                }
                return TaskResult.success();
            })
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing + ";" + incoming)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(ExecutorTaskDispatcher.fixedThreadPool(2, "blocking-worker-%d"))
            .registerDefinition(definition)
            .build()) {

            TaskKey key = TaskKey.of("dup-running");
            TaskSubmissionResult created = engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "first",
                List.of()
            ));

            Thread pollThread = new Thread(() -> IntegrationTestSupport.pollOnce(engine), "poll-thread");
            pollThread.start();
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            TaskSubmissionResult duplicate = engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "second",
                List.of()
            ));

            assertThat(duplicate.status()).isEqualTo(TaskSubmissionStatus.DUPLICATE);

            release.countDown();
            pollThread.join(2000);

            TaskSnapshot snapshot = IntegrationTestSupport.awaitStatus(
                engine,
                key,
                TaskStatus.COMPLETED,
                Duration.ofSeconds(2)
            );

            assertThat(snapshot).isNotNull();
            assertThat(codec.decode(snapshot.payload())).isEqualTo("first");

            List<TaskEventRecord> events = store.findEventsByTaskId(created.snapshot().id());
            assertThat(events).hasSize(1);
            List<TaskEventContextEntry> contexts = store.findContexts(events.get(0).id());
            assertThat(contexts).anyMatch(entry ->
                entry.kind() == TaskEventContextKind.DUPLICATE
                    && "second".equals(codec.decode(entry.payload()))
            );
        }
    }

    @Test
    @DisplayName("shouldRetryAndComplete")
    void shouldRetryAndComplete() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();
        AtomicInteger attempts = new AtomicInteger(0);

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("retry"))
            .handler(context -> {
                if (attempts.incrementAndGet() == 1) {
                    return TaskResult.failure(new IntegrationFixtures.RuntimeFailure());
                }
                return TaskResult.success();
            })
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.fixedDelay(2, Duration.ZERO))
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definition)
            .build()) {

            TaskKey key = TaskKey.of("retry-task");
            engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "ctx",
                List.of()
            ));

            IntegrationTestSupport.pollOnce(engine);

            TaskSnapshot retrySnapshot = engine.findByKey(key);
            assertThat(retrySnapshot).isNotNull();
            assertThat(retrySnapshot.status()).isEqualTo(TaskStatus.WAITING_RETRY);
            assertThat(retrySnapshot.nextRunAt()).isNotNull();

            IntegrationTestSupport.pollOnce(engine);

            TaskSnapshot completed = engine.findByKey(key);
            assertThat(completed).isNotNull();
            assertThat(completed.status()).isEqualTo(TaskStatus.COMPLETED);
            assertThat(store.findByKey(key).attempt()).isEqualTo(2);

            assertThat(store.findEventsByTaskId(completed.id())).hasSize(1);
        }
    }

    @Test
    @DisplayName("shouldFailOnInvalidTransition")
    void shouldFailOnInvalidTransition() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("invalid-transition"))
            .handler(context -> TaskResult.success(TaskState.of("INVALID")))
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definition)
            .build()) {

            TaskKey key = TaskKey.of("invalid-transition");
            engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                "ctx",
                List.of()
            ));

            IntegrationTestSupport.pollOnce(engine);

            TaskSnapshot snapshot = engine.findByKey(key);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.status()).isEqualTo(TaskStatus.FAILED);
            assertThat(store.findEventsByTaskId(snapshot.id())).hasSize(1);
        }
    }

}
