package cz.petrbalat.klib.jdk.io

import cz.petrbalat.klib.jdk.string.randomString
import cz.petrbalat.klib.jdk.string.removeDiakritiku
import cz.petrbalat.klib.jdk.string.removeNotAlowedInFileName
import cz.petrbalat.klib.jdk.tryOrNull
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

val emptyRegex by lazy { " +".toRegex() }

/**
 * odstraní diakritiku a mezery
 */
fun pureFileName(originalFilename: String): String = removeNotAlowedInFileName(originalFilename, " ")
    .removeDiakritiku().toLowerCase().replace(emptyRegex, "_")

/**
 * zkopíruje do adresáře s path.
 * odstraní z názvu diakritiku, mezery apod a přidá do názvu random string pokud již existuje soubor
 */
fun InputStream.copyTo(
    originalFilename: String, path: String, pathPrepend: String = "",
    perms: Set<PosixFilePermission> = permsDefault
): File {
    val origName: String = pureFileName(originalFilename)
    val uploadFile: File = File(path, "$pathPrepend$origName").let {
        if (it.exists()) File(path, "${randomString(4).toLowerCase()}-$pathPrepend$origName") else it
    }
    assert(uploadFile.createNewFile())
    tryOrNull {
        Files.setPosixFilePermissions(uploadFile.toPath(), perms)
    }
    FileOutputStream(uploadFile, false).use {
        this.use { inputStream ->
            inputStream.copyTo(it)
        }
    }

    return uploadFile
}

fun ZipInputStream.entrySequence(): Sequence<ZipEntry> {
    return generateSequence { nextEntry }
}

fun ZipInputStream.contentSequence(charsets: Charset = Charsets.UTF_8): Sequence<Pair<ZipEntry, String>> =
    entrySequence().map { zipEntry ->
        val ous = ByteArrayOutputStream()
        this.writeTo(ous)
        val content = String(ous.toByteArray(), charsets)
        Pair(zipEntry, content)
    }

fun ZipInputStream.writeTo(outputStream: OutputStream): Unit {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    outputStream.use { fos ->
        while (true) {
            val len = this.read(buffer)
            if (len <= 0) {
                break
            }
            fos.write(buffer, 0, len)
        }
    }
}

const val IMAGE_TYPE: String = "image"

/**
 * zkopíruje do souboru
 */
fun InputStream.toFile(file: File): Long = this.use { ins ->
    file.outputStream().use {
        ins.copyTo(it)
    }
}

val permsDefault: Set<PosixFilePermission> = setOf(
    PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE
)

val allPermission: Set<PosixFilePermission> = setOf(
    PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_READ,  PosixFilePermission.OWNER_READ,
    PosixFilePermission.OTHERS_WRITE, PosixFilePermission.GROUP_WRITE,  PosixFilePermission.OWNER_WRITE,
    PosixFilePermission.OTHERS_EXECUTE, PosixFilePermission.GROUP_EXECUTE,  PosixFilePermission.OWNER_EXECUTE,
)
