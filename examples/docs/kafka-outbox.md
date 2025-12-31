# Kafka Outbox Publisher Example

## Goal

Publish a task chain event from the Postgres outbox to Kafka and read it back.

## Prerequisites

- PostgreSQL schema applied from `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Kafka broker running and a topic created.
- Environment variables:
  - `TASK_EXAMPLE_DB_URL`
  - `TASK_EXAMPLE_DB_USER` (optional)
  - `TASK_EXAMPLE_DB_PASSWORD` (optional)
  - `TASK_EXAMPLE_KAFKA_BOOTSTRAP`
  - `TASK_EXAMPLE_KAFKA_TOPIC` (existing topic name)

## Run

```
./gradlew :examples:runKafkaOutbox
```

## What it does

- Creates a completed task in Postgres.
- Writes a chain event + contexts into the outbox.
- Publishes the outbox event to Kafka.
- Consumes a single record and prints event info.

## Expected output

- Prints the number of published events (should be 1).
- Prints the Kafka event id and context count.

## Troubleshooting

- No Kafka records: verify the topic exists and `TASK_EXAMPLE_KAFKA_TOPIC` is correct.
- Publish errors: check broker reachability via `TASK_EXAMPLE_KAFKA_BOOTSTRAP`.
- DB errors: ensure schema is present and DB env vars are set.
