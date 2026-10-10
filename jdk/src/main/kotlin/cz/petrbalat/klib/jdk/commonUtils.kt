package cz.petrbalat.klib.jdk

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.*
import java.util.concurrent.Executor

/**
 * Created by Petr Balat
 */
fun Boolean.toBooleanInt(): Int = if (this) 1 else 0

fun Throwable.causeSequence(): Sequence<Throwable> = generateSequence(this) {
    if (it.cause == it) {
        return@generateSequence null
    }
    it.cause
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
    get() = "${javaClass.name}.$name"

interface Describe {

    val text: String
}

inline val random get() = kotlin.random.Random


/** každá úloha dostane vlastní virtuální vlákno; nic se nezavírá */
val virtualThreadDispatcher: CoroutineDispatcher =
    Executor { Thread.startVirtualThread(it) }.asCoroutineDispatcher()
