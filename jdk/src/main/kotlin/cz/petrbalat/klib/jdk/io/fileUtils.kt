package cz.petrbalat.klib.jdk.io

import cz.petrbalat.klib.jdk.GIGA
import cz.petrbalat.klib.jdk.datetime.toLocalDateTime
import java.io.File
import java.net.URLConnection
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.util.*


/**
 * Přepíše název souboru bez extension
 */
fun File.renameName(newName: String, overwrite: Boolean = false): File {
    val newFile = File(this.directory, "$newName.${this.extension}")
    if (overwrite && newFile.exists()) {
        newFile.delete()
    }
    if (this.exists() && (!newFile.exists() || overwrite)) this.renameTo(newFile)
    return newFile
}

val File.directory: File
    get() = if (isDirectory) this else parentFile!!

fun File.mkdirIfNotExist(): Boolean {
    if (!this.exists()) {
        return this.mkdir()
    }

    return this.isDirectory
}

val File.probeContentType: String?
    get() = Files.probeContentType(toPath()) ?: URLConnection.guessContentTypeFromName(name)

fun File.lastModifiedTime(): LocalDateTime {
    val path = this.toPath()
    return Files.readAttributes(path, BasicFileAttributes::class.java).lastModifiedTime().toInstant().toLocalDateTime()
}


fun File.fileSystemSpace(unit: Double = GIGA): FileSystemSpaceDto {
    val dto = FileSystemSpaceDto()
    dto.freeSpace = freeSpace / unit
    dto.usableSpace = usableSpace / unit
    dto.totalSpace = totalSpace / unit
    return dto
}

fun File.loadAsProperties(): Properties = inputStream().use {
    Properties().apply {
        this.load(it)
    }
}

class FileSystemSpaceDto {
    var freeSpace: Double = 0.0
    var usableSpace: Double = 0.0
    var totalSpace: Double = 0.0
}

fun File.asFileSystem(): FileSystem = FileSystems.newFileSystem(this.toPath(), null as? ClassLoader)
