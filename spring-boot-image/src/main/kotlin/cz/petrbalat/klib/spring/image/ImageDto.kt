package cz.petrbalat.klib.spring.image

import com.fasterxml.jackson.annotation.JsonProperty
import cz.petrbalat.klib.jdk.io.directory
import cz.petrbalat.klib.jdk.string.randomString
import java.io.File

/**
 * Pomocná třída pro práci s obrázkem. Používá se jako dto pro uložení odkazu na obrázek,
 * Obsahuje metody pro další práci s
 */
data class ImageDto(val url: String, val webpUrl: String) {

    val name: String @JsonProperty get() = url.substringAfterLast("/")

    val webpName: String @JsonProperty get() = webpUrl.substringAfterLast("/")

    /**
     * Přepíše názvy (url i thumbUrl) souboru bez přílohy
     *
     * @param rootFile cesta k resources - soubor se poté vezme jako File(rootFile, url)
     * @param overwrite výchozí false - zda se má přepsat pokud již existuje
     */
    fun renameTo(newName: String, rootFile: File, overwrite: Boolean = false): ImageDto {
        val newFile: File = File(rootFile, url).copy(newName, overwrite)
        val newWebPFile: File = File(rootFile, webpUrl).copy(newName, overwrite)

        val baseUrl: String = url.substringBeforeLast("/")
        return copy(url = "$baseUrl/${newFile.name}", webpUrl = "$baseUrl/${newWebPFile.name}")
    }

    private fun File.copy(newName: String, overwrite: Boolean): File {
        var newFile = File(this.directory, "$newName.${this.extension}")
        if (newFile.exists()) {//pokud existuje
            if (overwrite) newFile.delete()
            else {
                newFile = File(this.directory, "${newName}_${randomString(2)}.${this.extension}")
            }
        }

        return this.copyTo(newFile)
    }
}

