package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskLinkType;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskStore;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

final class TaskChainResolver {

    private final @NonNull TaskStore store;

    TaskChainResolver(
        @NonNull TaskStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    boolean hasDependents(
        @NonNull TaskId taskId
    ) {
        return store.hasDependents(taskId);
    }

    @NonNull List<TaskRecord> loadChainRecords(
        @NonNull TaskRecord terminalRecord
    ) {
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
}
