package io.lonmstalker.task.spring.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "task.kafka")
public class TaskKafkaProperties {

    private boolean enabled = false;
    private boolean autoStart = true;
    private Duration pollInterval = Duration.ofSeconds(1);
    private Duration leaseDuration = Duration.ofSeconds(30);
    private int batchSize = 100;
    private Duration publishTimeout = Duration.ofSeconds(30);
    private Duration failureBackoff = Duration.ofSeconds(5);
    private int maxPublishAttempts = 10;
    private List<String> bootstrapServers = List.of();
    private Map<String, String> producerProperties = new LinkedHashMap<>();
    private String topic;
    private String leaseOwner;
    private String threadNameFormat = "task-kafka-publisher-%d";

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
        this.leaseDuration = requirePositiveDuration("leaseDuration", leaseDuration);
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = requirePositiveInt("batchSize", batchSize);
    }

    public Duration getPublishTimeout() {
        return publishTimeout;
    }

    public void setPublishTimeout(Duration publishTimeout) {
        this.publishTimeout = requirePositiveDuration("publishTimeout", publishTimeout);
    }

    public Duration getFailureBackoff() {
        return failureBackoff;
    }

    public void setFailureBackoff(Duration failureBackoff) {
        this.failureBackoff = requireNonNegativeDuration("failureBackoff", failureBackoff);
    }

    public int getMaxPublishAttempts() {
        return maxPublishAttempts;
    }

    public void setMaxPublishAttempts(int maxPublishAttempts) {
        this.maxPublishAttempts = requireNonNegativeInt("maxPublishAttempts", maxPublishAttempts);
    }

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(List<String> bootstrapServers) {
        this.bootstrapServers = normalizeList(bootstrapServers);
    }

    public Map<String, String> getProducerProperties() {
        return producerProperties;
    }

    public void setProducerProperties(Map<String, String> producerProperties) {
        if (producerProperties == null || producerProperties.isEmpty()) {
            this.producerProperties = new LinkedHashMap<>();
            return;
        }
        this.producerProperties = new LinkedHashMap<>(producerProperties);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public void setLeaseOwner(String leaseOwner) {
        this.leaseOwner = leaseOwner;
    }

    public String getThreadNameFormat() {
        return threadNameFormat;
    }

    public void setThreadNameFormat(String threadNameFormat) {
        this.threadNameFormat = normalizeThreadNameFormat(threadNameFormat, "task-kafka-publisher-%d");
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

    private static int requireNonNegativeInt(
        String name,
        int value
    ) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0");
        }
        return value;
    }

    private static List<String> normalizeList(
        List<String> value
    ) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        return List.copyOf(value);
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
