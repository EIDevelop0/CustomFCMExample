package com.paslonkit.customfcmexample.init

import android.util.Log
import com.paslonkit.customfcmexample.InstallIdentity
import com.paslonkit.customfcmexample.attribution.AdIdFetcher
import com.paslonkit.customfcmexample.attribution.InstallReferrerFetcher
import com.paslonkit.customfcmexample.network.InitApi
import com.paslonkit.customfcmexample.network.InitTokenAssembler
import com.paslonkit.customfcmexample.push.PushSnapshot
import com.paslonkit.customfcmexample.push.PushSnapshotSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

/**
 * Оркестратор init-flow (push-registration = расширение стандартного
 * wwa7 запроса).
 *
 * Триггеры (вызываются извне):
 *   - [onColdStart] — всегда шлём (первый вызов на процесс);
 *   - [onForegrounded] — шлём только если снимок параметров изменился;
 *   - [onFcmTokenChanged] — вызывается подписчиком `PushBus` из
 *     `App.onCreate`, когда прилетает `onNewToken`. Fingerprint-check
 *     гасит дубликат с cold start.
 *
 * `referrer`/`adId` прикладываются только до первого успешного запроса
 * ([InstallIdentity.installSent]); дальше — чистый repeat-open. Пустые
 * поля в payload не попадают (см. [InitTokenAssembler]).
 */
class InitService(
    private val identity: InstallIdentity,
    private val adIdFetcher: AdIdFetcher,
    private val referrerFetcher: InstallReferrerFetcher,
    private val pushSnapshot: PushSnapshotSource,
    private val tokenAssembler: InitTokenAssembler,
    private val api: InitApi,
) {

    private val mutex = Mutex()

    @Volatile
    private var coldStartDone: Boolean = false

    suspend fun onColdStart(): Boolean {
        val ok = run(force = true)
        coldStartDone = true
        return ok
    }

    /**
     * Запрос из `ProcessLifecycleOwner.onStart`. До первого успешного
     * cold-start-а не стреляем: иначе до ответа пользователя на диалог
     * `POST_NOTIFICATIONS` уйдёт некорректный `notification_enabled`.
     */
    suspend fun onForegrounded(): Boolean {
        if (!coldStartDone) return true
        return run(force = false)
    }

    suspend fun onFcmTokenChanged(): Boolean = run(force = false)

    private suspend fun run(force: Boolean): Boolean = mutex.withLock {
        val uuid = identity.uuid()
        val installSent = identity.installSent()

        val adId = if (installSent) "" else adIdFetcher.fetch()
        val referrer = if (installSent) "" else referrerFetcher.fetch()
        val push = pushSnapshot.current()

        val fp = fingerprint(uuid, adId, referrer, push)
        if (!force && fp == identity.lastSnapshotFingerprint()) {
            Log.d(TAG, "init skipped — snapshot unchanged")
            return@withLock true
        }

        val fields = InitTokenAssembler.Fields(uuid = uuid, adId = adId, referrer = referrer)
        val body = tokenAssembler.build(fields, push)

        val ok = runCatching {
            val response = api.init(body = body)
            Log.d(TAG, "init POST code=${response.code()} ok=${response.isSuccessful}")
            response.isSuccessful
        }.onFailure { Log.w(TAG, "init failed: ${it.message}") }.getOrDefault(false)

        if (ok) {
            if (!installSent && (adId.isNotEmpty() || referrer.isNotEmpty())) {
                identity.markInstallSent()
            }
            identity.saveSnapshotFingerprint(fp)
        }
        ok
    }

    private fun fingerprint(uuid: String, adId: String, referrer: String, push: PushSnapshot): String {
        val canonical = buildString {
            append(uuid).append('|')
            append(adId).append('|')
            append(referrer).append('|')
            append(push.fcmToken).append('|')
            append(push.timezone).append('|')
            append(push.country).append('|')
            append(push.notificationsEnabled)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TAG = "InitService"
    }
}
