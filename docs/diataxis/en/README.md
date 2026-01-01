# Task Lib Documentation (Diataxis)

This documentation set follows the Diataxis model: tutorial, how-to guides, reference, and explanation.

## Tutorial: run your first task

This walkthrough runs a single task end-to-end with the Postgres store.

1. Create the database schema from `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
2. Build a task definition (state machine + handler + codecs).
3. Build the engine, submit a task, and start processing.

```java
import io.lonmstalker.task.api.*;
import io.lonmstalker.task.api.model.*;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.impl.engine.TaskEngineBuilder;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;

import javax.sql.DataSource;
import java.util.List;

DataSource dataSource = /* create DataSource */;
TaskStore store = new PostgresTaskStore(dataSource);

TaskDefinition<String> definition = TaskDefinition.<String>builder()
    .type(new TaskType("email"))
    .handler(ctx -> TaskResult.success())
    .contextCodec(TaskContextCodecs.stringUtf8())
    .contextMerger((existing, incoming) -> existing + "," + incoming)
    .stateMachine(OrderedStateMachine.of(List.of(
        TaskState.of("NEW"),
        TaskState.of("DONE")
    )))
    .retryPolicy(RetryPolicies.none())
    .contributesToChainContext(true)
    .build();

TaskEngine engine = TaskEngineBuilder.builder()
    .store(store)
    .registerDefinition(definition)
    .build();

engine.start();

TaskRequest<String> request = TaskRequest.of(
    new TaskKey("email:123"),
    new TaskType("email"),
    TaskState.of("NEW"),
    "hello"
);

TaskSubmissionResult result = engine.submit(request);
```

Shut down the engine with `engine.close()` when your app stops.

## How-to guides

### Run with Spring Boot auto-configuration

- Add modules: `task-lib-spring-boot-starter`, `task-lib-impl` (Postgres), and your JDBC driver.
- Provide a `TaskStore` bean (for Postgres, `PostgresTaskStore`).
- Register `TaskDefinition` beans.

```java
@Bean
TaskStore taskStore(DataSource dataSource) {
    return new PostgresTaskStore(dataSource);
}

@Bean
TaskDefinition<String> emailTask() {
    return TaskDefinition.<String>builder()
        .type(new TaskType("email"))
        .handler(ctx -> TaskResult.success())
        .contextCodec(TaskContextCodecs.stringUtf8())
        .contextMerger((existing, incoming) -> existing)
        .stateMachine(OrderedStateMachine.of(List.of(
            TaskState.of("NEW"), TaskState.of("DONE")
        )))
        .retryPolicy(RetryPolicies.none())
        .contributesToChainContext(true)
        .build();
}
```

```yaml
task:
  engine:
    auto-start: true
    poll-interval: PT1S
    lease-duration: PT30S
```

### Publish chain events to Kafka

- Ensure outbox tables are present (they are in the Postgres schema).
- Enable the Kafka publisher and provide `topic` and `bootstrap-servers`.

```yaml
task:
  kafka:
    enabled: true
    topic: "task-events"
    bootstrap-servers:
      - "localhost:9092"
```

Optional customizations:
- Override `TaskEventMessageCodec` (defaults to Jackson).
- Override `TaskEventKeyProvider` (default key = task id).
- Provide a custom `TaskEventOutboxStore` if not using Postgres.

### Handle duplicates explicitly

- `TaskKey` is the idempotency key. Submitting the same key results in `TaskSubmissionStatus` of `CREATED`, `UPDATED`, or `DUPLICATE`.
- `TaskContextMerger` merges contexts only while the task is `PENDING` or `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` decides whether incoming states are applied, ignored, or rejected.

### Tune concurrency and recovery

- Polling: `task.engine.poll-interval`, `task.engine.claim-batch-size`.
- Leases: `task.engine.lease-duration`, `task.engine.recovery-interval`.
- Dispatcher: `task.engine.dispatcher.virtual-threads`, `task.engine.dispatcher.parallelism`,
  `task.engine.dispatcher.thread-name-format`.

### Create dependent tasks (chains)

- Use `TaskLink` with `TaskLinkType.DEPENDS_ON` in `TaskRequest.links`.
- A dependent task is claimed only after all dependencies are `COMPLETED`.

```java
TaskSubmissionResult parent = engine.submit(TaskRequest.of(
    TaskKey.of("parent:1"),
    TaskType.of("parent"),
    TaskState.of("NEW"),
    "parent-ctx"
));

