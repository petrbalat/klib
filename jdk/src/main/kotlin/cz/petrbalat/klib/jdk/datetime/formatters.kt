package cz.petrbalat.klib.jdk.datetime

import java.time.format.DateTimeFormatter

val ddMMyyyy = DateTimeFormatter.ofPattern("d.M.yyyy")

/**
 * yyyyMMddHHmmss
 */
val yyyyMMddHHmmss = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

/**
 * "d.M.yyyy HH:mm"
 */
val ddMMyyyy_HH_mm = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm")

/**
 * "yyyy-MM-dd HH:mm:ssX"
 */
val pgZonedDateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssX")

/**
 * "yyyy-MM-dd HH:mm:ss.SSSSSSX"
 */
val pgZonedDateTimeFormat2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSX")
