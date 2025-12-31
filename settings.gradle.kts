plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "task-lib"

include("task-lib-api")
include("task-lib-impl")
include("task-lib-kafka")
include("task-lib-spring-boot-starter")
include("task-lib-integration-test")
include("examples")
