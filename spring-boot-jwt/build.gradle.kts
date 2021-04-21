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
//	compileOnly(kotlin("kotlin-reflect"))

	implementation("org.springframework.boot:spring-boot-starter-security")
	compileOnly("org.springframework.boot:spring-boot-starter-web")

	implementation("com.fasterxml.jackson.core:jackson-databind")

	//	jwt
	implementation("io.jsonwebtoken:jjwt-api:0.11.2")
	implementation("io.jsonwebtoken:jjwt-impl:0.11.2")

	developmentOnly("org.springframework.boot:spring-boot-devtools")

	testImplementation("org.springframework.boot:spring-boot-starter-json")
	testImplementation("org.springframework.boot:spring-boot-starter-web")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}
	testImplementation(kotlin("test-junit5"))
}

publishing {
	publishingKlib(this)
}
