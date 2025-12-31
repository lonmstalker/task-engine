plugins {
    id("java-library")
    id("me.champeau.jmh") version "0.7.2"
}

dependencies {
    implementation(libs.postgresql)

    testImplementation(project(":task-lib-impl"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.junit.platform.launcher)

    jmhImplementation(project(":task-lib-impl"))
    jmhImplementation(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator.annprocess)
    jmhImplementation(libs.postgresql)
    jmhImplementation(libs.testcontainers)
    jmhImplementation(libs.testcontainers.postgresql)
}

jmh {
    warmupIterations.set(2)
    iterations.set(3)
    fork.set(1)
}