TaskLink dependsOnParent = new TaskLink(parent.snapshot().id(), TaskLinkType.DEPENDS_ON);

engine.submit(new TaskRequest<>(
    TaskKey.of("child:1"),
    TaskType.of("child"),
    TaskState.of("NEW"),
    null,
    "child-ctx",
    List.of(dependsOnParent)
));
```

You can also submit dependents from inside a handler using the current task id:

```java
TaskDefinition<String> parentDefinition = TaskDefinition.<String>builder()
    .type(TaskType.of("parent"))
    .handler(ctx -> {
        TaskId parentId = ctx.snapshot().id();
        TaskLink link = new TaskLink(parentId, TaskLinkType.DEPENDS_ON);
        engine.submit(new TaskRequest<>(
            TaskKey.of("child:" + parentId.value()),
            TaskType.of("child"),
            TaskState.of("NEW"),
            null,
            "child-ctx",
            List.of(link)
        ));
        return TaskResult.success(TaskState.of("DONE"));
    })
    .build();
```

### Complete a task and emit events

- A task becomes `COMPLETED` when its `TaskState` is terminal.
- `TaskResult.success()` moves to `stateMachine.nextState(current)`; use
  `TaskResult.success(nextState)` to override it.
- `TaskResult.failure(error)` marks the task `FAILED` (with retries if allowed).
- Chain events are emitted only when a `TaskEventStore` is configured.
  `PostgresTaskStore` provides this automatically; for custom stores, pass
  `eventStore(...)` to `TaskEngineBuilder`.

## Reference

### Modules

- `task-lib-api`: public API types.
- `task-lib-impl`: task engine implementation and Postgres store.
- `task-lib-kafka`: Kafka outbox publisher and Postgres outbox store.
- `task-lib-spring-boot-starter`: Spring Boot auto-configuration.
- `task-lib-integration-test`: integration tests and JMH benchmarks.

### Core API types

- `TaskEngine`: submit tasks, query by id/key, start/close.
- `TaskDefinition`: ties together `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
  `TaskStateMachine`, and `RetryPolicy`.
- `TaskRequest`: idempotency key, task type, initial state, context, and links.
- `TaskStatus`: `PENDING`, `RUNNING`, `WAITING_RETRY`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `TaskStateMachine`: controls business state progression.
- `TaskEventStore`: records request/duplicate contexts and creates chain events.
- `TaskEventOutboxStore`: claims and publishes Kafka outbox records.

### Optional store extensions

- `TaskLeaseStore`: safe updates guarded by lease owner/expiry.
- `TaskMaintenanceStore`: purge terminal tasks after a retention window.
- `TaskStoreStatsProvider`: task backlog metrics.
- `TaskEventOutboxAdminStore`: publish attempt tracking + dead-lettering.
- `TaskEventOutboxBatchStore`: batch context loading.
- `TaskEventOutboxMaintenanceStore`: purge published/dead-lettered outbox events.
- `TaskEventOutboxStatsProvider`: outbox backlog metrics.

### Spring Boot properties

Prefix `task.engine`:

| Property | Default |
| --- | --- |
| `enabled` | `true` |
| `auto-start` | `true` |
| `poll-interval` | `PT1S` |
| `lease-duration` | `PT30S` |
| `recovery-interval` | `PT10S` |
| `claim-batch-size` | `100` |
| `engine-id` | none |
| `dispatcher.virtual-threads` | `true` |
| `dispatcher.parallelism` | CPU count |
| `dispatcher.thread-name-format` | `task-worker-%d` |

