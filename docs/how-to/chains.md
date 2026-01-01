# Create Dependent Tasks and Emit Events

## Create dependent tasks (DEPENDS_ON)

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

A dependent task is claimed only after all dependencies are `COMPLETED`.

## Create dependents from a handler

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

## Complete tasks and emit events

- A task becomes `COMPLETED` when its `TaskState` is terminal.
- `TaskResult.success()` advances to `stateMachine.nextState(current)`.
- `TaskResult.success(nextState)` overrides the next state.
- `TaskResult.failure(error)` marks the task `FAILED` (with retries if allowed).
- Chain events are emitted only when a `TaskEventStore` is configured.
  `PostgresTaskStore` provides it automatically; for custom stores, pass
  `eventStore(...)` to `TaskEngineBuilder`.
