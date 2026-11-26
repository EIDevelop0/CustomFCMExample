package com.paslonkit.customfcmexample.network

import android.util.Log
import com.paslonkit.customfcmexample.push.PushConstants
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Fire-and-forget отправка факта тапа по пушу. Без auth, без шифрования —
 * голый POST с JSON `{uid, push_send_id}` на отдельный URL.
 */
class PushOpenedTelemetry(
    private val api: PushApi,
    private val endpointUrl: String,
) {

    suspend fun report(uuid: String, pushSendId: String): Boolean {
        if (uuid.isEmpty() || pushSendId.isEmpty()) return false
        val json = JSONObject().apply {
            put(PushConstants.JSON_KEY_UID, uuid)
            put(PushConstants.JSON_KEY_PUSH_SEND_ID, pushSendId)
        }.toString()
        val body = json.toRequestBody(JSON_MEDIA_TYPE.toMediaType())

        return runCatching {
            val response = api.submitPushOpened(endpointUrl, body)
            val ok = response.isSuccessful
            Log.d(TAG, "push-opened code=${response.code()} ok=$ok")
            ok
        }.onFailure {
            Log.w(TAG, "push-opened failed: ${it.message}")
        }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "PushOpenedTelemetry"
        const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"
    }
}
