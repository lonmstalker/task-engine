# Spring Boot Starter Example

## Goal

Run Task Lib through Spring Boot auto-configuration and execute one task.

## Prerequisites

- PostgreSQL schema applied from `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Environment variables:
  - `TASK_EXAMPLE_DB_URL`
  - `TASK_EXAMPLE_DB_USER` (optional)
  - `TASK_EXAMPLE_DB_PASSWORD` (optional)

## Run

```
./gradlew :examples:runSpringBoot
```

## What it does

- Bootstraps Spring Boot and registers a `TaskStore` bean.
- Registers a `TaskDefinition` bean.
- Uses the starter to auto-configure `TaskEngine`.
- Submits a task and waits for `COMPLETED`.

## Expected output

- Prints a line confirming the task completion.

## Troubleshooting

- Missing DB env vars: set `TASK_EXAMPLE_DB_URL` (and optionally user/password).
- Schema errors: verify the schema script is applied to the database.
