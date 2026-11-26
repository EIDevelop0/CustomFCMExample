package com.paslonkit.customfcmexample.push

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Pub-sub, не зависящий от DI. Переживает процессный rebind
 * FirebaseMessagingService (сервис может быть поднят до Application.onCreate).
 */
object PushBus {

    private val tokenRefreshListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val notificationTapListeners = CopyOnWriteArrayList<(String) -> Unit>()

    fun publishTokenRefresh(token: String) {
        if (token.isEmpty()) return
        for (l in tokenRefreshListeners) runCatching { l(token) }
    }

    fun publishNotificationTap(pushSendId: String) {
        if (pushSendId.isEmpty()) return
        for (l in notificationTapListeners) runCatching { l(pushSendId) }
    }

    fun subscribeTokenRefresh(l: (String) -> Unit) { tokenRefreshListeners.add(l) }
    fun unsubscribeTokenRefresh(l: (String) -> Unit) { tokenRefreshListeners.remove(l) }

    fun subscribeNotificationTap(l: (String) -> Unit) { notificationTapListeners.add(l) }
    fun unsubscribeNotificationTap(l: (String) -> Unit) { notificationTapListeners.remove(l) }
}
