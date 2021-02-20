package cz.petrbalat.klib.jdk.collections

import java.math.BigDecimal

/**
 * Created by Petr balat
 */

/**
 * return item after @param item
 */
fun <T> List<T>.next(item: T): T? {
    val index = indexOfOrNull(item) ?: return null
    return getOrNull(index + 1)
}

/**
 * return previous item before @param item
 */
fun <T> List<T>.previous(item: T): T? {
    val index = indexOfOrNull(item) ?: return null
    return getOrNull(index - 1)
}

fun Iterable<BigDecimal>.sum(): BigDecimal = this.sumOf { it }

fun <K, V> Map.Entry<K, V>.toMap(): Map<K, V> = mapOf(toPair())

fun <T> List<T>.indexOfOrNull(element: T): Int? = indexOf(element).takeIf { it > -1 }

