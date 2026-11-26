package com.paslonkit.customfcmexample.security

import android.util.Base64

/**
 * base64url без паддинга и переносов, как на сервере.
 */
internal fun ByteArray.encodeBase64Url(): String =
    Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
