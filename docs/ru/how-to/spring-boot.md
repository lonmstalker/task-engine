# Запуск со Spring Boot Auto-Configuration

## Зависимости

Добавьте:
- `task-lib-spring-boot-starter`
- `task-lib-impl` (Postgres хранилище)
- ваш JDBC драйвер

## TaskStore и TaskDefinition

```java
@Bean
TaskStore taskStore(DataSource dataSource) {
    return new PostgresTaskStore(dataSource);
}

@Bean
TaskDefinition<String> emailTask() {
    return TaskDefinition.<String>builder()
        .type(TaskType.of("email"))
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

## Настройка свойств

```yaml
task:
  engine:
    auto-start: true
    poll-interval: PT1S
    lease-duration: PT30S
```
