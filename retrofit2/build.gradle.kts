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
    implementation(kotlin("stdlib"))
//    implementation(kotlin("reflect"))
    implementation("jakarta.xml.bind:jakarta.xml.bind-api")

    api("com.squareup.retrofit2:retrofit:2.9.0")

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
