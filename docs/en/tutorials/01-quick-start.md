# Quick Start Tutorial

Run a single task end-to-end with the Postgres store.

## 1. Create the schema

Run the SQL from:
`task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`

## 2. Define a task

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
    .type(TaskType.of("email"))
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
```

## 3. Build the engine and submit a task

```java
TaskEngine engine = TaskEngineBuilder.builder()
    .store(store)
    .registerDefinition(definition)
    .build();

engine.start();

TaskRequest<String> request = TaskRequest.of(
    TaskKey.of("email:123"),
    TaskType.of("email"),
    TaskState.of("NEW"),
    "hello"
);

TaskSubmissionResult result = engine.submit(request);
```

Shut down the engine with `engine.close()` when your app stops.
