plugins {
    id("java")
}

dependencies {
    implementation(project(":task-lib-impl"))
    implementation(project(":task-lib-kafka"))
    implementation(project(":task-lib-spring-boot-starter"))
    implementation(libs.postgresql)
}

tasks.register<JavaExec>("runBasic") {
    group = "application"
    description = "Runs the basic TaskEngine example."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.lonmstalker.task.examples.basic.BasicTaskEngineExample")
}

tasks.register<JavaExec>("runKafkaOutbox") {
    group = "application"
    description = "Runs the Kafka outbox publisher example."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.lonmstalker.task.examples.kafka.KafkaOutboxPublisherExample")
}

tasks.register<JavaExec>("runSpringBoot") {
    group = "application"
    description = "Runs the Spring Boot starter example."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.lonmstalker.task.examples.springboot.SpringBootTaskEngineExample")
}
