# Документация Task Lib (Diataxis)

Этот набор документации следует модели Diataxis: учебник, практические руководства, справка и объяснение.

## Учебник: первый запуск задачи

Этот пример запускает одну задачу от начала до конца с Postgres хранилищем.

1. Создайте схему БД из `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
2. Соберите определение задачи (машина состояний + обработчик + кодеки).
3. Соберите движок, отправьте задачу и запустите обработку.

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

Остановите движок через `engine.close()` при завершении приложения.

## Руководства: как сделать

### Запуск со Spring Boot auto-configuration

- Добавьте модули: `task-lib-spring-boot-starter`, `task-lib-impl` (Postgres) и ваш JDBC драйвер.
- Определите бин `TaskStore` (для Postgres это `PostgresTaskStore`).
- Зарегистрируйте бины `TaskDefinition`.

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

### Публикация событий цепочек в Kafka

- Проверьте, что таблицы outbox созданы (они есть в Postgres схеме).
- Включите Kafka publisher и задайте `topic` и `bootstrap-servers`.

```yaml
task:
  kafka:
    enabled: true
    topic: "task-events"
    bootstrap-servers:
      - "localhost:9092"
```

Дополнительные настройки:
- Переопределите `TaskEventMessageCodec` (по умолчанию Jackson).
- Переопределите `TaskEventKeyProvider` (ключ по умолчанию = task id).
- Задайте свой `TaskEventOutboxStore`, если Postgres не используется.

### Явная обработка дубликатов

- `TaskKey` - ключ идемпотентности. Повторная отправка возвращает `TaskSubmissionStatus`:
  `CREATED`, `UPDATED` или `DUPLICATE`.
- `TaskContextMerger` объединяет контекст только пока задача в `PENDING` или `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` решает, применять, игнорировать или отклонять входящее состояние.

### Настройка параллелизма и восстановления

- Опрос: `task.engine.poll-interval`, `task.engine.claim-batch-size`.
- Лизы: `task.engine.lease-duration`, `task.engine.recovery-interval`.
- Диспетчер: `task.engine.dispatcher.virtual-threads`, `task.engine.dispatcher.parallelism`,
  `task.engine.dispatcher.thread-name-format`.

### Создание зависимых задач (цепочки)

- Используйте `TaskLink` с `TaskLinkType.DEPENDS_ON` в `TaskRequest.links`.
- Зависимая задача будет взята в работу только после `COMPLETED` всех зависимостей.

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

Также можно создавать зависимую задачу прямо в обработчике, используя id текущей задачи:

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

### Завершение задачи и события

- Задача становится `COMPLETED`, когда ее `TaskState` терминальный.
- `TaskResult.success()` двигает в `stateMachine.nextState(current)`, а
  `TaskResult.success(nextState)` позволяет задать состояние явно.
- `TaskResult.failure(error)` переводит в `FAILED` (с повторами, если они разрешены).
- События цепочек создаются только если настроен `TaskEventStore`.
  `PostgresTaskStore` включает его автоматически; для кастомных хранилищ
  передайте `eventStore(...)` в `TaskEngineBuilder`.

## Справка

### Модули

- `task-lib-api`: публичные типы API.
- `task-lib-impl`: реализация движка и Postgres хранилище.
- `task-lib-kafka`: outbox publisher для Kafka и Postgres outbox store.
- `task-lib-spring-boot-starter`: auto-configuration для Spring Boot.
- `task-lib-integration-test`: интеграционные тесты и JMH бенчмарки.

### Основные типы API

- `TaskEngine`: отправка задач, поиск по id/key, старт/остановка.
- `TaskDefinition`: связывает `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
  `TaskStateMachine` и `RetryPolicy`.
- `TaskRequest`: ключ идемпотентности, тип задачи, начальное состояние, контекст и связи.
- `TaskStatus`: `PENDING`, `RUNNING`, `WAITING_RETRY`, `COMPLETED`, `FAILED`, `CANCELLED`.
- `TaskStateMachine`: управление бизнес состояниями.
- `TaskEventStore`: хранит контексты запросов/дубликатов и создает события цепочек.
- `TaskEventOutboxStore`: выборка и публикация outbox записей для Kafka.

### Дополнительные расширения хранилищ

