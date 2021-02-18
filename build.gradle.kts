//parent klib
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

repositories {
    jcenter()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

plugins {
    id("org.springframework.boot") version "2.4.3" apply false
    id("io.spring.dependency-management") version "1.0.11.RELEASE" apply false
    id("com.jfrog.bintray") version "1.8.5" apply true
    kotlin("jvm") version "1.4.30" apply false
    kotlin("plugin.spring") version "1.4.30" apply false
}

allprojects {
    group = "com.github.petrbalat.klib"
    version = "0.12.0"

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
        jcenter()
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
    }

    apply {
        plugin("io.spring.dependency-management")
        plugin("maven-publish")
        plugin("com.jfrog.bintray")
    }

    bintray {
        val localProperties = File(rootDir, "local.properties").inputStream().use {
            java.util.Properties().apply {
                load(it)
            }
        }
        user = localProperties.getProperty("bintray.user")
        key = localProperties.getProperty("bintray.key")
        publish = true
        setPublications("mavenPublication")

        val projectName = this@subprojects.name
        pkg.apply {
            repo = "klib"
            this.name = projectName
            userOrg = "petrbalat"
            vcsUrl = "https://github.com/petrbalat/klib.git"
            description = "Kotlin library"
            setLabels("kotlin", "library", projectName)
            setLicenses("MIT")
            desc = description

            version.apply {
                this.name = projectName
            }
        }

    }
}
