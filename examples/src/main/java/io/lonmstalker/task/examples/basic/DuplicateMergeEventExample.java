package io.lonmstalker.task.examples.basic;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskContextCodecs;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.examples.common.ExampleSettings;
import io.lonmstalker.task.examples.common.ExampleWaiter;
import io.lonmstalker.task.impl.dispatcher.ExecutorTaskDispatcher;
import io.lonmstalker.task.impl.engine.TaskEngineBuilder;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.sql.DataSource;

public final class DuplicateMergeEventExample {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_STEP = TaskState.of("STEP");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    private DuplicateMergeEventExample() {
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = ExampleSettings.createDataSource();
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = TaskContextCodecs.stringUtf8();

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("dup-merge"))
            .handler(context -> {
                TaskState current = context.snapshot().state();
                if (STATE_NEW.equals(current)) {
                    return TaskResult.success(STATE_STEP);
                }
                return TaskResult.success(STATE_DONE);
            })
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing + "|" + incoming)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_STEP, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(ExecutorTaskDispatcher.fixedThreadPool(1, "example-dup-merge-%d"))
            .pollInterval(Duration.ofSeconds(5))
            .registerDefinition(definition)
            .build()) {

            engine.start();

            TaskKey key = TaskKey.of("dup-merge-" + UUID.randomUUID());
            TaskSubmissionResult created = engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                null,
                "first",
                List.of()
            ));

            System.out.println("Created status: " + created.status());

            TaskSnapshot stepSnapshot = awaitSnapshot(
                engine,
                key,
                snapshot -> snapshot.status() == TaskStatus.PENDING
                    && snapshot.state().equals(STATE_STEP),
                Duration.ofSeconds(10)
            );

            System.out.println("Reached step: " + stepSnapshot.state().value());

            TaskSubmissionResult duplicate = engine.submit(new TaskRequest<>(
                key,
                definition.type(),
                STATE_NEW,
                null,
                "second",
                List.of()
            ));

            System.out.println("Duplicate status: " + duplicate.status());

            TaskSnapshot mergedSnapshot = awaitSnapshot(
                engine,
                key,
                snapshot -> codec.decode(snapshot.payload()).contains("second"),
                Duration.ofSeconds(5)
            );

            System.out.println("Merged payload: " + codec.decode(mergedSnapshot.payload()));

            TaskSnapshot completed = ExampleWaiter.awaitStatus(
                engine,
                key,
                TaskStatus.COMPLETED,
                Duration.ofSeconds(10)
            );

            System.out.println("Completed state: " + completed.state().value());

            List<TaskEventRecord> events = store.findEventsByTaskId(completed.id());
            System.out.println("Events: " + events.size());

            for (TaskEventRecord event : events) {
                List<TaskEventContextEntry> contexts = store.findContexts(event.id());
                long duplicateCount = contexts.stream()
                    .filter(entry -> entry.kind() == TaskEventContextKind.DUPLICATE)
                    .count();
                System.out.println("Event " + event.id().value() + " duplicate contexts: " + duplicateCount);
                for (TaskEventContextEntry entry : contexts) {
                    System.out.println("  " + entry.kind() + ": " + codec.decode(entry.payload()));
                }
            }
        }
    }

    private static TaskSnapshot awaitSnapshot(
        TaskEngine engine,
        TaskKey key,
        Predicate<TaskSnapshot> predicate,
        Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            TaskSnapshot snapshot = engine.findByKey(key);
            if (snapshot != null && predicate.test(snapshot)) {
                return snapshot;
            }
            Thread.sleep(100);
        }

        throw new IllegalStateException("Timed out waiting for task " + key.value());
    }
}
