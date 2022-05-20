package cz.petrbalat.klib.retrofit2

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

fun File.toRequestBody(): RequestBody = this.asRequestBody("multipart/form-data".toMediaTypeOrNull())
