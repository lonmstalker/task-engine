package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.impl.engine.TaskEngineBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(TaskEngine.class)
@EnableConfigurationProperties(TaskEngineProperties.class)
@ConditionalOnProperty(prefix = "task.engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TaskEngineAutoConfiguration {

    @Bean
    @ConditionalOnBean(TaskStore.class)
    @ConditionalOnMissingBean
    public TaskDispatcherFactory taskDispatcherFactory() {
        return new DefaultTaskDispatcherFactory();
    }

    @Bean
    @ConditionalOnBean(TaskStore.class)
    @ConditionalOnMissingBean
    public TaskDispatcher taskDispatcher(
        TaskDispatcherFactory factory,
        TaskEngineProperties properties
    ) {
        return factory.create(properties.getDispatcher());
    }

    @Bean
    @ConditionalOnBean(TaskStore.class)
    @ConditionalOnMissingBean
    public TaskEngine taskEngine(
        TaskStore store,
        TaskDispatcher dispatcher,
        ObjectProvider<Clock> clockProvider,
        ObjectProvider<TaskDefinition<?>> definitionsProvider,
        TaskEngineProperties properties
    ) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(dispatcher, "dispatcher");

        Clock clock = clockProvider.getIfAvailable(Clock::systemUTC);
        List<TaskDefinition<?>> definitions = definitionsProvider.orderedStream().toList();

        TaskEngineBuilder builder = TaskEngineBuilder.builder()
            .store(store)
            .dispatcher(dispatcher)
            .clock(clock)
            .pollInterval(properties.getPollInterval())
            .leaseDuration(properties.getLeaseDuration())
            .recoveryInterval(properties.getRecoveryInterval())
            .claimBatchSize(properties.getClaimBatchSize());

        String engineId = properties.getEngineId();
        if (engineId != null && !engineId.isBlank()) {
            builder.engineId(engineId);
        }

        for (TaskDefinition<?> definition : definitions) {
            builder.registerDefinition(definition);
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnBean(TaskEngine.class)
    public TaskEngineLifecycle taskEngineLifecycle(
        TaskEngine engine,
        TaskEngineProperties properties
    ) {
        return new TaskEngineLifecycle(engine, properties.isAutoStart());
    }

    @Bean
    @ConditionalOnBean(TaskDispatcher.class)
    public TaskDispatcherReporter taskDispatcherReporter(
        TaskDispatcher dispatcher,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        return new TaskDispatcherReporter(dispatcher, meterRegistryProvider);
    }
}
