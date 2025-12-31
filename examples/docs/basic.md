# Basic TaskEngine Example

## Goal

Run a single task end-to-end using the Postgres store and the default engine.

## Prerequisites

- PostgreSQL schema applied from `task-lib-impl/src/main/resources/io/lonmstalker/task/impl/store/postgres/schema.sql`.
- Environment variables:
  - `TASK_EXAMPLE_DB_URL`
  - `TASK_EXAMPLE_DB_USER` (optional)
  - `TASK_EXAMPLE_DB_PASSWORD` (optional)

## Run

```
./gradlew :examples:runBasic
```

## What it does

- Creates a task definition with an ordered state machine.
- Builds a `TaskEngine` with a fixed thread pool dispatcher.
- Submits a task and waits until it reaches `COMPLETED`.

## Expected output

- Prints a submission status (`CREATED` for the first run).
- Prints the completed task id.

## Troubleshooting

- Missing DB env vars: set `TASK_EXAMPLE_DB_URL` (and optionally user/password).
- Schema errors: verify the schema script is applied to the database.
