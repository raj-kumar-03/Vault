package com.example.autofill

import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import java.net.IDN
import java.net.URI
import java.util.Locale

enum class OriginMatchStrength {
    EXACT_HOST,
    SAME_REGISTRABLE_DOMAIN,
    VERIFIED_NATIVE_APP,
}

data class MatchedVaultCredential(
    val item: VaultItem,
    val strength: OriginMatchStrength,
    val requiresExplicitConfirmation: Boolean,
    val matchReason: String,
)

sealed class AutofillMatchDecision {
    data class Allowed(
        val matches: List<MatchedVaultCredential>,
        val requiresExplicitSelection: Boolean,
        val verifiedOriginLabel: String,
    ) : AutofillMatchDecision()

    data class BlockedInsecureHttp(
        val domain: String,
        val reason: String,
    ) : AutofillMatchDecision()

    data class BlockedSecurityViolation(
        val reason: String,
    ) : AutofillMatchDecision()
}

data class AutofillTargetIdentity(
    val packageName: String,
    val webDomain: String? = null,
    val webScheme: String? = null,
    val signingCertSha256Digests: Set<String> = emptySet(),
    val hasConflictingWebOrigins: Boolean = false,
) {
    val isWebContext: Boolean
        get() = !webDomain.isNullOrBlank()
}

/**
 * Strict Phase 5 origin, hostname, scheme, WebAuthn RP ID, and native Android app-signature
 * matching engine designed to prevent cross-origin leakage, phishing lookalikes, and repackaged
 * app credential theft.
 */
object OriginMatcher {

    // Common multi-label public suffixes so "evil.co.uk" NEVER matches "bank.co.uk".
    private val MULTI_LABEL_PUBLIC_SUFFIXES = setOf(
        "co.uk", "org.uk", "gov.uk", "ac.uk", "me.uk", "net.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp",
        "co.nz", "net.nz", "org.nz", "govt.nz",
        "co.in", "net.in", "org.in", "gov.in", "ac.in",
        "com.br", "net.br", "org.br", "gov.br",
        "com.mx", "org.mx", "gob.mx",
        "com.cn", "net.cn", "org.cn", "gov.cn",
        "com.sg", "org.sg", "gov.sg",
        "com.hk", "org.hk", "gov.hk",
        "co.kr", "or.kr", "go.kr",
        "co.za", "org.za", "gov.za",
        "com.tr", "org.tr", "gov.tr",
        "com.tw", "org.tw", "gov.tw",
        "github.io", "pages.dev", "vercel.app", "netlify.app", "appspot.com", "web.app",
    )

    private val VALID_HOST_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val IPV4_PATTERN = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$")
    private val HEX_SHA256_PATTERN = Regex("^[0-9A-F]{64}$")

    fun normalizeHost(rawHostOrUrl: String?): String? {
        val raw = rawHostOrUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (raw.contains('\n') || raw.contains('\r') || raw.contains('\u0000')) return null

        // Reject userinfo spoofing like "https://bank.com@evil.com" immediately.
        val hostCandidate = if (raw.contains("://")) {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (!uri.rawUserInfo.isNullOrEmpty()) return null
            uri.host ?: return null
        } else {
            if (raw.contains('@')) return null
            val stripped = raw
                .substringBefore('/')
                .substringBefore('?')
                .substringBefore('#')
                .substringBefore(':')
            stripped
        }

        val ascii = runCatching { IDN.toASCII(hostCandidate, IDN.ALLOW_UNASSIGNED) }
            .getOrNull()
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase(Locale.US)
            ?: return null

        if (ascii.isEmpty() || ascii.length > 253) return null
        if (IPV4_PATTERN.matches(ascii) || ascii.contains(':')) return null

        val labels = ascii.split('.')
        if (labels.size < 2) return null
        if (labels.any { !VALID_HOST_LABEL.matches(it) }) return null
        return ascii
    }

    /**
     * Extracts the registrable domain (eTLD+1) using the multi-label public suffix rules.
     * Returns null for bare public suffixes (e.g., "co.uk" or "github.io").
     */
    fun registrableDomain(normalizedHost: String): String? {
        val labels = normalizedHost.split('.')
        if (labels.size < 2) return null
        val lastTwo = "${labels[labels.size - 2]}.${labels[labels.size - 1]}"
        return if (lastTwo in MULTI_LABEL_PUBLIC_SUFFIXES) {
            if (labels.size < 3) null
            else "${labels[labels.size - 3]}.$lastTwo"
        } else {
            lastTwo
        }
    }

