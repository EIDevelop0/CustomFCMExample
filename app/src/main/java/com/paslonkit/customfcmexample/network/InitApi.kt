package com.paslonkit.customfcmexample.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit-интерфейс init-запроса (расширение стандартного wwa7).
 * Тело — конверт `{data, sig, ts, nonce}`, см. [InitRequest].
 * Authorization-header и прочая auth-обвязка по контракту прод-кита
 * вешаются в OkHttp-интерцепторе; здесь — минимум.
 */
interface InitApi {

    @POST("init")
    suspend fun init(@Body body: InitRequest): Response<Unit>
}
