package io.lonmstalker.task.integration;

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
import io.lonmstalker.task.impl.store.postgres.PostgresTaskStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Task store Postgres integration")
class TaskStorePostgresIntegrationTest extends PostgresIntegrationTestBase {

    private static final TaskType TASK_TYPE = TaskType.of("store");
    private static final TaskState TASK_STATE = TaskState.of("NEW");
    private static final TaskPayload PAYLOAD =
        new TaskPayload("payload".getBytes(StandardCharsets.UTF_8), "text/plain");

    @Test
    @DisplayName("shouldRespectDependenciesAndNextRunAt")
    void shouldRespectDependenciesAndNextRunAt() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        Instant now = Instant.now();

        TaskRecord taskA = record(TaskKey.of("task-a"), TaskStatus.PENDING, now.minusSeconds(20), now, null, null);
        TaskRecord taskB = record(TaskKey.of("task-b"), TaskStatus.PENDING, now.minusSeconds(10), now, null, null);
        TaskRecord taskC = record(TaskKey.of("task-c"), TaskStatus.PENDING, now.minusSeconds(5), now.plusSeconds(60), null, null);

        store.create(taskA, List.of());
        store.create(taskB, List.of(new TaskLink(taskA.id(), TaskLinkType.DEPENDS_ON)));
        store.create(taskC, List.of());

        TaskClaim claim = new TaskClaim(10, "worker", Duration.ofSeconds(30), now);
        List<TaskRecord> claimed = store.claim(claim);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).id()).isEqualTo(taskA.id());

        TaskRecord completedA = new TaskRecord(
            claimed.get(0).id(),
            taskA.key(),
            taskA.type(),
            taskA.state(),
            TaskStatus.COMPLETED,
            claimed.get(0).attempt(),
            taskA.maxAttempts(),
            null,
            null,
            null,
            taskA.payload(),
            taskA.createdAt(),
            now,
            null
        );
        store.update(completedA);

        List<TaskRecord> claimedAfter = store.claim(new TaskClaim(10, "worker", Duration.ofSeconds(30), now));
        assertThat(claimedAfter).hasSize(1);
        assertThat(claimedAfter.get(0).id()).isEqualTo(taskB.id());

        List<TaskRecord> claimedFuture = store.claim(new TaskClaim(10, "worker", Duration.ofSeconds(30), now));
        assertThat(claimedFuture).isEmpty();
    }

    @Test
    @DisplayName("shouldResetExpiredLeases")
    void shouldResetExpiredLeases() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        Instant now = Instant.now();

        TaskRecord running = record(
            TaskKey.of("lease-task"),
            TaskStatus.RUNNING,
            now.minusSeconds(30),
            null,
            "owner",
            now.minusSeconds(5)
        );

        store.create(running, List.of());

        store.resetExpiredLeases(now);

        TaskRecord reset = store.findByKey(running.key());
        assertThat(reset).isNotNull();
        assertThat(reset.status()).isEqualTo(TaskStatus.PENDING);
        assertThat(reset.leaseOwner()).isNull();
        assertThat(reset.leaseUntil()).isNull();
    }

    @Test
    @DisplayName("shouldUpdateOnDuplicateAddLinks")
    void shouldUpdateOnDuplicateAddLinks() {
        PostgresTaskStore store = new PostgresTaskStore(dataSource);
        Instant now = Instant.now();

        TaskRecord dependency = record(TaskKey.of("dep"), TaskStatus.PENDING, now, now, null, null);
        TaskRecord task = record(TaskKey.of("dup"), TaskStatus.PENDING, now, now, null, null);

        store.create(dependency, List.of());
        store.create(task, List.of());

        TaskRecord updated = store.updateOnDuplicate(task.key(), existing -> new TaskRecord(
            existing.id(),
            existing.key(),
            existing.type(),
            existing.state(),
            existing.status(),
            existing.attempt(),
            existing.maxAttempts(),
            existing.nextRunAt(),
            existing.leaseOwner(),
            existing.leaseUntil(),
            existing.payload(),
            existing.createdAt(),
            Instant.now(),
            existing.lastError()
        ), List.of(new TaskLink(dependency.id(), TaskLinkType.DEPENDS_ON)));

        assertThat(updated.id()).isEqualTo(task.id());

        List<TaskLink> links = store.findLinks(task.id());
        assertThat(links).anyMatch(link ->
            link.type() == TaskLinkType.DEPENDS_ON && link.targetId().equals(dependency.id())
        );
        assertThat(store.hasDependents(dependency.id())).isTrue();
    }

    private static TaskRecord record(
        TaskKey key,
        TaskStatus status,
        Instant createdAt,
        Instant nextRunAt,
        String leaseOwner,
        Instant leaseUntil
    ) {
        Instant updatedAt = createdAt == null ? Instant.now() : createdAt;
        return new TaskRecord(
            TaskId.random(),
            key,
            TASK_TYPE,
            TASK_STATE,
            status,
            0,
            1,
            nextRunAt,
            leaseOwner,
            leaseUntil,
            PAYLOAD,
            createdAt == null ? Instant.now() : createdAt,
            updatedAt,
            null
        );
    }
}
