package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.model.TaskLink;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskStore;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

final class TaskSnapshotMapper {

    private final @NonNull TaskStore store;

    TaskSnapshotMapper(
        @NonNull TaskStore store
    ) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @NonNull TaskSnapshot toSnapshot(
        @NonNull TaskRecord record
    ) {
        List<TaskLink> links = store.findLinks(record.id());

        return new TaskSnapshot(
            record.id(),
            record.key(),
            record.type(),
            record.state(),
            record.status(),
            record.attempt(),
            record.maxAttempts(),
            record.nextRunAt(),
            record.createdAt(),
            record.updatedAt(),
            record.payload(),
            links
        );
    }
}
