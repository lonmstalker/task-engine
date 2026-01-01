# Explanation

## TaskDefinition and state machine

`TaskDefinition` binds together `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
`TaskStateMachine`, and `RetryPolicy`.

- `TaskContextCodec` encodes the request context into `TaskPayload` for storage and
  decodes it before handler execution.
- `TaskContextMerger` is used only for duplicate submissions while a task is `PENDING`
  or `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` is used on initial submit and on duplicates
  to decide whether to apply, ignore, or reject the incoming state.
- On success the engine uses the handler-provided next state (if any) or
  `stateMachine.nextState(current)`. Invalid transitions result in failure and follow
  the retry policy.

## Idempotency and duplicates

`TaskKey` is unique per task. On duplicate submits, the engine updates state via
`TaskStateMachine.resolveExternalState` and optionally merges context (only for `PENDING`
or `WAITING_RETRY`). The submission result tells whether the task was created, updated,
or a pure duplicate.

## Execution model

The engine polls the store, claims tasks with leases, and dispatches each task via
`TaskDispatcher`. A recovery loop clears expired leases so tasks can be re-claimed.

## Business state vs execution status

`TaskState` is domain-specific and advanced by a state machine. `TaskStatus` is execution
state (`PENDING`, `RUNNING`, `COMPLETED`, etc). A handler can override the next state via
`TaskResult.success(nextState)`.

## Chain events and context aggregation

A `CHAIN_COMPLETED` event is emitted when the terminal task reaches a terminal state, or
when it fails. For non-failed tasks, the event is delayed until there are no `DEPENDS_ON`
dependents. Contexts include:
- `REQUEST`: the initial submit payload.
- `DUPLICATE`: payloads from duplicate submits.
- `CHAIN`: payloads from tasks in the chain that opted in via `contributesToChainContext`.

## Dependency failures

`DEPENDS_ON` requires the dependency to complete successfully. If a dependency fails,
dependent tasks are cancelled with `TaskStatus.CANCELLED` and an error of type
`DependencyFailed`. Submissions that reference already failed dependencies are cancelled
immediately.

## Lease-safe updates

Stores that implement `TaskLeaseStore` guard updates by lease owner and expiry, preventing
stale workers from overwriting newer task state after lease loss.

## Outbox retries and dead letters

Kafka outbox publishing uses a failure backoff and a maximum publish attempt count. When
attempts are exhausted, events are marked as dead-lettered and skipped by the claim query.

## Outbox publishing

Events are written to `task_event_outbox` and published by `KafkaTaskEventPublisher`. The
publisher claims events with a lease, serializes them, publishes to Kafka, and marks them
as published. Failures release the lease for retry.

## Maintenance and metrics

Postgres stores provide retention helpers (`TaskMaintenanceStore`,
`TaskEventOutboxMaintenanceStore`) and stats providers for backlog gauges
(`TaskStoreStatsProvider`, `TaskEventOutboxStatsProvider`). When using the Spring Boot
starter with Micrometer, these gauges are exported automatically.
