package com.pashaoleynik.droiddeploy.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RestResponse<T>(
    val data: T? = null,
    val message: String? = null,
    val errors: List<String> = emptyList(),
    val success: Boolean = true
)
