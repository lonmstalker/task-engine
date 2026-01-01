package io.lonmstalker.task.spring.autoconfigure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task.engine")
public class TaskEngineProperties {

    private boolean enabled = true;
    private boolean autoStart = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private Duration recoveryInterval = Duration.ofSeconds(10);
    private int claimBatchSize = 100;
    private String engineId;
    private final Dispatcher dispatcher = new Dispatcher();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public Duration getPollInterval() {
        return pollInterval;
    }

    public void setPollInterval(Duration pollInterval) {
        this.pollInterval = requirePositiveDuration("pollInterval", pollInterval);
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = requireNonNegativeDuration("leaseDuration", leaseDuration);
    }

    public Duration getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(Duration recoveryInterval) {
        this.recoveryInterval = requirePositiveDuration("recoveryInterval", recoveryInterval);
    }

    public int getClaimBatchSize() {
        return claimBatchSize;
    }

    public void setClaimBatchSize(int claimBatchSize) {
        this.claimBatchSize = requirePositiveInt("claimBatchSize", claimBatchSize);
    }

    public String getEngineId() {
        return engineId;
    }

    public void setEngineId(String engineId) {
        this.engineId = engineId;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public static class Dispatcher {
        private boolean virtualThreads = true;
        private int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        private String threadNameFormat = "task-worker-%d";

        public boolean isVirtualThreads() {
            return virtualThreads;
        }

        public void setVirtualThreads(boolean virtualThreads) {
            this.virtualThreads = virtualThreads;
        }

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = requirePositiveInt("dispatcher.parallelism", parallelism);
        }

        public String getThreadNameFormat() {
            return threadNameFormat;
        }

        public void setThreadNameFormat(String threadNameFormat) {
            this.threadNameFormat = normalizeThreadNameFormat(threadNameFormat, "task-worker-%d");
        }
    }

    private static Duration requirePositiveDuration(
        String name,
        Duration value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be set");
        }
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static Duration requireNonNegativeDuration(
        String name,
        Duration value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must be set");
        }
        if (value.isNegative()) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }

    private static int requirePositiveInt(
        String name,
        int value
    ) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
        return value;
    }

    private static String normalizeThreadNameFormat(
        String value,
        String fallback
    ) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
