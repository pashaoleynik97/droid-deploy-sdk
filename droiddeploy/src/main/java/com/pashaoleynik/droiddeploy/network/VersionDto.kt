package com.pashaoleynik.droiddeploy.network

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VersionDto(
    val versionName: String? = null,
    val versionCode: Long? = null,
    val stable: Boolean = false
)
