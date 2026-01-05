package com.pashaoleynik.droiddeploy.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

internal class HostStore(initialHost: String) {
    private val hostUrl = AtomicReference<HttpUrl>(initialHost.toHttpUrl())

    fun get(): HttpUrl = hostUrl.get()

    fun set(newHost: String) {
        hostUrl.set(newHost.toHttpUrl())
    }
}
