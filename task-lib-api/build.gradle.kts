plugins {
    id("java-library")
}

dependencies {
    api(libs.checker.qual)
    api(libs.jcip.annotations)

    implementation(libs.guava)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj)
    testRuntimeOnly(libs.junit.platform.launcher)
}
