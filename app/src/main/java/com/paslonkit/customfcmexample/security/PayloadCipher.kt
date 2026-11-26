package com.paslonkit.customfcmexample.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM шифрование JSON-payload.
 *
 * Выход: `base64url(iv_12 || ciphertext_with_gcm_tag)`.
 * Длина ключа выбирает вариант AES:
 *   16 байт → AES-128, 24 → AES-192, 32 → AES-256.
 */
class PayloadCipher {

    private val random = SecureRandom()

    fun encrypt(plain: ByteArray, key: ByteArray): String {
        require(key.size in setOf(16, 24, 32)) {
            "AES key must be 16, 24 or 32 bytes (was ${key.size})"
        }
        val iv = ByteArray(IV_LEN).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
        }
        val ct = cipher.doFinal(plain)
        val wire = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, wire, 0, iv.size)
        System.arraycopy(ct, 0, wire, iv.size, ct.size)
        return wire.encodeBase64Url()
    }

    private companion object {
        const val IV_LEN = 12
        const val GCM_TAG_BITS = 128
    }
}
