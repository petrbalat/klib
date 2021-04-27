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
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
//    implementation(kotlin("reflect"))

    //  pdf
    implementation("org.xhtmlrenderer:flying-saucer-core:9.1.20")
    implementation("org.xhtmlrenderer:flying-saucer-pdf-itext5:9.1.20")

    compileOnly("org.springframework.boot:spring-boot-starter-web")
//    compileOnly("org.slf4j:slf4j-api:1.7.30")


    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(kotlin("test-junit5"))
}

publishing {
    publishingKlib(this)
}

