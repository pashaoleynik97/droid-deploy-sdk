package com.pashaoleynik.droiddeploy.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class ApiKeyLoginRequest(
    val apiKey: String
)
