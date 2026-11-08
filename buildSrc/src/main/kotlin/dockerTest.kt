import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.gradle.kotlin.dsl.*

/**
 * Task `dockerTest` — testy modulu v CDC-like Linux kontejneru ze `src/dockerTest/Dockerfile`.
 * Bajtkód zkompiluje Gradle na hostu, kontejner jen spustí JUnit Console Launcher (rychlé,
 * bez Gradle v image). Bez dostupného Dockeru nebo bez user/PID namespace v Docker runtime (`dockerTestPreflight`)
 * se task přeskočí; `check` na něm závisí.
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

    val namespaceDockerOptions = listOf(
        "--cap-add=SYS_ADMIN",
        "--cap-add=DAC_OVERRIDE",
        "--cap-add=DAC_READ_SEARCH",
        "--security-opt=seccomp=unconfined",
        "--security-opt=systempaths=unconfined",
        // docker-default profil (Ubuntu/Debian s AppArmor) má `deny mount` — --mount-proc by selhal
        "--security-opt=apparmor=unconfined",
    )
    // Docker bez user/PID namespace (např. AppArmor/userns omezení na CI agentovi) by namespace testy jen přeskočil —
    // dockerTest se pak přeskočí celý s varováním; -Pklib.process.requirePidNamespace=true z toho udělá chybu.
    val requirePidNamespace: Boolean = providers.gradleProperty("klib.process.requirePidNamespace")
        .map { it.toBoolean() }.getOrElse(false)
    val preflightResult = layout.buildDirectory.file("docker-test/pid-namespace-preflight.txt")
    val preflightCommand: List<String> = listOf(docker, "run", "--rm") + namespaceDockerOptions + listOf(
        "--entrypoint=/usr/bin/tini", image, "-s", "--", "unshare",
        "--user", "--map-current-user", "--pid", "--fork", "--kill-child", "--mount-proc",
        "sh", "-c", "test \"\$\$\" -eq 1 && test -e /proc/1/comm",
    )
    val dockerTestPreflight = tasks.register("dockerTestPreflight") {
        group = "verification"
        description = "Checks that this Docker runtime permits a user and PID namespace for unshare."
        dependsOn(dockerTestImage)
        outputs.file(preflightResult)
        outputs.upToDateWhen { false }
        onlyIf { dockerAvailable() }
        doLast {
            val process = ProcessBuilder(preflightCommand).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            val result = preflightResult.get().asFile.apply { parentFile.mkdirs() }
            if (exitCode == 0) {
                result.writeText(PREFLIGHT_OK)
                return@doLast
            }
            val message = "Docker runtime does not permit user/PID namespace for unshare (exit $exitCode): $output"
            if (requirePidNamespace) throw GradleException(message)
            logger.warn("WARNING: $message\n  → dockerTest skipped; -Pklib.process.requirePidNamespace=true makes this an error")
            result.writeText(message)
        }
    }

    val dockerTest = tasks.register<Exec>("dockerTest") {
        group = "verification"
        description = "Runs tests inside a CDC-like Linux container with PID namespace support."
        dependsOn(dockerTestImage, dockerTestClasspath, dockerTestPreflight)
        inputs.file("src/dockerTest/Dockerfile")
        inputs.dir(cpDirectory)
        outputs.dir(reportsDirectory)
        val cp = cpDirectory.get().asFile
        val reports = reportsDirectory.get().asFile
        doFirst {
            reports.deleteRecursively()
            reports.mkdirs()
        }
        commandLine(
            listOf(docker, "run", "--rm") + namespaceDockerOptions + listOf("-v", "$cp:/cp:ro", "-v", "$reports:/reports", image,
            "-cp", "/cp/classes:/cp/lib/*", "org.junit.platform.console.ConsoleLauncher", "execute",
            "--scan-class-path", "/cp/classes", "--reports-dir", "/reports", "--details", "tree", "--disable-banner"),
        )
        onlyIf("Docker is available") { dockerAvailable() }
        onlyIf("Docker runtime permits user/PID namespace (dockerTestPreflight)") {
            preflightResult.get().asFile.let { it.isFile && it.readText() == PREFLIGHT_OK }
        }
    }

    tasks.named("check") { dependsOn(dockerTest) }
    dockerTestImage.configure { onlyIf { dockerAvailable() } }
}

private const val PREFLIGHT_OK = "ok"

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
