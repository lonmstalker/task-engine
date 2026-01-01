package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.TaskDispatcher;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

final class TaskDispatcherReporter implements SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskDispatcherReporter.class);

    private final TaskDispatcher dispatcher;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    TaskDispatcherReporter(
        TaskDispatcher dispatcher,
        ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.meterRegistryProvider = Objects.requireNonNull(meterRegistryProvider, "meterRegistryProvider");
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (dispatcher instanceof TaskDispatcherDescriptor descriptor) {
            boolean virtualThreads = descriptor.usesVirtualThreads();
            LOGGER.info(
                "Task dispatcher configured: type={}, virtualThreads={}, parallelism={}, threadNameFormat={}",
                dispatcher.getClass().getName(),
                virtualThreads,
                descriptor.parallelism(),
                descriptor.threadNameFormat()
            );

            MeterRegistry registry = meterRegistryProvider.getIfAvailable();
            if (registry != null) {
                Gauge.builder("task.engine.dispatcher.virtual_threads", descriptor, d -> d.usesVirtualThreads() ? 1 : 0)
                    .description("Whether TaskDispatcher uses virtual threads (1 = true, 0 = false)")
                    .register(registry);
                Gauge.builder("task.engine.dispatcher.parallelism", descriptor, TaskDispatcherDescriptor::parallelism)
                    .description("Configured TaskDispatcher parallelism")
                    .register(registry);
            }
        } else {
            LOGGER.info(
                "Task dispatcher configured: type={}, virtualThreads=unknown (implement TaskDispatcherDescriptor for observability)",
                dispatcher.getClass().getName()
            );
        }
    }
}
