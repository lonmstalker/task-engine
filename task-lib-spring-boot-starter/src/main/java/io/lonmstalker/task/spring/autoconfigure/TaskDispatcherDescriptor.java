package io.lonmstalker.task.spring.autoconfigure;

/**
 * Optional metadata for task dispatchers used for observability.
 */
public interface TaskDispatcherDescriptor {

    boolean usesVirtualThreads();

    int parallelism();

    String threadNameFormat();
}
