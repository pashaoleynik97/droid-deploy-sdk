package com.pashaoleynik.droiddeploy.network

import java.util.concurrent.atomic.AtomicReference

internal class InMemoryTokenStore {
    private val token = AtomicReference<String?>(null)

    fun getToken(): String? = token.get()

    fun setToken(newToken: String?) {
        token.set(newToken)
    }

    fun clear() {
        token.set(null)
    }
}
