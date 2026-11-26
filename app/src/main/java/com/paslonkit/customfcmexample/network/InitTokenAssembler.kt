package com.paslonkit.customfcmexample.network

import com.paslonkit.customfcmexample.push.PushSnapshot
import com.paslonkit.customfcmexample.security.PayloadCipher
import com.paslonkit.customfcmexample.security.TokenSigner
import com.paslonkit.customfcmexample.security.encodeBase64Url
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Собирает зашифрованный и подписанный конверт init-запроса.
 *
 * Пустые поля НЕ отправляются — сервер перезапишет своё значение на
 * пустое и, например, пуши перестанут ходить (см. spec §3.2).
 */
class InitTokenAssembler(
    private val payloadCipher: PayloadCipher,
    private val tokenSigner: TokenSigner,
    private val aesKey: ByteArray,
) {

    /**
     * @param uuid всегда, непустой
     * @param referrer — если пусто, поле не добавляется (повторные запуски)
     * @param adId — если пусто, поле не добавляется (LAT / repeat)
     */
    data class Fields(
        val uuid: String,
        val adId: String,
        val referrer: String,
    )

    fun build(fields: Fields, push: PushSnapshot): InitRequest {
        val payloadJson = assemblePayload(fields, push)
        val data = payloadCipher.encrypt(payloadJson.toByteArray(Charsets.UTF_8), aesKey)
        val ts = System.currentTimeMillis() / 1000
        val nonce = freshNonce()
        val canonical = "$ts\n$nonce\n$data"
        val sig = tokenSigner.sign(canonical.toByteArray(Charsets.UTF_8)).encodeBase64Url()
        return InitRequest(data = data, sig = sig, ts = ts, nonce = nonce)
    }

    private fun assemblePayload(fields: Fields, push: PushSnapshot): String =
        JSONObject().apply {
            put(PayloadKeys.UUID, fields.uuid)
            if (fields.referrer.isNotEmpty()) put(PayloadKeys.REFERRER, fields.referrer)
            if (fields.adId.isNotEmpty()) put(PayloadKeys.AD_ID, fields.adId)
            if (push.fcmToken.isNotEmpty()) put(PayloadKeys.FCM_TOKEN, push.fcmToken)
            if (push.timezone.isNotEmpty()) put(PayloadKeys.TIMEZONE, push.timezone)
            if (push.country.isNotEmpty()) put(PayloadKeys.COUNTRY, push.country)
            put(PayloadKeys.NOTIFICATIONS_ENABLED, push.notificationsEnabled)
        }.toString()

    private fun freshNonce(): String {
        val raw = ByteArray(NONCE_LEN)
        SecureRandom().nextBytes(raw)
        return raw.encodeBase64Url()
    }

    private object PayloadKeys {
        const val UUID = "uuid"
        const val AD_ID = "ad_id"
        const val REFERRER = "install_referrer"
        const val FCM_TOKEN = "fcm_token"
        const val TIMEZONE = "tz"
        const val COUNTRY = "country"
        const val NOTIFICATIONS_ENABLED = "notification_enabled"
    }

    private companion object {
        const val NONCE_LEN = 16
    }
}
