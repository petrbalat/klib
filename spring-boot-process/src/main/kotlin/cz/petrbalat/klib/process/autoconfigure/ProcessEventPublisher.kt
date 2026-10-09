package cz.petrbalat.klib.process.autoconfigure

import cz.petrbalat.klib.process.GiveUp
import cz.petrbalat.klib.process.ProcessGroup
import cz.petrbalat.klib.process.ProcessGroupsListener
import cz.petrbalat.klib.process.ProcessState
import org.springframework.context.ApplicationEventPublisher

/** Restart a vzdání se watchdogu jako Spring eventy [ProcessRestartedEvent] / [ProcessGaveUpEvent]. */
internal class ProcessEventPublisher(
    private val publisher: ApplicationEventPublisher,
    private val owner: String,
) : ProcessGroupsListener {

    override fun restarting(group: ProcessGroup, name: String, restarting: ProcessState.Restarting) =
        publisher.publishEvent(ProcessRestartedEvent(owner, group.id, name, restarting.restarts, restarting.exitCode))

    override fun gaveUp(group: ProcessGroup, giveUp: GiveUp) =
        publisher.publishEvent(ProcessGaveUpEvent(owner, group.id, giveUp))
}
