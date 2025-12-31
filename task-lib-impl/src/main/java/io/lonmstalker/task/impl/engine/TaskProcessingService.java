package io.lonmstalker.task.impl.engine;

import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskDefinition;
import io.lonmstalker.task.api.error.TaskExecutionException;
import io.lonmstalker.task.api.error.TaskException;
import io.lonmstalker.task.api.error.TaskStateException;
import io.lonmstalker.task.api.model.TaskErrorInfo;
import io.lonmstalker.task.api.model.TaskExecutionContext;
import io.lonmstalker.task.api.model.TaskPayload;
import io.lonmstalker.task.api.model.TaskResult;
import io.lonmstalker.task.api.model.TaskResult.Success;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskState;
import io.lonmstalker.task.api.model.TaskStatus;
import io.lonmstalker.task.api.store.TaskRecord;
import io.lonmstalker.task.api.store.TaskStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@Slf4j
final class TaskProcessingService {

    private final @NonNull TaskStore store;
    private final @NonNull TaskEventPublisher eventPublisher;
    private final @NonNull TaskSnapshotMapper snapshotMapper;
    private final @NonNull Clock clock;

    TaskProcessingService(
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

    void process(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> rawDefinition
    ) {
        Objects.requireNonNull(record, "record");

        processTyped(record, rawDefinition);
    }

    @SuppressWarnings("unchecked")
    private <C> void processTyped(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> rawDefinition
    ) {
        TaskDefinition<C> definition = (TaskDefinition<C>) rawDefinition;

        if (definition.stateMachine().isTerminal(record.state())) {
            TaskRecord completed = new TaskRecord(
                record.id(),
                record.key(),
                record.type(),
                record.state(),
                TaskStatus.COMPLETED,
                record.attempt(),
                record.maxAttempts(),
                null,
                null,
                null,
                record.payload(),
                record.createdAt(),
                now(),
                null
            );

            finalizeWithEvent(completed, definition);
            return;
        }

        TaskPayload payload = record.payload();
        TaskContextCodec<C> codec = definition.contextCodec();
        C context;

        try {
            context = codec.decode(payload);
        } catch (RuntimeException e) {
            log.error("Failed to decode task context for {}", record.id().value(), e);
            handleFailure(record, definition, new TaskExecutionException("Context decoding failed", e));
            return;
        }

        TaskSnapshot snapshot = snapshotMapper.toSnapshot(record);
        TaskExecutionContext<C> executionContext = new TaskExecutionContext<>(snapshot, context);

        TaskResult result;
        try {
            result = definition.handler().handle(executionContext);
        } catch (TaskException e) {
            result = TaskResult.failure(e);
        } catch (RuntimeException e) {
            result = TaskResult.failure(new TaskExecutionException("Task handler failed", e));
        }
        if (result == null) {
            handleFailure(record, definition, new TaskExecutionException("Task handler returned null"));
            return;
        }

        if (result instanceof TaskResult.Success success) {
            handleSuccess(record, definition, success);
            return;
        }

        if (result instanceof TaskResult.Failure failure) {
            handleFailure(record, definition, failure.error());
        }
    }

    private <C> void handleSuccess(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition,
        @NonNull Success success
    ) {
        TaskState nextState;
        try {
            nextState = resolveNextState(record, definition, success.nextState());
        } catch (TaskStateException e) {
            handleFailure(record, definition, e);
            return;
        } catch (RuntimeException e) {
            handleFailure(
                record,
                definition,
                new TaskExecutionException("State machine transition failed", e)
            );
            return;
        }
        Instant now = now();

        if (definition.stateMachine().isTerminal(nextState)) {
            TaskRecord completed = new TaskRecord(
                record.id(),
                record.key(),
                record.type(),
                nextState,
                TaskStatus.COMPLETED,
                record.attempt(),
                record.maxAttempts(),
                null,
                null,
                null,
                record.payload(),
                record.createdAt(),
                now,
                null
            );

            finalizeWithEvent(completed, definition);
            return;
        }

        TaskRecord updated = new TaskRecord(
            record.id(),
            record.key(),
            record.type(),
            nextState,
            TaskStatus.PENDING,
            record.attempt(),
            record.maxAttempts(),
            now,
            null,
            null,
            record.payload(),
            record.createdAt(),
            now,
            null
        );

        store.update(updated);
    }

    private <C> void handleFailure(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition,
        @NonNull TaskException error
    ) {
        TaskErrorInfo errorInfo = toErrorInfo(error);
        Instant now = now();

        boolean shouldRetry = definition.retryPolicy().shouldRetry(error)
            && record.attempt() < record.maxAttempts();

        if (shouldRetry) {
            Duration delay = definition.retryPolicy().delayBetweenAttempts(record.attempt());
            Instant nextRunAt = now.plus(delay);

            TaskRecord rescheduled = new TaskRecord(
                record.id(),
                record.key(),
                record.type(),
                record.state(),
                TaskStatus.WAITING_RETRY,
                record.attempt(),
                record.maxAttempts(),
                nextRunAt,
                null,
                null,
                record.payload(),
                record.createdAt(),
                now,
                errorInfo
            );

            store.update(rescheduled);
            return;
        }

        TaskRecord failed = new TaskRecord(
            record.id(),
            record.key(),
            record.type(),
            record.state(),
            TaskStatus.FAILED,
            record.attempt(),
            record.maxAttempts(),
            null,
            null,
            null,
            record.payload(),
            record.createdAt(),
            now,
            errorInfo
        );

        finalizeWithEvent(failed, definition);
    }

    private <C> TaskState resolveNextState(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<C> definition,
        @Nullable TaskState override
    ) {
        if (override == null) {
            return definition.stateMachine().nextState(record.state());
        }

        if (!definition.stateMachine().isValidTransition(record.state(), override)) {
            throw new TaskStateException(
                "Invalid transition from " + record.state().value() + " to " + override.value()
            );
        }

        return override;
    }

    private void finalizeWithEvent(
        @NonNull TaskRecord record,
        @NonNull TaskDefinition<?> definition
    ) {
        Instant now = record.updatedAt();

        eventPublisher.inSharedTransaction(() -> {
            store.update(record);
            eventPublisher.onFinalized(record, definition, now);
            return null;
        });
    }

    private @NonNull Instant now() {
        return Instant.now(clock);
    }

    private @NonNull TaskErrorInfo toErrorInfo(
        @NonNull TaskException error
    ) {
        Objects.requireNonNull(error, "error");

        String type = error.getClass().getSimpleName();
        String message = sanitizeMessage(error.getMessage());

        return new TaskErrorInfo(type, message);
    }

    private @NonNull String sanitizeMessage(
        @Nullable String message
    ) {
        if (message == null || message.isBlank()) {
            return "Task failed";
        }

        String sanitized = message.replaceAll("[\r\n]+", " ").trim();
        if (sanitized.length() > 500) {
            sanitized = sanitized.substring(0, 500);
        }

        return sanitized;
    }
}
