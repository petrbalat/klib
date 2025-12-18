package cz.petrbalat.klib.jdk.delegate

import cz.petrbalat.klib.jdk.io.mkdirIfNotExist
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.Reader
import java.io.Writer
import kotlin.reflect.KProperty

class JsonFileDbDelegate<T>(private val clazz: Class<T>,
                            private val directory: File,
                            private val serialize: (value: T, writer: Writer) -> Unit,
                            private val deserialize: (reader: Reader) -> T,
                            private val dbName: String = if (clazz.isArray) "${clazz.componentType.simpleName.lowercase()}s.json"
                            else "${clazz.simpleName.lowercase()}.json",
                            lock: Any? = null) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val lock: Any = lock ?: this

    private val dbFile: File by lazy {
        if (!directory.mkdirIfNotExist()) {
            logger.error("Chyba při vytvoření adresáře pro databázi")
        }

        val file = File(directory, dbName)
        if (file.isDirectory && !file.delete()) {
            logger.error("Chyba při smazání adresáře ${file.path}")
        }

        if (!file.exists()) {
            if (file.createNewFile()) {
                if (clazz.isArray) {
                    file.writeText("[]")
                } else {
                    file.writeText("{}")
                }
            } else {
                logger.error("Chyba při vytvoření databáze")
            }
        }

        file
    }

    operator fun getValue(thisRef: Any, property: KProperty<*>): T = synchronized(lock) {
        dbFile.bufferedReader().use {
            try {
                deserialize(it)
            } catch (th: Throwable) {
                logger.error("Chyba při deserializaci db ${clazz.name}", th)
                clazz.getDeclaredConstructor().newInstance()
            }
        }
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(lock) {
            dbFile.bufferedWriter().use { writer: BufferedWriter ->
                try {
                    serialize(value, writer)
                } catch (th: Throwable) {
                    logger.error("Chyba při serializaci db ${clazz.name}", th)
                }
            }
        }
    }
}
