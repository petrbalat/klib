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
//    implementation(kotlin("reflect"))

    api("org.geolatte:geolatte-geom:1.4.0")
    compileOnly("com.fasterxml.jackson.core:jackson-databind")

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
