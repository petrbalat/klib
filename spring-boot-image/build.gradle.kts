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
//	compileOnly(kotlin("kotlin-reflect"))
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${KlibVersions.coroutines}")
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

val sourcesJar by tasks.registering(Jar::class) {
	classifier = "sources"
	from(sourceSets.main.get().allSource)
}

publishing {
	publications {
		register("mavenJava", MavenPublication::class) {
			from(components["java"])
			artifact(sourcesJar.get())
		}
	}

	publishingKlib(this)
}

signing {
	sign(publishing.publications["mavenJava"])
}
