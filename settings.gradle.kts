pluginManagement {
    repositories {
        gradlePluginPortal()
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
include("spring-boot-jwt", "spring-boot-web", "spring-boot-webflux", "spring-boot-mail",
    "spring-boot-nats", "spring-boot-image", "spring-boot-image",  "spring-boot-graphql", "geom", "retrofit2", "jdk", "pdf", "qr", "ftp")

