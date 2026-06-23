package cz.petrbalat.klib.cdn77

import java.io.File
import java.net.URI


val testJpg: URI = object {}.javaClass.getResource("/test.jpg")!!.toURI()
val testJpgFile = File(testJpg)
