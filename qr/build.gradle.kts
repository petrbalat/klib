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
    implementation(project(":jdk"))
//    implementation(kotlin("reflect"))

    //	  Zxing - QRCode generovani
    api("com.google.zxing:core:3.4.1")
    api("com.google.zxing:javase:3.4.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(kotlin("test-junit5"))
}

publishing {
    publishingKlib(this)
}
