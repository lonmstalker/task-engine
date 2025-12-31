package io.lonmstalker.task.examples.basic;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodecs;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskEngine;
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
import javax.sql.DataSource;

public final class BasicTaskEngineExample {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    private BasicTaskEngineExample() {
    }

    public static void main(String[] args) throws Exception {
        DataSource dataSource = ExampleSettings.createDataSource();
        PostgresTaskStore store = new PostgresTaskStore(dataSource);

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("basic"))
            .handler(context -> TaskResult.success())
            .contextCodec(TaskContextCodecs.stringUtf8())
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .contributesToChainContext(true)
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(ExecutorTaskDispatcher.fixedThreadPool(4, "example-worker-%d"))
            .registerDefinition(definition)
            .build()) {

            engine.start();

            TaskKey key = TaskKey.of("basic-" + UUID.randomUUID());
            TaskSubmissionResult result = engine.submit(TaskRequest.of(
                key,
                definition.type(),
                STATE_NEW,
                "hello"
            ));

            System.out.println("Submission status: " + result.status());

            TaskSnapshot completed = ExampleWaiter.awaitStatus(
                engine,
                key,
                TaskStatus.COMPLETED,
                Duration.ofSeconds(5)
            );

            System.out.println("Completed task: " + completed.id().value());
        }
    }
}
