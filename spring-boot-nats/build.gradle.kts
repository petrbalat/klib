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

//TODO do společného nastavení
bintray {
	val projectName = tasks.jar.get().archiveBaseName.get()
	user = System.getProperty("bintray.user")
	key = System.getProperty("bintray.key")
	publish = true
	setPublications("mavenPublication")

	pkg.apply {
		repo = "klib"
		name = projectName
		userOrg = "petrbalat"
		vcsUrl = "https://github.com/petrbalat/klib.git"
		description = "Kotlin library"
		setLabels("kotlin", "library")
		setLicenses("MIT")
		desc = description
//        websiteUrl = pomUrl
//        issueTrackerUrl = pomIssueUrl
//        githubReleaseNotesFile = githubReadme

		version.apply {
			name = projectName
//            desc = pomDesc
//            released = nowSeconds().toString()
//            vcsTag = artifactVersion
		}
	}

}

val sourcesJar by tasks.creating(Jar::class) {
	archiveClassifier.set("sources")
	from(sourceSets.getByName("main").allSource)
}

publishing {
	publications {
		create<MavenPublication>("mavenPublication") {
			from(components["java"])

			artifact(sourcesJar)

			versionMapping {
				usage("java-api") {
					fromResolutionOf("runtimeClasspath")
				}
				usage("java-runtime") {
					fromResolutionResult()
				}
			}

			pom {
				scm {
					connection.set("scm:git:git://github.com/petrbalat/klib.git")
					developerConnection.set("scm:git:https://github.com/petrbalat/klib.git")
					url.set("https://github.com/petrbalat/klib")
				}
			}

			repositories {
				maven {
					url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
				}
			}
		}
	}
}
