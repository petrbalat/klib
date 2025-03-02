//parent klib
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
}

plugins {
    id("org.springframework.boot") version "3.3.9" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
}

allprojects {
    group = "io.github.petrbalat"
    version = "1.2.1"

    tasks.withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive", "-Xjvm-default=all")
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

}

subprojects {
    repositories {
        mavenCentral()
//        maven { url = uri("https://repo.spring.io/milestone") }
    }

    apply {
        plugin("io.spring.dependency-management")
        plugin("maven-publish")
        plugin("signing")
    }
}
