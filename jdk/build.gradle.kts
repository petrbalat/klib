plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
//    implementation(kotlin("reflect"))

    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    compileOnly("org.slf4j:slf4j-api:1.7.30")

    testImplementation("org.slf4j:slf4j-api:1.7.30")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test-junit5"))
}

val sourcesJar by tasks.registering(Jar::class) {
    classifier = "sources"
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
