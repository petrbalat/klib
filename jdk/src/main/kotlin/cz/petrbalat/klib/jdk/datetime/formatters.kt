package cz.petrbalat.klib.jdk.datetime

import java.time.format.DateTimeFormatter

/**
 * yyyyMMddHHmmss
 */
val yyyyMMddHHmmss = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

/**
 * "yyyyMMdd_HHmmss"
 */
val yyyyMMdd_HHmmss = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

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
