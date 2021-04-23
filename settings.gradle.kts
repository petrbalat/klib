pluginManagement {
    repositories {
        gradlePluginPortal()
        maven( url = uri("https://dl.bintray.com/kotlin/kotlin-eap/"))
        maven { url = uri("https://repo.spring.io/milestone") }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.springframework.boot") {
                useModule("org.springframework.boot:spring-boot-gradle-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "klib"
include("spring-boot-jwt", "spring-boot-web", "spring-boot-mail", "spring-boot-nats", "geom", "retrofit2", "jdk", "pdf", "qr", "ftp")

