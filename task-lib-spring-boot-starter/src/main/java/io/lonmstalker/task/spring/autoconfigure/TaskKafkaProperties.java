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
        this.pollInterval = pollInterval;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getPublishTimeout() {
        return publishTimeout;
    }

    public void setPublishTimeout(Duration publishTimeout) {
        this.publishTimeout = publishTimeout;
    }

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(List<String> bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public Map<String, String> getProducerProperties() {
        return producerProperties;
    }

    public void setProducerProperties(Map<String, String> producerProperties) {
        this.producerProperties = producerProperties;
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
        this.threadNameFormat = threadNameFormat;
    }
}
