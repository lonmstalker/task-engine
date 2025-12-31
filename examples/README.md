# Examples

Runnable example projects for Task Lib.

## Example docs

- Basic TaskEngine: `examples/docs/basic.md`
- Kafka outbox publisher: `examples/docs/kafka-outbox.md`
- Spring Boot starter: `examples/docs/spring-boot.md`

## Prerequisites

- PostgreSQL schema applied from `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Environment variables:
  - `TASK_EXAMPLE_DB_URL` (e.g. `jdbc:postgresql://localhost:5432/taskdb`)
  - `TASK_EXAMPLE_DB_USER` (optional)
  - `TASK_EXAMPLE_DB_PASSWORD` (optional)

For Kafka example also set:
- `TASK_EXAMPLE_KAFKA_BOOTSTRAP` (e.g. `localhost:9092`)
- `TASK_EXAMPLE_KAFKA_TOPIC` (existing topic name)

## Run examples

- Basic engine example:
  - `./gradlew :examples:runBasic`
- Kafka outbox publisher example:
  - `./gradlew :examples:runKafkaOutbox`
- Spring Boot starter example:
  - `./gradlew :examples:runSpringBoot`
