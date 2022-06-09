package cz.petrbalat.klib.jdk

import java.util.*

/**
 * Created by Petr Balat
 */
fun Boolean.toBooleanInt(): Int = if (this) 1 else 0

fun Throwable.causeSequence(): Sequence<Throwable> = generateSequence(this, {
    if (it.cause == it) {
        return@generateSequence null
    }
    it.cause
})

@Deprecated("use tryOrNull", ReplaceWith(expression = "tryOrNull(action)", "cz.petrbalat.klib.jdk.tryOrNull"))
inline fun <T> tryOn(action: () -> T?): T? = try {
    action()
} catch (ex: Throwable) {
    null
}

inline fun <T> tryOrNull(action: () -> T?): T? = try {
    action()
} catch (ex: Throwable) {
    null
}

/**
 * porovnání čísel na 8 desetiných míst
 */
infix fun Double.relativelyEqual(other: Double): Boolean = this.relativelyEqual(other, 1E-8)

/**
 * porovnání čísel na decimalPlace desetiných míst
 */
fun Double.relativelyEqual(other: Double, decimalPlace: Double): Boolean = Math.abs(this - other) <= decimalPlace

fun <T> Optional<T>.orNull(): T? = orElse(null)

inline val <E : Enum<E>> Enum<E>.fullName: String
    get() = "${javaClass.declaringClass.name}.$name"

interface Describe {

    val text: String
}

inline val random get() = kotlin.random.Random
