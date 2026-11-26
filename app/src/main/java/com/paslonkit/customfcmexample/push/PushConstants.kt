package com.paslonkit.customfcmexample.push

/**
 * Ключи push-контракта. Id канала / имя — в strings.xml
 * (`default_notification_channel_id` / `default_notification_channel_name`).
 */
object PushConstants {

    /** Extra в Intent, через который pushSendId передаётся из PendingIntent в MainActivity. */
    const val INTENT_EXTRA_PUSH_SEND_ID = "push_send_id"

    /** Ключи в data-payload FCM-сообщения. */
    const val DATA_KEY_PUSH_SEND_ID = "push_send_id"
    const val DATA_KEY_TITLE = "title"
    const val DATA_KEY_BODY = "body"
    const val DATA_KEY_IMAGE = "image"

    /** JSON-ключи тела POST /push-opened. */
    const val JSON_KEY_UID = "uid"
    const val JSON_KEY_PUSH_SEND_ID = "push_send_id"
}
