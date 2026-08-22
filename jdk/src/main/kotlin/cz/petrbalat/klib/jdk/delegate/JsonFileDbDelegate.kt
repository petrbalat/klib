package cz.petrbalat.klib.jdk.delegate

import cz.petrbalat.klib.jdk.io.mkdirIfNotExist
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.File
import java.io.Reader
import java.io.Writer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
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
                emptyInstance()
            }
        }
    }

    //pole nemá bezparametrický konstruktor, na rozdíl od POJO musí vzniknout přes Array.newInstance
    @Suppress("UNCHECKED_CAST")
    private fun emptyInstance(): T =
        if (clazz.isArray) java.lang.reflect.Array.newInstance(clazz.componentType, 0) as T
        else clazz.getDeclaredConstructor().newInstance()

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        synchronized(lock) {
            //zápis přes dočasný soubor + atomic move, aby výpadek uprostřed zápisu neponechal poškozený dbFile
            val tmpFile = File(dbFile.parentFile, "$dbName.tmp")
            try {
                tmpFile.bufferedWriter().use { writer: BufferedWriter -> serialize(value, writer) }
                Files.move(tmpFile.toPath(), dbFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (th: Throwable) {
                logger.error("Chyba při serializaci db ${clazz.name}", th)
                tmpFile.delete()
            }
        }
    }
}
