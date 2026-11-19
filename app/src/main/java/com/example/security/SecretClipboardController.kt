package com.example.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.example.domain.model.ClipboardClearInterval
import java.security.MessageDigest

/**
 * Best-effort secret clipboard controller (Phase 4).
 *
 * - Tags copied secrets with ClipDescription.EXTRA_IS_SENSITIVE = true on Android 13+ so the OS
 *   clipboard confirmation overlay masks passwords.
 * - Retains only a SHA-256 fingerprint (never plaintext) of the last copied secret so timer expiry
 *   or vault lock clears the clipboard ONLY if it still contains that secret—avoiding overwriting
 *   unrelated items the user copied later.
 * - Explicitly acknowledges Android OS / third-party IME clipboard history limitations.
 */
class SecretClipboardController(
    private val context: Context,
) {
    @Volatile
    private var lastSecretHash: ByteArray? = null

    fun copyValue(
        label: String,
        value: String,
        isSecret: Boolean,
        clearInterval: ClipboardClearInterval,
    ): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText(label, value)
            if (isSecret && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            }
            clipboard.setPrimaryClip(clip)
        }

        lastSecretHash = if (isSecret) sha256(value) else null

        return if (isSecret && clearInterval != ClipboardClearInterval.OFF) {
            "$label copied • Best-effort clear in ${clearInterval.label}"
        } else {
            "$label copied"
        }
    }

    /**
     * Clears the primary clipboard ONLY if it still matches the last secret copied by Aran.
     * Returns true if a matching secret was cleared.
     */
    fun clearIfStillHoldingSecret(): Boolean {
        val expectedHash = lastSecretHash ?: return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return false

        val currentText = runCatching {
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
        }.getOrNull()

        val matches = currentText != null && MessageDigest.isEqual(sha256(currentText), expectedHash)
        lastSecretHash = null
        if (!matches) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { clipboard.clearPrimaryClip() }
        } else {
            runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("", "")) }
        }
        return true
    }

    fun delayMsFor(interval: ClipboardClearInterval): Long = when (interval) {
        ClipboardClearInterval.THIRTY_SECONDS -> 30_000L
        ClipboardClearInterval.ONE_MINUTE -> 60_000L
        ClipboardClearInterval.OFF -> 0L
    }

    companion object {
        internal fun sha256(text: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    }
}
