# Task Engine

[![CI](https://github.com/lonmstalker/task-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/lonmstalker/task-engine/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.lonmstalker/task-lib-api.svg?label=task-lib-api)](https://central.sonatype.com/artifact/io.github.lonmstalker/task-lib-api)

Task execution engine with Postgres storage, Kafka outbox publishing, and Spring Boot auto-configuration.

## Highlights

- Idempotent task submission with state machine transitions.
- PostgreSQL-backed task store with leasing, retries, and dependency tracking.
- Kafka outbox publisher for reliable event delivery.
- Spring Boot auto-configuration for quick wiring.

## Install

### Gradle (Kotlin)

```kotlin
dependencies {
    implementation("io.github.lonmstalker:task-lib-api:1.0.0")
    implementation("io.github.lonmstalker:task-lib-impl:1.0.0")
    implementation("io.github.lonmstalker:task-lib-kafka:1.0.0")
    implementation("io.github.lonmstalker:task-lib-spring-boot-starter:1.0.0")
}
```

### Maven

```xml
<dependency>
  <groupId>io.github.lonmstalker</groupId>
  <artifactId>task-lib-api</artifactId>
  <version>1.0.0</version>
</dependency>
```

## Documentation

- English: `docs/en/README.md`
- Russian: `docs/ru/README.md`

## Modules

- `task-lib-api` - public API types.
- `task-lib-impl` - task engine implementation and Postgres store.
- `task-lib-kafka` - Kafka outbox publisher and Postgres outbox store.
- `task-lib-spring-boot-starter` - Spring Boot auto-configuration.
- `task-lib-integration-test` - integration tests and JMH benchmarks.

## Maven Central

- `io.github.lonmstalker:task-lib-api`
- `io.github.lonmstalker:task-lib-impl`
- `io.github.lonmstalker:task-lib-kafka`
- `io.github.lonmstalker:task-lib-spring-boot-starter`

## Release

- Update `gradle.properties` version and `CHANGELOG.md`.
- Publish locally: `./gradlew publishToMavenLocal`
- Publish a release: `./gradlew -Prelease publish` or `./gradlew -PreleaseVersion=1.0.0 publish`
  (requires signing + POM metadata in `gradle.properties`)

## License

Apache License 2.0. See `LICENSE`.
