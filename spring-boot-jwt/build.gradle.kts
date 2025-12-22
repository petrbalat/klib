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
//	compileOnly(kotlin("kotlin-reflect"))

	implementation("org.springframework.boot:spring-boot-starter-security")
	compileOnly("org.springframework.boot:spring-boot-starter-web")

	implementation("com.fasterxml.jackson.core:jackson-databind")

	//	jwt
	implementation("io.jsonwebtoken:jjwt-api:0.11.5")
	implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
	implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

	developmentOnly("org.springframework.boot:spring-boot-devtools")

	testImplementation("org.springframework.boot:spring-boot-starter-json")
//	testImplementation("org.springframework.boot:spring-boot-starter-web")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
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

//signing {
//	sign(publishing.publications["mavenJava"])
//}
