package cz.petrbalat.klib.jdk.math

import java.math.BigDecimal
import java.math.BigInteger

/**
 * @author Petr Balat
 *
 * wrapper over BigDecimal with operator +->< and mainly equalsTo method
 *
 */
inline class BD(val value: BigDecimal) : Comparable<BD> {

    override fun compareTo(other: BD): Int = value.compareTo(other.value)

    infix fun equalsTo(other: Any?): Boolean {
        val otherValue = (other as? BD)?.value ?: other as? BigDecimal ?: return false
        return this.value equalsTo otherValue
    }

    fun roundHalfUp(newScale: Int = 0): BD = value.roundHalfUp(newScale).toBD()

//    TODO in kotlin 1.4
//    override fun equals(other: Any?): Boolean = this equalsTo other

    operator fun plus(other: BD): BD = this.value.plus(other.value).toBD()

    operator fun plus(other: Number): BD = this.value.plus(other.toBigDecimal()).toBD()

    operator fun minus(other: BD): BD = this.value.minus(other.value).toBD()

    operator fun minus(other: Number): BD = this.value.minus(other.toBigDecimal()).toBD()

    operator fun div(other: BD): BD = (this.value divide128 other.value).toBD()

    operator fun div(other: Number): BD = (this.value divide128 other.toBigDecimal()).toBD()

    operator fun times(other: BD): BD = (this.value.times(other.value)).toBD()

    operator fun times(other: Number): BD = this.value.times(other.toBigDecimal()).toBD()

    operator fun rem(other: BD): BD = (this.value.rem(other.value)).toBD()

    operator fun rem(other: Number): BD = (this.value.rem(other.toBigDecimal())).toBD()

    operator fun unaryMinus(): BD = this.value.unaryMinus().toBD()

    operator fun inc(): BD = this.value.inc().toBD()

    operator fun dec(): BD = this.value.inc().toBD()

    infix fun plusPercent(percent: BD): BD = this + (percent.value percentOf this.value).toBD()

    infix fun plusPercent(percent: Number): BD = this + (percent.toBigDecimal() percentOf this.value).toBD()

    infix fun minusPercent(percent: BD): BD = this - (percent.value minusPercent this.value).toBD()

    infix fun minusPercent(percent: Number): BD = this - (percent.toBigDecimal() minusPercent this.value).toBD()

    infix fun percentOf(percent: Number): BD = (value percentOf percent.toBigDecimal()).toBD()

    infix fun percentOf(percent: BD): BD = (value percentOf percent.value).toBD()

    override fun toString(): String = value.toString()
}

fun BD?.orZEro(): BD = this ?: BigDecimal.ZERO.toBD()

inline fun Number.toBD(): BD = BD(this.toBigDecimal())
inline fun Long.toBD(): BD = BD(this.toBigDecimal())
inline fun Int.toBD(): BD = BD(this.toBigDecimal())
inline fun Double.toBD(): BD = BD(this.toBigDecimal())
inline fun BigInteger.toBD(): BD = BD(this.toBigDecimal())
inline fun BigDecimal.toBD(): BD = BD(this)
inline fun String.toBD(): BD = BD(this.toBigDecimal())
inline fun String.toBigDecimalExOrNull(): BD? = this.toBigDecimalOrNull()?.let { BD(it) }
