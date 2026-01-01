package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.impl.dispatcher.ExecutorTaskDispatcher;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

final class DefaultTaskDispatcherFactory implements TaskDispatcherFactory {

    @Override
    public TaskDispatcher create(
        TaskEngineProperties.Dispatcher properties
    ) {
        Objects.requireNonNull(properties, "properties");

        boolean virtualThreads = properties.isVirtualThreads();
        TaskDispatcher dispatcher;

        if (virtualThreads) {
            ThreadFactory factory = virtualThreadFactory(properties.getThreadNameFormat());
            ExecutorService executor = Executors.newFixedThreadPool(properties.getParallelism(), factory);
            dispatcher = new ExecutorTaskDispatcher(executor, properties.getParallelism());
        } else {
            dispatcher = ExecutorTaskDispatcher.fixedThreadPool(
                properties.getParallelism(),
                properties.getThreadNameFormat()
            );
        }

        return new DescribedTaskDispatcher(
            dispatcher,
            virtualThreads,
            properties.getParallelism(),
            properties.getThreadNameFormat()
        );
    }

    private static ThreadFactory virtualThreadFactory(
        String nameFormat
    ) {
        String format = (nameFormat == null || nameFormat.isBlank()) ? "task-worker-%d" : nameFormat;
        AtomicLong counter = new AtomicLong(0);
        return runnable -> Thread.ofVirtual()
            .name(String.format(format, counter.incrementAndGet()))
            .unstarted(runnable);
    }
}
