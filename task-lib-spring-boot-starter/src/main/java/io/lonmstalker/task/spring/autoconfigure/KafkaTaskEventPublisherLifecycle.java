package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

final class KafkaTaskEventPublisherLifecycle implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaTaskEventPublisherLifecycle.class);

    private final KafkaTaskEventPublisher publisher;
    private final Duration pollInterval;
    private final boolean autoStartup;
    private final String threadNameFormat;
    private final AtomicInteger threadIndex = new AtomicInteger(0);
    private volatile boolean running;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;

    KafkaTaskEventPublisherLifecycle(
        KafkaTaskEventPublisher publisher,
        Duration pollInterval,
        boolean autoStartup,
        String threadNameFormat
    ) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.autoStartup = autoStartup;
        this.threadNameFormat = Objects.requireNonNull(threadNameFormat, "threadNameFormat");
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(String.format(threadNameFormat, threadIndex.incrementAndGet()));
            return thread;
        });

        long delayMillis = Math.max(0, pollInterval.toMillis());
        future = executor.scheduleWithFixedDelay(this::safePublish, 0, delayMillis, TimeUnit.MILLISECONDS);
        running = true;
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        if (future != null) {
            future.cancel(false);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        publisher.close();
        running = false;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void safePublish() {
        try {
            publisher.publishOnce();
        } catch (RuntimeException e) {
            LOGGER.warn("Kafka task event publish failed", e);
        }
    }
}
