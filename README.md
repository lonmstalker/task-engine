# Task Engine

Task execution engine with Postgres storage, Kafka outbox publishing, and Spring Boot auto-configuration.

## Documentation

- English: `docs/en/README.md`
- Russian: `docs/ru/README.md`

## Modules

- `task-lib-api`
- `task-lib-impl`
- `task-lib-kafka`
- `task-lib-spring-boot-starter`
- `task-lib-integration-test`

## Release

- Update `gradle.properties` version and `CHANGELOG.md`.
- Publish locally: `./gradlew publishToMavenLocal`
- Publish a release: `./gradlew -Prelease publish` or `./gradlew -PreleaseVersion=1.0.0 publish`
  (requires signing + POM metadata in `gradle.properties`)

## License

Apache License 2.0. See `LICENSE`.
