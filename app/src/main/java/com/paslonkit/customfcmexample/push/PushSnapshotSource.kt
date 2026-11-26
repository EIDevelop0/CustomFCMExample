package com.paslonkit.customfcmexample.push

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.paslonkit.customfcmexample.R
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume
import android.icu.util.TimeZone as IcuTimeZone

/**
 * Снимок параметров для init-запроса:
 *   fcmToken / tz / country / notificationsEnabled
 *
 * Пустые строки означают "значение получить не удалось — не слать поле
 * серверу" (см. spec: пустые поля не отправляются, иначе бекенд
 * перезапишет текущее значение на пустое и пуши перестанут ходить).
 */
data class PushSnapshot(
    val fcmToken: String,
    val timezone: String,
    val country: String,
    val notificationsEnabled: Boolean,
)

class PushSnapshotSource(
    private val context: Context,
) {

    suspend fun current(): PushSnapshot = PushSnapshot(
        fcmToken = fetchFcmToken(),
        timezone = currentIanaTimezone(),
        country = currentCountry(),
        notificationsEnabled = checkPushPermission(),
    )

    private suspend fun fetchFcmToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            val value = if (task.isSuccessful) task.result.orEmpty() else ""
            if (cont.isActive) cont.resume(value)
        }
    }

    /**
     * IANA id (`Europe/Kyiv`). Offset-строки (`+03:00`, `UTC+3`, `MSK`)
     * сервер не принимает — скедулинг пушей ломается. Fallback через
     * ICU по offsetMs, крайний случай — `Etc/GMT±N`.
     */
    private fun currentIanaTimezone(): String {
        val primary = IcuTimeZone.getDefault().id.orEmpty()
        if (primary.contains('/')) return primary

        val offsetMs = TimeZone.getDefault().rawOffset
        IcuTimeZone.getAvailableIDs(offsetMs)
            .firstOrNull { it.contains('/') && !it.startsWith("Etc/") }
            ?.let { return it }

        val offsetH = -offsetMs / (60 * 60 * 1000)
        return "Etc/GMT" + if (offsetH >= 0) "+$offsetH" else "$offsetH"
    }

    private fun currentCountry(): String =
        Locale.getDefault().country.orEmpty()

    /**
     * Разрешены ли пуши глобально И не выключен ли канал по умолчанию.
     * Пользователь может оставить `POST_NOTIFICATIONS` разрешённым, но
     * задушить канал — тогда серверу нужен `false`, чтобы не слать пуши,
     * которые не дойдут.
     */
    private fun checkPushPermission(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(
            context.getString(R.string.default_notification_channel_id),
        )
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
