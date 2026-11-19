package com.example.autofill

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.util.Locale

/**
 * Verifies Android native app identity by reading the calling package's APK signing certificate
 * SHA-256 digests from [PackageManager].
 *
 * Used by both [AranAutofillService] (for pinned `androidapp://<pkg>?sha256=...` logins)
 * and [com.example.passkey.AranCredentialProviderService] (for `android:apk-key-hash:<b64url>`
 * WebAuthn clientDataJSON origins).
 */
object AppSignatureVerifier {

    fun getSigningCertSha256HexDigests(context: Context, packageName: String): Set<String> {
        val rawDigests = getSigningCertSha256Bytes(context, packageName)
        return rawDigests.mapTo(LinkedHashSet()) { digest ->
            digest.joinToString("") { "%02X".format(Locale.US, it) }
        }
    }

    fun getPrimaryApkKeyHashOrigin(context: Context, packageName: String): String? {
        val firstDigest = getSigningCertSha256Bytes(context, packageName).firstOrNull() ?: return null
        val b64Url = Base64.encodeToString(
            firstDigest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        return "android:apk-key-hash:$b64Url"
    }

    @Suppress("DEPRECATION")
    private fun getSigningCertSha256Bytes(context: Context, packageName: String): List<ByteArray> {
        val pm = context.packageManager
        return runCatching {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                when {
                    signingInfo == null -> emptyList()
                    signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.toList()
                    else -> signingInfo.signingCertificateHistory.toList()
                }
            } else {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                info.signatures?.toList().orEmpty()
            }
            val sha256 = MessageDigest.getInstance("SHA-256")
            signatures.map { sig -> sha256.digest(sig.toByteArray()) }
        }.getOrDefault(emptyList())
    }
}
