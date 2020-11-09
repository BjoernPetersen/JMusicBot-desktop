@file:Suppress("UnstableApiUsage")

import com.diffplug.spotless.LineEnding
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.diffplug.spotless") version Plugin.SPOTLESS
    id("io.gitlab.arturbosch.detekt") version Plugin.DETEKT

    id("com.github.ben-manes.versions") version Plugin.VERSIONS

    kotlin("jvm") version Plugin.KOTLIN
    application
    idea
}

group = "com.github.bjoernpetersen"
version = "0.20.0-SNAPSHOT"

repositories {
    mavenLocal {
        mavenContent {
            snapshotsOnly()
        }
    }
    jcenter()
    maven("https://oss.sonatype.org/content/repositories/snapshots") {
        mavenContent {
            snapshotsOnly()
        }
    }
}

application {
    mainClassName = "net.bjoernpetersen.deskbot.view.DeskBot"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}

idea {
    module {
        isDownloadJavadoc = true
    }
}

spotless {
    kotlin {
        ktlint(Plugin.KTLINT)
        lineEndings = LineEnding.UNIX
        endWithNewline()
    }
    kotlinGradle {
        ktlint(Plugin.KTLINT)
        lineEndings = LineEnding.UNIX
        endWithNewline()
    }
    format("markdown") {
        target("**/*.md")
        lineEndings = LineEnding.UNIX
        endWithNewline()
    }
}

detekt {
    toolVersion = Plugin.DETEKT
    config = files("$rootDir/buildConfig/detekt.yml")
    buildUponDefaultConfig = true
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(1, TimeUnit.MINUTES)
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xnew-inference"
            )
        }
    }

    "processResources"(ProcessResources::class) {
        filesMatching("**/version.properties") {
            filter {
                it.replace("%APP_VERSION%", version.toString())
            }
        }
    }

    withType<Test> {
        useJUnitPlatform()
    }

    withType<Jar> {
        from(project.projectDir) {
            include("LICENSE")
        }
    }

    dependencyUpdates {
        rejectVersionIf {
            isUnstable(candidate.version, currentVersion)
        }
    }
}

dependencies {
    runtimeOnly(
        group = "org.slf4j",
        name = "slf4j-simple",
        version = Lib.SLF4J
    )
    runtimeOnly(group = "org.xerial", name = "sqlite-jdbc", version = Lib.SQLITE)
    implementation(
        group = "com.github.bjoernpetersen",
        name = "musicbot",
        version = Lib.MUSICBOT
    ) {
        isChanging = Lib.MUSICBOT.contains("SNAPSHOT")
    }

    implementation(
        group = "org.jetbrains.kotlinx",
        name = "kotlinx-coroutines-javafx",
        version = Lib.KOTLIN_COROUTINES
    )

    // Ktor
    implementation(
        group = "io.ktor",
        name = "ktor-client-okhttp",
        version = Lib.KTOR
    )
    implementation(
        group = "io.ktor",
        name = "ktor-client-jackson",
        version = Lib.KTOR
    )
    implementation(
        group = "io.ktor",
        name = "ktor-locations",
        version = Lib.KTOR
    )
    implementation(
        group = "io.ktor",
        name = "ktor-jackson",
        version = Lib.KTOR
    )

    // JavaFX
    implementation("org.controlsfx:controlsfx:${Lib.CONTROLS_FX}")

    implementation(
        group = "com.fasterxml.jackson.core",
        name = "jackson-databind",
        version = Lib.JACKSON
    )
    implementation(
        group = "com.fasterxml.jackson.module",
        name = "jackson-module-kotlin",
        version = Lib.JACKSON
    )
    implementation(
        group = "com.fasterxml.jackson.dataformat",
        name = "jackson-dataformat-yaml",
        version = Lib.JACKSON
    )

    testRuntimeOnly(
        group = "org.junit.jupiter",
        name = "junit-jupiter-engine",
        version = Lib.JUNIT
    )
    testImplementation(
        group = "org.junit.jupiter",
        name = "junit-jupiter-api",
        version = Lib.JUNIT
    )
    testImplementation(
        group = "name.falgout.jeffrey.testing.junit5",
        name = "guice-extension",
        version = Lib.JUNIT_GUICE
    )
    testImplementation(
        group = "io.ktor",
        name = "ktor-network-tls",
        version = Lib.KTOR
    )
    testImplementation(group = "io.mockk", name = "mockk", version = Lib.MOCK_K)
    testImplementation(group = "org.assertj", name = "assertj-core", version = Lib.ASSERT_J)
}
