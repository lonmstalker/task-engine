plugins {
    id("java-library")
}

dependencies {
    api(project(":task-lib-impl"))
    api(libs.spring.boot.starter)
    implementation(libs.micrometer.core)

    compileOnly(libs.spring.boot.configuration.processor)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
