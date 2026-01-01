# Handle Duplicates Explicitly

- `TaskKey` is the idempotency key. Submitting the same key returns `TaskSubmissionStatus`
  of `CREATED`, `UPDATED`, or `DUPLICATE`.
- `TaskContextMerger` merges contexts only while the task is `PENDING` or `WAITING_RETRY`.
- `TaskStateMachine.resolveExternalState` decides whether incoming states are applied,
  ignored, or rejected.
