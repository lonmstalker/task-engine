package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskContextMerger;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.error.TaskExecutionException;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSubmissionStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.impl.dispatcher.ExecutorTaskDispatcher;
import io.lonmstalker.task.impl.store.memory.InMemoryTaskEventStore;
import io.lonmstalker.task.impl.store.memory.InMemoryTaskStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Nested
    @DisplayName("Concurrency")
    class Concurrency {

        @Test
        @DisplayName("shouldMergeContext_WhenConcurrentDuplicateSubmissions")
        void shouldMergeContext_WhenConcurrentDuplicateSubmissions() throws Exception {
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

                TaskKey key = TaskKey.of("task-concurrent");
                int threads = 6;

                ExecutorService executor = Executors.newFixedThreadPool(threads);
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
                            try {
                                if (!start.await(2, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("Start latch timed out");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException("Interrupted while waiting to start", e);
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

                    List<TaskSubmissionResult> results = new ArrayList<>();
                    for (Future<TaskSubmissionResult> future : futures) {
                        results.add(future.get(2, TimeUnit.SECONDS));
                    }

                    TaskSubmissionResult first = results.get(0);
                    long created = results.stream()
                        .filter(result -> result.status() == TaskSubmissionStatus.CREATED)
                        .count();

                    assertThat(created).isEqualTo(1);
                    assertThat(results).allMatch(result -> result.snapshot().id().equals(first.snapshot().id()));

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
        @DisplayName("shouldProcessTasksInParallel")
        void shouldProcessTasksInParallel() throws Exception {
            InMemoryTaskStore store = new InMemoryTaskStore();
            AtomicInteger running = new AtomicInteger();
            AtomicInteger maxRunning = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);

            TaskDefinition<String> definition = TaskDefinition.<String>builder()
                .type(TaskType.of("parallel"))
                .handler(context -> {
                    int current = running.incrementAndGet();
                    maxRunning.updateAndGet(previous -> Math.max(previous, current));
                    started.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            return TaskResult.failure(new TaskExecutionException("Timeout waiting for release"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return TaskResult.failure(new TaskExecutionException("Interrupted", e));
                    } finally {
                        running.decrementAndGet();
                        done.countDown();
                    }
                    return TaskResult.success();
                })
                .contextCodec(new StringCodec())
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .build();

            try (TaskEngine engine = TaskEngineBuilder.builder()
                .store(store)
                .dispatcher(ExecutorTaskDispatcher.fixedThreadPool(2, "parallel-worker-%d"))
                .registerDefinition(definition)
                .build()) {

                TaskKey key1 = TaskKey.of("task-parallel-1");
                TaskKey key2 = TaskKey.of("task-parallel-2");

                engine.submit(new TaskRequest<>(
                    key1,
                    definition.type(),
                    STATE_NEW,
                    "one",
                    List.of()
                ));
                engine.submit(new TaskRequest<>(
                    key2,
                    definition.type(),
                    STATE_NEW,
                    "two",
                    List.of()
                ));

                TaskEngineImpl impl = (TaskEngineImpl) engine;
                impl.pollOnce();

                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
                assertThat(maxRunning.get()).isGreaterThanOrEqualTo(2);

                release.countDown();
                assertThat(done.await(2, TimeUnit.SECONDS)).isTrue();

                TaskSnapshot snapshot1 = awaitStatus(engine, key1, TaskStatus.COMPLETED);
                TaskSnapshot snapshot2 = awaitStatus(engine, key2, TaskStatus.COMPLETED);

                assertThat(snapshot1).isNotNull();
                assertThat(snapshot2).isNotNull();
            }
        }

        @Test
        @DisplayName("shouldKeepOriginalContext_WhenDuplicateArrivesDuringProcessing")
        void shouldKeepOriginalContext_WhenDuplicateArrivesDuringProcessing() throws Exception {
            InMemoryTaskStore store = new InMemoryTaskStore();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            TaskContextCodec<String> codec = new StringCodec();

            TaskDefinition<String> definition = TaskDefinition.<String>builder()
                .type(TaskType.of("blocking"))
                .handler(context -> {
                    started.countDown();
                    try {
                        if (!release.await(2, TimeUnit.SECONDS)) {
                            return TaskResult.failure(new TaskExecutionException("Timeout waiting for release"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return TaskResult.failure(new TaskExecutionException("Interrupted", e));
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
                .dispatcher(new DirectTaskDispatcher())
                .registerDefinition(definition)
                .build()) {

                TaskKey key = TaskKey.of("task-running");
                TaskSubmissionResult created = engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    "first",
                    List.of()
                ));

                TaskEngineImpl impl = (TaskEngineImpl) engine;
                Thread pollThread = new Thread(impl::pollOnce, "task-poll-thread");
                pollThread.start();

                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

                TaskSubmissionResult duplicate = engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    "second",
                    List.of()
                ));

                assertThat(duplicate.snapshot().id()).isEqualTo(created.snapshot().id());
                assertThat(duplicate.status()).isEqualTo(TaskSubmissionStatus.DUPLICATE);

                release.countDown();
                pollThread.join(2000);
                assertThat(pollThread.isAlive()).isFalse();

                TaskSnapshot snapshot = engine.findByKey(key);
                assertThat(snapshot).isNotNull();
                assertThat(snapshot.status()).isEqualTo(TaskStatus.COMPLETED);
                assertThat(snapshot.state()).isEqualTo(STATE_DONE);
                assertThat(codec.decode(snapshot.payload())).isEqualTo("first");
            }
        }
    }

    @Nested
    @DisplayName("Events")
    class Events {

        @Test
        @DisplayName("shouldCreateChainEventWithRequestContexts")
        void shouldCreateChainEventWithRequestContexts() {
            InMemoryTaskStore store = new InMemoryTaskStore();
            InMemoryTaskEventStore eventStore = new InMemoryTaskEventStore();
            TaskContextCodec<String> codec = new StringCodec();

            TaskDefinition<String> definitionA = TaskDefinition.<String>builder()
                .type(TaskType.of("chain-a"))
                .handler(context -> TaskResult.success())
                .contextCodec(codec)
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .contributesToChainContext(true)
                .build();

            TaskDefinition<String> definitionC = TaskDefinition.<String>builder()
                .type(TaskType.of("chain-c"))
                .handler(context -> TaskResult.success())
                .contextCodec(codec)
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .build();

            TaskDefinition<String> definitionB = TaskDefinition.<String>builder()
                .type(TaskType.of("chain-b"))
                .handler(context -> TaskResult.success())
                .contextCodec(codec)
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .build();

            try (TaskEngine engine = TaskEngineBuilder.builder()
                .store(store)
                .eventStore(eventStore)
                .dispatcher(new DirectTaskDispatcher())
                .registerDefinition(definitionA)
                .registerDefinition(definitionC)
                .registerDefinition(definitionB)
                .build()) {

                TaskSubmissionResult createdA = engine.submit(new TaskRequest<>(
                    TaskKey.of("chain-a"),
                    definitionA.type(),
                    STATE_NEW,
                    "ctx-a",
                    List.of()
                ));

                TaskSubmissionResult createdC = engine.submit(new TaskRequest<>(
                    TaskKey.of("chain-c"),
                    definitionC.type(),
                    STATE_NEW,
                    "ctx-c",
                    List.of()
                ));

                TaskLink linkA = new TaskLink(createdA.snapshot().id(), TaskLinkType.DEPENDS_ON);
                TaskLink linkC = new TaskLink(createdC.snapshot().id(), TaskLinkType.DEPENDS_ON);

                TaskSubmissionResult createdB = engine.submit(new TaskRequest<>(
                    TaskKey.of("chain-b"),
                    definitionB.type(),
                    STATE_NEW,
                    "ctx-b",
                    List.of(linkA, linkC)
                ));

                TaskEngineImpl impl = (TaskEngineImpl) engine;
                impl.pollOnce();
                impl.pollOnce();
                impl.pollOnce();

                List<TaskEventRecord> events = eventStore.findEventsByTaskId(createdB.snapshot().id());
                assertThat(events).hasSize(1);

                TaskEventRecord event = events.get(0);
                List<TaskEventContextEntry> contexts = eventStore.findContexts(event.id());

                List<TaskEventContextEntry> chainContexts = contexts.stream()
                    .filter(entry -> entry.kind() == TaskEventContextKind.CHAIN)
                    .toList();
                List<TaskEventContextEntry> requestContexts = contexts.stream()
                    .filter(entry -> entry.kind() == TaskEventContextKind.REQUEST)
                    .toList();

                assertThat(chainContexts).hasSize(2);
                assertThat(chainContexts).anyMatch(entry -> entry.taskId().equals(createdA.snapshot().id()));
                assertThat(chainContexts).anyMatch(entry -> entry.taskId().equals(createdB.snapshot().id()));
                assertThat(chainContexts).noneMatch(entry -> entry.taskId().equals(createdC.snapshot().id()));

                assertThat(requestContexts).hasSize(3);
                assertThat(requestContexts).anyMatch(entry -> entry.taskId().equals(createdA.snapshot().id()));
                assertThat(requestContexts).anyMatch(entry -> entry.taskId().equals(createdB.snapshot().id()));
                assertThat(requestContexts).anyMatch(entry -> entry.taskId().equals(createdC.snapshot().id()));
            }
        }

        @Test
        @DisplayName("shouldCaptureDuplicateContextsBeforeCompletion")
        void shouldCaptureDuplicateContextsBeforeCompletion() {
            InMemoryTaskStore store = new InMemoryTaskStore();
            InMemoryTaskEventStore eventStore = new InMemoryTaskEventStore();
            TaskContextCodec<String> codec = new StringCodec();

            TaskDefinition<String> definition = TaskDefinition.<String>builder()
                .type(TaskType.of("dup-before"))
                .handler(context -> TaskResult.success())
                .contextCodec(codec)
                .contextMerger((existing, incoming) -> existing)
                .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
                .retryPolicy(RetryPolicies.none())
                .build();

            try (TaskEngine engine = TaskEngineBuilder.builder()
                .store(store)
                .eventStore(eventStore)
                .dispatcher(new DirectTaskDispatcher())
                .registerDefinition(definition)
                .build()) {

                TaskKey key = TaskKey.of("dup-before");
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

                TaskEngineImpl impl = (TaskEngineImpl) engine;
                impl.pollOnce();

                List<TaskEventRecord> events = eventStore.findEventsByTaskId(created.snapshot().id());
                assertThat(events).hasSize(1);

                TaskEventRecord event = events.get(0);
                List<TaskEventContextEntry> contexts = eventStore.findContexts(event.id());

                assertThat(contexts).anyMatch(entry ->
                    entry.kind() == TaskEventContextKind.REQUEST
                        && codec.decode(entry.payload()).equals("base")
                );
                assertThat(contexts).anyMatch(entry ->
                    entry.kind() == TaskEventContextKind.DUPLICATE
                        && codec.decode(entry.payload()).equals("dup")
                );
                assertThat(contexts).anyMatch(entry -> entry.kind() == TaskEventContextKind.CHAIN);
            }
        }

        @Test
        @DisplayName("shouldCreateEventForLateDuplicates")
        void shouldCreateEventForLateDuplicates() {
            InMemoryTaskStore store = new InMemoryTaskStore();
            InMemoryTaskEventStore eventStore = new InMemoryTaskEventStore();
            TaskContextCodec<String> codec = new StringCodec();

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
                .eventStore(eventStore)
                .dispatcher(new DirectTaskDispatcher())
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

                TaskEngineImpl impl = (TaskEngineImpl) engine;
                impl.pollOnce();

                engine.submit(new TaskRequest<>(
                    key,
                    definition.type(),
                    STATE_NEW,
                    "late",
                    List.of()
                ));

                List<TaskEventRecord> events = eventStore.findEventsByTaskId(created.snapshot().id());
                assertThat(events).hasSize(2);

                TaskEventRecord lateEvent = events.stream()
                    .filter(event -> eventStore.findContexts(event.id()).stream()
                        .anyMatch(entry -> entry.kind() == TaskEventContextKind.DUPLICATE))
                    .findFirst()
                    .orElseThrow();

                List<TaskEventContextEntry> contexts = eventStore.findContexts(lateEvent.id());
                assertThat(contexts).anyMatch(entry ->
                    entry.kind() == TaskEventContextKind.DUPLICATE
                        && codec.decode(entry.payload()).equals("late")
                );
                assertThat(contexts).noneMatch(entry -> entry.kind() == TaskEventContextKind.REQUEST);
            }
        }
    }

    private static TaskSnapshot awaitStatus(
        TaskEngine engine,
        TaskKey key,
        TaskStatus expected
    ) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        TaskSnapshot snapshot = null;
        while (System.nanoTime() < deadline) {
            snapshot = engine.findByKey(key);
            if (snapshot != null && snapshot.status() == expected) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        return snapshot;
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
