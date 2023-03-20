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
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
}

repositories {
    mavenCentral()
}