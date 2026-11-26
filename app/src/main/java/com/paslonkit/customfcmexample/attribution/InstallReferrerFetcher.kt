package com.paslonkit.customfcmexample.attribution

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Suspend-обёртка над Google Play Install Referrer SDK.
 * Возвращает raw referrer string или "" на любой ошибке/таймауте.
 */
class InstallReferrerFetcher(
    private val context: Context,
    private val timeoutMs: Long = 5_000L,
) {

    suspend fun fetch(): String {
        val raw = withTimeoutOrNull(timeoutMs.milliseconds) {
            suspendCancellableCoroutine<String> { cont ->
                val client = runCatching { InstallReferrerClient.newBuilder(context).build() }
                    .getOrElse {
                        if (cont.isActive) cont.resume("")
                        return@suspendCancellableCoroutine
                    }
                val listener = object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        val value = if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            runCatching { client.installReferrer.installReferrer }
                                .getOrNull().orEmpty()
                        } else {
                            ""
                        }
                        runCatching { client.endConnection() }
                        if (cont.isActive) cont.resume(value)
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        if (cont.isActive) cont.resume("")
                    }
                }
                runCatching { client.startConnection(listener) }.getOrElse {
                    runCatching { client.endConnection() }
                    if (cont.isActive) cont.resume("")
                }
                cont.invokeOnCancellation { runCatching { client.endConnection() } }
            }
        }
        return raw.orEmpty()
    }
}
