package io.lonmstalker.task.integration;

import io.lonmstalker.task.api.TaskEngine;
import io.lonmstalker.task.api.model.TaskKey;
import io.lonmstalker.task.api.model.TaskSnapshot;
import io.lonmstalker.task.api.model.TaskStatus;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Objects;

final class IntegrationTestSupport {

    private IntegrationTestSupport() {
    }

    static void pollOnce(
        TaskEngine engine
    ) {
        invokeNoArg(engine, "pollOnce");
    }

    static void recoverExpiredLeases(
        TaskEngine engine
    ) {
        invokeNoArg(engine, "recoverExpiredLeases");
    }

    static TaskSnapshot awaitStatus(
        TaskEngine engine,
        TaskKey key,
        TaskStatus expected,
        Duration timeout
    ) throws InterruptedException {
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(timeout, "timeout");

        long deadline = System.nanoTime() + timeout.toNanos();
        TaskSnapshot snapshot = null;
        while (System.nanoTime() < deadline) {
            snapshot = engine.findByKey(key);
            if (snapshot != null && snapshot.status() == expected) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        return snapshot;
    }

    private static void invokeNoArg(
        Object target,
        String name
    ) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke " + name, e);
        }
    }
}
