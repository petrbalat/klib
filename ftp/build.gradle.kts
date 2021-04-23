plugins {
    kotlin("jvm")
    id("org.springframework.boot")
}

tasks.getByName<Jar>("jar") {
    enabled = true
}
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}

dependencies {
    compileOnly(kotlin("stdlib-jdk8"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.4.2")
//    implementation(kotlin("reflect"))

    implementation(project(":jdk"))
    api("commons-net:commons-net:3.8.0")
    compileOnly("org.slf4j:slf4j-api:1.7.30")

    testImplementation("org.slf4j:slf4j-api:1.7.30")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.4.2")
    testImplementation("commons-net:commons-net:3.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(kotlin("test-junit5"))
}

publishing {
    publishingKlib(this)
}