- `TaskLeaseStore`: безопасные обновления по lease owner/expiry.
- `TaskMaintenanceStore`: очистка терминальных задач по retention окну.
- `TaskStoreStatsProvider`: метрики backlog по задачам.
- `TaskEventOutboxAdminStore`: попытки публикации + dead-letter.
- `TaskEventOutboxBatchStore`: пакетная загрузка контекстов.
- `TaskEventOutboxMaintenanceStore`: очистка опубликованных/dead-letter событий.
- `TaskEventOutboxStatsProvider`: метрики backlog по outbox.

### Spring Boot свойства

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

### Postgres схема

- Путь: `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Таблицы: `task_tasks`, `task_links`, `task_event_outbox`, `task_event_contexts`.
- Инкрементальные обновления: `schema-update.sql`.

### Kafka payload

- `TaskEventEnvelope` с `TaskEventRecord` и списком `TaskEventContextEntry`.
- Сериализатор по умолчанию: `JacksonTaskEventMessageCodec`.

## Объяснение

### Идемпотентность и дубликаты

`TaskKey` уникален для задачи. При повторной отправке движок обновляет состояние через
`TaskStateMachine.resolveExternalState` и при необходимости объединяет контекст (только для `PENDING`
или `WAITING_RETRY`). Результат отправки показывает, была ли задача создана, обновлена или это чистый
дубликат.

### Модель исполнения

Движок опрашивает хранилище, берет задачи в аренду (lease) и отправляет их в `TaskDispatcher`.
Цикл восстановления снимает просроченные аренды, чтобы задачи можно было забрать снова.

### Бизнес состояние и статус исполнения

`TaskState` - бизнес состояние, управляется машиной состояний. `TaskStatus` - статус исполнения
(`PENDING`, `RUNNING`, `COMPLETED` и т.д.). Обработчик может переопределить следующий state через
`TaskResult.success(nextState)`.

### TaskDefinition и машина состояний

- `TaskDefinition` связывает `TaskHandler`, `TaskContextCodec`, `TaskContextMerger`,
  `TaskStateMachine` и `RetryPolicy`.
- `TaskContextCodec` кодирует контекст в `TaskPayload` для хранения и
  декодирует его перед выполнением обработчика.
- `TaskContextMerger` вызывается только для дубликатов, когда задача в `PENDING`
  или `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` используется при первичной отправке и для
  дубликатов, чтобы решить, применять, игнорировать или отклонять входящее состояние.
- При успехе движок использует состояние из обработчика (если оно есть) либо
  `stateMachine.nextState(current)`. Невалидные переходы приводят к ошибке и
  обрабатываются политикой повторов.

### События цепочек и агрегация контекста

Событие `CHAIN_COMPLETED` создается, когда терминальная задача достигла терминального состояния
или завершилась с ошибкой. Для успешных задач событие откладывается, пока нет зависимых
`DEPENDS_ON` задач. Контексты включают:
- `REQUEST`: payload исходного запроса.
- `DUPLICATE`: payload повторных запросов.
- `CHAIN`: payload задач в цепочке, которые включили `contributesToChainContext`.

### Ошибки зависимостей

`DEPENDS_ON` требует успешного завершения зависимости. Если зависимость завершилась ошибкой,
зависимые задачи помечаются как `CANCELLED` с ошибкой типа `DependencyFailed`. Задачи, которые
ссылаются на уже упавшие зависимости, отменяются при создании.

### Lease‑безопасные обновления

Хранилища, реализующие `TaskLeaseStore`, защищают обновления по lease owner/expiry и не дают
устаревшим воркерам перезаписывать состояние.

### Повторы outbox и dead‑letter

Для Kafka‑публикации используются backoff и максимальное число попыток. При превышении лимита событие
маркируется как dead‑letter и больше не забирается claim запросом.

### Публикация outbox

События пишутся в `task_event_outbox` и публикуются `KafkaTaskEventPublisher`. Паблишер берет
события в аренду, сериализует, публикует в Kafka и помечает как опубликованные. При ошибке аренда
освобождается для повторной попытки.

### Maintenance и метрики

Postgres‑хранилища предоставляют операции retention (`TaskMaintenanceStore`,
`TaskEventOutboxMaintenanceStore`) и статистику backlog (`TaskStoreStatsProvider`,
`TaskEventOutboxStatsProvider`). В стартере Spring Boot эти метрики экспортируются через Micrometer.
