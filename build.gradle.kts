kotlin {
    jvmToolchain(8)
}

plugins {
    kotlin("jvm") version "1.8.10" // or kotlin("multiplatform") or any other kotlin plugin
    kotlin("plugin.serialization") version "1.8.10"
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-Beta")
    implementation("ru.gildor.coroutines:kotlin-coroutines-okhttp:1.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("io.github.oshai:kotlin-logging-jvm:4.0.0-beta-22")
}

repositories {
    mavenCentral()
}