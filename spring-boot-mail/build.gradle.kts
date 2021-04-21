import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot")
	kotlin("jvm")
	kotlin("plugin.spring")
	id("com.jfrog.bintray")
}

tasks.getByName<Jar>("jar") {
	enabled = true
	val name = archiveFileName.get().replace("-plan", "")
	archiveFileName.set(name)
}
tasks.getByName<BootJar>("bootJar") {
	enabled = false
}

dependencies {
	implementation(project(":jdk"))
	implementation(kotlin("stdlib-jdk8"))

	implementation("org.springframework.boot:spring-boot-starter-mail")

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation(kotlin("test-junit5"))
}

publishing {
	publishingKlib(this)
}

