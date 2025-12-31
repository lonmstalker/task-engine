package io.lonmstalker.task.api;

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Merges context when a duplicate task is submitted.
 */
@FunctionalInterface
public interface TaskContextMerger<C> {

    /**
     * Merges existing and incoming contexts.
     *
     * @param existing existing context
     * @param incoming incoming context
     * @return merged context
     */
    @NonNull C merge(
        @NonNull C existing,
        @NonNull C incoming
    );
}
