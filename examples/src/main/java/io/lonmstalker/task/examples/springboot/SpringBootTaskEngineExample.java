package io.lonmstalker.task.examples.springboot;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodecs;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.examples.common.ExampleSettings;
import io.lonmstalker.task.examples.common.ExampleWaiter;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringBootTaskEngineExample {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    public static void main(String[] args) {
        SpringApplication.run(SpringBootTaskEngineExample.class, args);
    }

    @Bean
    DataSource dataSource() {
        return ExampleSettings.createDataSource();
    }

    @Bean
    TaskStore taskStore(DataSource dataSource) {
        return new PostgresTaskStore(dataSource);
    }

    @Bean
    TaskDefinition<String> exampleTaskDefinition() {
        return TaskDefinition.<String>builder()
            .type(TaskType.of("spring-boot"))
            .handler(context -> TaskResult.success())
            .contextCodec(TaskContextCodecs.stringUtf8())
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .contributesToChainContext(true)
            .build();
    }

    @Bean
    ApplicationRunner runner(TaskEngine engine, TaskDefinition<String> definition) {
        return args -> {
            TaskKey key = TaskKey.of("spring-boot-" + UUID.randomUUID());
            engine.submit(TaskRequest.of(key, definition.type(), STATE_NEW, "hello"));

            try {
                ExampleWaiter.awaitStatus(engine, key, TaskStatus.COMPLETED, Duration.ofSeconds(5));
                System.out.println("Spring Boot task completed: " + key.value());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for task", e);
            }
        };
    }
}
