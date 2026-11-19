package com.example.crypto

import com.example.domain.model.VaultItemType
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Holds the unlocked 256-bit Vault Encryption Key (VEK) in memory only while unlocked.
 * Locking the vault calls wipe(), overwriting the backing byte array with zeroes.
 */
class VaultSessionKey internal constructor(
    val vaultId: String,
    val formatVersion: Int,
    private val vekBytes: ByteArray,
) {
    @Volatile
    private var wiped: Boolean = false

    val isActive: Boolean
        get() = !wiped

    internal fun borrowKeyBytes(): ByteArray {
        if (wiped) throw VaultLockedException()
        return vekBytes.copyOf()
    }

    fun wipe() {
        wiped = true
        vekBytes.fill(0)
    }

    companion object {
        internal fun fromUnwrappedVek(
            vaultId: String,
            formatVersion: Int,
            vekBytes: ByteArray,
        ): VaultSessionKey {
            require(vekBytes.size == VaultFormatConstants.AES_KEY_BYTES) {
                "Unwrapped VEK must be ${VaultFormatConstants.AES_KEY_BYTES} bytes."
            }
            return VaultSessionKey(
                vaultId = vaultId,
                formatVersion = formatVersion,
                vekBytes = vekBytes.copyOf(),
            )
        }
    }
}

data class CreatedVaultBundle(
    val header: VaultHeader,
    val sessionKey: VaultSessionKey,
    val formattedRecoveryKey: String,
)

interface NonceGenerator {
    fun nextNonce(): ByteArray
}

/**
 * Generates fresh 96-bit CSPRNG nonces for AES-256-GCM and enforces uniqueness within the session.
 */
class SecureRandomNonceGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) : NonceGenerator {
    private val issuedNonces = HashSet<String>()

    @Synchronized
    override fun nextNonce(): ByteArray {
        repeat(8) {
            val candidate = ByteArray(VaultFormatConstants.GCM_NONCE_BYTES)
            secureRandom.nextBytes(candidate)
            if (candidate.all { it == 0.toByte() }) return@repeat
            val fingerprint = candidate.joinToString("") { "%02x".format(it) }
            if (issuedNonces.add(fingerprint)) {
                return candidate
            }
        }
        throw NonceGenerationException()
    }
}

interface VaultCryptoEngine {
    fun createVault(
        masterPassword: CharArray,
        kdfParameters: KdfParameters = KdfParameters.OWASP_MOBILE_BALANCED,
    ): CreatedVaultBundle

    fun unlockWithMasterPassword(
        header: VaultHeader,
        masterPassword: CharArray,
    ): VaultSessionKey

    fun unlockWithRecoveryKey(
        header: VaultHeader,
        formattedRecoveryKey: String,
    ): VaultSessionKey

    /**
     * Re-wraps the existing VEK under a fresh salt and new master-password KEK.
     * Existing encrypted records remain valid without re-encryption.
     */
    fun changeMasterPassword(
        header: VaultHeader,
        currentMasterPassword: CharArray,
        newMasterPassword: CharArray,
        newKdfParameters: KdfParameters = header.kdfParameters,
    ): VaultHeader

    /**
     * Unwraps the existing VEK using the independent recovery key and re-wraps it
     * under a new master password without re-encrypting existing records.
     */
    fun recoverAndResetMasterPassword(
        header: VaultHeader,
        formattedRecoveryKey: String,
        newMasterPassword: CharArray,
        newKdfParameters: KdfParameters = header.kdfParameters,
    ): Pair<VaultHeader, VaultSessionKey>

    fun encryptRecord(
        sessionKey: VaultSessionKey,
        recordId: String,
        recordType: VaultItemType,
        plaintext: ByteArray,
    ): EncryptedVaultRecord

    fun decryptRecord(
        sessionKey: VaultSessionKey,
        record: EncryptedVaultRecord,
    ): ByteArray

    fun benchmarkKdf(parameters: KdfParameters = KdfParameters.OWASP_MOBILE_BALANCED): KdfBenchmarkResult
}

/**
 * Production Phase 2 cryptographic engine using:
 * - Bouncy Castle Argon2id (RFC 9106 v0x13) for master-password KEK derivation
 * - Bouncy Castle HKDF-SHA256 (RFC 5869) for independent 256-bit recovery key KEK derivation
 * - Platform JCA AES/GCM/NoPadding (256-bit key, 96-bit CSPRNG nonce, 128-bit auth tag, AAD)
 */
