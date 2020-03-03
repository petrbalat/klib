import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot")
	kotlin("jvm")
	kotlin("plugin.spring")
}

tasks.getByName<Jar>("jar") {
	enabled = true
}
tasks.getByName<BootJar>("bootJar") {
	enabled = false
}

dependencies {
	implementation(kotlin("stdlib-jdk8"))
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")

	implementation("org.springframework.boot:spring-boot-starter")

	implementation("com.fasterxml.jackson.core:jackson-databind")

	//	nats
	implementation("io.nats:nats-spring:0.4.0")
	implementation("io.nats:jnats:2.6.6")
	implementation("io.nats:java-nats-streaming:2.2.3")

	testImplementation("org.springframework.boot:spring-boot-starter-json")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation(kotlin("test-junit5"))
}
