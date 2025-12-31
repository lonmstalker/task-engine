package io.lonmstalker.task.examples.common;

import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskStatus;
import java.time.Duration;

public final class ExampleWaiter {

    private ExampleWaiter() {
    }

    public static TaskSnapshot awaitStatus(
        TaskEngine engine,
        TaskKey key,
        TaskStatus status,
        Duration timeout
    ) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            TaskSnapshot snapshot = engine.findByKey(key);
            if (snapshot != null && snapshot.status() == status) {
                return snapshot;
            }
            Thread.sleep(100);
        }

        throw new IllegalStateException("Timed out waiting for status " + status + " for key " + key.value());
    }
}
