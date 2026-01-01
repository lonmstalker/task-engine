import com.github.spotbugs.snom.Confidence
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

plugins {
    id("base")
    id("com.github.spotbugs") version "6.0.20" apply false
}

fun Project.requireProperty(
    name: String
): String {
    return providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Missing required Gradle property '$name' for release publishing.")
}

val releaseVersion = providers.gradleProperty("releaseVersion").orNull
    ?.trim()
    ?.ifBlank { null }
val isRelease = providers.gradleProperty("release").isPresent || releaseVersion != null
val resolvedVersion = releaseVersion
    ?: if (isRelease) project.version.toString().removeSuffix("-SNAPSHOT") else project.version.toString()

if (isRelease && resolvedVersion.endsWith("-SNAPSHOT")) {
    throw GradleException(
        "Release version must not end with -SNAPSHOT. Set -PreleaseVersion=... or update gradle.properties."
    )
}

val publishableProjects = setOf(
    "task-lib-api",
    "task-lib-impl",
    "task-lib-kafka",
    "task-lib-spring-boot-starter"
)

val moduleDescriptions = mapOf(
    "task-lib" to "Task execution library with Postgres storage, Kafka outbox publishing, " +
        "and Spring Boot auto-configuration.",
    "task-lib-api" to "Public API for defining tasks, states, handlers, and submission.",
    "task-lib-impl" to "Task engine implementation and PostgreSQL-backed store.",
    "task-lib-kafka" to "Kafka outbox publisher and PostgreSQL outbox store.",
    "task-lib-spring-boot-starter" to "Spring Boot starter that auto-configures TaskEngine and related infrastructure.",
    "task-lib-integration-test" to "Integration tests and benchmarks.",
    "examples" to "Example applications and documentation snippets."
)

