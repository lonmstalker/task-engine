package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.RetryPolicies;
import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.error.TaskDuplicateException;
import io.lonmstalker.task.api.error.TaskStoreException;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSubmissionStatus;
import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.state.OrderedStateMachine;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.impl.engine.TaskEngineImpl;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TaskEngineAutoConfigurationTest {

    private static final TaskState STATE_NEW = TaskState.of("NEW");
    private static final TaskState STATE_DONE = TaskState.of("DONE");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TaskEngineAutoConfiguration.class));

    @Test
    void createsEngineWithConfiguredProperties() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues(
                "task.engine.poll-interval=PT5S",
                "task.engine.lease-duration=PT12S",
                "task.engine.recovery-interval=PT9S",
                "task.engine.claim-batch-size=7",
                "task.engine.engine-id=engine-test",
                "task.engine.dispatcher.virtual-threads=false",
                "task.engine.dispatcher.parallelism=2",
                "task.engine.dispatcher.thread-name-format=test-worker-%d"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(TaskEngine.class);

                TaskEngineImpl engine = (TaskEngineImpl) context.getBean(TaskEngine.class);
                assertThat(readField(engine, "pollInterval", Duration.class)).isEqualTo(Duration.ofSeconds(5));
                assertThat(readField(engine, "leaseDuration", Duration.class)).isEqualTo(Duration.ofSeconds(12));
                assertThat(readField(engine, "recoveryInterval", Duration.class)).isEqualTo(Duration.ofSeconds(9));
                assertThat(readField(engine, "claimBatchSize", Integer.class)).isEqualTo(7);
                assertThat(readField(engine, "engineId", String.class)).isEqualTo("engine-test");

                TaskDispatcher dispatcher = context.getBean(TaskDispatcher.class);
                assertThat(dispatcher).isInstanceOf(TaskDispatcherDescriptor.class);
                TaskDispatcherDescriptor descriptor = (TaskDispatcherDescriptor) dispatcher;
                assertThat(descriptor.parallelism()).isEqualTo(2);
                assertThat(descriptor.threadNameFormat()).isEqualTo("test-worker-%d");
                assertDispatcherUsesVirtualThreads(dispatcher, false);

                Map<?, ?> definitions = readField(engine, "definitions", Map.class);
                assertThat(definitions).hasSize(1);
            });
    }

    @Test
    void backsOffWhenDisabled() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(TaskEngine.class);
                assertThat(context).doesNotHaveBean(TaskDispatcher.class);
                assertThat(context).doesNotHaveBean(TaskDispatcherFactory.class);
            });
    }

    @Test
    void backsOffWithoutTaskStore() {
        contextRunner
            .withUserConfiguration(DefinitionsOnlyConfig.class)
            .run(context -> {
                assertThat(context).doesNotHaveBean(TaskEngine.class);
                assertThat(context).doesNotHaveBean(TaskEngineLifecycle.class);
            });
    }

    @Test
    void usesCustomDispatcher() {
        contextRunner
            .withUserConfiguration(CustomDispatcherConfig.class)
            .run(context -> {
                TaskDispatcher dispatcher = context.getBean(TaskDispatcher.class);
                TaskEngineImpl engine = (TaskEngineImpl) context.getBean(TaskEngine.class);
                TaskDispatcher engineDispatcher = readField(engine, "dispatcher", TaskDispatcher.class);

                assertThat(engineDispatcher).isSameAs(dispatcher);
            });
    }

    @Test
    void usesCustomDispatcherFactory() {
        contextRunner
            .withUserConfiguration(CustomDispatcherFactoryConfig.class)
            .run(context -> {
                TaskDispatcher dispatcher = context.getBean(TaskDispatcher.class);
                assertThat(dispatcher).isInstanceOf(FactoryDispatcher.class);
                assertThat(((TaskDispatcherDescriptor) dispatcher).usesVirtualThreads()).isFalse();
            });
    }

    @Test
    void autoStartRespectsProperty() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.auto-start=false")
            .run(context -> {
                TaskEngineLifecycle lifecycle = context.getBean(TaskEngineLifecycle.class);
                assertThat(lifecycle.isAutoStartup()).isFalse();
            });
    }

    @Test
    void defaultsToVirtualThreads() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .run(context -> {
                TaskDispatcher dispatcher = context.getBean(TaskDispatcher.class);
                assertDispatcherUsesVirtualThreads(dispatcher, true);
            });
    }

    @Test
    void submitAndFindByKeyAndId() {
        contextRunner
            .withUserConfiguration(InMemoryConfig.class)
            .withPropertyValues("task.engine.auto-start=false")
            .run(context -> {
                TaskEngine engine = context.getBean(TaskEngine.class);
                TaskKey key = TaskKey.of("task-plain");

                TaskSubmissionResult result = engine.submit(new TaskRequest<>(
                    key,
                    TaskType.of("sample"),
                    STATE_NEW,
                    null,
                    "payload",
                    List.of()
                ));

                assertThat(result.status()).isEqualTo(TaskSubmissionStatus.CREATED);
                assertThat(engine.findByKey(key))
                    .isNotNull()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.status()).isEqualTo(TaskStatus.PENDING);
                        assertThat(snapshot.state()).isEqualTo(STATE_NEW);
                        assertThat(new String(snapshot.payload().data(), StandardCharsets.UTF_8))
                            .isEqualTo("payload");
                    });
                assertThat(engine.findById(result.snapshot().id())).isNotNull();
            });
    }

    @Test
    void processesTaskToCompletion() {
        contextRunner
            .withUserConfiguration(InMemoryConfig.class)
            .withPropertyValues("task.engine.auto-start=false")
            .run(context -> {
                TaskEngineImpl engine = (TaskEngineImpl) context.getBean(TaskEngine.class);
                TaskKey key = TaskKey.of("task-process");

                engine.submit(new TaskRequest<>(
                    key,
                    TaskType.of("sample"),
                    STATE_NEW,
                    null,
                    "payload",
                    List.of()
                ));

                invokeNoArg(engine, "pollOnce");

                assertThat(engine.findByKey(key))
                    .isNotNull()
                    .satisfies(snapshot -> {
                        assertThat(snapshot.status()).isEqualTo(TaskStatus.COMPLETED);
                        assertThat(snapshot.state()).isEqualTo(STATE_DONE);
                    });
            });
    }

    @Test
    void startsWithoutDefinitions() {
        contextRunner
            .withUserConfiguration(StoreOnlyConfig.class)
            .withPropertyValues("task.engine.auto-start=false")
            .run(context -> {
                TaskEngineImpl engine = (TaskEngineImpl) context.getBean(TaskEngine.class);
                Map<?, ?> definitions = readField(engine, "definitions", Map.class);
                assertThat(definitions).isEmpty();
            });
    }

    @Test
    void autoStartStartsEngine() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .run(context -> {
                TaskEngineImpl engine = (TaskEngineImpl) context.getBean(TaskEngine.class);
                AtomicBoolean started = readField(engine, "started", AtomicBoolean.class);
                assertThat(started.get()).isTrue();
            });
    }

    @Test
    void blankEngineIdFallsBackToDefault() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.engine-id=   ")
            .run(context -> {
                TaskEngineImpl engine = (TaskEngineImpl) context.getBean(TaskEngine.class);
                String engineId = readField(engine, "engineId", String.class);
                assertThat(engineId).isNotBlank();
            });
    }

    @Test
    void failsWhenPollIntervalNegative() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.poll-interval=-1s")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("pollInterval must be > 0");
            });
    }

    @Test
    void failsWhenLeaseDurationNegative() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.lease-duration=-1s")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("leaseDuration must be >= 0");
            });
    }

    @Test
    void failsWhenRecoveryIntervalNegative() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.recovery-interval=-1s")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("recoveryInterval must be > 0");
            });
    }

    @Test
    void failsWhenClaimBatchSizeNonPositive() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.claim-batch-size=0")
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("claimBatchSize must be > 0");
            });
    }

    @Test
    void failsWhenDispatcherParallelismNonPositive() {
        contextRunner
            .withUserConfiguration(BaseConfig.class)
            .withPropertyValues("task.engine.dispatcher.parallelism=0")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void registersDispatcherMetrics() {
        contextRunner
            .withUserConfiguration(BaseConfig.class, MetricsConfig.class)
            .run(context -> {
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                Gauge virtualThreads = registry.find("task.engine.dispatcher.virtual_threads").gauge();
                Gauge parallelism = registry.find("task.engine.dispatcher.parallelism").gauge();

                assertThat(virtualThreads).isNotNull();
                assertThat(parallelism).isNotNull();
                assertThat(virtualThreads.value()).isEqualTo(1.0);
            });
    }

    @Test
    void skipsMetricsWhenDispatcherNotDescribed() {
        contextRunner
            .withUserConfiguration(CustomDispatcherConfig.class, MetricsConfig.class)
            .run(context -> {
                MeterRegistry registry = context.getBean(MeterRegistry.class);
                Gauge virtualThreads = registry.find("task.engine.dispatcher.virtual_threads").gauge();
                Gauge parallelism = registry.find("task.engine.dispatcher.parallelism").gauge();

                assertThat(virtualThreads).isNull();
                assertThat(parallelism).isNull();
            });
    }

    private static TaskDefinition<String> sampleDefinition() {
        return TaskDefinition.<String>builder()
            .type(TaskType.of("sample"))
            .handler(context -> TaskResult.success())
            .contextCodec(new StringCodec())
            .contextMerger((existing, incoming) -> existing)
            .stateMachine(OrderedStateMachine.of(List.of(STATE_NEW, STATE_DONE)))
            .retryPolicy(RetryPolicies.none())
            .build();
    }

    private static <T> T readField(Object target, String name, Class<T> type) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return type.cast(field.get(target));
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("Missing field: " + name);
    }

    private static void invokeNoArg(Object target, String name) {
        try {
            var method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke " + name, e);
        }
    }

    private static void assertDispatcherUsesVirtualThreads(
        TaskDispatcher dispatcher,
        boolean expected
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean isVirtual = new AtomicBoolean(false);

        dispatcher.dispatch(() -> {
            isVirtual.set(Thread.currentThread().isVirtual());
            latch.countDown();
        });

        try {
            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Dispatch interrupted", e);
        }

        assertThat(isVirtual.get()).isEqualTo(expected);
    }

    @Configuration(proxyBeanMethods = false)
    static class BaseConfig {

        @Bean
        TaskStore taskStore() {
            return new NoopTaskStore();
        }

        @Bean
        TaskDefinition<String> taskDefinition() {
            return sampleDefinition();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DefinitionsOnlyConfig {

        @Bean
        TaskDefinition<String> taskDefinition() {
            return sampleDefinition();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDispatcherConfig {

        @Bean
        TaskStore taskStore() {
            return new NoopTaskStore();
        }

        @Bean
        TaskDefinition<String> taskDefinition() {
            return sampleDefinition();
        }

        @Bean
        TaskDispatcher taskDispatcher() {
            return new TestDispatcher();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InMemoryConfig {

        @Bean
        TaskStore taskStore() {
            return new InMemoryTaskStore();
        }

        @Bean
        TaskDefinition<String> taskDefinition() {
            return sampleDefinition();
        }

        @Bean
        TaskDispatcher taskDispatcher() {
            return new TestDispatcher();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StoreOnlyConfig {

        @Bean
        TaskStore taskStore() {
            return new InMemoryTaskStore();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomDispatcherFactoryConfig {

        @Bean
        TaskStore taskStore() {
            return new NoopTaskStore();
        }

        @Bean
        TaskDefinition<String> taskDefinition() {
            return sampleDefinition();
        }

        @Bean
        TaskDispatcherFactory taskDispatcherFactory() {
            return properties -> new FactoryDispatcher();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MetricsConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final class StringCodec implements TaskContextCodec<String> {

        @Override
        public TaskPayload encode(String context) {
            return new TaskPayload(context.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        @Override
        public String decode(TaskPayload payload) {
            return new String(payload.data(), StandardCharsets.UTF_8);
        }
    }

    private static final class NoopTaskStore implements TaskStore {

        @Override
        public TaskRecord create(TaskRecord record, List<TaskLink> links) {
            return record;
        }

        @Override
        public TaskRecord updateOnDuplicate(TaskKey key, TaskRecordUpdater updater, List<TaskLink> links) {
            throw new UnsupportedOperationException("updateOnDuplicate not supported");
        }

        @Override
        public TaskRecord findByKey(TaskKey key) {
            return null;
        }

        @Override
        public TaskRecord findById(TaskId id) {
            return null;
        }

        @Override
        public List<TaskRecord> claim(TaskClaim claim) {
            return List.of();
        }

        @Override
        public TaskRecord update(TaskRecord record) {
            return record;
        }

        @Override
        public List<TaskLink> findLinks(TaskId id) {
            return List.of();
        }

        @Override
        public boolean hasDependents(TaskId id) {
            return false;
        }

        @Override
        public void resetExpiredLeases(Instant now) {
        }

        @Override
        public void close() {
        }
    }

    private static final class InMemoryTaskStore implements TaskStore {

        private final Map<TaskKey, TaskRecord> byKey = new ConcurrentHashMap<>();
        private final Map<TaskId, TaskRecord> byId = new ConcurrentHashMap<>();
        private final Map<TaskId, List<TaskLink>> links = new ConcurrentHashMap<>();
        private final Object lock = new Object();

        @Override
        public TaskRecord create(TaskRecord record, List<TaskLink> links) {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(links, "links");

            TaskRecord existing = byKey.putIfAbsent(record.key(), record);
            if (existing != null) {
                throw new TaskDuplicateException("Task already exists");
            }

            byId.put(record.id(), record);
            addLinks(record.id(), links);
            return record;
        }

        @Override
        public TaskRecord updateOnDuplicate(TaskKey key, TaskRecordUpdater updater, List<TaskLink> links) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(updater, "updater");
            Objects.requireNonNull(links, "links");

            synchronized (lock) {
                TaskRecord existing = byKey.get(key);
                if (existing == null) {
                    throw new TaskStoreException("Task not found for key: " + key.value());
                }

                TaskRecord updated = updater.update(existing);
                byKey.put(key, updated);
                byId.put(updated.id(), updated);
                addLinks(updated.id(), links);
                return updated;
            }
        }

        @Override
        public TaskRecord findByKey(TaskKey key) {
            return byKey.get(key);
        }

        @Override
        public TaskRecord findById(TaskId id) {
            return byId.get(id);
        }

        @Override
        public List<TaskRecord> claim(TaskClaim claim) {
            Objects.requireNonNull(claim, "claim");

            List<TaskRecord> claimed = new ArrayList<>();
            Instant now = claim.now();
            Instant leaseUntil = now.plus(claim.leaseDuration());

            synchronized (lock) {
                List<TaskRecord> candidates = new ArrayList<>(byId.values());
                candidates.sort(Comparator.comparing(TaskRecord::createdAt));

                for (TaskRecord record : candidates) {
                    if (claimed.size() >= claim.maxTasks()) {
                        break;
                    }
                    if (!isEligible(record, now)) {
                        continue;
                    }

                    TaskRecord running = new TaskRecord(
                        record.id(),
                        record.key(),
                        record.type(),
                        record.state(),
                        TaskStatus.RUNNING,
                        null,
                        record.attempt() + 1,
                        record.maxAttempts(),
                        record.nextRunAt(),
                        claim.leaseOwner(),
                        leaseUntil,
                        record.payload(),
                        record.createdAt(),
                        now,
                        record.lastError()
                    );

                    byKey.put(record.key(), running);
                    byId.put(record.id(), running);
                    claimed.add(running);
                }
            }

            return List.copyOf(claimed);
        }

        @Override
        public TaskRecord update(TaskRecord record) {
            byKey.put(record.key(), record);
            byId.put(record.id(), record);
            return record;
        }

        @Override
        public List<TaskLink> findLinks(TaskId id) {
            List<TaskLink> found = links.get(id);
            if (found == null) {
                return List.of();
            }
            return List.copyOf(found);
        }

        @Override
        public boolean hasDependents(TaskId id) {
            Objects.requireNonNull(id, "id");

            for (List<TaskLink> taskLinks : links.values()) {
                for (TaskLink link : taskLinks) {
                    if (link.type() == TaskLinkType.DEPENDS_ON && link.targetId().equals(id)) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public void resetExpiredLeases(Instant now) {
            synchronized (lock) {
                for (TaskRecord record : byId.values()) {
                    Instant leaseUntil = record.leaseUntil();
                    if (record.status() != TaskStatus.RUNNING || leaseUntil == null) {
                        continue;
                    }
                    if (leaseUntil.isAfter(now)) {
                        continue;
                    }

                    TaskRecord reset = new TaskRecord(
                        record.id(),
                        record.key(),
                        record.type(),
                        record.state(),
                        TaskStatus.PENDING,
                        null,
                        record.attempt(),
                        record.maxAttempts(),
                        record.nextRunAt(),
                        null,
                        null,
                        record.payload(),
                        record.createdAt(),
                        now,
                        record.lastError()
                    );

                    byKey.put(record.key(), reset);
                    byId.put(record.id(), reset);
                }
            }
        }

        @Override
        public void close() {
        }

        private void addLinks(TaskId id, List<TaskLink> newLinks) {
            if (newLinks.isEmpty()) {
                return;
            }

            links.compute(id, (key, existing) -> {
                List<TaskLink> merged = new ArrayList<>();
                if (existing != null) {
                    merged.addAll(existing);
                }
                merged.addAll(newLinks);
                return List.copyOf(merged);
            });
        }

        private boolean isEligible(TaskRecord record, Instant now) {
            if (record.status() != TaskStatus.PENDING && record.status() != TaskStatus.WAITING_RETRY) {
                return false;
            }
            Instant nextRunAt = record.nextRunAt();
            if (nextRunAt != null && nextRunAt.isAfter(now)) {
                return false;
            }
            Instant leaseUntil = record.leaseUntil();
            return leaseUntil == null || !leaseUntil.isAfter(now);
        }
    }

    private static final class TestDispatcher implements TaskDispatcher {

        @Override
        public void dispatch(Runnable task) {
            task.run();
        }

        @Override
        public int parallelism() {
            return 1;
        }

        @Override
        public void close() {
        }
    }

    private static final class FactoryDispatcher implements TaskDispatcher, TaskDispatcherDescriptor {

        @Override
        public void dispatch(Runnable task) {
            task.run();
        }

        @Override
        public int parallelism() {
            return 1;
        }

        @Override
        public void close() {
        }

        @Override
        public boolean usesVirtualThreads() {
            return false;
        }

        @Override
        public String threadNameFormat() {
            return "custom-dispatcher-%d";
        }
    }
}
