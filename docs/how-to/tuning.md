# Tune Concurrency and Recovery

- Polling: `task.engine.poll-interval`, `task.engine.claim-batch-size`.
- Leases: `task.engine.lease-duration`, `task.engine.recovery-interval`.
- Dispatcher: `task.engine.dispatcher.virtual-threads`,
  `task.engine.dispatcher.parallelism`, `task.engine.dispatcher.thread-name-format`.
