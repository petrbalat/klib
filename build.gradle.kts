//parent klib
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
        mavenCentral()
    }
}

repositories {
    jcenter()
    mavenCentral()
//    maven { url = uri("https://repo.spring.io/milestone") }
}

plugins {
    id("org.springframework.boot") version "2.3.2.RELEASE" apply false
    id("io.spring.dependency-management") version "1.0.9.RELEASE" apply false
    kotlin("jvm") version "1.4.0-rc" apply false
    kotlin("plugin.spring") version "1.4.0-rc" apply false
}

allprojects {
    version = "0.6.0"

    tasks.withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict", "-progressive", "-Xjvm-default=all", "-Xinline-classes")
            jvmTarget = "1.8"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

subprojects {
    repositories {
        jcenter()
        mavenCentral()
//        maven { url = uri("https://repo.spring.io/milestone") }
    }

    apply {
        plugin("io.spring.dependency-management")
        plugin("maven")
    }
}
//
//configure(subprojects) {
//
//    dependencies {
//
//    }
//}
