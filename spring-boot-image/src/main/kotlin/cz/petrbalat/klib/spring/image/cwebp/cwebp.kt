package cz.petrbalat.klib.spring.image.cwebp

import cz.petrbalat.klib.jdk.io.directory
import java.io.File
import java.util.concurrent.TimeUnit

var CWEBP_PATH:String? = null

fun convertToWebP(
        file: File, quality: Int = 80, m: Int = 6, override: Boolean = false,
        fileName: String = file.nameWithoutExtension, heightThumbnail: Int? = null
): File? {
    val cwebpPath: String = CWEBP_PATH ?: return null

    val newFile = File(file.directory, "${fileName}.webp")

    if (newFile.exists()) {
        if (override) newFile.delete()
        else return newFile
    }

    val resize:String = if(heightThumbnail != null) " -resize 0 $heightThumbnail" else ""
    val process: Process = Runtime.getRuntime().exec("$cwebpPath -q $quality$resize -m $m ${file.absolutePath} -o ${newFile.absolutePath}")
    process.waitFor(10, TimeUnit.SECONDS)
    return newFile
}
