package cz.petrbalat.klib.process.autoconfigure

import cz.petrbalat.klib.process.GiveUp

/**
 * Watchdog procesu skupiny vyčerpal restarty. Publikuje se z vlákna watchdogu — synchronní listener smí
 * skupinu zastavit (i přes `runBlocking`), jinak nesmí dlouho blokovat. Po `ProcessGroup.stop()` se už nepublikuje.
 */
class ProcessGaveUpEvent(val owner: String, val groupId: String, val giveUp: GiveUp) {
    override fun toString(): String = "ProcessGaveUpEvent(owner=$owner, groupId=$groupId, giveUp=$giveUp)"
}

/**
 * Proces skupiny spadl a watchdog ho za [RestartPolicy.delay][cz.petrbalat.klib.process.RestartPolicy.delay]
 * restartuje ([restarts]. restart; [exitCode] null = selhalo spuštění). Publikuje se z vlákna watchdogu.
 */
class ProcessRestartedEvent(val owner: String, val groupId: String, val name: String, val restarts: Int, val exitCode: Int?) {
    override fun toString(): String =
        "ProcessRestartedEvent(owner=$owner, groupId=$groupId, name=$name, restarts=$restarts, exitCode=$exitCode)"
}
