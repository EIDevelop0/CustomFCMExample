package com.paslonkit.customfcmexample.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Единый `User-Agent` для всех запросов — системный `http.agent`
 * (например, `Dalvik/2.1.0 (Linux; U; Android 13; SM-S918B Build/…)`).
 */
class UserAgentInterceptor : Interceptor {

    private val userAgent: String = System.getProperty("http.agent") ?: "Android"

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .build()
        return chain.proceed(request)
    }
}
