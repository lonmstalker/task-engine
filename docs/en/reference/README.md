# Reference

## Modules

- `task-lib-api`: public API types.
- `task-lib-impl`: task engine implementation and Postgres store.
- `task-lib-kafka`: Kafka outbox publisher and Postgres outbox store.
- `task-lib-spring-boot-starter`: Spring Boot auto-configuration.
- `task-lib-integration-test`: integration tests and JMH benchmarks.

## Core API types

- `TaskEngine`: submit tasks, query by id/key, start/close.
- `TaskDefinition`: ties together `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
  `TaskStateMachine`, and `RetryPolicy`.
- `TaskRequest`: idempotency key, task type, initial state, context, and links.
- `TaskStatus`: `PENDING`, `RUNNING`, `WAITING_RETRY`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `TaskStateMachine`: controls business state progression.
- `TaskEventStore`: records request/duplicate contexts and creates chain events.
- `TaskEventOutboxStore`: claims and publishes Kafka outbox records.

## Optional store extensions

- `TaskLeaseStore`: safe updates guarded by lease owner/expiry.
- `TaskMaintenanceStore`: purge terminal tasks after a retention window.
- `TaskStoreStatsProvider`: task backlog metrics.
- `TaskEventOutboxAdminStore`: publish attempt tracking + dead-lettering.
- `TaskEventOutboxBatchStore`: batch context loading.
- `TaskEventOutboxMaintenanceStore`: purge published/dead-lettered outbox events.
- `TaskEventOutboxStatsProvider`: outbox backlog metrics.

## Spring Boot properties

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

## Postgres schema

- Location: `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Tables: `task_tasks`, `task_links`, `task_event_outbox`, `task_event_contexts`.
- Incremental updates: `schema-update.sql`.

## Kafka payload

- `TaskEventEnvelope` with `TaskEventRecord` plus `TaskEventContextEntry` list.
- Default serializer: `JacksonTaskEventMessageCodec`.
