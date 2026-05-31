plugins {
    id("org.springframework.boot")
    kotlin("jvm")
}

tasks.getByName<Jar>("jar") {
    enabled = true
}
tasks.getByName<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    enabled = false
}


dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
//    implementation(kotlin("reflect"))

    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("org.slf4j:slf4j-api")

    testImplementation("org.slf4j:slf4j-api")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("tools.jackson.module:jackson-module-kotlin")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test-junit5"))
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        register("mavenJava", MavenPublication::class) {
            from(components["java"])
            artifact(sourcesJar.get())
        }
    }

    publishingKlib(this)
}

//signing {
//    sign(publishing.publications["mavenJava"])
//}
