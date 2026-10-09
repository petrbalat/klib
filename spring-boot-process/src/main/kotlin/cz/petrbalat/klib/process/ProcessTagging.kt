package cz.petrbalat.klib.process

/**
 * Tag procesů v env proměnné [envName] — dědí ho i potomci, podle něj uklízí [ProcessReaper].
 * Tag = `<owner>/<groupId>`; vlastník odděluje aplikace na jednom stroji, aby si nezabíjely procesy.
 */
class ProcessTagging(val owner: String, val envName: String = "KLIB_PROCESS_TAG") {

    init {
        require(owner.isNotBlank() && '/' !in owner) { "owner must be non-blank and must not contain '/': '$owner'" }
        require(envName.isNotBlank()) { "envName must be non-blank" }
    }

    fun tag(groupId: String): String = "$owner/$groupId"

    fun environment(groupId: String): Map<String, String> = mapOf(envName to tag(groupId))

    /** tag patří tomuto vlastníkovi */
    fun isOwned(tag: String): Boolean = tag.startsWith("$owner/")
}