    /**
     * Parses an Android app URI stored in a VaultItem's website field:
     *   androidapp://com.example.bank
     *   androidapp://com.example.bank?sha256=AB:CD:...
     */
    fun parseAndroidAppBinding(rawWebsite: String): AndroidAppBinding? {
        val trimmed = rawWebsite.trim()
        if (!trimmed.startsWith("androidapp://", ignoreCase = true)) return null
        val withoutScheme = trimmed.substring("androidapp://".length)
        val pkg = withoutScheme.substringBefore('?').substringBefore('/').trim()
        if (pkg.isEmpty() || !pkg.contains('.')) return null

        val query = withoutScheme.substringAfter('?', "")
        val shaParam = query.split('&')
            .mapNotNull { part ->
                val kv = part.split('=', limit = 2)
                if (kv.size == 2 && kv[0].equals("sha256", ignoreCase = true)) kv[1] else null
            }
            .firstOrNull()
            ?.let(::canonicalizeSha256Hex)

        return AndroidAppBinding(packageName = pkg, pinnedCertSha256 = shaParam)
    }

    fun canonicalizeSha256Hex(raw: String): String? {
        val cleaned = raw.replace(":", "").replace(" ", "").trim().uppercase(Locale.US)
        return if (HEX_SHA256_PATTERN.matches(cleaned)) cleaned else null
    }

    /**
     * Evaluates all decrypted login items against a verified [AutofillTargetIdentity].
     */
    fun matchCredentials(
        target: AutofillTargetIdentity,
        items: List<VaultItem>,
        ownPackageName: String,
        allowInsecureHttp: Boolean = false,
    ): AutofillMatchDecision {
        if (target.packageName.equals(ownPackageName, ignoreCase = true)) {
            return AutofillMatchDecision.BlockedSecurityViolation(
                "Refusing to autofill into Aran itself."
            )
        }

        if (target.hasConflictingWebOrigins) {
            return AutofillMatchDecision.BlockedSecurityViolation(
                "Form contains mixed or conflicting embedded web origins; autofill blocked to prevent cross-origin leakage."
            )
        }

        val loginItems = items.filter { it.type == VaultItemType.LOGIN && it.secret.isNotEmpty() }

        if (target.isWebContext) {
            val normalizedTargetHost = normalizeHost(target.webDomain)
                ?: return AutofillMatchDecision.BlockedSecurityViolation(
                    "Invalid or non-canonical web domain in autofill request."
                )

            val scheme = target.webScheme?.trim()?.lowercase(Locale.US)
            if (scheme == "http" && !allowInsecureHttp) {
                return AutofillMatchDecision.BlockedInsecureHttp(
                    domain = normalizedTargetHost,
                    reason = "Insecure HTTP autofill is blocked by default for $normalizedTargetHost.",
                )
            }
            if (scheme != null && scheme != "https" && scheme != "http") {
                return AutofillMatchDecision.BlockedSecurityViolation(
                    "Unsupported web scheme '$scheme' blocked from autofill."
                )
            }

            val targetRegistrable = registrableDomain(normalizedTargetHost)
                ?: return AutofillMatchDecision.BlockedSecurityViolation(
                    "Target host '$normalizedTargetHost' is a public suffix and cannot be matched."
                )

            val matches = loginItems.mapNotNull { item ->
                val itemHost = normalizeHost(item.website.ifBlank { item.normalizedDomain })
                    ?: return@mapNotNull null
                val itemRegistrable = registrableDomain(itemHost) ?: return@mapNotNull null

                when {
                    itemHost == normalizedTargetHost -> MatchedVaultCredential(
                        item = item,
                        strength = OriginMatchStrength.EXACT_HOST,
                        requiresExplicitConfirmation = false,
                        matchReason = "Exact HTTPS host match ($normalizedTargetHost)",
                    )

                    itemRegistrable == targetRegistrable -> MatchedVaultCredential(
                        item = item,
                        strength = OriginMatchStrength.SAME_REGISTRABLE_DOMAIN,
                        requiresExplicitConfirmation = true,
                        matchReason = "Subdomain match under $targetRegistrable — confirm before filling",
                    )

                    else -> null
                }
            }.sortedWith(
                compareBy<MatchedVaultCredential> { it.strength.ordinal }
                    .thenByDescending { it.item.lastUsedEpochMs }
            )

            val ambiguous = matches.size > 1 || matches.any { it.requiresExplicitConfirmation }
            return AutofillMatchDecision.Allowed(
                matches = matches,
                requiresExplicitSelection = ambiguous,
                verifiedOriginLabel = "${scheme ?: "https"}://$normalizedTargetHost",
            )
        }

        // Native Android app autofill matching with signing certificate verification.
        val normalizedCallerDigests = target.signingCertSha256Digests
            .mapNotNull(::canonicalizeSha256Hex)
            .toSet()

        var rejectedDueToCertMismatch = false
        val matches = loginItems.mapNotNull { item ->
            val appBinding = parseAndroidAppBinding(item.website)
                ?: parseAndroidAppBinding(item.secondaryIdentifier)
            if (appBinding != null && appBinding.packageName == target.packageName) {
                val pinned = appBinding.pinnedCertSha256
                if (pinned != null) {
                    if (pinned in normalizedCallerDigests) {
                        MatchedVaultCredential(
                            item = item,
                            strength = OriginMatchStrength.VERIFIED_NATIVE_APP,
                            requiresExplicitConfirmation = false,
                            matchReason = "Verified native app & SHA-256 signing certificate",
                        )
                    } else {
                        rejectedDueToCertMismatch = true
                        null
                    }
                } else {
                    // Package name matched without pinned certificate -> always require explicit user confirmation.
                    MatchedVaultCredential(
                        item = item,
                        strength = OriginMatchStrength.VERIFIED_NATIVE_APP,
                        requiresExplicitConfirmation = true,
                        matchReason = "Native package match (${target.packageName}) — confirm identity",
                    )
                }
            } else {
                // Reverse-DNS package fallback (e.g., com.codeforge.android -> codeforge.com) ONLY with confirmation
                val pkgDomain = inferDomainFromPackage(target.packageName)
                val itemHost = normalizeHost(item.website.ifBlank { item.normalizedDomain })
                val itemReg = itemHost?.let(::registrableDomain)
                if (pkgDomain != null && itemReg != null && pkgDomain == itemReg) {
                    MatchedVaultCredential(
                        item = item,
                        strength = OriginMatchStrength.SAME_REGISTRABLE_DOMAIN,
                        requiresExplicitConfirmation = true,
                        matchReason = "App package ${target.packageName} matches $itemReg — confirm before filling",
                    )
                } else {
                    null
                }
            }
        }.sortedByDescending { it.item.lastUsedEpochMs }

        if (matches.isEmpty() && rejectedDueToCertMismatch) {
            return AutofillMatchDecision.BlockedSecurityViolation(
                "App signing certificate for ${target.packageName} does not match the pinned SHA-256 certificate in your vault."
            )
        }

        return AutofillMatchDecision.Allowed(
            matches = matches,
            requiresExplicitSelection = true,
            verifiedOriginLabel = "androidapp://${target.packageName}",
        )
    }

