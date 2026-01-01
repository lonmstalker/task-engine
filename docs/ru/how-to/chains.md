# Зависимые задачи и события

## Создание зависимых задач (DEPENDS_ON)

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

Зависимая задача будет взята в работу только после `COMPLETED` всех зависимостей.

## Создание зависимых задач из обработчика

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

## Завершение задачи и события

- Задача становится `COMPLETED`, когда ее `TaskState` терминальный.
- `TaskResult.success()` двигает в `stateMachine.nextState(current)`.
- `TaskResult.success(nextState)` позволяет задать состояние явно.
- `TaskResult.failure(error)` переводит в `FAILED` (с повторами, если они разрешены).
- События цепочек создаются только если настроен `TaskEventStore`.
  `PostgresTaskStore` включает его автоматически; для кастомных хранилищ
  передайте `eventStore(...)` в `TaskEngineBuilder`.
