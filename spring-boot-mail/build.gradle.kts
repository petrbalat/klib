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
	implementation("org.springframework.boot:spring-boot-starter-mail")

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation(kotlin("test-junit5"))
}
