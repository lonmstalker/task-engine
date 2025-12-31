package io.lonmstalker.task.api.state;

import io.lonmstalker.task.api.model.TaskState;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * State machine for task progression.
 */
public interface TaskStateMachine {

    /**
     * Returns initial state.
     *
     * @return initial state
     */
    @NonNull TaskState initialState();

    /**
     * Returns next state for normal progression.
     *
     * @param current current state
     * @return next state
     */
    @NonNull TaskState nextState(
        @NonNull TaskState current
    );

    /**
     * Returns whether state is terminal.
     *
     * @param state state to check
     * @return true if terminal
     */
    boolean isTerminal(
        @NonNull TaskState state
    );

    /**
     * Validates internal transition.
     *
     * @param from current state
     * @param to next state
     * @return true if valid
     */
    boolean isValidTransition(
        @NonNull TaskState from,
        @NonNull TaskState to
    );

    /**
     * Resolves external state updates.
     *
     * @param current current state
     * @param incoming incoming state
     * @return update decision
     */
    @NonNull TaskStateUpdate resolveExternalState(
        @NonNull TaskState current,
        @NonNull TaskState incoming
    );
}
