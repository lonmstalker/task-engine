package io.lonmstalker.task.impl.store.memory;

import io.lonmstalker.task.api.error.TaskDuplicateException;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.store.TaskClaim;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
import io.lonmstalker.task.api.store.TaskStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.jcip.annotations.ThreadSafe;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * In-memory store for tests.
 */
@ThreadSafe
public final class InMemoryTaskStore implements TaskStore {

    private final @NonNull Map<TaskKey, TaskRecord> byKey = new ConcurrentHashMap<>();
    private final @NonNull Map<TaskId, TaskRecord> byId = new ConcurrentHashMap<>();
    private final @NonNull Map<TaskKey, Object> keyLocks = new ConcurrentHashMap<>();
    private final @NonNull Map<TaskId, List<TaskLink>> links = new ConcurrentHashMap<>();
    private final @NonNull Object claimLock = new Object();

    @Override
    public @NonNull TaskRecord create(
        @NonNull TaskRecord record,
        @NonNull List<TaskLink> links
    ) {
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
    public @NonNull TaskRecord updateOnDuplicate(
        @NonNull TaskKey key,
        @NonNull TaskRecordUpdater updater,
        @NonNull List<TaskLink> newLinks
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(updater, "updater");
        Objects.requireNonNull(newLinks, "newLinks");

        Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            TaskRecord existing = byKey.get(key);
            if (existing == null) {
                throw new IllegalStateException("Task not found for key: " + key.value());
            }

            TaskRecord updated = updater.update(existing);
            byKey.put(key, updated);
            byId.put(updated.id(), updated);
            addLinks(updated.id(), newLinks);

            return updated;
        }
    }

    @Override
    public @Nullable TaskRecord findByKey(
        @NonNull TaskKey key
    ) {
        Objects.requireNonNull(key, "key");

        return byKey.get(key);
    }

    @Override
    public @Nullable TaskRecord findById(
        @NonNull TaskId id
    ) {
        Objects.requireNonNull(id, "id");

        return byId.get(id);
    }

    @Override
    public @NonNull List<TaskRecord> claim(
        @NonNull TaskClaim claim
    ) {
        Objects.requireNonNull(claim, "claim");

        List<TaskRecord> claimed = new ArrayList<>();
        Instant now = claim.now();
        Instant leaseUntil = now.plus(claim.leaseDuration());

        synchronized (claimLock) {
            List<TaskRecord> candidates = new ArrayList<>(byId.values());
            candidates.sort(Comparator.comparing(TaskRecord::createdAt));

            for (TaskRecord record : candidates) {
                if (claimed.size() >= claim.maxTasks()) {
                    break;
                }
                if (!isEligible(record, now)) {
                    continue;
                }
                if (!dependenciesSatisfied(record)) {
                    continue;
                }

                TaskRecord running = new TaskRecord(
                    record.id(),
                    record.key(),
                    record.type(),
                    record.state(),
                    TaskStatus.RUNNING,
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
    public @NonNull TaskRecord update(
        @NonNull TaskRecord record
    ) {
        Objects.requireNonNull(record, "record");

        byKey.put(record.key(), record);
        byId.put(record.id(), record);
        return record;
    }

    @Override
    public @NonNull List<TaskLink> findLinks(
        @NonNull TaskId id
    ) {
        Objects.requireNonNull(id, "id");

        List<TaskLink> taskLinks = links.get(id);
        if (taskLinks == null) {
            return List.of();
        }

        return List.copyOf(taskLinks);
    }

    @Override
    public void resetExpiredLeases(
        @NonNull Instant now
    ) {
        Objects.requireNonNull(now, "now");

        synchronized (claimLock) {
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

    private boolean isEligible(
        @NonNull TaskRecord record,
        @NonNull Instant now
    ) {
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

    private boolean dependenciesSatisfied(
        @NonNull TaskRecord record
    ) {
        List<TaskLink> taskLinks = links.get(record.id());
        if (taskLinks == null || taskLinks.isEmpty()) {
            return true;
        }

        for (TaskLink link : taskLinks) {
            if (link.type() != TaskLinkType.DEPENDS_ON) {
                continue;
            }

            TaskRecord dependency = byId.get(link.targetId());
            if (dependency == null || dependency.status() != TaskStatus.COMPLETED) {
                return false;
            }
        }

        return true;
    }

    private void addLinks(
        @NonNull TaskId id,
        @NonNull List<TaskLink> newLinks
    ) {
        if (newLinks.isEmpty()) {
            return;
        }

        links.compute(id, (key, existing) -> {
            List<TaskLink> updated = new ArrayList<>();
            if (existing != null) {
                updated.addAll(existing);
            }
            updated.addAll(newLinks);
            return List.copyOf(updated);
        });
    }
}
