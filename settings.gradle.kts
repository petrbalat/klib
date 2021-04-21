pluginManagement {
    repositories {
        gradlePluginPortal()
        maven { url = uri("https://repo.spring.io/milestone") }
    }
}

rootProject.name = "klib"
include("spring-boot-jwt", "spring-boot-web", "spring-boot-mail", "spring-boot-nats", "geom", "retrofit2", "jdk", "pdf", "qr")

