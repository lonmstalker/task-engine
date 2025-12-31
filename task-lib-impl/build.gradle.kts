plugins {
    id("java-library")
}

dependencies {
    api(project(":task-lib-api"))
    implementation(libs.slf4j.api)
    implementation(libs.guava)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
