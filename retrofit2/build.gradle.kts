import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("org.springframework.boot")
    kotlin("jvm")
}

tasks.getByName<Jar>("jar") {
    enabled = true
    val name = archiveFileName.get().replace("-plan", "")
    archiveFileName.set(name)
}
tasks.getByName<BootJar>("bootJar") {
    enabled = false
}


dependencies {
    implementation(kotlin("stdlib-jdk8"))
//    implementation(kotlin("reflect"))

    api("com.squareup.retrofit2:retrofit:2.9.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(kotlin("test-junit5"))
}

publishing {
    publishingKlib(this)
}