    /**
     * Validates a WebAuthn / FIDO2 RP ID against a verified origin per W3C WebAuthn §5.4.1:
     * - RP ID must be a valid domain (not a public suffix or IP).
     * - Calling origin must be HTTPS (or an authenticated android:apk-key-hash:... origin).
     * - For web origins, calling host must equal rpId or be a strict subdomain of rpId.
     */
    fun isRpIdValidForOrigin(rpId: String, callingOrigin: String): Boolean {
        val normalizedRpId = normalizeHost(rpId) ?: return false
        if (registrableDomain(normalizedRpId) == null) return false

        if (callingOrigin.startsWith("android:apk-key-hash:")) {
            // Native Android Credential Manager callers use apk-key-hash origin verified by the OS.
            return callingOrigin.length > "android:apk-key-hash:".length
        }

        if (!callingOrigin.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val originHost = normalizeHost(callingOrigin) ?: return false
        return originHost == normalizedRpId || originHost.endsWith(".$normalizedRpId")
    }

    private fun inferDomainFromPackage(packageName: String): String? {
        val parts = packageName.lowercase(Locale.US).split('.')
        if (parts.size < 2) return null
        val tld = parts[0]
        if (tld !in setOf("com", "org", "net", "io", "dev", "app", "co")) return null
        val candidate = "${parts[1]}.$tld"
        return normalizeHost(candidate)?.let(::registrableDomain)
    }

    data class AndroidAppBinding(
        val packageName: String,
        val pinnedCertSha256: String?,
    )
}
