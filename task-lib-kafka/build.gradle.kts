plugins {
    id("java-library")
}

dependencies {
    api(project(":task-lib-api"))
    api(libs.kafka.clients)
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
