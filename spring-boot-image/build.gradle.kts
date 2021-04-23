import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot")
	kotlin("jvm")
	kotlin("plugin.spring")
	id("com.jfrog.bintray")
}

tasks.getByName<Jar>("jar") {
	enabled = true
}
tasks.getByName<BootJar>("bootJar") {
	enabled = false
}

dependencies {
	implementation(kotlin("stdlib-jdk8"))
//	compileOnly(kotlin("kotlin-reflect"))
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
	implementation("org.springframework.boot:spring-boot-starter")
	compileOnly("com.fasterxml.jackson.core:jackson-databind")

	api(project(":jdk"))
	api(project(":ftp"))
	api("org.imgscalr:imgscalr-lib:4.2")
	api("org.sejda.imageio:webp-imageio:0.1.6")

	testImplementation(project(":ftp"))
	testImplementation("org.sejda.imageio:webp-imageio:0.1.6")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation(kotlin("test-junit5"))
}

publishing {
	publishingKlib(this)
}

