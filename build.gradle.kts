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
    id("org.springframework.boot") version "2.7.13" apply false
    id("io.spring.dependency-management") version "1.0.15.RELEASE" apply false
    kotlin("jvm") version "1.8.21" apply false
    kotlin("plugin.spring") version "1.8.21" apply false
}

allprojects {
    group = "io.github.petrbalat"
    version = "0.95.0"

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive", "-Xjvm-default=all")
            jvmTarget = "1.8"
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
