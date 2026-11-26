package com.paslonkit.customfcmexample

import android.content.Context
import java.util.UUID

/**
 * uuid + вспомогательные флаги:
 *   - [installSent] — был ли первый non-organic запрос с referrer/adId.
 *     После успешной отправки referrer/adId больше не прикладываются.
 *   - [lastSnapshotFingerprint] — отпечаток последнего успешно
 *     отправленного снимка, чтобы на foreground не дёргать сервер
 *     вхолостую.
 */
class InstallIdentity(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun uuid(): String {
        val existing = prefs.getString(KEY_UUID, null)
        if (!existing.isNullOrEmpty()) return existing
        val fresh = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_UUID, fresh).apply()
        return fresh
    }

    fun installSent(): Boolean = prefs.getBoolean(KEY_INSTALL_SENT, false)
    fun markInstallSent() {
        prefs.edit().putBoolean(KEY_INSTALL_SENT, true).apply()
    }

    fun lastSnapshotFingerprint(): String? = prefs.getString(KEY_LAST_FP, null)
    fun saveSnapshotFingerprint(fp: String) {
        prefs.edit().putString(KEY_LAST_FP, fp).apply()
    }

    private companion object {
        const val PREFS_NAME = "install_identity"
        const val KEY_UUID = "uuid"
        const val KEY_INSTALL_SENT = "install_sent"
        const val KEY_LAST_FP = "last_snapshot_fp"
    }
}
