plugins {
    id("java-library")
    id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    implementation(libs.postgresql)
    implementation(libs.kafka.clients)

    testImplementation(project(":task-lib-impl"))
    testImplementation(project(":task-lib-kafka"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)

    jmhImplementation(project(":task-lib-impl"))
    jmhImplementation(project(":task-lib-kafka"))
    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
    jmhImplementation(libs.postgresql)
    jmhImplementation(libs.testcontainers)
    jmhImplementation(libs.testcontainers.postgresql)
    jmhImplementation(libs.testcontainers.kafka)
    jmhImplementation(libs.kafka.clients)
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
}
