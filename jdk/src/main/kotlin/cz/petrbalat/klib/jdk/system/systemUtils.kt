package cz.petrbalat.klib.jdk.system

/**
 * Created by balat on 10.5.2016.
 */

val isWindows: Boolean get() = System.getProperty("os.name").contains("win", true)
