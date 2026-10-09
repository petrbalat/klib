import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
	id("org.springframework.boot")
	kotlin("jvm")
	kotlin("plugin.spring")
}

tasks.getByName<Jar>("jar") {
	enabled = true
	archiveClassifier.set("")
}
tasks.getByName<BootJar>("bootJar") {
	enabled = false
}

dependencies {
	implementation(kotlin("stdlib"))
	implementation(kotlin("reflect"))                         // constructor binding data class ProcessProperties
	api("org.jetbrains.kotlinx:kotlinx-coroutines-core")     // CoroutineScope/suspend ve veřejném API
	implementation("org.springframework.boot:spring-boot-autoconfigure")
	implementation("org.slf4j:slf4j-api")
	implementation(project(":jdk"))                          // virtualThreadDispatcher
	compileOnly("io.micrometer:micrometer-core")              // metriky, jen když je konzument má

	testImplementation("io.micrometer:micrometer-core")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
	testImplementation(kotlin("test-junit5"))
}

val sourcesJar by tasks.registering(Jar::class) {
	archiveClassifier.set("sources")
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

// testy se skutečnými procesy na Linuxu (/proc, sirotci, zombie) — viz src/dockerTest/Dockerfile
registerDockerTest("klib-process-test")
