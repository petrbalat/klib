plugins {
    kotlin("jvm")
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
//    implementation(kotlin("reflect"))

    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.11.0")
    compileOnly("org.slf4j:slf4j-api:1.7.30")

    testImplementation("org.slf4j:slf4j-api:1.7.30")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.11.0")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.5.2")
    testImplementation(kotlin("test-junit5"))
}

publishing {
    publishingKlib(this)
}

