package cz.petrbalat.klib.jdk.string


import java.text.Normalizer
import java.util.*
import java.util.regex.Pattern

fun randomString(length: Int): String {
    val uuid = UUID.randomUUID().toString().replace("-", "")
    return if (length < uuid.length) uuid.substring(0, length) else uuid
}

fun String.maxSize(maxSize: Int): String = if (maxSize >= length) this else this.substring(0, maxSize)

fun String.removeDiakritiku(): String {
    val normalize = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalize.filter { it <= '\u007F' }
}

val regexpNonAlphaNorNumeric by lazy {
    Regex("[^\\p{L}\\p{Nd}]+")
}

val regexpAlowedInUrl by lazy {
    Regex("[^a-z0-9-]")
}

fun String.removeNonAlphaNorNumeric(replacement: String = ""): String =
    this.replace(regexpNonAlphaNorNumeric, replacement)

val regexpNotAlowedInFileName by lazy {
    Regex("[^a-zA-Z0-9æøåÆØÅ_. -]")
}

fun removeNotAlowedInFileName(fileName: String, replacement: String = ""): String =
    fileName.replace(regexpNotAlowedInFileName, replacement).replace(" ", "")

fun String?.emptyToNull(): String? = if (this.isNullOrEmpty()) null else this
fun String?.blankToNull(): String? = if (this.isNullOrBlank()) null else this

fun String.replaceFirst(replacement: CharSequence): String = this.replaceRange(0, 1, replacement)

fun String.replaceLast(replacement: CharSequence): String = this.replaceRange(length - 1, length, replacement)

private val emailmPattern by lazy {
    Pattern.compile("[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
}

fun parseEmail(str: String): Sequence<String> {
    val matcher = emailmPattern.matcher(str)

    return generateSequence {
        if (matcher.find()) matcher.group() else null
    }
}
