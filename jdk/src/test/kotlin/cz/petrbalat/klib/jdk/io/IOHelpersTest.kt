package cz.petrbalat.klib.jdk.io

import cz.petrbalat.klib.jdk.system.isWindows
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Paths
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Created by Petr on 29. 4. 2015.
 */
class IOHelpersTest {

    @Test
    fun zipEntrySequence() {
        ZipInputStream(FileInputStream(zipPath)).use { zip ->
            val list = zip.entrySequence().toList()

            assertEquals(3, list.size)
            assertTrue(list.any { it.name == "1.txt" })
            assertTrue(list.any { it.name == "2soubor.txt" })
            assertTrue(list.any { it.name == "3.txt" })
        }
    }

    @Test
    fun zipWriteTo() {
        val pathTestFile = Paths.get("testZip.txt").toAbsolutePath().toString()
        val testWriteFile = File(pathTestFile)
        testWriteFile.delete()
        val fos = FileOutputStream(testWriteFile)

        ZipInputStream(FileInputStream(zipPath)).use { zip ->
            zip.entrySequence().firstOrNull { it.name == "2soubor.txt" }?.let {
                zip.writeTo(fos)
            }
        }

        assertEquals("ble ble", testWriteFile.readText())
    }

    @Test
    fun contentSequence() {
        ZipInputStream(FileInputStream(zipPath)).use { zip ->
            zip.contentSequence().forEach {
                if (it.first.name == "2soubor.txt") {
                    assertEquals("ble ble", it.second)
                } else if (it.first.name == "1.txt") {
                    assertEquals("aaa", it.second)
                } else if (it.first.name == "3.txt") {
                    assertEquals("ccccc", it.second)
                }
            }
        }
    }

    @Test
    fun loadAsProperties() {
        val properties = File(propertiesPath).loadAsProperties()
        assertEquals("baf", properties["mess1"])
        assertEquals("bleble", properties["mess.baf2"])
    }

    @Test
    fun probeContentType() {
        if (isWindows) return

        assertEquals("text/plain", File(txtPath).probeContentType)
        assertEquals("application/x-zip-compressed", File(zipPath).probeContentType)
        assertEquals("image/jpeg", File(jpgPath).probeContentType)
        assertEquals("video/avi", File(aviPath).probeContentType)
        assertEquals("application/pdf", File(pdfPath).probeContentType)
        assertEquals("application/x-shar", File(shPath).probeContentType)
        assertEquals("application/application/vnd.oasis.opendocument.formula", File(odfPath).probeContentType)
        assertEquals("application/vnd.ms-powerpoint", File(pptPath).probeContentType)
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", File(pptxPath).probeContentType)
    }

    val txtPath = Paths.get("src/test/resources/test.txt").toAbsolutePath().toString()
    val zipPath = Paths.get("src/test/resources/test.zip").toAbsolutePath().toString()
    val jpgPath = Paths.get("src/test/resources/drevo.jpg").toAbsolutePath().toString()
    val aviPath = Paths.get("src/test/resources/flame.avi").toAbsolutePath().toString()
    val pdfPath = Paths.get("src/test/resources/test.pdf").toAbsolutePath().toString()
    val shPath = Paths.get("src/test/resources/test.sh").toAbsolutePath().toString()
    val pptPath = Paths.get("src/test/resources/test.ppt").toAbsolutePath().toString()
    val pptxPath = Paths.get("src/test/resources/test.pptx").toAbsolutePath().toString()
    val odfPath = Paths.get("src/test/resources/test.odf").toAbsolutePath().toString()
    val propertiesPath = Paths.get("src/test/resources/test.properties").toAbsolutePath().toString()
}
