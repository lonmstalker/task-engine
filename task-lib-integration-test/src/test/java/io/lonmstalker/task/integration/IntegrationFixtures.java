package io.lonmstalker.task.integration;

import io.lonmstalker.task.api.TaskContextCodec;
import io.lonmstalker.task.api.TaskContextMerger;
import io.lonmstalker.task.api.TaskDispatcher;
import io.lonmstalker.task.api.error.TaskException;
import io.lonmstalker.task.api.model.TaskPayload;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class IntegrationFixtures {

    private IntegrationFixtures() {
    }

    static final class DirectTaskDispatcher implements TaskDispatcher {

        @Override
        public void dispatch(
            Runnable task
        ) {
            task.run();
        }

        @Override
        public int parallelism() {
            return 1;
        }

        @Override
        public void close() {
        }
    }

    static final class StringCodec implements TaskContextCodec<String> {

        @Override
        public TaskPayload encode(
            String context
        ) {
            return new TaskPayload(context.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        @Override
        public String decode(
            TaskPayload payload
        ) {
            return new String(payload.data(), StandardCharsets.UTF_8);
        }
    }

    static final class CsvListCodec implements TaskContextCodec<List<String>> {

        @Override
        public TaskPayload encode(
            List<String> context
        ) {
            String joined = String.join(";", context);
            return new TaskPayload(joined.getBytes(StandardCharsets.UTF_8), "text/plain");
        }

        @Override
        public List<String> decode(
            TaskPayload payload
        ) {
            String decoded = new String(payload.data(), StandardCharsets.UTF_8);
            if (decoded.isBlank()) {
                return List.of();
            }
            return List.of(decoded.split(";"));
        }
    }

    static final class UniqueListMerger implements TaskContextMerger<List<String>> {

        @Override
        public List<String> merge(
            List<String> existing,
            List<String> incoming
        ) {
            Set<String> merged = new HashSet<>(existing);
            merged.addAll(incoming);
            return new ArrayList<>(merged);
        }
    }

    static final class RuntimeFailure extends TaskException {

        RuntimeFailure() {
            super("boom");
        }
    }
}