allprojects {
    group = rootProject.group
    version = resolvedVersion
    description = moduleDescriptions[name] ?: "Task Lib module: ${project.name}"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("java") {
        apply(plugin = "checkstyle")
        apply(plugin = "pmd")
        apply(plugin = "com.github.spotbugs")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withSourcesJar()
            withJavadocJar()
        }

        extensions.configure<CheckstyleExtension> {
            toolVersion = "10.17.0"
            configDirectory.set(rootProject.file("config/checkstyle"))
            isShowViolations = true
        }

        tasks.withType<Checkstyle>().configureEach {
            reports {
                xml.required.set(false)
                html.required.set(true)
            }
        }

        extensions.configure<PmdExtension> {
            toolVersion = "6.55.0"
            ruleSets = emptyList()
            ruleSetFiles = rootProject.files("config/pmd/pmd.xml")
            isIgnoreFailures = false
        }

        tasks.withType<Pmd>().configureEach {
            reports {
                xml.required.set(false)
                html.required.set(true)
            }
        }

        extensions.configure<SpotBugsExtension> {
            toolVersion.set("4.8.6")
        }

        tasks.withType<SpotBugsTask>().configureEach {
            if (name.contains("Jmh")) {
                enabled = false
                return@configureEach
            }
            effort.set(Effort.MIN)
            reportLevel.set(Confidence.HIGH)
            reports.maybeCreate("xml").required.set(false)
            reports.maybeCreate("html").required.set(true)
        }

        if (name in publishableProjects) {
            apply(plugin = "maven-publish")
            apply(plugin = "signing")

            val basePomName = providers.gradleProperty("pomName").orNull ?: "Task Lib"
            val pomName = "$basePomName - ${project.name}"
            val pomDescription = project.description
                ?: providers.gradleProperty("pomDescription").orNull
                ?: "Task Lib module: ${project.name}"
            val pomUrl = providers.gradleProperty("pomUrl").orNull?.takeIf { it.isNotBlank() }
            val pomScmUrl = providers.gradleProperty("pomScmUrl").orNull?.takeIf { it.isNotBlank() }
            val pomScmConnection = providers.gradleProperty("pomScmConnection").orNull?.takeIf { it.isNotBlank() }
            val pomScmDeveloperConnection = providers.gradleProperty("pomScmDeveloperConnection").orNull
                ?.takeIf { it.isNotBlank() }
            val pomLicenseName = providers.gradleProperty("pomLicenseName").orNull
                ?: "Apache License, Version 2.0"
            val pomLicenseUrl = providers.gradleProperty("pomLicenseUrl").orNull
                ?: "https://www.apache.org/licenses/LICENSE-2.0.txt"
            val pomDeveloperId = providers.gradleProperty("pomDeveloperId").orNull?.takeIf { it.isNotBlank() }
            val pomDeveloperName = providers.gradleProperty("pomDeveloperName").orNull?.takeIf { it.isNotBlank() }
            val pomDeveloperEmail = providers.gradleProperty("pomDeveloperEmail").orNull?.takeIf { it.isNotBlank() }

            if (isRelease) {
                if (pomUrl == null) requireProperty("pomUrl")
                if (pomScmUrl == null) requireProperty("pomScmUrl")
                if (pomScmConnection == null) requireProperty("pomScmConnection")
                if (pomScmDeveloperConnection == null) requireProperty("pomScmDeveloperConnection")
                if (pomDeveloperId == null) requireProperty("pomDeveloperId")
                if (pomDeveloperName == null) requireProperty("pomDeveloperName")
                if (pomDeveloperEmail == null) requireProperty("pomDeveloperEmail")
            }

            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("mavenJava") {
                        from(components["java"])
                        groupId = project.group.toString()
                        artifactId = project.name
                        version = project.version.toString()

                        pom {
                            name.set(pomName)
                            description.set(pomDescription)
                            if (pomUrl != null) {
                                url.set(pomUrl)
                            }
                            licenses {
                                license {
                                    name.set(pomLicenseName)
                                    url.set(pomLicenseUrl)
                                    distribution.set("repo")
                                }
                            }
                            if (pomDeveloperId != null || pomDeveloperName != null || pomDeveloperEmail != null) {
                                developers {
                                    developer {
                                        if (pomDeveloperId != null) {
                                            id.set(pomDeveloperId)
                                        }
                                        if (pomDeveloperName != null) {
                                            name.set(pomDeveloperName)
                                        }
                                        if (pomDeveloperEmail != null) {
                                            email.set(pomDeveloperEmail)
                                        }
                                    }
                                }
                            }
                            if (pomScmUrl != null || pomScmConnection != null || pomScmDeveloperConnection != null) {
                                scm {
                                    if (pomScmUrl != null) {
                                        url.set(pomScmUrl)
                                    }
                                    if (pomScmConnection != null) {
                                        connection.set(pomScmConnection)
                                    }
                                    if (pomScmDeveloperConnection != null) {
                                        developerConnection.set(pomScmDeveloperConnection)
                                    }
                                }
                            }
                        }
                    }
                }

                repositories {
                    val publishUrl = providers.gradleProperty("publishUrl").orNull?.takeIf { it.isNotBlank() }
                    if (publishUrl != null) {
                        maven {
                            name = "release"
                            url = uri(publishUrl)
                            credentials {
                                username = providers.gradleProperty("publishUsername").orNull
                                password = providers.gradleProperty("publishPassword").orNull
                            }
                        }
                    }
                }
            }

            extensions.configure<SigningExtension> {
                val signingKey = providers.gradleProperty("signingKey").orNull?.takeIf { it.isNotBlank() }
                val signingPassword = providers.gradleProperty("signingPassword").orNull?.takeIf { it.isNotBlank() }
                val signingKeyRingFile = providers.gradleProperty("signingKeyRingFile").orNull
                    ?.takeIf { it.isNotBlank() }

                if (isRelease) {
                    if (signingPassword == null) {
                        throw GradleException("signingPassword must be set for release signing.")
                    }
                    if (signingKey == null && signingKeyRingFile == null) {
                        throw GradleException("signingKey or signingKeyRingFile must be set for release signing.")
                    }
                }

                if (signingKey != null && signingPassword != null) {
                    useInMemoryPgpKeys(signingKey, signingPassword)
                }
                isRequired = isRelease
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
