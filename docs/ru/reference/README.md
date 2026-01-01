# Справка

## Модули

- `task-lib-api`: публичные типы API.
- `task-lib-impl`: реализация движка и Postgres хранилище.
- `task-lib-kafka`: outbox publisher для Kafka и Postgres outbox store.
- `task-lib-spring-boot-starter`: auto-configuration для Spring Boot.
- `task-lib-integration-test`: интеграционные тесты и JMH бенчмарки.

## Основные типы API

- `TaskEngine`: отправка задач, поиск по id/key, старт/остановка.
- `TaskDefinition`: связывает `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
  `TaskStateMachine` и `RetryPolicy`.
- `TaskRequest`: ключ идемпотентности, тип задачи, начальное состояние, контекст и связи.
- `TaskStatus`: `PENDING`, `RUNNING`, `WAITING_RETRY`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `TaskStateMachine`: управление бизнес состояниями.
- `TaskEventStore`: хранит контексты запросов/дубликатов и создает события цепочек.
- `TaskEventOutboxStore`: выборка и публикация outbox записей для Kafka.

## Дополнительные расширения хранилищ

- `TaskLeaseStore`: безопасные обновления по lease owner/expiry.
- `TaskMaintenanceStore`: очистка терминальных задач по retention окну.
- `TaskStoreStatsProvider`: метрики backlog по задачам.
- `TaskEventOutboxAdminStore`: попытки публикации + dead-letter.
- `TaskEventOutboxBatchStore`: пакетная загрузка контекстов.
- `TaskEventOutboxMaintenanceStore`: очистка опубликованных/dead-letter событий.
- `TaskEventOutboxStatsProvider`: метрики backlog по outbox.

## Свойства Spring Boot

Префикс `task.engine`:

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

Префикс `task.kafka`:

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

## Postgres схема

- Путь: `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Таблицы: `task_tasks`, `task_links`, `task_event_outbox`, `task_event_contexts`.
- Инкрементальные обновления: `schema-update.sql`.

## Kafka payload

- `TaskEventEnvelope` с `TaskEventRecord` и списком `TaskEventContextEntry`.
- Сериализатор по умолчанию: `JacksonTaskEventMessageCodec`.
