package cz.petrbalat.klib.jdk

import java.nio.ByteBuffer

/**
 * Created by balat on 13.9.2016.
 */

fun Double.toByteArray(): ByteArray = ByteBuffer.allocate(java.lang.Double.BYTES).putDouble(this).array()

fun toDouble(bytes:ByteArray): Double = ByteBuffer.wrap(bytes).double

fun Float.toByteArray(): ByteArray = ByteBuffer.allocate(java.lang.Float.BYTES).putFloat(this).array()

fun toFloat(bytes:ByteArray): Float = ByteBuffer.wrap(bytes).float

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(java.lang.Integer.BYTES).putInt(this).array()

fun toInt(bytes:ByteArray): Int = ByteBuffer.wrap(bytes).int

fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(this).array()

fun toLong(bytes:ByteArray): Long = ByteBuffer.wrap(bytes).long
