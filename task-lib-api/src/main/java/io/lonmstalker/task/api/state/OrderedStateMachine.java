package io.lonmstalker.task.api.state;

import com.google.common.base.Preconditions;
import io.lonmstalker.task.api.error.TaskStateException;
import io.lonmstalker.task.api.model.TaskState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Linear state machine with ordered states.
 */
public final class OrderedStateMachine implements TaskStateMachine {

    private final @NonNull List<TaskState> states;
    private final @NonNull Map<TaskState, Integer> indexByState;

    private OrderedStateMachine(
        @NonNull List<TaskState> states
    ) {
        Objects.requireNonNull(states, "states");
        Preconditions.checkArgument(!states.isEmpty(), "states must not be empty");

        this.states = List.copyOf(states);
        this.indexByState = indexStates(this.states);
    }

    /**
     * Creates ordered state machine.
     *
     * @param states ordered states
     * @return state machine
     */
    public static @NonNull OrderedStateMachine of(
        @NonNull List<TaskState> states
    ) {
        return new OrderedStateMachine(states);
    }

    @Override
    public @NonNull TaskState initialState() {
        return states.get(0);
    }

    @Override
    public @NonNull TaskState nextState(
        @NonNull TaskState current
    ) {
        Objects.requireNonNull(current, "current");

        Integer index = indexByState.get(current);
        if (index == null) {
            throw new TaskStateException("Unknown state: " + current.value());
        }
        if (index == states.size() - 1) {
            throw new TaskStateException("No next state for terminal: " + current.value());
        }

        return states.get(index + 1);
    }

    @Override
    public boolean isTerminal(
        @NonNull TaskState state
    ) {
        Objects.requireNonNull(state, "state");

        Integer index = indexByState.get(state);
        return index != null && index == states.size() - 1;
    }

    @Override
    public boolean isValidTransition(
        @NonNull TaskState from,
        @NonNull TaskState to
    ) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");

        Integer fromIndex = indexByState.get(from);
        Integer toIndex = indexByState.get(to);
        if (fromIndex == null || toIndex == null) {
            return false;
        }

        return toIndex == fromIndex + 1;
    }

    @Override
    public @NonNull TaskStateUpdate resolveExternalState(
        @NonNull TaskState current,
        @NonNull TaskState incoming
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(incoming, "incoming");

        Integer currentIndex = indexByState.get(current);
        Integer incomingIndex = indexByState.get(incoming);
        if (currentIndex == null || incomingIndex == null) {
            return TaskStateUpdate.reject("Unknown state");
        }
        if (incomingIndex < currentIndex) {
            return TaskStateUpdate.ignore();
        }

        return TaskStateUpdate.apply(incoming);
    }

    private static @NonNull Map<TaskState, Integer> indexStates(
        @NonNull List<TaskState> states
    ) {
        Map<TaskState, Integer> index = new HashMap<>(states.size());

        for (int i = 0; i < states.size(); i++) {
            TaskState state = states.get(i);
            if (index.put(state, i) != null) {
                throw new IllegalArgumentException("Duplicate state: " + state.value());
            }
        }

        return Map.copyOf(index);
    }
}
