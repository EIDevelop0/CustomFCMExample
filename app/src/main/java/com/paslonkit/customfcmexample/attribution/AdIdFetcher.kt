package com.paslonkit.customfcmexample.attribution

import android.content.Context
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Advertising Id. Возвращает "" при LAT (limit ad tracking),
 * отсутствии Google Play сервисов или любой ошибке.
 */
class AdIdFetcher(private val context: Context) {

    suspend fun fetch(): String = withContext(Dispatchers.IO) {
        runCatching {
            val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
            if (info.isLimitAdTrackingEnabled) "" else info.id.orEmpty()
        }.getOrDefault("")
    }
}
