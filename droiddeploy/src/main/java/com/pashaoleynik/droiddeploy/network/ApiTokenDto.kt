package com.pashaoleynik.droiddeploy.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ApiTokenDto(
    val accessToken: String
)
