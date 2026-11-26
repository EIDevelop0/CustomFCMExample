package com.paslonkit.customfcmexample.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 подпись канонической строки `"ts\nnonce\ndata_b64url"`.
 *
 * В прод-реализации ключ прячут в `libsigner.so` (JNI) и наружу торчит
 * только `sign()`. Для прототипа ключ приходит как байты в конструктор.
 */
class TokenSigner(
    private val signKey: ByteArray,
) {

    fun sign(canonical: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(signKey, "HmacSHA256"))
        }
        return mac.doFinal(canonical)
    }
}
