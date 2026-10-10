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
	implementation(project(":jdk"))
	implementation(kotlin("stdlib"))

	implementation("org.springframework.boot:spring-boot-starter-mail")
	compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core") // jen pro sendSuspend

	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation(kotlin("test-junit5"))
	testImplementation("org.springframework.boot:spring-boot-starter-restclient")
	testImplementation(kotlin("reflect"))
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
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

//signing {
//	sign(publishing.publications["mavenJava"])
//}

