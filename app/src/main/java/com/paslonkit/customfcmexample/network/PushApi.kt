package com.paslonkit.customfcmexample.network

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Push-opened endpoint. По спеке — отдельный URL (`domain + pushOpenedEndpoint`),
 * без Authorization header, без шифрования, тело — голый JSON с
 * `uid` и `push_send_id`.
 */
interface PushApi {

    @POST
    suspend fun submitPushOpened(
        @Url url: String,
        @Body body: RequestBody,
    ): Response<Unit>
}
