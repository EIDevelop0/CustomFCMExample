package com.paslonkit.customfcmexample

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.paslonkit.customfcmexample.attribution.AdIdFetcher
import com.paslonkit.customfcmexample.attribution.InstallReferrerFetcher
import com.paslonkit.customfcmexample.init.InitService
import com.paslonkit.customfcmexample.network.InitTokenAssembler
import com.paslonkit.customfcmexample.network.NetworkModule
import com.paslonkit.customfcmexample.network.PushOpenedTelemetry
import com.paslonkit.customfcmexample.push.PushBus
import com.paslonkit.customfcmexample.push.PushSnapshotSource
import com.paslonkit.customfcmexample.security.PayloadCipher
import com.paslonkit.customfcmexample.security.TokenSigner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual-DI корень. На Application.onCreate:
 *   1) создаёт канал уведомлений;
 *   2) собирает OkHttp / Retrofit / APIs (User-Agent интерцептор);
 *   3) собирает [InitService] (шифрование payload + HMAC-подпись +
 *      POST wwa7 /init);
 *   4) собирает [PushOpenedTelemetry] (POST /push-opened при тапе);
 *   5) вешает [ProcessLifecycleOwner] observer — re-send на foreground;
 *   6) подписывается на `PushBus.tokenRefresh` — re-send на onNewToken.
 *
 * Для живого прототипа заменить константы [BASE_URL], [PUSH_OPENED_URL],
 * [AES_KEY], [HMAC_SIGN_KEY], и положить `google-services.json` в `app/`.
 */
class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var identity: InstallIdentity
        private set
    lateinit var telemetry: PushOpenedTelemetry
        private set
    lateinit var initService: InitService
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        identity = InstallIdentity(this)
        createNotificationChannel()

        val okHttp = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(BASE_URL, okHttp)
        val pushApi = NetworkModule.providePushApi(retrofit)
        val initApi = NetworkModule.provideInitApi(retrofit)

        telemetry = PushOpenedTelemetry(api = pushApi, endpointUrl = PUSH_OPENED_URL)

        val payloadCipher = PayloadCipher()
        val tokenSigner = TokenSigner(signKey = HMAC_SIGN_KEY.toByteArray(Charsets.UTF_8))
        val assembler = InitTokenAssembler(
            payloadCipher = payloadCipher,
            tokenSigner = tokenSigner,
            aesKey = AES_KEY.toByteArray(Charsets.UTF_8),
        )

        initService = InitService(
            identity = identity,
            adIdFetcher = AdIdFetcher(this),
            referrerFetcher = InstallReferrerFetcher(this),
            pushSnapshot = PushSnapshotSource(this),
            tokenAssembler = assembler,
            api = initApi,
        )

        // Foreground → re-send if что-то изменилось.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch { initService.onForegrounded() }
            }
        })

        // onNewToken из PushMessagingService → re-send с новым FCM.
        PushBus.subscribeTokenRefresh {
            appScope.launch { initService.onFcmTokenChanged() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            getString(R.string.default_notification_channel_id),
            getString(R.string.default_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        /** TODO: домен бекенда (например, "https://api.example.com/"). */
        const val BASE_URL = "https://<domen>/"

        /**
         * TODO: полный URL push-open эндпоинта (`domain + pushOpenedEndpoint`
         * из wwa api). Отдельный POST без auth / шифрования.
         */
        const val PUSH_OPENED_URL = "https://<domen>/push-opened"

        /**
         * TODO: AES-GCM ключ. Длина строго 16/24/32 байта (AES-128/192/256).
         * Прод прячет его в native-слое — тут plain-text для прототипа.
         */
        const val AES_KEY = "0123456789abcdef0123456789abcdef" // 32 bytes → AES-256

        /** TODO: HMAC-SHA256 ключ для подписи канонической строки. */
        const val HMAC_SIGN_KEY = "replace-me-with-server-hmac-key"

        private lateinit var instance: App
        fun get(): App = instance
    }
}
