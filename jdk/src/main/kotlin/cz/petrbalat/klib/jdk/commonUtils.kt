package cz.petrbalat.klib.jdk

import java.util.*

/**
 * Created by Petr Balat
 */
fun Boolean.toBooleanInt(): Int = if(this) 1 else 0

fun Throwable.causeSequence(): Sequence<Throwable> = generateSequence(this, {
    if (it.cause == it) {
        return@generateSequence null
    }
    it.cause
})

inline fun <T> tryOn(action: () -> T?): T? = try {
    action()
} catch (ex: Throwable) {
    null
}

infix fun Double.relativelyEqual(other:Double):Boolean = Math.abs(this - other) <= 1E-8

fun <T> Optional<T>.orNull(): T? = orElse(null)

inline val <E : Enum<E>> Enum<E>.fullName: String
    get() = "${declaringClass.name}.$name"

interface Describe {

    val text: String
}
