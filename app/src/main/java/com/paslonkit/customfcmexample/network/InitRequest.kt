package com.paslonkit.customfcmexample.network

import com.google.gson.annotations.SerializedName

/**
 * Outer-envelope для init-запроса:
 * ```
 *   data  = base64url(iv_12 || aes256_gcm_ct_with_tag)
 *   sig   = base64url(HMAC_SHA256(sign_key, "ts\nnonce\ndata"))
 *   ts    = unix_seconds
 *   nonce = base64url(random_16_bytes)
 * ```
 */
data class InitRequest(
    @SerializedName("data") val data: String,
    @SerializedName("sig") val sig: String,
    @SerializedName("ts") val ts: Long,
    @SerializedName("nonce") val nonce: String,
)
