package cz.petrbalat.klib.spring.nats

/**
 * @author Petr Balat
 */

import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Message
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Method
import kotlin.coroutines.suspendCoroutine

typealias StreamingMessage = io.nats.streaming.Message

@kotlin.ExperimentalStdlibApi
fun Method.invokeMessage(it: Message, bean: Any, mapper: ObjectMapper): Any? =
        this.invokeMessage(it.data, it, bean, mapper)

@kotlin.ExperimentalStdlibApi
fun Method.invokeMessage(it: StreamingMessage, bean: Any, mapper: ObjectMapper): Any? =
        this.invokeMessage(it.data, it, bean, mapper)

@kotlin.ExperimentalStdlibApi
fun Method.invokeMessage(data_: ByteArray, message: Any, bean: Any, mapper: ObjectMapper): Any? {
    //první parametr data
    val dataType = this.parameterTypes[0]
    val data: Any = if (dataType == String::class.java) data_.decodeToString()
    else mapper.readValue(data_, dataType)

    var args: Array<Any> = arrayOf(data)
    val isSuspend = this.parameterTypes.last().name == "kotlin.coroutines.Continuation"
    val parCount: Int = if(isSuspend) 2 else 1
    if (this.parameterCount > parCount) {
        //druhý parametr musí být zpráva
        args = arrayOf(data, message)
    }
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
}

const val PREFIX = "nats.streaming"
const val NATS_CLUSTER_ID = "\${$PREFIX.clusterId}"
const val NATS_CLIENT_ID = "\${$PREFIX.clientId}"
