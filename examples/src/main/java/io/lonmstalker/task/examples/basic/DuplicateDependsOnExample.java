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
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

public final class DuplicateDependsOnExample {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    private DuplicateDependsOnExample() {
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = ExampleSettings.createDataSource();
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = TaskContextCodecs.stringUtf8();

        TaskDefinition<String> rootDefinition = TaskDefinition.<String>builder()
            .type(TaskType.of("dup-root"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        TaskDefinition<String> childDefinition = TaskDefinition.<String>builder()
            .type(TaskType.of("dup-child"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(ExecutorTaskDispatcher.fixedThreadPool(2, "example-dup-dep-%d"))
            .registerDefinition(rootDefinition)
            .registerDefinition(childDefinition)
            .build()) {

            engine.start();

            TaskKey rootKey = TaskKey.of("dup-root-" + UUID.randomUUID());
            engine.submit(new TaskRequest<>(
                rootKey,
                rootDefinition.type(),
                STATE_NEW,
                null,
                "root",
                List.of()
            ));

            TaskSnapshot rootCompleted = ExampleWaiter.awaitStatus(
                engine,
                rootKey,
                TaskStatus.COMPLETED,
                Duration.ofSeconds(5)
            );

            System.out.println("Root completed: " + rootCompleted.id().value());

            TaskSubmissionResult duplicate = engine.submit(new TaskRequest<>(
                rootKey,
                rootDefinition.type(),
                STATE_NEW,
                null,
                "root-duplicate",
                List.of()
            ));

            System.out.println("Root duplicate status: " + duplicate.status());

            List<TaskEventRecord> rootEvents = store.findEventsByTaskId(rootCompleted.id());
            System.out.println("Root events: " + rootEvents.size());

            for (TaskEventRecord event : rootEvents) {
                List<TaskEventContextEntry> contexts = store.findContexts(event.id());
                long duplicateCount = contexts.stream()
                    .filter(entry -> entry.kind() == TaskEventContextKind.DUPLICATE)
                    .count();
                System.out.println("Event " + event.id().value() + " duplicate contexts: " + duplicateCount);
                for (TaskEventContextEntry entry : contexts) {
                    System.out.println("  " + entry.kind() + ": " + codec.decode(entry.payload()));
                }
            }

            TaskKey childKey = TaskKey.of("dup-child-" + UUID.randomUUID());
            TaskLink dependsOnRoot = new TaskLink(rootCompleted.id(), TaskLinkType.DEPENDS_ON);
            engine.submit(new TaskRequest<>(
                childKey,
                childDefinition.type(),
                STATE_NEW,
                null,
                "child",
                List.of(dependsOnRoot)
            ));

            Instant start = Instant.now();
            TaskSnapshot childCompleted = ExampleWaiter.awaitStatus(
                engine,
                childKey,
                TaskStatus.COMPLETED,
                Duration.ofSeconds(5)
            );
            long elapsedMs = Duration.between(start, Instant.now()).toMillis();

            System.out.println(
                "Child completed: " + childCompleted.id().value() + " in " + elapsedMs + "ms (dependency ready)"
            );
        }
    }
}
