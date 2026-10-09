import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.*

/**
 * Task `dockerTest` — testy modulu v Linux kontejneru ze `src/dockerTest/Dockerfile`.
 * Bajtkód zkompiluje Gradle na hostu, kontejner jen spustí JUnit Console Launcher (rychlé,
 * bez Gradle v image). Bez dostupného Dockeru se task přeskočí; `check` na něm závisí.
 */
fun Project.registerDockerTest(image: String) {
    val sourceSets = extensions.getByName("sourceSets") as SourceSetContainer
    val test = sourceSets.getByName("test")

    // launcher z BOM — stejná verze JUnit Platform jako testy (standalone jar by ji zdvojil)
    val dockerTestRuntime = configurations.create("dockerTestRuntime") {
        extendsFrom(configurations.getByName("testRuntimeClasspath"))
    }
    dependencies.add(dockerTestRuntime.name, "org.junit.platform:junit-platform-console")

    val docker: String = dockerExecutable()
    val cpDirectory = layout.buildDirectory.dir("docker-test/cp")
    val reportsDirectory = layout.buildDirectory.dir("test-results/dockerTest")

    val dockerTestImage = tasks.register<Exec>("dockerTestImage") {
        inputs.file("src/dockerTest/Dockerfile")
        commandLine(docker, "build", "-q", "-t", image, "-f", "src/dockerTest/Dockerfile", "src/dockerTest")
    }

    val dockerTestClasspath = tasks.register<Sync>("dockerTestClasspath") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        // test před main — testovací resources mají přednost
        into(cpDirectory)
        into("classes") {
            from(test.output)
            from(sourceSets.getByName("main").output)
        }
        into("lib") {
            from(dockerTestRuntime.filter { it.isFile && it.name.endsWith(".jar") })
        }
    }

    val dockerTest = tasks.register<Exec>("dockerTest") {
        group = "verification"
        description = "Runs tests inside a Linux container (src/dockerTest/Dockerfile)."
        dependsOn(dockerTestImage, dockerTestClasspath)
        inputs.dir(cpDirectory)
        outputs.dir(reportsDirectory)
        val cp = cpDirectory.get().asFile
        val reports = reportsDirectory.get().asFile
        doFirst {
            reports.deleteRecursively()
            reports.mkdirs()
        }
        commandLine(
            docker, "run", "--rm", "-v", "$cp:/cp:ro", "-v", "$reports:/reports", image,
            "java", "-cp", "/cp/classes:/cp/lib/*", "org.junit.platform.console.ConsoleLauncher", "execute",
            "--scan-class-path", "/cp/classes", "--reports-dir", "/reports", "--details", "tree", "--disable-banner",
        )
        onlyIf { dockerAvailable() }
    }

    tasks.named("check") { dependsOn(dockerTest) }
    dockerTestImage.configure { onlyIf { dockerAvailable() } }
}

/** daemon spuštěný z IDE nemusí mít v PATH /usr/local/bin (Docker Desktop) ani /opt/homebrew/bin */
private fun dockerExecutable(): String {
    val path: List<String> = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
    return (path + listOf("/usr/local/bin", "/opt/homebrew/bin", "/usr/bin"))
        .map { java.io.File(it, "docker") }
        .firstOrNull { it.canExecute() }
        ?.path ?: "docker"
}

private fun dockerAvailable(): Boolean = runCatching {
    ProcessBuilder(dockerExecutable(), "info")
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start().waitFor() == 0
}.getOrDefault(false)
