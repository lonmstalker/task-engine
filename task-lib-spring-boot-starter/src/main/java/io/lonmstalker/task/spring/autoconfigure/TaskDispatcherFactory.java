package io.lonmstalker.task.spring.autoconfigure;

import io.lonmstalker.task.api.TaskDispatcher;

/**
 * Strategy for creating task dispatchers.
 */
public interface TaskDispatcherFactory {

    TaskDispatcher create(
        TaskEngineProperties.Dispatcher properties
    );
}
