package cz.petrbalat.klib.jdk.dto

data class SelectItemDto<T>(val label: String, val value: T, val styleClass: String? = null, val icon: String? = null)
