package com.paslonkit.customfcmexample.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.paslonkit.customfcmexample.R
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * FirebaseMessagingService: приём FCM и отрисовка уведомления (foreground).
 *
 * Логика ниже отрабатывает **только в foreground** — в background/killed
 * Firebase-runtime сам рисует нотификацию по `notification`-payload. Мы
 * здесь нужны как foreground-fallback и чтобы вытащить `push_send_id` на
 * трекинг open-события (через [PushBus.publishTokenRefresh]).
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        if (token.isEmpty()) return
        Log.d(TAG, "FCM token refreshed")
        PushBus.publishTokenRefresh(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title
            ?: message.data[PushConstants.DATA_KEY_TITLE].orEmpty()
        val body = message.notification?.body
            ?: message.data[PushConstants.DATA_KEY_BODY].orEmpty()
        val pushSendId = message.data[PushConstants.DATA_KEY_PUSH_SEND_ID]
        val imageUri: Uri? = message.notification?.imageUrl
            ?: message.data[PushConstants.DATA_KEY_IMAGE]
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }

        // runBlocking — осознанно. Если launch без блокировки, сервис
        // завершится до загрузки картинки, и BigPicture не доедет до
        // notification bar. FCM даёт ~10 c service time.
        val largeBitmap = imageUri?.let { runBlocking { loadBitmap(it) } }
        showNotification(title, body, pushSendId, largeBitmap)
    }

    private suspend fun loadBitmap(uri: Uri): Bitmap? =
        withTimeoutOrNull(IMAGE_TIMEOUT_MS.milliseconds) {
            runCatching {
                val loader = ImageLoader(applicationContext)
                val request = ImageRequest.Builder(applicationContext)
                    .data(uri.toString())
                    .allowHardware(false) // notifications need software bitmap
                    .build()
                val result = loader.execute(request)
                (result as? SuccessResult)?.drawable
                    ?.let { it as? BitmapDrawable }
                    ?.bitmap
            }.getOrNull()
        }

    private fun showNotification(
        title: String,
        body: String,
        pushSendId: String?,
        largeBitmap: Bitmap?,
    ) {
        val channelId = getString(R.string.default_notification_channel_id)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(channelId) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    getString(R.string.default_notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val notificationId = Random.nextInt()

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (!pushSendId.isNullOrEmpty()) {
                putExtra(PushConstants.INTENT_EXTRA_PUSH_SEND_ID, pushSendId)
            }
        }
        val pending = intent?.let {
            PendingIntent.getActivity(
                this,
                notificationId,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)

        if (largeBitmap != null) {
            builder
                .setLargeIcon(largeBitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(largeBitmap)
                        .bigLargeIcon(null as Bitmap?) // спрятать thumbnail в развёрнутом виде
                        .setBigContentTitle(title)
                        .setSummaryText(body),
                )
        }

        notifyIfAllowed(notificationId, builder.build())
    }

    private fun notifyIfAllowed(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "POST_NOTIFICATIONS не выдан — уведомление не показано")
            return
        }
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    companion object {
        const val EXTRA_PUSH_SEND_ID = PushConstants.INTENT_EXTRA_PUSH_SEND_ID
        private const val TAG = "PushMessagingService"
        private const val IMAGE_TIMEOUT_MS = 8_000L
    }
}
