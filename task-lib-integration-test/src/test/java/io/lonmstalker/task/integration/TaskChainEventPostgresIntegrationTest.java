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
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.impl.engine.TaskEngineBuilder;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task chain event Postgres integration")
class TaskChainEventPostgresIntegrationTest extends PostgresIntegrationTestBase {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_PROCESSING = TaskState.of("PROCESSING");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    @Test
    @DisplayName("shouldCreateChainEventWithContributingContexts")
    void shouldCreateChainEventWithContributingContexts() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

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
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definitionA)
            .registerDefinition(definitionC)
            .registerDefinition(definitionB)
            .build()) {

            TaskSubmissionResult createdA = engine.submit(new TaskRequest<>(
                TaskKey.of("chain-a"),
                definitionA.type(),
                STATE_NEW,
                null,
                "ctx-a",
                List.of()
            ));

            TaskSubmissionResult createdC = engine.submit(new TaskRequest<>(
                TaskKey.of("chain-c"),
                definitionC.type(),
                STATE_NEW,
                null,
                "ctx-c",
                List.of()
            ));

            TaskLink linkA = new TaskLink(createdA.snapshot().id(), TaskLinkType.DEPENDS_ON);
            TaskLink linkC = new TaskLink(createdC.snapshot().id(), TaskLinkType.DEPENDS_ON);

            TaskSubmissionResult createdB = engine.submit(new TaskRequest<>(
                TaskKey.of("chain-b"),
                definitionB.type(),
                STATE_NEW,
                null,
                "ctx-b",
                List.of(linkA, linkC)
            ));

            IntegrationTestSupport.pollOnce(engine);
            IntegrationTestSupport.pollOnce(engine);
            IntegrationTestSupport.pollOnce(engine);

            List<TaskEventRecord> events = store.findEventsByTaskId(createdB.snapshot().id());
            assertThat(events).hasSize(1);

            List<TaskEventContextEntry> contexts = store.findContexts(events.get(0).id());
            List<TaskEventContextEntry> chainContexts = contexts.stream()
                .filter(entry -> entry.kind() == TaskEventContextKind.CHAIN)
                .toList();
            List<TaskEventContextEntry> requestContexts = contexts.stream()
                .filter(entry -> entry.kind() == TaskEventContextKind.REQUEST)
                .toList();

            assertThat(chainContexts).anyMatch(entry -> entry.taskId().equals(createdA.snapshot().id()));
            assertThat(chainContexts).anyMatch(entry -> entry.taskId().equals(createdB.snapshot().id()));
            assertThat(chainContexts).noneMatch(entry -> entry.taskId().equals(createdC.snapshot().id()));

            assertThat(requestContexts).anyMatch(entry -> entry.taskId().equals(createdA.snapshot().id()));
            assertThat(requestContexts).anyMatch(entry -> entry.taskId().equals(createdB.snapshot().id()));
            assertThat(requestContexts).anyMatch(entry -> entry.taskId().equals(createdC.snapshot().id()));
        }
    }

    @Test
    @DisplayName("shouldEmitEventOnlyOnTerminalState")
    void shouldEmitEventOnlyOnTerminalState() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        TaskContextCodec<String> codec = new IntegrationFixtures.StringCodec();

        TaskDefinition<String> definition = TaskDefinition.<String>builder()
            .type(TaskType.of("multi-state"))
            .handler(context -> TaskResult.success())
            .contextCodec(codec)
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_PROCESSING, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();

        try (TaskEngine engine = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(new IntegrationFixtures.DirectTaskDispatcher())
            .registerDefinition(definition)
            .build()) {

            TaskSubmissionResult created = engine.submit(new TaskRequest<>(
                TaskKey.of("multi-state"),
                definition.type(),
                STATE_NEW,
                null,
                "ctx",
                List.of()
            ));

            IntegrationTestSupport.pollOnce(engine);

            List<TaskEventRecord> eventsAfterFirst = store.findEventsByTaskId(created.snapshot().id());
            assertThat(eventsAfterFirst).isEmpty();

            IntegrationTestSupport.pollOnce(engine);

            List<TaskEventRecord> eventsAfterSecond = store.findEventsByTaskId(created.snapshot().id());
            assertThat(eventsAfterSecond).hasSize(1);
            assertThat(engine.findByKey(created.snapshot().key()).status()).isEqualTo(TaskStatus.COMPLETED);
        }
    }
}
