package io.lonmstalker.task.benchmark;

import io.lonmstalker.task.api.event.TaskEventContextEntry;
import io.lonmstalker.task.api.event.TaskEventContextKind;
import io.lonmstalker.task.api.event.TaskEventId;
import io.lonmstalker.task.api.event.TaskEventRecord;
import io.lonmstalker.task.api.event.TaskEventType;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import io.lonmstalker.task.integration.PostgresTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
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
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class PostgresTaskStoreBenchmark {

    private static final TaskType TASK_TYPE = TaskType.of("bench");
    private static final TaskState TASK_STATE = TaskState.of("NEW");
    private static final TaskPayload PAYLOAD =
        new TaskPayload("payload".getBytes(StandardCharsets.UTF_8), "text/plain");

    @Benchmark
    public TaskRecord createTask(
        CreateState state
    ) {
        TaskRecord record = state.nextRecord();
        return state.store.create(record, List.of());
    }

    @Benchmark
    public List<TaskRecord> claimTasks(
        ClaimState state
    ) {
        TaskClaim claim = new TaskClaim(
            state.batchSize,
            "bench",
            Duration.ofSeconds(30),
            Instant.now()
        );
        return state.store.claim(claim);
    }

    @Benchmark
    public TaskRecord updateOnDuplicate(
        DuplicateState state
    ) {
        return state.store.updateOnDuplicate(state.key, state.updater, List.of());
    }

    @Benchmark
    public TaskRecord findByKey(
        FindState state
    ) {
        return state.store.findByKey(state.key);
    }

    @Benchmark
    public TaskRecord findById(
        FindState state
    ) {
        return state.store.findById(state.id);
    }

    @Benchmark
    public List<TaskRecord> loadChainRecords(
        ChainState state
    ) {
        return state.store.loadChainRecords(state.terminalId);
    }

    @Benchmark
    public List<TaskId> loadDependentTaskIds(
        ChainState state
    ) {
        return state.store.loadDependentTaskIds(state.rootId);
    }

    @Benchmark
    public TaskEventRecord createEvent(
        EventState state
    ) {
        TaskEventRecord event = new TaskEventRecord(
            TaskEventId.random(),
            state.terminalId,
            state.terminalKey,
            TASK_TYPE,
            TaskEventType.CHAIN_COMPLETED,
            Instant.now()
        );
        return state.store.createEvent(event, state.chainContexts, state.chainTaskIds);
    }

    @State(Scope.Benchmark)
    public static class CreateState {
        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private PostgresTaskStore store;
        private final AtomicInteger counter = new AtomicInteger();

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
            store = new PostgresTaskStore(dataSource);
        }

        @Setup(Level.Iteration)
        public void reset() {
            PostgresTestSupport.truncateAll(dataSource);
            counter.set(0);
        }

        @TearDown(Level.Trial)
        public void teardown() {
            postgres.stop();
        }

        TaskRecord nextRecord() {
            int seq = counter.incrementAndGet();
            Instant now = Instant.now();
            return new TaskRecord(
                TaskId.random(),
                TaskKey.of("bench-" + seq),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                null,
                0,
                1,
                now,
                null,
                null,
                PAYLOAD,
                now,
                now,
                null
            );
        }
    }

    @State(Scope.Benchmark)
    public static class ClaimState {
        private static final int DEFAULT_TASKS = 200;

        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private PostgresTaskStore store;
        private final AtomicInteger counter = new AtomicInteger();
        private final int batchSize = 50;

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
            store = new PostgresTaskStore(dataSource);
        }

        @Setup(Level.Invocation)
        public void prepare() {
            PostgresTestSupport.truncateAll(dataSource);
            counter.set(0);
            for (int i = 0; i < DEFAULT_TASKS; i++) {
                store.create(nextRecord(), List.of());
            }
        }

        @TearDown(Level.Trial)
        public void teardown() {
            postgres.stop();
        }

        TaskRecord nextRecord() {
            int seq = counter.incrementAndGet();
            Instant now = Instant.now();
            return new TaskRecord(
                TaskId.random(),
                TaskKey.of("bench-claim-" + seq),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                null,
                0,
                1,
                now,
                null,
                null,
                PAYLOAD,
                now,
                now,
                null
            );
        }
    }

    @State(Scope.Benchmark)
    public static class DuplicateState {
        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private PostgresTaskStore store;
        private TaskKey key;
        private TaskRecordUpdater updater;

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
            store = new PostgresTaskStore(dataSource);

            TaskRecord record = new TaskRecord(
                TaskId.random(),
                TaskKey.of("dup"),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                null,
                0,
                1,
                Instant.now(),
                null,
                null,
                PAYLOAD,
                Instant.now(),
                Instant.now(),
                null
            );
            store.create(record, List.of());
            key = record.key();
            updater = existing -> new TaskRecord(
                existing.id(),
                existing.key(),
                existing.type(),
                existing.state(),
                existing.status(),
                null,
                existing.attempt(),
                existing.maxAttempts(),
                existing.nextRunAt(),
                existing.leaseOwner(),
                existing.leaseUntil(),
                existing.payload(),
                existing.createdAt(),
                Instant.now(),
                existing.lastError()
            );
        }

        @TearDown(Level.Trial)
        public void teardown() {
            postgres.stop();
        }
    }

    @State(Scope.Benchmark)
    public static class FindState {
        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private PostgresTaskStore store;
        private TaskKey key;
        private TaskId id;

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
            store = new PostgresTaskStore(dataSource);
        }

        @Setup(Level.Iteration)
        public void prepare() {
            PostgresTestSupport.truncateAll(dataSource);
            TaskRecord record = new TaskRecord(
                TaskId.random(),
                TaskKey.of("find"),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                null,
                0,
                1,
                Instant.now(),
                null,
                null,
                PAYLOAD,
                Instant.now(),
                Instant.now(),
                null
            );
            store.create(record, List.of());
            key = record.key();
            id = record.id();
        }

        @TearDown(Level.Trial)
        public void teardown() {
            postgres.stop();
        }
    }

    @State(Scope.Benchmark)
    public static class ChainState {
        private static final int CHAIN_LENGTH = 30;

        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private PostgresTaskStore store;
        private TaskId terminalId;
        private TaskId rootId;

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
            store = new PostgresTaskStore(dataSource);
        }

        @Setup(Level.Iteration)
        public void prepare() {
            PostgresTestSupport.truncateAll(dataSource);
            TaskRecord previous = null;
            for (int i = 0; i < CHAIN_LENGTH; i++) {
                TaskRecord record = new TaskRecord(
                    TaskId.random(),
                    TaskKey.of("chain-" + i),
                    TASK_TYPE,
                    TASK_STATE,
                    TaskStatus.PENDING,
                    null,
                    0,
                    1,
                    Instant.now(),
                    null,
                    null,
                    PAYLOAD,
                    Instant.now(),
                    Instant.now(),
                    null
                );
                List<TaskLink> links = previous == null
                    ? List.of()
                    : List.of(new TaskLink(previous.id(), TaskLinkType.DEPENDS_ON));
                store.create(record, links);
                if (previous == null) {
                    rootId = record.id();
                }
                previous = record;
            }
            terminalId = previous.id();
        }

        @TearDown(Level.Trial)
        public void teardown() {
            postgres.stop();
        }
    }

    @State(Scope.Benchmark)
    public static class EventState {
        private PostgreSQLContainer<?> postgres;
        private DataSource dataSource;
        private PostgresTaskStore store;
        private TaskId terminalId;
        private TaskKey terminalKey;
        private List<TaskId> chainTaskIds;
        private List<TaskEventContextEntry> chainContexts;

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
            store = new PostgresTaskStore(dataSource);

            TaskRecord root = new TaskRecord(
                TaskId.random(),
                TaskKey.of("event-root"),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                null,
                0,
                1,
                Instant.now(),
                null,
                null,
                PAYLOAD,
                Instant.now(),
                Instant.now(),
                null
            );
            TaskRecord terminal = new TaskRecord(
                TaskId.random(),
                TaskKey.of("event-terminal"),
                TASK_TYPE,
                TASK_STATE,
                TaskStatus.PENDING,
                null,
                0,
                1,
                Instant.now(),
                null,
                null,
                PAYLOAD,
                Instant.now(),
                Instant.now(),
                null
            );
            store.create(root, List.of());
            store.create(terminal, List.of(new TaskLink(root.id(), TaskLinkType.DEPENDS_ON)));

            terminalId = terminal.id();
            terminalKey = terminal.key();
            chainTaskIds = List.of(root.id(), terminal.id());
            chainContexts = List.of(
                new TaskEventContextEntry(root.id(), TASK_TYPE, TaskEventContextKind.CHAIN, PAYLOAD, Instant.now()),
                new TaskEventContextEntry(terminal.id(), TASK_TYPE, TaskEventContextKind.CHAIN, PAYLOAD, Instant.now())
            );
        }

        @Setup(Level.Invocation)
        public void prepare() {
            PostgresTestSupport.truncateEvents(dataSource);
            Instant now = Instant.now();
            store.recordRequestContext(chainTaskIds.get(0), TASK_TYPE, PAYLOAD, now);
            store.recordDuplicateContext(chainTaskIds.get(1), TASK_TYPE, PAYLOAD, now);
        }

        @TearDown(Level.Trial)
        public void teardown() {
            postgres.stop();
        }
    }
}
