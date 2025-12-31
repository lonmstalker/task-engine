package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.TaskEngine;
import java.util.Objects;
import org.springframework.context.SmartLifecycle;

public final class TaskEngineLifecycle implements SmartLifecycle {

    private final TaskEngine engine;
    private final boolean autoStartup;
    private volatile boolean running;

    public TaskEngineLifecycle(
        TaskEngine engine,
        boolean autoStartup
    ) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.autoStartup = autoStartup;
    }

    @Override
    public void start() {
        engine.start();
        running = true;
    }

    @Override
    public void stop() {
        engine.close();
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
}
