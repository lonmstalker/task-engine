package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.error.TaskDuplicateException;
import io.lonmstalker.task.api.error.TaskStateException;
import io.lonmstalker.task.api.model.TaskId;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskRequest;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.model.TaskSubmissionResult;
import io.lonmstalker.task.api.model.TaskSubmissionStatus;
import io.lonmstalker.task.api.state.TaskStateUpdate;
import io.lonmstalker.task.api.state.TaskStateUpdateAction;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskRecordUpdater;
import io.lonmstalker.task.api.store.TaskStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.NonNull;

final class TaskSubmissionService {

    private final @NonNull TaskStore store;
    private final @NonNull TaskEventPublisher eventPublisher;
    private final @NonNull TaskSnapshotMapper snapshotMapper;
    private final @NonNull Clock clock;

    TaskSubmissionService(
        @NonNull TaskStore store,
        @NonNull TaskEventPublisher eventPublisher,
        @NonNull TaskSnapshotMapper snapshotMapper,
        @NonNull Clock clock
    ) {
        this.store = Objects.requireNonNull(store, "store");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.snapshotMapper = Objects.requireNonNull(snapshotMapper, "snapshotMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @SuppressWarnings("unchecked")
    <C> @NonNull TaskSubmissionResult submit(
        @NonNull TaskRequest<?> rawRequest,
        @NonNull TaskDefinition<?> rawDefinition
    ) {
        TaskRequest<C> request = (TaskRequest<C>) rawRequest;
        TaskDefinition<C> definition = (TaskDefinition<C>) rawDefinition;

        Instant now = now();
        TaskState initial = definition.stateMachine().initialState();
        TaskState resolvedState = resolveInitialState(definition, initial, request.state());
        TaskPayload payload = definition.contextCodec().encode(request.context());

        TaskRecord record = new TaskRecord(
            TaskId.random(),
            request.key(),
            request.type(),
            resolvedState,
            TaskStatus.PENDING,
            0,
            definition.retryPolicy().maxAttempts(),
            now,
            null,
            null,
            payload,
            now,
            now,
            null
        );

        try {
            return eventPublisher.inSharedTransaction(() -> {
                TaskRecord created = store.create(record, request.links());
                eventPublisher.onRequestCreated(created, payload, now);
                TaskSnapshot snapshot = snapshotMapper.toSnapshot(created);
                return new TaskSubmissionResult(snapshot, TaskSubmissionStatus.CREATED);
            });
        } catch (TaskDuplicateException ex) {
            return updateDuplicate(request, definition);
        }
    }

    private <C> TaskSubmissionResult updateDuplicate(
        @NonNull TaskRequest<C> request,
        @NonNull TaskDefinition<C> definition
    ) {
        return eventPublisher.inSharedTransaction(() -> updateDuplicateInternal(request, definition));
    }

    private <C> TaskSubmissionResult updateDuplicateInternal(
        @NonNull TaskRequest<C> request,
        @NonNull TaskDefinition<C> definition
    ) {
        Instant now = now();
        TaskRecordUpdater updater = existing -> mergeDuplicate(existing, request, definition, now);
        UpdateTracker tracker = new UpdateTracker();

        TaskRecord updated = store.updateOnDuplicate(request.key(), record -> {
            TaskRecord merged = updater.update(record);
            tracker.changed = tracker.changed || isChanged(record, merged) || !request.links().isEmpty();
            return merged;
        }, request.links());

        TaskSnapshot snapshot = snapshotMapper.toSnapshot(updated);
        TaskSubmissionStatus status = tracker.changed ? TaskSubmissionStatus.UPDATED : TaskSubmissionStatus.DUPLICATE;

        if (eventPublisher.isEnabled()) {
            TaskPayload payload = definition.contextCodec().encode(request.context());
            eventPublisher.onDuplicate(updated, payload, now, definition);
        }

        return new TaskSubmissionResult(snapshot, status);
    }

    private <C> TaskRecord mergeDuplicate(
        @NonNull TaskRecord existing,
        @NonNull TaskRequest<C> request,
        @NonNull TaskDefinition<C> definition,
        @NonNull Instant now
    ) {
        TaskStateUpdate update = definition.stateMachine().resolveExternalState(existing.state(), request.state());
        TaskState newState = existing.state();

        if (update.action() == TaskStateUpdateAction.APPLY) {
            newState = Optional.ofNullable(update.state())
                .orElseThrow(() -> new TaskStateException("Missing state for update"));
        } else if (update.action() == TaskStateUpdateAction.REJECT) {
            String reason = Optional.ofNullable(update.reason()).orElse("Rejected state update");
            throw new TaskStateException(reason);
        }

        boolean canMergeContext = existing.status() == TaskStatus.PENDING
            || existing.status() == TaskStatus.WAITING_RETRY;

        TaskPayload newPayload = existing.payload();
        if (canMergeContext) {
            TaskContextCodec<C> codec = definition.contextCodec();
            C existingContext = codec.decode(existing.payload());
            C merged = definition.contextMerger().merge(existingContext, request.context());
            newPayload = codec.encode(merged);
        }

        return new TaskRecord(
            existing.id(),
            existing.key(),
            existing.type(),
            newState,
            existing.status(),
            existing.attempt(),
            existing.maxAttempts(),
            existing.nextRunAt(),
            existing.leaseOwner(),
            existing.leaseUntil(),
            newPayload,
            existing.createdAt(),
            now,
            existing.lastError()
        );
    }

    private TaskState resolveInitialState(
        @NonNull TaskDefinition<?> definition,
        @NonNull TaskState initial,
        @NonNull TaskState incoming
    ) {
        TaskStateUpdate update = definition.stateMachine().resolveExternalState(initial, incoming);

        if (update.action() == TaskStateUpdateAction.APPLY) {
            return Optional.ofNullable(update.state())
                .orElseThrow(() -> new TaskStateException("Missing state for update"));
        }
        if (update.action() == TaskStateUpdateAction.REJECT) {
            String reason = Optional.ofNullable(update.reason()).orElse("Rejected state update");
            throw new TaskStateException(reason);
        }

        return initial;
    }

    private boolean isChanged(
        @NonNull TaskRecord existing,
        @NonNull TaskRecord updated
    ) {
        if (!existing.state().equals(updated.state())) {
            return true;
        }
        if (!existing.payload().contentType().equals(updated.payload().contentType())) {
            return true;
        }

        return !Arrays.equals(existing.payload().data(), updated.payload().data());
    }

    private @NonNull Instant now() {
        return Instant.now(clock);
    }

    private static final class UpdateTracker {
        private boolean changed;
    }
}
