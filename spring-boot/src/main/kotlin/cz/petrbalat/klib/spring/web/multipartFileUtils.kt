package cz.petrbalat.klib.spring.web

import cz.petrbalat.klib.jdk.io.permsDefault
import cz.petrbalat.klib.jdk.io.pureFileName
import cz.petrbalat.klib.jdk.string.randomString
import cz.petrbalat.klib.jdk.tryOn
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * vrátí fileName
 */
val MultipartFile.fileName: String
    get() {
        val name: String = parseOriginalFilename() ?: randomString(4)
        val newFile = File(name)
        val newName = pureFileName(newFile.nameWithoutExtension)
        return "$newName.${newFile.extension}"
    }

/**
 * pokud se jedná o cestu k souboru tak vracím pouze název souboru jinak originalFilename
 */
fun MultipartFile.parseOriginalFilename(): String? = originalFilename?.substringAfterLast("/")?.substringAfterLast("\\")

/**
 * Zkopíruje do adresáře s directory.
 *
 * odstraní z názvu diakritiku, mezery apod a přidá do názvu random string pokud již existuje soubor
 */
fun MultipartFile.copyTo(directory: String, overwrite: Boolean = false,
                         perms: Set<PosixFilePermission> = permsDefault): String {
    val fileName = this.fileName
    val destFile = File(directory, fileName).let {
        if (it.exists() && !overwrite) File(directory, "${it.nameWithoutExtension}-${randomString(4)}.${it.extension}") // přidám náhodný řetězec
        else if (it.exists() && overwrite) {
            it.delete()
            it
        }
        else it
    }
    this.transferTo(destFile)
    tryOn {
        Files.setPosixFilePermissions(destFile.toPath(), perms)
    }

    return destFile.name
}
