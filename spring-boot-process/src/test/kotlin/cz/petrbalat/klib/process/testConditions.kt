package cz.petrbalat.klib.process

import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable

/**
 * Test spouští spravovaný proces (`unshare` + `tini` v user/PID namespace) — poběží jen tam, kde runtime projde
 * [checkPidNamespaceSupport] (Linux, util-linux, `/usr/bin/tini`, povolený user namespace). Na macOS a na hostu
 * bez namespace se přeskočí; ověří ho `dockerTest`. Testy bez spravovaných procesů tuto anotaci nemají a běží všude.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@EnabledIf("cz.petrbalat.klib.process.TestConditionsKt#pidNamespaceAvailable")
annotation class RequiresPidNamespace

/**
 * Test počítá s testovacím image ze `src/dockerTest/Dockerfile` (uživatel `pi` uid 1000, skupiny zařízení,
 * fixture `/tmp/klib-video-group-only`) — image nastavuje `KLIB_PROCESS_DOCKER_TEST=true`.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@RequiresPidNamespace
@EnabledIfEnvironmentVariable(named = "KLIB_PROCESS_DOCKER_TEST", matches = "true")
annotation class RequiresDockerTestImage

private val pidNamespace: Boolean by lazy { runCatching { checkPidNamespaceSupport() }.isSuccess }

/** podmínka pro [RequiresPidNamespace] — zjišťuje se jednou za běh JVM */
fun pidNamespaceAvailable(): Boolean = pidNamespace
