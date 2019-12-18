package cz.petrbalat.klib.jdk.collections

import java.math.BigDecimal

/**
 * Created by Petr balat
 */

/**
 * return item after @param item
 */
fun <T> List<T>.next(item: T): T? {
    val index = indexOf(item).takeIf { it >= 0 } ?: return null
    return getOrNull(index + 1)
}

/**
 * return previous item before @param item
 */
fun <T> List<T>.previous(item: T): T? {
    val index = indexOf(item).takeIf { it > 0 } ?: return null
    return getOrNull(index - 1)
}

inline fun <T> Iterable<T>.sumByBigDecimal(selector: (T) -> BigDecimal): BigDecimal {
    var sum = BigDecimal.ZERO
    for (element in this) {
        sum += selector(element)
    }
    return sum
}


fun Iterable<BigDecimal>.sum(): BigDecimal = this.sumByBigDecimal { it }

fun <K,V> Map.Entry<K,V>.toMap(): Map<K,V> = mapOf(toPair())