Prefix `task.kafka`:

| Property | Default |
| --- | --- |
| `enabled` | `false` |
| `auto-start` | `true` |
| `poll-interval` | `PT1S` |
| `lease-duration` | `PT30S` |
| `batch-size` | `100` |
| `publish-timeout` | `PT30S` |
| `failure-backoff` | `PT5S` |
| `max-publish-attempts` | `10` |
| `bootstrap-servers` | empty |
| `producer-properties.*` | empty |
| `topic` | none |
| `lease-owner` | random |
| `thread-name-format` | `task-kafka-publisher-%d` |

### Postgres schema

- Location: `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Tables: `task_tasks`, `task_links`, `task_event_outbox`, `task_event_contexts`.
- Incremental updates: `schema-update.sql`.

### Kafka payload

- `TaskEventEnvelope` with `TaskEventRecord` plus `TaskEventContextEntry` list.
- Default serializer: `JacksonTaskEventMessageCodec`.

## Explanation

### Idempotency and duplicates

`TaskKey` is unique per task. On duplicate submits, the engine updates state via
`TaskStateMachine.resolveExternalState` and optionally merges context (only for `PENDING` or
`WAITING_RETRY`). The submission result tells whether the task was created, updated, or a pure duplicate.

### Execution model

The engine polls the store, claims tasks with leases, and dispatches each task via `TaskDispatcher`.
A recovery loop clears expired leases so tasks can be re-claimed.

### Business state vs execution status

`TaskState` is domain-specific and advanced by a state machine. `TaskStatus` is execution state
(`PENDING`, `RUNNING`, `COMPLETED`, etc). A handler can override the next state via
`TaskResult.success(nextState)`.

### TaskDefinition and state machine

- `TaskDefinition` ties together `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
  `TaskStateMachine`, and `RetryPolicy`.
- `TaskContextCodec` encodes the request context into `TaskPayload` for storage and
  decodes it before handler execution.
- `TaskContextMerger` runs only for duplicate submissions while a task is `PENDING`
  or `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` is used for the initial submit and for duplicates
  to decide whether to apply, ignore, or reject the incoming state.
- On success the engine uses the handler-provided next state (if any) or
  `stateMachine.nextState(current)`. Invalid transitions result in failure and follow
  the retry policy.

### Chain events and context aggregation

A `CHAIN_COMPLETED` event is emitted when the terminal task reaches a terminal state, or when it fails.
For non-failed tasks, the event is delayed until there are no `DEPENDS_ON` dependents. Contexts include:
- `REQUEST`: the initial submit payload.
- `DUPLICATE`: payloads from duplicate submits.
- `CHAIN`: payloads from tasks in the chain that opted in via `contributesToChainContext`.

### Dependency failures

`DEPENDS_ON` requires the dependency to complete successfully. If a dependency fails, dependent tasks are
cancelled with `TaskStatus.CANCELLED` and an error of type `DependencyFailed`. Submissions that reference
already failed dependencies are cancelled immediately.

### Lease-safe updates

Stores that implement `TaskLeaseStore` guard updates by lease owner and expiry, preventing stale workers
from overwriting newer task state after lease loss.

### Outbox retries and dead letters

Kafka outbox publishing uses a failure backoff and a maximum publish attempt count. When attempts are
exhausted, events are marked as dead-lettered and skipped by the claim query.

### Outbox publishing

Events are written to `task_event_outbox` and published by `KafkaTaskEventPublisher`. The publisher
claims events with a lease, serializes them, publishes to Kafka, and marks them as published. Failures
release the lease for retry.

### Maintenance and metrics

Postgres stores provide retention helpers (`TaskMaintenanceStore`, `TaskEventOutboxMaintenanceStore`) and
stats providers for backlog gauges (`TaskStoreStatsProvider`, `TaskEventOutboxStatsProvider`). When using
the Spring Boot starter with Micrometer, these gauges are exported automatically.
