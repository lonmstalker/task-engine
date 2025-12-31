package io.lonmstalker.task.api;

import io.lonmstalker.task.api.model.TaskType;
import io.lonmstalker.task.api.state.TaskStateMachine;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Definition of a task type.
 */
public final class TaskDefinition<C> {

    private final @NonNull TaskType type;
    private final @NonNull TaskHandler<C> handler;
    private final @NonNull TaskContextCodec<C> contextCodec;
    private final @NonNull TaskContextMerger<C> contextMerger;
    private final @NonNull TaskStateMachine stateMachine;
    private final @NonNull RetryPolicy retryPolicy;

    private TaskDefinition(
        @NonNull Builder<C> builder
    ) {
        this.type = Objects.requireNonNull(builder.type, "type");
        this.handler = Objects.requireNonNull(builder.handler, "handler");
        this.contextCodec = Objects.requireNonNull(builder.contextCodec, "contextCodec");
        this.contextMerger = Objects.requireNonNull(builder.contextMerger, "contextMerger");
        this.stateMachine = Objects.requireNonNull(builder.stateMachine, "stateMachine");
        this.retryPolicy = Objects.requireNonNull(builder.retryPolicy, "retryPolicy");
    }

    public static <C> @NonNull Builder<C> builder() {
        return new Builder<>();
    }

    /**
     * Returns task type.
     *
     * @return task type
     */
    public @NonNull TaskType type() {
        return type;
    }

    /**
     * Returns handler for task execution.
     *
     * @return task handler
     */
    public @NonNull TaskHandler<C> handler() {
        return handler;
    }

    /**
     * Returns codec for task context.
     *
     * @return context codec
     */
    public @NonNull TaskContextCodec<C> contextCodec() {
        return contextCodec;
    }

    /**
     * Returns merger for duplicate submissions.
     *
     * @return context merger
     */
    public @NonNull TaskContextMerger<C> contextMerger() {
        return contextMerger;
    }

    /**
     * Returns state machine definition.
     *
     * @return state machine
     */
    public @NonNull TaskStateMachine stateMachine() {
        return stateMachine;
    }

    /**
     * Returns retry policy.
     *
     * @return retry policy
     */
    public @NonNull RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public static final class Builder<C> {
        private @Nullable TaskType type;
        private @Nullable TaskHandler<C> handler;
        private @Nullable TaskContextCodec<C> contextCodec;
        private @Nullable TaskContextMerger<C> contextMerger;
        private @Nullable TaskStateMachine stateMachine;
        private @Nullable RetryPolicy retryPolicy;

        private Builder() {
        }

        /**
         * Sets task type.
         *
         * @param type task type
         * @return builder
         */
        public @NonNull Builder<C> type(
            @NonNull TaskType type
        ) {
            this.type = Objects.requireNonNull(type, "type");
            return this;
        }

        /**
         * Sets task handler.
         *
         * @param handler task handler
         * @return builder
         */
        public @NonNull Builder<C> handler(
            @NonNull TaskHandler<C> handler
        ) {
            this.handler = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * Sets context codec.
         *
         * @param contextCodec context codec
         * @return builder
         */
        public @NonNull Builder<C> contextCodec(
            @NonNull TaskContextCodec<C> contextCodec
        ) {
            this.contextCodec = Objects.requireNonNull(contextCodec, "contextCodec");
            return this;
        }

        /**
         * Sets context merger for duplicate tasks.
         *
         * @param contextMerger context merger
         * @return builder
         */
        public @NonNull Builder<C> contextMerger(
            @NonNull TaskContextMerger<C> contextMerger
        ) {
            this.contextMerger = Objects.requireNonNull(contextMerger, "contextMerger");
            return this;
        }

        /**
         * Sets state machine.
         *
         * @param stateMachine state machine
         * @return builder
         */
        public @NonNull Builder<C> stateMachine(
            @NonNull TaskStateMachine stateMachine
        ) {
            this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
            return this;
        }

        /**
         * Sets retry policy.
         *
         * @param retryPolicy retry policy
         * @return builder
         */
        public @NonNull Builder<C> retryPolicy(
            @NonNull RetryPolicy retryPolicy
        ) {
            this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
            return this;
        }

        /**
         * Builds task definition.
         *
         * @return task definition
         */
        public @NonNull TaskDefinition<C> build() {
            return new TaskDefinition<>(this);
        }
    }
}
