plugins {
    kotlin("jvm") version "1.8.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jeasy:easy-rules-core:4.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.6")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.+")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.+")
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.8.10")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("MainKt")
}