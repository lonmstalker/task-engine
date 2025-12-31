package io.lonmstalker.task.api.model;

/**
 * Execution status of a task.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    WAITING_RETRY,
    COMPLETED,
    FAILED,
    CANCELLED
}
