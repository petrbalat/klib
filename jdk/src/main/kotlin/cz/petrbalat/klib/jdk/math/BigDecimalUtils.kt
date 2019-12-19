package cz.petrbalat.klib.jdk.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

fun Number.toBigDecimal(): BigDecimal = when (this) {
    is Long -> BigDecimal(this)
    is Int -> BigDecimal(this)
    is Double -> BigDecimal(this)
    is BigInteger -> BigDecimal(this)
    is BigDecimal -> this
    else -> TODO("unknown number")
}

/**
 * vrací  BigDecimal.ZERO pokud je ukazatel null
 */
inline fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO

/**
 * porovnává zda jsou da bigDecimaly stejné
 */
inline infix fun BigDecimal.isEquals(other: BigDecimal): Boolean = this.compareTo(other) == 0

/**
 * vydělí správně s 128 přesností viz BigDecimal#divide(other, MathContext.DECIMAL128)
 */
inline infix fun BigDecimal.divide128(other: BigDecimal): BigDecimal = this.divide(other, MathContext.DECIMAL128)

/**
 * vypočte kolik je to procent z druhé hodnoty
 */
inline infix fun BigDecimal.howManyPercentOf(other: BigDecimal): BigDecimal = (this divide128 other) * BigDecimal(100)

/**
 * vypočte hodnotu kde procento je na levé straně a hodnota na druhé
 */
inline infix fun BigDecimal.percentOf(other: BigDecimal): BigDecimal = (this divide128 BigDecimal(100)) * other

/**
 * přičte procenta z parametru
 */
inline infix fun BigDecimal.plusPercent(percent: BigDecimal): BigDecimal = this + (percent percentOf this)

/**
 * odečte procenta z parametru
 */
inline infix fun BigDecimal.minusPercent(percent: BigDecimal): BigDecimal = this - (percent percentOf this)


/**
 * zaokrouhlí matematicky 0.5 nahoru
 */
fun BigDecimal.roundHalfUp(newScale: Int = 0): BigDecimal = this.setScale(newScale, RoundingMode.HALF_UP)
