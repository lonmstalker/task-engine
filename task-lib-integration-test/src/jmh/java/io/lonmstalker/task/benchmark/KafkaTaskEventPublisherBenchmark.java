package io.lonmstalker.task.benchmark;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import io.lonmstalker.task.integration.KafkaTestSupport;
import io.lonmstalker.task.integration.PostgresTestSupport;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisher;
import io.lonmstalker.task.kafka.KafkaTaskEventPublisherConfig;
import io.lonmstalker.task.kafka.outbox.postgres.PostgresTaskEventOutboxStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class KafkaTaskEventPublisherBenchmark {

    @Benchmark
    public int publishOnce(
        PublisherState state
    ) {
        return state.publisher.publishOnce();
    }

    @State(Scope.Benchmark)
    public static class PublisherState {

        private static final TaskType TASK_TYPE = TaskType.of("bench");
        private static final TaskState TASK_STATE = TaskState.of("NEW");
        private static final TaskPayload PAYLOAD =
            new TaskPayload("payload".getBytes(StandardCharsets.UTF_8), "text/plain");

        PostgreSQLContainer<?> postgres;
        KafkaContainer kafka;
        DataSource dataSource;
        PostgresTaskStore store;
        PostgresTaskEventOutboxStore outboxStore;
        KafkaProducer<String, byte[]> producer;
        KafkaTaskEventPublisher publisher;
        String topic;
        TaskRecord task;

        @Setup(Level.Trial)
        public void setup() {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
            dataSource = PostgresTestSupport.createDataSource(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword()
            );
            PostgresTestSupport.applySchema(dataSource);

            kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));
            kafka.start();

            topic = "task-bench-" + UUID.randomUUID();
            KafkaTestSupport.createTopic(kafka.getBootstrapServers(), topic);

            store = new PostgresTaskStore(dataSource);
            outboxStore = new PostgresTaskEventOutboxStore(dataSource);
            producer = KafkaTestSupport.createProducer(kafka.getBootstrapServers());

            KafkaTaskEventPublisherConfig config = new KafkaTaskEventPublisherConfig(
                topic,
                "bench",
                Duration.ofSeconds(30),
                1,
                Duration.ofSeconds(5)
            );
            publisher = new KafkaTaskEventPublisher(outboxStore, producer, config);

            task = new TaskRecord(
                TaskId.random(),
                TaskKey.of("task-" + UUID.randomUUID()),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                0,
                1,
                null,
                null,
                null,
                PAYLOAD,
                Instant.now(),
                Instant.now(),
                null
            );
            store.create(task, List.of());
        }

        @Setup(Level.Invocation)
        public void setupInvocation() {
            PostgresTestSupport.truncateEvents(dataSource);

            Instant now = Instant.now();
            TaskEventRecord event = new TaskEventRecord(
                TaskEventId.random(),
                task.id(),
                task.key(),
                task.type(),
                TaskEventType.CHAIN_COMPLETED,
                now
            );
            TaskEventContextEntry context = new TaskEventContextEntry(
                task.id(),
                task.type(),
                TaskEventContextKind.CHAIN,
                PAYLOAD,
                now
            );
            store.createEvent(event, List.of(context), List.of(task.id()));
        }

        @TearDown(Level.Trial)
        public void tearDown() {
            if (publisher != null) {
                publisher.close();
                producer = null;
            }
            if (kafka != null) {
                kafka.stop();
            }
            if (postgres != null) {
                postgres.stop();
            }
        }
    }
}
