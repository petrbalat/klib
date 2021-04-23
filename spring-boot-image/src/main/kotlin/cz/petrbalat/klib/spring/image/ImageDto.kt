package cz.petrbalat.klib.spring.image

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Pomocná třída pro práci s obrázkem. Používá se jako dto pro uložení odkazu na obrázek,
 * Obsahuje metody pro další práci s
 */
data class ImageDto(val url: String, val webpUrl: String) {

    val name: String @JsonProperty get() = url.substringAfterLast("/")

    val webpName: String? @JsonProperty get() = webpUrl?.substringAfterLast("/")
}

