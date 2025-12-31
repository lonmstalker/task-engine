package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.TaskDispatcher;
import java.util.Objects;

final class DescribedTaskDispatcher implements TaskDispatcher, TaskDispatcherDescriptor {

    private final TaskDispatcher delegate;
    private final boolean virtualThreads;
    private final int parallelism;
    private final String threadNameFormat;

    DescribedTaskDispatcher(
        TaskDispatcher delegate,
        boolean virtualThreads,
        int parallelism,
        String threadNameFormat
    ) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.virtualThreads = virtualThreads;
        this.parallelism = parallelism;
        this.threadNameFormat = threadNameFormat;
    }

    @Override
    public void dispatch(Runnable task) {
        delegate.dispatch(task);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public boolean usesVirtualThreads() {
        return virtualThreads;
    }

    @Override
    public int parallelism() {
        return parallelism;
    }

    @Override
    public String threadNameFormat() {
        return threadNameFormat;
    }
}
