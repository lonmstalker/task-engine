package io.lonmstalker.task.integration;

import java.util.Collections;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public final class KafkaTestSupport {

    private KafkaTestSupport() {
    }

    public static KafkaProducer<String, byte[]> createProducer(
        String bootstrapServers
    ) {
        return new KafkaProducer<>(producerProperties(bootstrapServers));
    }

    public static KafkaConsumer<String, byte[]> createConsumer(
        String bootstrapServers,
        String groupId
    ) {
        return new KafkaConsumer<>(consumerProperties(bootstrapServers, groupId));
    }

    public static void createTopic(
        String bootstrapServers,
        String topic
    ) {
        createTopic(bootstrapServers, topic, 1);
    }

    public static void createTopic(
        String bootstrapServers,
        String topic,
        int partitions
    ) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers");
        Objects.requireNonNull(topic, "topic");
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        try (AdminClient admin = AdminClient.create(properties)) {
            admin.createTopics(Collections.singleton(new NewTopic(topic, partitions, (short) 1)))
                .all()
                .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create topic " + topic, e);
        }
    }

    private static Properties producerProperties(
        String bootstrapServers
    ) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers");
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.put(ProducerConfig.RETRIES_CONFIG, "3");
        return properties;
    }

    private static Properties consumerProperties(
        String bootstrapServers,
        String groupId
    ) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers");
        Objects.requireNonNull(groupId, "groupId");
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        return properties;
    }
}
