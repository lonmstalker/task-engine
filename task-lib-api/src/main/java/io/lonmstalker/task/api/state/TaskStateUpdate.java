package io.lonmstalker.task.api.state;

import io.lonmstalker.task.api.model.TaskState;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Decision for external state update.
 */
public record TaskStateUpdate(
    @NonNull TaskStateUpdateAction action,
    @Nullable TaskState state,
    @Nullable String reason
) {

    public TaskStateUpdate {
        Objects.requireNonNull(action, "action");
    }

    /**
     * Creates apply decision with new state.
     *
     * @param state new state
     * @return update decision
     */
    public static @NonNull TaskStateUpdate apply(
        @NonNull TaskState state
    ) {
        Objects.requireNonNull(state, "state");

        return new TaskStateUpdate(TaskStateUpdateAction.APPLY, state, null);
    }

    /**
     * Creates ignore decision.
     *
     * @return update decision
     */
    public static @NonNull TaskStateUpdate ignore() {
        return new TaskStateUpdate(TaskStateUpdateAction.IGNORE, null, null);
    }

    /**
     * Creates reject decision.
     *
     * @param reason rejection reason
     * @return update decision
     */
    public static @NonNull TaskStateUpdate reject(
        @NonNull String reason
    ) {
        Objects.requireNonNull(reason, "reason");

        return new TaskStateUpdate(TaskStateUpdateAction.REJECT, null, reason);
    }
}
