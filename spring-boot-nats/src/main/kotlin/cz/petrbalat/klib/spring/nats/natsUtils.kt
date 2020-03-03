package cz.petrbalat.klib.spring.nats


import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Message
import java.lang.reflect.Method

@kotlin.ExperimentalStdlibApi
fun Method.invokeMessage(it: Message, bean: Any, mapper: ObjectMapper) {
    this.invokeMessage(it.data, it, bean, mapper)
}

@kotlin.ExperimentalStdlibApi
fun Method.invokeMessage(it: io.nats.streaming.Message, bean: Any, mapper: ObjectMapper) {
    this.invokeMessage(it.data, it, bean, mapper)
}

@kotlin.ExperimentalStdlibApi
fun Method.invokeMessage(data: ByteArray, message: Any, bean: Any, mapper: ObjectMapper) {
    //první parametr data
    val dataType = this.parameterTypes[0]
    val data: Any = if (dataType == String::class.java) data.decodeToString()
    else mapper.readValue(data, dataType)

    var args: Array<Any> = arrayOf(data)
    if (this.parameterCount > 1) {
        //druhý parametr musí být zpráva
        args = arrayOf(data, message)
    }
    this.invoke(bean, *args)
}

const val NATS_PREFIX = "nats.streaming"
const val NATS_CLUSTER_ID = "\${$NATS_PREFIX.clusterId}"
const val NATS_CLIENT_ID = "\${$NATS_PREFIX.clientId}"
