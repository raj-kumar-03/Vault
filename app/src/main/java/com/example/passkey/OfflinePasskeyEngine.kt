package com.example.passkey

import android.util.Base64
import com.example.autofill.OriginMatcher
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

/**
 * Encrypted-at-rest payload serialized inside [VaultItem.secret] for [VaultItemType.PASSKEY].
 *
 * The ECDSA P-256 private key (`privateKeyPkcs8B64Url`) never leaves the vault unencrypted:
 * it lives exclusively inside the AES-256-GCM encrypted record blob in local SQLite.
 */
data class PasskeyRecordPayload(
    val credentialIdB64Url: String,
    val rpId: String,
    val rpName: String,
    val userHandleB64Url: String,
    val userName: String,
    val userDisplayName: String,
    val privateKeyPkcs8B64Url: String,
    val publicKeySpkiB64Url: String,
    val signCount: Long,
    val createdAtEpochMs: Long,
) {
    fun toJsonString(): String = JSONObject().apply {
        put("format", PAYLOAD_FORMAT)
        put("alg", COSE_ALG_ES256)
        put("credentialId", credentialIdB64Url)
        put("rpId", rpId)
        put("rpName", rpName)
        put("userHandle", userHandleB64Url)
        put("userName", userName)
        put("userDisplayName", userDisplayName)
        put("privateKeyPkcs8", privateKeyPkcs8B64Url)
        put("publicKeySpki", publicKeySpkiB64Url)
        put("signCount", signCount)
        put("createdAtEpochMs", createdAtEpochMs)
    }.toString()

    fun toVaultItem(existingId: String = ""): VaultItem = VaultItem(
        id = existingId,
        type = VaultItemType.PASSKEY,
        title = rpName.ifBlank { rpId },
        username = userName.ifBlank { userDisplayName },
        secret = toJsonString(),
        website = "https://$rpId",
        normalizedDomain = rpId,
        notes = "Offline FIDO2/WebAuthn ES256 Passkey (signCount=$signCount). Private key encrypted with AES-256-GCM.",
        isFavorite = false,
        createdAtLabel = "Just now",
        updatedAtLabel = "Just now",
        lastUsedEpochMs = System.currentTimeMillis(),
        secondaryIdentifier = "ES256 • $rpId",
    )

    companion object {
        const val PAYLOAD_FORMAT = "aran-fido2-es256-v1"
        const val COSE_ALG_ES256 = -7

        fun fromJsonOrNull(raw: String): PasskeyRecordPayload? = runCatching {
            val obj = JSONObject(raw)
            if (obj.optString("format") != PAYLOAD_FORMAT) return null
            PasskeyRecordPayload(
                credentialIdB64Url = obj.getString("credentialId"),
                rpId = obj.getString("rpId"),
                rpName = obj.optString("rpName", obj.getString("rpId")),
                userHandleB64Url = obj.getString("userHandle"),
                userName = obj.optString("userName", ""),
                userDisplayName = obj.optString("userDisplayName", ""),
                privateKeyPkcs8B64Url = obj.getString("privateKeyPkcs8"),
                publicKeySpkiB64Url = obj.getString("publicKeySpki"),
                signCount = obj.optLong("signCount", 0L),
                createdAtEpochMs = obj.optLong("createdAtEpochMs", 0L),
            )
        }.getOrNull()
    }
}

data class PasskeyRegistrationResult(
    val record: PasskeyRecordPayload,
    val registrationResponseJson: String,
)

data class PasskeyAssertionResult(
    val updatedRecord: PasskeyRecordPayload,
    val authenticationResponseJson: String,
)

class PasskeySecurityException(message: String) : SecurityException(message)

/**
 * 100% Offline FIDO2 / W3C WebAuthn Passkey cryptographic engine (Phase 5).
 *
 * Implements:
 * - Real ECDSA P-256 (`secp256r1`, COSE alg `-7` / ES256) key generation.
 * - Strict Relying Party ID (`rpId`) vs verified calling origin enforcement.
 * - `excludeCredentials` / `allowCredentials` enforcement.
 * - Authentic WebAuthn `authenticatorData`, RFC 8949 CBOR `COSE_Key` & `attestationObject`.
 * - Authentic `SHA256withECDSA` assertion signing over `authenticatorData || SHA-256(clientDataJSON)`
 *   (or browser-supplied `clientDataHash` for privileged callers).
 */
