package cz.petrbalat.klib.jdk.string

import java.util.*

/**
 * @see http://www.davekoelle.com/alphanum.html
 *
 * This is an updated version with enhancements made by Daniel Migowski,
 * Andre Bogus, and David Koelle
 *
 *
 * To convert to use Templates (Java 1.5+):
 * - Change "implements Comparator" to "implements Comparator<String>"
 * - Change "compare(Object o1, Object o2)" to "compare(String s1, String s2)"
 * - Remove the type checking and casting in compare().
</String> *
 *
 * To use this class:
 * Use the static "sort" method from the java.util.Collections class:
 * Collections.sort(your list, new AlphanumComparator());
 */
object AlphanumComparator : Comparator<String> {

    private fun isDigit(ch: Char): Boolean = ch.toInt() in 48..57

    /**
     * Length of string is passed in for improved efficiency (only need to calculate it once)
     */
    private fun getChunk(s: String, slength: Int, marker: Int): String {
        var marker = marker
        val chunk = StringBuilder()
        var c = s[marker]
        chunk.append(c)
        marker++
        if (isDigit(c)) {
            while (marker < slength) {
                c = s[marker]
                if (!isDigit(c))
                    break
                chunk.append(c)
                marker++
            }
        } else {
            while (marker < slength) {
                c = s[marker]
                if (isDigit(c))
                    break
                chunk.append(c)
                marker++
            }
        }
        return chunk.toString()
    }

    override fun compare(o1: String?, o2: String?): Int {
        if (o1 == o2) {
            return 0
        }

        val o1 = o1.orEmpty()
        val o2 = o2.orEmpty()

        try {

            var thisMarker = 0
            var thatMarker = 0
            val s1Length = o1.length
            val s2Length = o2.length

            while (thisMarker < s1Length && thatMarker < s2Length) {
                val thisChunk = getChunk(o1, s1Length, thisMarker)
                thisMarker += thisChunk.length

                val thatChunk = getChunk(o2, s2Length, thatMarker)
                thatMarker += thatChunk.length

                // If both chunks contain numeric characters, sort them numerically
                var result = 0
                if (isDigit(thisChunk[0]) && isDigit(thatChunk[0])) {
                    // Simple chunk comparison by length.
                    val thisChunkLength = thisChunk.length
                    result = thisChunkLength - thatChunk.length
                    // If equal, the first different number counts
                    if (result == 0) {
                        for (i in 0..thisChunkLength - 1) {
                            result = thisChunk[i] - thatChunk[i]
                            if (result != 0) {
                                return result
                            }
                        }
                    }
                } else {
                    result = thisChunk.compareTo(thatChunk)
                }

                if (result != 0)
                    return result
            }

            return s1Length - s2Length
        } catch (th: Throwable) {
            return o1.compareTo(o2)
        }
    }
}
