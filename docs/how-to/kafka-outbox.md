# Publish Chain Events to Kafka

## Prerequisites

- Ensure the outbox tables exist (they are included in the Postgres schema).
- Enable the Kafka publisher and provide `topic` and `bootstrap-servers`.

```yaml
task:
  kafka:
    enabled: true
    topic: "task-events"
    bootstrap-servers:
      - "localhost:9092"
```

## Optional customizations

- Override `TaskEventMessageCodec` (default: Jackson).
- Override `TaskEventKeyProvider` (default key = task id).
- Provide a custom `TaskEventOutboxStore` if not using Postgres.