object OfflinePasskeyEngine {

    private val random = SecureRandom()
    // Static non-identifying AAGUID for offline Aran provider (16 zero bytes per WebAuthn "none" attestation)
    private val ARAN_AAGUID = ByteArray(16)

    // AuthenticatorData flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10)
    private const val FLAG_UP = 0x01
    private const val FLAG_UV = 0x04
    private const val FLAG_BE = 0x08
    private const val FLAG_BS = 0x10
    private const val FLAG_AT = 0x40

    fun createPasskeyRegistration(
        requestJson: String,
        verifiedCallingOrigin: String,
        clientDataHash: ByteArray? = null,
        existingPasskeys: List<PasskeyRecordPayload> = emptyList(),
    ): PasskeyRegistrationResult {
        val options = JSONObject(requestJson)
        val rpObj = options.getJSONObject("rp")
        val rpId = OriginMatcher.normalizeHost(rpObj.optString("id"))
            ?: throw PasskeySecurityException("Missing or invalid WebAuthn rp.id.")
        val rpName = rpObj.optString("name", rpId)

        if (!OriginMatcher.isRpIdValidForOrigin(rpId, verifiedCallingOrigin)) {
            throw PasskeySecurityException(
                "Cross-origin passkey registration blocked: rpId '$rpId' is not valid for '$verifiedCallingOrigin'."
            )
        }

        // Verify ES256 (-7) is accepted if pubKeyCredParams is specified.
        val params = options.optJSONArray("pubKeyCredParams")
        if (params != null && params.length() > 0) {
            var supportsEs256 = false
            for (i in 0 until params.length()) {
                val alg = params.getJSONObject(i).optInt("alg", 0)
                if (alg == PasskeyRecordPayload.COSE_ALG_ES256) {
                    supportsEs256 = true
                    break
                }
            }
            if (!supportsEs256) {
                throw PasskeySecurityException("Relying party does not accept ES256 (-7) passkeys.")
            }
        }

        // Enforce excludeCredentials to prevent duplicate credential registration.
        val excluded = options.optJSONArray("excludeCredentials")
        if (excluded != null) {
            val excludedIds = HashSet<String>()
            for (i in 0 until excluded.length()) {
                val id = excluded.optJSONObject(i)?.optString("id").orEmpty()
                if (id.isNotBlank()) excludedIds.add(id)
            }
            val alreadyExists = existingPasskeys.any {
                it.rpId.equals(rpId, ignoreCase = true) && it.credentialIdB64Url in excludedIds
            }
            if (alreadyExists) {
                throw PasskeySecurityException("A passkey for this account already exists in Aran.")
            }
        }

        val userObj = options.getJSONObject("user")
        val userHandleB64Url = userObj.getString("id")
        val userName = userObj.optString("name", "")
        val userDisplayName = userObj.optString("displayName", userName)
        val challengeB64Url = options.getString("challenge")

        // Generate fresh 32-byte credential ID and ECDSA P-256 key pair.
        val credentialId = ByteArray(32).also { random.nextBytes(it) }
        val credentialIdB64Url = b64UrlEncode(credentialId)

        val keyPairGen = KeyPairGenerator.getInstance("EC")
        keyPairGen.initialize(ECGenParameterSpec("secp256r1"), random)
        val keyPair = keyPairGen.generateKeyPair()
        val ecPublicKey = keyPair.public as ECPublicKey

        val cosePublicKeyBytes = encodeEcP256CoseKey(ecPublicKey)
        val authData = buildRegistrationAuthData(
            rpId = rpId,
            credentialId = credentialId,
            cosePublicKeyBytes = cosePublicKeyBytes,
            signCount = 0L,
        )
        val attestationObject = encodeNoneAttestationObject(authData)

        val clientDataJsonBytes = if (clientDataHash != null) {
            // Per Android Credential Manager docs for privileged browser callers using clientDataHash.
            """{"type":"webauthn.create","challenge":"$challengeB64Url","origin":"$verifiedCallingOrigin"}"""
                .toByteArray(StandardCharsets.UTF_8)
        } else {
            JSONObject().apply {
                put("type", "webauthn.create")
                put("challenge", challengeB64Url)
                put("origin", verifiedCallingOrigin)
                put("crossOrigin", false)
            }.toString().toByteArray(StandardCharsets.UTF_8)
        }

        val spkiBytes = keyPair.public.encoded
        val pkcs8Bytes = keyPair.private.encoded

        val responseJson = JSONObject().apply {
            put("id", credentialIdB64Url)
            put("rawId", credentialIdB64Url)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put(
                "response",
                JSONObject().apply {
                    put("clientDataJSON", b64UrlEncode(clientDataJsonBytes))
                    put("attestationObject", b64UrlEncode(attestationObject))
                    put("authenticatorData", b64UrlEncode(authData))
                    put("publicKey", b64UrlEncode(spkiBytes))
                    put("publicKeyAlgorithm", PasskeyRecordPayload.COSE_ALG_ES256)
                    put("transports", JSONArray().put("internal"))
                },
            )
            put("clientExtensionResults", JSONObject())
        }.toString()

        val record = PasskeyRecordPayload(
            credentialIdB64Url = credentialIdB64Url,
            rpId = rpId,
            rpName = rpName,
            userHandleB64Url = userHandleB64Url,
            userName = userName,
            userDisplayName = userDisplayName,
            privateKeyPkcs8B64Url = b64UrlEncode(pkcs8Bytes),
            publicKeySpkiB64Url = b64UrlEncode(spkiBytes),
            signCount = 0L,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        pkcs8Bytes.fill(0)

        return PasskeyRegistrationResult(
            record = record,
            registrationResponseJson = responseJson,
        )
    }

    fun signPasskeyAssertion(
        requestJson: String,
        passkey: PasskeyRecordPayload,
        verifiedCallingOrigin: String,
        clientDataHash: ByteArray? = null,
    ): PasskeyAssertionResult {
        val options = JSONObject(requestJson)
        val requestedRpId = OriginMatcher.normalizeHost(options.optString("rpId"))
            ?: throw PasskeySecurityException("Missing or invalid WebAuthn rpId in assertion request.")

        if (!requestedRpId.equals(passkey.rpId, ignoreCase = true)) {
            throw PasskeySecurityException(
                "Passkey rpId mismatch: credential belongs to '${passkey.rpId}', not '$requestedRpId'."
            )
        }

        if (!OriginMatcher.isRpIdValidForOrigin(requestedRpId, verifiedCallingOrigin)) {
            throw PasskeySecurityException(
                "Cross-origin passkey assertion blocked: '$verifiedCallingOrigin' is not valid for '$requestedRpId'."
            )
        }

        val allowCreds = options.optJSONArray("allowCredentials")
        if (allowCreds != null && allowCreds.length() > 0) {
            var allowed = false
            for (i in 0 until allowCreds.length()) {
                if (allowCreds.optJSONObject(i)?.optString("id") == passkey.credentialIdB64Url) {
                    allowed = true
                    break
                }
            }
            if (!allowed) {
                throw PasskeySecurityException("Selected passkey is not in allowCredentials.")
            }
        }

        val challengeB64Url = options.getString("challenge")
        val nextSignCount = passkey.signCount + 1L
        val authData = buildAssertionAuthData(rpId = passkey.rpId, signCount = nextSignCount)

        val clientDataJsonBytes = JSONObject().apply {
            put("type", "webauthn.get")
            put("challenge", challengeB64Url)
            put("origin", verifiedCallingOrigin)
            put("crossOrigin", false)
        }.toString().toByteArray(StandardCharsets.UTF_8)

        val effectiveHash = clientDataHash ?: sha256(clientDataJsonBytes)
        val signedPayload = ByteArray(authData.size + effectiveHash.size)
        System.arraycopy(authData, 0, signedPayload, 0, authData.size)
        System.arraycopy(effectiveHash, 0, signedPayload, authData.size, effectiveHash.size)

        val pkcs8Bytes = b64UrlDecode(passkey.privateKeyPkcs8B64Url)
        val signatureBytes = try {
            val privateKey: PrivateKey = KeyFactory.getInstance("EC")
                .generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
            Signature.getInstance("SHA256withECDSA").run {
                initSign(privateKey, random)
                update(signedPayload)
                sign()
            }
        } finally {
            pkcs8Bytes.fill(0)
        }

        val responseJson = JSONObject().apply {
            put("id", passkey.credentialIdB64Url)
            put("rawId", passkey.credentialIdB64Url)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put(
                "response",
                JSONObject().apply {
                    put("clientDataJSON", b64UrlEncode(clientDataJsonBytes))
                    put("authenticatorData", b64UrlEncode(authData))
                    put("signature", b64UrlEncode(signatureBytes))
                    put("userHandle", passkey.userHandleB64Url)
                },
            )
            put("clientExtensionResults", JSONObject())
        }.toString()

        return PasskeyAssertionResult(
            updatedRecord = passkey.copy(signCount = nextSignCount),
            authenticationResponseJson = responseJson,
        )
    }

    /**
     * Cryptographically verifies an assertion response against the passkey's SPKI public key.
     * Used in Phase 5 gate tests to confirm authentic WebAuthn ECDSA P-256 signatures.
     */
    fun verifyAssertionSignature(
        authenticationResponseJson: String,
        publicKeySpkiB64Url: String,
        overrideClientDataHash: ByteArray? = null,
    ): Boolean = runCatching {
        val obj = JSONObject(authenticationResponseJson)
        val resp = obj.getJSONObject("response")
        val authData = b64UrlDecode(resp.getString("authenticatorData"))
        val signature = b64UrlDecode(resp.getString("signature"))
        val hash = overrideClientDataHash ?: sha256(b64UrlDecode(resp.getString("clientDataJSON")))

        val signedPayload = ByteArray(authData.size + hash.size)
        System.arraycopy(authData, 0, signedPayload, 0, authData.size)
        System.arraycopy(hash, 0, signedPayload, authData.size, hash.size)

        val publicKey: PublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(b64UrlDecode(publicKeySpkiB64Url)))
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(signedPayload)
            verify(signature)
        }
    }.getOrDefault(false)

    fun extractMatchingPasskeys(
        items: List<VaultItem>,
        rpId: String,
        allowCredentialIds: Set<String> = emptySet(),
    ): List<Pair<VaultItem, PasskeyRecordPayload>> {
        val normRpId = OriginMatcher.normalizeHost(rpId) ?: return emptyList()
        return items.asSequence()
            .filter { it.type == VaultItemType.PASSKEY }
            .mapNotNull { item ->
                val payload = PasskeyRecordPayload.fromJsonOrNull(item.secret) ?: return@mapNotNull null
                if (!payload.rpId.equals(normRpId, ignoreCase = true)) return@mapNotNull null
                if (allowCredentialIds.isNotEmpty() && payload.credentialIdB64Url !in allowCredentialIds) {
                    return@mapNotNull null
                }
                item to payload
            }
            .toList()
    }

    private fun buildRegistrationAuthData(
        rpId: String,
        credentialId: ByteArray,
        cosePublicKeyBytes: ByteArray,
        signCount: Long,
    ): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray(StandardCharsets.UTF_8))
        val flags = (FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS or FLAG_AT).toByte()
        val out = ByteArrayOutputStream()
        out.write(rpIdHash)
        out.write(byteArrayOf(flags))
        out.write(uint32BigEndian(signCount))
        out.write(ARAN_AAGUID)
        out.write(uint16BigEndian(credentialId.size))
        out.write(credentialId)
        out.write(cosePublicKeyBytes)
        return out.toByteArray()
    }

    private fun buildAssertionAuthData(rpId: String, signCount: Long): ByteArray {
        val rpIdHash = sha256(rpId.toByteArray(StandardCharsets.UTF_8))
        val flags = (FLAG_UP or FLAG_UV or FLAG_BE or FLAG_BS).toByte()
        val out = ByteArrayOutputStream()
        out.write(rpIdHash)
        out.write(byteArrayOf(flags))
        out.write(uint32BigEndian(signCount))
        return out.toByteArray()
    }

    /**
     * Encodes an ECDSA P-256 public key as a canonical COSE_Key CBOR map:
     * { 1: 2 (EC2), 3: -7 (ES256), -1: 1 (P-256), -2: x (32B), -3: y (32B) }
     */
    private fun encodeEcP256CoseKey(publicKey: ECPublicKey): ByteArray {
        val x = bigIntToFixed32(publicKey.w.affineX)
        val y = bigIntToFixed32(publicKey.w.affineY)
        val out = ByteArrayOutputStream()
        cborWriteMapHeader(out, 5)
        cborWriteInt(out, 1); cborWriteInt(out, 2)       // kty: EC2
        cborWriteInt(out, 3); cborWriteInt(out, -7)      // alg: ES256
        cborWriteInt(out, -1); cborWriteInt(out, 1)      // crv: P-256
        cborWriteInt(out, -2); cborWriteBytes(out, x)    // x coord
        cborWriteInt(out, -3); cborWriteBytes(out, y)    // y coord
        return out.toByteArray()
    }

    /**
     * Encodes WebAuthn "none" attestationObject in CBOR:
     * { "fmt": "none", "attStmt": {}, "authData": bstr }
     */
    private fun encodeNoneAttestationObject(authData: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        cborWriteMapHeader(out, 3)
        cborWriteText(out, "fmt"); cborWriteText(out, "none")
        cborWriteText(out, "attStmt"); cborWriteMapHeader(out, 0)
        cborWriteText(out, "authData"); cborWriteBytes(out, authData)
        return out.toByteArray()
    }

    private fun cborWriteTypeAndLength(out: ByteArrayOutputStream, majorType: Int, value: Int) {
        val prefix = majorType shl 5
        when {
            value < 24 -> out.write(prefix or value)
            value < 256 -> {
                out.write(prefix or 24)
                out.write(value)
            }
            value < 65536 -> {
                out.write(prefix or 25)
                out.write((value ushr 8) and 0xFF)
                out.write(value and 0xFF)
            }
            else -> {
                out.write(prefix or 26)
                out.write((value ushr 24) and 0xFF)
                out.write((value ushr 16) and 0xFF)
                out.write((value ushr 8) and 0xFF)
                out.write(value and 0xFF)
            }
        }
    }

    private fun cborWriteMapHeader(out: ByteArrayOutputStream, size: Int) =
        cborWriteTypeAndLength(out, majorType = 5, value = size)

    private fun cborWriteInt(out: ByteArrayOutputStream, value: Int) {
        if (value >= 0) {
            cborWriteTypeAndLength(out, majorType = 0, value = value)
        } else {
            cborWriteTypeAndLength(out, majorType = 1, value = -1 - value)
        }
    }

    private fun cborWriteBytes(out: ByteArrayOutputStream, bytes: ByteArray) {
        cborWriteTypeAndLength(out, majorType = 2, value = bytes.size)
        out.write(bytes)
    }

    private fun cborWriteText(out: ByteArrayOutputStream, text: String) {
        val utf8 = text.toByteArray(StandardCharsets.UTF_8)
        cborWriteTypeAndLength(out, majorType = 3, value = utf8.size)
        out.write(utf8)
    }

    private fun bigIntToFixed32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val out = ByteArray(32)
        if (raw.size == 32) return raw
        if (raw.size > 32) {
            System.arraycopy(raw, raw.size - 32, out, 0, 32)
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.size, raw.size)
        }
        return out
    }

    private fun uint16BigEndian(value: Int): ByteArray =
        ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(value.toShort()).array()

    private fun uint32BigEndian(value: Long): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value.toInt()).array()

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun b64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun b64UrlDecode(encoded: String): ByteArray =
        Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
