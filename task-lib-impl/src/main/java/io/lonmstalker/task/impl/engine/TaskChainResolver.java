package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskStore;
import io.lonmstalker.task.impl.store.TaskChainStore;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

final class TaskChainResolver {

    private final @NonNull TaskStore store;
    private final @Nullable TaskChainStore chainStore;

    TaskChainResolver(
        @NonNull TaskStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.chainStore = store instanceof TaskChainStore ? (TaskChainStore) store : null;
    }

    boolean hasDependents(
        @NonNull TaskId taskId
    ) {
        return store.hasDependents(taskId);
    }

    @NonNull List<TaskRecord> loadChainRecords(
        @NonNull TaskRecord terminalRecord
    ) {
        if (chainStore != null) {
            return chainStore.loadChainRecords(terminalRecord.id());
        }

        Map<TaskId, TaskRecord> records = new LinkedHashMap<>();
        Deque<TaskId> queue = new ArrayDeque<>();

        records.put(terminalRecord.id(), terminalRecord);
        queue.add(terminalRecord.id());

        while (!queue.isEmpty()) {
            TaskId current = queue.removeFirst();
            List<TaskLink> links = store.findLinks(current);
            for (TaskLink link : links) {
                if (link.type() != TaskLinkType.DEPENDS_ON) {
                    continue;
                }
                TaskId dependencyId = link.targetId();
                if (records.containsKey(dependencyId)) {
                    continue;
                }

                TaskRecord dependency = store.findById(dependencyId);
                if (dependency == null) {
                    continue;
                }

                records.put(dependencyId, dependency);
                queue.add(dependencyId);
            }
        }

        return List.copyOf(records.values());
    }

    @NonNull List<TaskId> loadDependentTaskIds(
        @NonNull TaskId rootTaskId
    ) {
        if (chainStore == null) {
            return List.of();
        }

        return chainStore.loadDependentTaskIds(rootTaskId);
    }
}
