package cz.petrbalat.klib.spring.nats

/**
 * @author Petr Balat
 */

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Message
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import java.lang.reflect.Method
import kotlin.coroutines.suspendCoroutine

typealias StreamingMessage = io.nats.streaming.Message

fun Method.invokeMessage(it: Message, bean: Any, mapper: ObjectMapper, logger: Logger): Any? =
    this.invokeMessage(it.data, it, bean, mapper, logger)

fun Method.invokeMessage(it: StreamingMessage, bean: Any, mapper: ObjectMapper, logger: Logger): Any? =
    this.invokeMessage(it.data, it, bean, mapper, logger)

private fun Method.invokeMessage(data_: ByteArray, message: Any, bean: Any, mapper: ObjectMapper, logger: Logger): Any? {
    //první parametr data
    val dataType = this.parameterTypes[0]
    val data: Any = if (dataType == String::class.java) data_.decodeToString()
    else mapper.readValue(data_, dataType)

    var args: Array<Any> = arrayOf(data)
    val isSuspend = this.parameterTypes.last().name == "kotlin.coroutines.Continuation"
    val parCount: Int = if (isSuspend) 2 else 1
    if (this.parameterCount > parCount) {
        //druhý parametr musí být zpráva
        args = arrayOf(data, message)
    }
    try {
        //kotlin suspend fun
        if (isSuspend) {
            return runBlocking {
                suspendCoroutine<Any?> {
                    args += it
                    invoke(bean, *args)
                }
            }
        }
        return invoke(bean, *args)
    } catch (th: Throwable) {
        logger.error(th.message, th)
        throw th
    }
}

const val PREFIX = "nats.streaming"
const val NATS_CLUSTER_ID = "\${$PREFIX.clusterId}"
const val NATS_CLIENT_ID = "\${$PREFIX.clientId}"
