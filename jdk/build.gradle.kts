import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot")
    kotlin("jvm")
}

tasks.getByName<Jar>("jar") {
    enabled = true
}
tasks.getByName<BootJar>("bootJar") {
    enabled = false
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
//    implementation(kotlin("reflect"))

    compileOnly("com.fasterxml.jackson.core:jackson-databind")
    compileOnly("org.slf4j:slf4j-api")

    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.slf4j:slf4j-api")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(kotlin("test-junit5"))
}
