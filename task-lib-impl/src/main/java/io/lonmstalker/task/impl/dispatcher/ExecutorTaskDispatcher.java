package io.lonmstalker.task.impl.dispatcher;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.lonmstalker.task.api.TaskDispatcher;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Task dispatcher backed by ExecutorService.
 */
@ThreadSafe
public final class ExecutorTaskDispatcher implements TaskDispatcher {

    private final @NonNull ExecutorService executor;
    private final int parallelism;

    public ExecutorTaskDispatcher(
        @NonNull ExecutorService executor,
        int parallelism
    ) {
        Objects.requireNonNull(executor, "executor");
        Preconditions.checkArgument(parallelism > 0, "parallelism must be > 0");

        this.executor = executor;
        this.parallelism = parallelism;
    }

    public static @NonNull ExecutorTaskDispatcher fixedThreadPool(
        int parallelism,
        @NonNull String nameFormat
    ) {
        Preconditions.checkArgument(parallelism > 0, "parallelism must be > 0");
        Objects.requireNonNull(nameFormat, "nameFormat");

        ThreadFactory factory = new ThreadFactoryBuilder()
            .setNameFormat(nameFormat)
            .setDaemon(true)
            .build();

        ExecutorService executor = Executors.newFixedThreadPool(parallelism, factory);

        return new ExecutorTaskDispatcher(executor, parallelism);
    }

    @Override
    public void dispatch(
        @NonNull Runnable task
    ) {
        Objects.requireNonNull(task, "task");

        executor.submit(task);
    }

    @Override
    public int parallelism() {
        return parallelism;
    }

    @Override
    public void close() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