class DefaultVaultCryptoEngine(
    private val secureRandom: SecureRandom = SecureRandom(),
    private val nonceGenerator: NonceGenerator = SecureRandomNonceGenerator(secureRandom),
    private val clockEpochMs: () -> Long = { System.currentTimeMillis() },
) : VaultCryptoEngine {

    override fun createVault(
        masterPassword: CharArray,
        kdfParameters: KdfParameters,
    ): CreatedVaultBundle {
        val vaultId = UUID.randomUUID().toString()
        val now = clockEpochMs()
        val formatVersion = VaultFormatConstants.CURRENT_FORMAT_VERSION
        val rotationEpoch = 1

        val vek = randomBytes(VaultFormatConstants.AES_KEY_BYTES)
        val masterSalt = randomBytes(kdfParameters.saltLengthBytes)
        val recoverySalt = randomBytes(KdfParameters.DEFAULT_SALT_LENGTH_BYTES)
        val recoverySecret = randomBytes(32)

        var masterKek: ByteArray? = null
        var recoveryKek: ByteArray? = null

        try {
            masterKek = deriveArgon2id(masterPassword, masterSalt, kdfParameters)
            val masterWrapAad = AadBuilder.forMasterVekWrap(formatVersion, vaultId, rotationEpoch)
            val masterWrappedVek = aesGcmEncrypt(
                keyBytes = masterKek,
                plaintext = vek,
                aad = masterWrapAad,
                nonce = nonceGenerator.nextNonce(),
            )

            val formattedRecoveryKey = RecoveryKeyCodec.encode(recoverySecret)
            recoveryKek = RecoveryKeyCodec.deriveRecoveryKek(recoverySecret, recoverySalt, vaultId)
            val recoveryWrapAad = AadBuilder.forRecoveryVekWrap(formatVersion, vaultId, rotationEpoch)
            val recoveryWrappedVek = aesGcmEncrypt(
                keyBytes = recoveryKek,
                plaintext = vek,
                aad = recoveryWrapAad,
                nonce = nonceGenerator.nextNonce(),
            )

            val header = VaultHeader(
                formatVersion = formatVersion,
                vaultId = vaultId,
                kdfParameters = kdfParameters,
                masterSalt = masterSalt,
                masterWrappedVek = masterWrappedVek,
                recoverySalt = recoverySalt,
                recoveryWrappedVek = recoveryWrappedVek,
                keyRotationEpoch = rotationEpoch,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            )

            val sessionKey = VaultSessionKey(
                vaultId = vaultId,
                formatVersion = formatVersion,
                vekBytes = vek.copyOf(),
            )

            return CreatedVaultBundle(
                header = header,
                sessionKey = sessionKey,
                formattedRecoveryKey = formattedRecoveryKey,
            )
        } finally {
            vek.fill(0)
            recoverySecret.fill(0)
            masterKek?.fill(0)
            recoveryKek?.fill(0)
        }
    }

    override fun unlockWithMasterPassword(
        header: VaultHeader,
        masterPassword: CharArray,
    ): VaultSessionKey {
        val currentHeader = VaultFormatMigrator.migrateHeaderToCurrent(header)
        var masterKek: ByteArray? = null
        try {
            masterKek = deriveArgon2id(masterPassword, currentHeader.masterSalt, currentHeader.kdfParameters)
            val aad = AadBuilder.forMasterVekWrap(
                formatVersion = currentHeader.formatVersion,
                vaultId = currentHeader.vaultId,
                keyRotationEpoch = currentHeader.keyRotationEpoch,
            )
            val vek = try {
                aesGcmDecrypt(masterKek, currentHeader.masterWrappedVek, aad)
            } catch (_: TamperedDataException) {
                throw InvalidCredentialsException()
            }
            if (vek.size != VaultFormatConstants.AES_KEY_BYTES) {
                vek.fill(0)
                throw InvalidCredentialsException()
            }
            return VaultSessionKey(
                vaultId = currentHeader.vaultId,
                formatVersion = currentHeader.formatVersion,
                vekBytes = vek,
            )
        } finally {
            masterKek?.fill(0)
        }
    }

    override fun unlockWithRecoveryKey(
        header: VaultHeader,
        formattedRecoveryKey: String,
    ): VaultSessionKey {
        val currentHeader = VaultFormatMigrator.migrateHeaderToCurrent(header)
        val rawRecoverySecret = RecoveryKeyCodec.decode(formattedRecoveryKey)
        var recoveryKek: ByteArray? = null
        try {
            recoveryKek = RecoveryKeyCodec.deriveRecoveryKek(
                rawRecoverySecret = rawRecoverySecret,
                recoverySalt = currentHeader.recoverySalt,
                vaultId = currentHeader.vaultId,
            )
            val aad = AadBuilder.forRecoveryVekWrap(
                formatVersion = currentHeader.formatVersion,
                vaultId = currentHeader.vaultId,
                keyRotationEpoch = currentHeader.keyRotationEpoch,
            )
            val vek = try {
                aesGcmDecrypt(recoveryKek, currentHeader.recoveryWrappedVek, aad)
            } catch (_: TamperedDataException) {
                throw InvalidCredentialsException("Recovery key does not match this vault.")
            }
            if (vek.size != VaultFormatConstants.AES_KEY_BYTES) {
                vek.fill(0)
                throw InvalidCredentialsException("Recovery key does not match this vault.")
            }
            return VaultSessionKey(
                vaultId = currentHeader.vaultId,
                formatVersion = currentHeader.formatVersion,
                vekBytes = vek,
            )
        } finally {
            rawRecoverySecret.fill(0)
            recoveryKek?.fill(0)
        }
    }

    override fun changeMasterPassword(
        header: VaultHeader,
        currentMasterPassword: CharArray,
        newMasterPassword: CharArray,
        newKdfParameters: KdfParameters,
    ): VaultHeader {
        val sessionKey = unlockWithMasterPassword(header, currentMasterPassword)
        try {
            return rewrapVekWithNewMasterPassword(
                header = header,
                sessionKey = sessionKey,
                newMasterPassword = newMasterPassword,
                newKdfParameters = newKdfParameters,
            )
        } finally {
            sessionKey.wipe()
        }
    }

    override fun recoverAndResetMasterPassword(
        header: VaultHeader,
        formattedRecoveryKey: String,
        newMasterPassword: CharArray,
        newKdfParameters: KdfParameters,
    ): Pair<VaultHeader, VaultSessionKey> {
        val sessionKey = unlockWithRecoveryKey(header, formattedRecoveryKey)
        try {
            val updatedHeader = rewrapVekWithNewMasterPassword(
                header = header,
                sessionKey = sessionKey,
                newMasterPassword = newMasterPassword,
                newKdfParameters = newKdfParameters,
            )
            return updatedHeader to sessionKey
        } catch (t: Throwable) {
            sessionKey.wipe()
            throw t
        }
    }

    private fun rewrapVekWithNewMasterPassword(
        header: VaultHeader,
        sessionKey: VaultSessionKey,
        newMasterPassword: CharArray,
        newKdfParameters: KdfParameters,
    ): VaultHeader {
        val vek = sessionKey.borrowKeyBytes()
        val newSalt = randomBytes(newKdfParameters.saltLengthBytes)
        var newKek: ByteArray? = null
        try {
            newKek = deriveArgon2id(newMasterPassword, newSalt, newKdfParameters)
            // Keep keyRotationEpoch unchanged for recovery wrap compatibility; fresh salt + fresh nonce.
            val aad = AadBuilder.forMasterVekWrap(
                formatVersion = header.formatVersion,
                vaultId = header.vaultId,
                keyRotationEpoch = header.keyRotationEpoch,
            )
            val newWrappedVek = aesGcmEncrypt(
                keyBytes = newKek,
                plaintext = vek,
                aad = aad,
                nonce = nonceGenerator.nextNonce(),
            )
            return header.copy(
                kdfParameters = newKdfParameters,
                masterSalt = newSalt,
                masterWrappedVek = newWrappedVek,
                updatedAtEpochMs = clockEpochMs(),
            )
        } finally {
            vek.fill(0)
            newKek?.fill(0)
        }
    }

    override fun encryptRecord(
        sessionKey: VaultSessionKey,
        recordId: String,
        recordType: VaultItemType,
        plaintext: ByteArray,
    ): EncryptedVaultRecord {
        VaultFormatMigrator.validateFormatVersion(sessionKey.formatVersion)
        val vek = sessionKey.borrowKeyBytes()
        try {
            val aad = AadBuilder.forRecord(
                formatVersion = sessionKey.formatVersion,
                vaultId = sessionKey.vaultId,
                recordId = recordId,
                recordType = recordType,
            )
            val payload = aesGcmEncrypt(
                keyBytes = vek,
                plaintext = plaintext,
                aad = aad,
                nonce = nonceGenerator.nextNonce(),
            )
            return EncryptedVaultRecord(
                formatVersion = sessionKey.formatVersion,
                vaultId = sessionKey.vaultId,
                recordId = recordId,
                recordType = recordType,
                payload = payload,
                updatedAtEpochMs = clockEpochMs(),
            )
        } finally {
            vek.fill(0)
        }
    }

    override fun decryptRecord(
        sessionKey: VaultSessionKey,
        record: EncryptedVaultRecord,
    ): ByteArray {
        val currentRecord = VaultFormatMigrator.migrateRecordToCurrent(record)
        if (currentRecord.vaultId != sessionKey.vaultId) {
            throw TamperedDataException("Record vault identity does not match active session.")
        }
        val vek = sessionKey.borrowKeyBytes()
        try {
            val aad = AadBuilder.forRecord(
                formatVersion = currentRecord.formatVersion,
                vaultId = currentRecord.vaultId,
                recordId = currentRecord.recordId,
                recordType = currentRecord.recordType,
            )
            return aesGcmDecrypt(vek, currentRecord.payload, aad)
        } finally {
            vek.fill(0)
        }
    }

    override fun benchmarkKdf(parameters: KdfParameters): KdfBenchmarkResult {
        val samplePassword = "benchmark-only-not-a-secret".toCharArray()
        val sampleSalt = randomBytes(parameters.saltLengthBytes)
        var derived: ByteArray? = null
        val startNs = System.nanoTime()
        try {
            derived = deriveArgon2id(samplePassword, sampleSalt, parameters)
        } finally {
            samplePassword.fill('\u0000')
            derived?.fill(0)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
        return KdfBenchmarkResult(parameters = parameters, durationMs = elapsedMs)
    }

    private fun randomBytes(length: Int): ByteArray {
        val out = ByteArray(length)
        secureRandom.nextBytes(out)
        return out
    }

    companion object {
        internal fun deriveArgon2id(
            password: CharArray,
            salt: ByteArray,
            parameters: KdfParameters,
            secret: ByteArray? = null,
            associatedData: ByteArray? = null,
        ): ByteArray {
            val passwordBytes = charArrayToUtf8Bytes(password)
            try {
                val builder = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .withIterations(parameters.iterations)
                    .withMemoryAsKB(parameters.memoryKiB)
                    .withParallelism(parameters.parallelism)
                    .withSalt(salt)
                if (secret != null) {
                    builder.withSecret(secret)
                }
                if (associatedData != null) {
                    builder.withAdditional(associatedData)
                }
                val generator = Argon2BytesGenerator()
                generator.init(builder.build())
                val derived = ByteArray(parameters.hashLengthBytes)
                generator.generateBytes(passwordBytes, derived, 0, derived.size)
                return derived
            } finally {
                passwordBytes.fill(0)
            }
        }

        internal fun aesGcmEncrypt(
            keyBytes: ByteArray,
            plaintext: ByteArray,
            aad: ByteArray,
            nonce: ByteArray,
        ): EncryptedPayload {
            require(keyBytes.size == VaultFormatConstants.AES_KEY_BYTES) {
                "AES-256 key must be 32 bytes."
            }
            require(nonce.size == VaultFormatConstants.GCM_NONCE_BYTES) {
                "AES-GCM nonce must be 12 bytes."
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(VaultFormatConstants.GCM_TAG_BITS, nonce)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            if (aad.isNotEmpty()) {
                cipher.updateAAD(aad)
            }
            val ciphertextAndTag = cipher.doFinal(plaintext)
            return EncryptedPayload(
                nonce = nonce.copyOf(),
                ciphertextAndTag = ciphertextAndTag,
            )
        }

        internal fun aesGcmDecrypt(
            keyBytes: ByteArray,
            payload: EncryptedPayload,
            aad: ByteArray,
        ): ByteArray {
            require(keyBytes.size == VaultFormatConstants.AES_KEY_BYTES) {
                "AES-256 key must be 32 bytes."
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val keySpec = SecretKeySpec(keyBytes, "AES")
            val gcmSpec = GCMParameterSpec(VaultFormatConstants.GCM_TAG_BITS, payload.nonce)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            if (aad.isNotEmpty()) {
                cipher.updateAAD(aad)
            }
            return try {
                cipher.doFinal(payload.ciphertextAndTag)
            } catch (_: AEADBadTagException) {
                throw TamperedDataException()
            } catch (_: Exception) {
                throw TamperedDataException()
            }
        }

        private fun charArrayToUtf8Bytes(chars: CharArray): ByteArray {
            val charBuffer = CharBuffer.wrap(chars)
            val byteBuffer: ByteBuffer = Charsets.UTF_8.encode(charBuffer)
            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)
            if (byteBuffer.hasArray()) {
                byteBuffer.array().fill(0)
            }
            return bytes
        }
    }
}
