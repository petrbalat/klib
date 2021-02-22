package cz.petrbalat.klib.retrofit2

import okhttp3.MediaType
import okhttp3.RequestBody
import java.io.File

fun File.toRequestBody(): RequestBody = RequestBody.create(MediaType.parse("multipart/form-data"), this)
