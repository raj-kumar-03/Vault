package com.example.crypto

import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Versioned Vault Format specification (v1).
 *
 * Separates unencrypted routing metadata (formatVersion, vaultId, recordId, recordType)
 * from AES-256-GCM authenticated ciphertext, while cryptographically binding all routing
 * metadata as canonical length-prefixed AAD so tampering or record-swapping is rejected.
 */
object VaultFormatConstants {
    const val CURRENT_FORMAT_VERSION: Int = 1
    val SUPPORTED_FORMAT_VERSIONS: Set<Int> = setOf(1)
    const val AES_KEY_BYTES: Int = 32
    const val GCM_NONCE_BYTES: Int = 12
    const val GCM_TAG_BITS: Int = 128
    const val GCM_TAG_BYTES: Int = 16
}

data class EncryptedPayload(
    val nonce: ByteArray,
    val ciphertextAndTag: ByteArray,
) {
    init {
        require(nonce.size == VaultFormatConstants.GCM_NONCE_BYTES) {
            "AES-GCM nonce must be ${VaultFormatConstants.GCM_NONCE_BYTES} bytes."
        }
        require(ciphertextAndTag.size >= VaultFormatConstants.GCM_TAG_BYTES) {
            "AES-GCM payload must contain at least a ${VaultFormatConstants.GCM_TAG_BYTES}-byte authentication tag."
        }
    }

    fun deepCopy(): EncryptedPayload = EncryptedPayload(
        nonce = nonce.copyOf(),
        ciphertextAndTag = ciphertextAndTag.copyOf(),
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedPayload) return false
        return nonce.contentEquals(other.nonce) && ciphertextAndTag.contentEquals(other.ciphertextAndTag)
    }

    override fun hashCode(): Int = 31 * nonce.contentHashCode() + ciphertextAndTag.contentHashCode()
}

data class VaultHeader(
    val formatVersion: Int,
    val vaultId: String,
    val kdfParameters: KdfParameters,
    val masterSalt: ByteArray,
    val masterWrappedVek: EncryptedPayload,
    val recoverySalt: ByteArray,
    val recoveryWrappedVek: EncryptedPayload,
    val keyRotationEpoch: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultHeader) return false
        return formatVersion == other.formatVersion &&
            vaultId == other.vaultId &&
            kdfParameters == other.kdfParameters &&
            masterSalt.contentEquals(other.masterSalt) &&
            masterWrappedVek == other.masterWrappedVek &&
            recoverySalt.contentEquals(other.recoverySalt) &&
            recoveryWrappedVek == other.recoveryWrappedVek &&
            keyRotationEpoch == other.keyRotationEpoch &&
            createdAtEpochMs == other.createdAtEpochMs &&
            updatedAtEpochMs == other.updatedAtEpochMs
    }

    override fun hashCode(): Int {
        var result = formatVersion
        result = 31 * result + vaultId.hashCode()
        result = 31 * result + kdfParameters.hashCode()
        result = 31 * result + masterSalt.contentHashCode()
        result = 31 * result + masterWrappedVek.hashCode()
        result = 31 * result + recoverySalt.contentHashCode()
        result = 31 * result + recoveryWrappedVek.hashCode()
        result = 31 * result + keyRotationEpoch
        return result
    }
}

data class EncryptedVaultRecord(
    val formatVersion: Int,
    val vaultId: String,
    val recordId: String,
    val recordType: VaultItemType,
    val payload: EncryptedPayload,
    val updatedAtEpochMs: Long,
)

/**
 * Canonical length-prefixed AAD construction.
 * Length-prefixing prevents canonicalization / boundary-shift collisions across variable-length strings.
 */
object AadBuilder {
    private const val DOMAIN_RECORD = "SV-REC-AAD-v1"
    private const val DOMAIN_MASTER_WRAP = "SV-WRAP-MASTER-v1"
    private const val DOMAIN_RECOVERY_WRAP = "SV-WRAP-RECOVERY-v1"

    fun forRecord(
        formatVersion: Int,
        vaultId: String,
        recordId: String,
        recordType: VaultItemType,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeLengthPrefixedUtf8(out, DOMAIN_RECORD)
        writeInt32BE(out, formatVersion)
        writeLengthPrefixedUtf8(out, vaultId)
        writeLengthPrefixedUtf8(out, recordId)
        writeLengthPrefixedUtf8(out, recordType.name)
        return out.toByteArray()
    }

    fun forMasterVekWrap(
        formatVersion: Int,
        vaultId: String,
        keyRotationEpoch: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeLengthPrefixedUtf8(out, DOMAIN_MASTER_WRAP)
        writeInt32BE(out, formatVersion)
        writeLengthPrefixedUtf8(out, vaultId)
        writeInt32BE(out, keyRotationEpoch)
        return out.toByteArray()
    }

    fun forRecoveryVekWrap(
        formatVersion: Int,
        vaultId: String,
        keyRotationEpoch: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        writeLengthPrefixedUtf8(out, DOMAIN_RECOVERY_WRAP)
        writeInt32BE(out, formatVersion)
        writeLengthPrefixedUtf8(out, vaultId)
        writeInt32BE(out, keyRotationEpoch)
        return out.toByteArray()
    }

    private fun writeInt32BE(out: ByteArrayOutputStream, value: Int) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array()
        out.write(buf)
    }

    private fun writeLengthPrefixedUtf8(out: ByteArrayOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeInt32BE(out, bytes.size)
        out.write(bytes)
    }
}

/**
 * Deterministic binary serializer for plaintext VaultItem fields inside an encrypted record payload.
 * Uses explicit length-prefixed UTF-8 encoding with zero reflection or external JSON dependencies.
 */
object VaultRecordCodec {
    private const val PAYLOAD_SCHEMA_VERSION = 1

    fun encode(item: VaultItem): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { out ->
            out.writeInt(PAYLOAD_SCHEMA_VERSION)
            writeString(out, item.title)
            writeString(out, item.username)
            writeString(out, item.secret)
            writeString(out, item.website)
            writeString(out, item.normalizedDomain)
            writeString(out, item.notes)
            out.writeBoolean(item.isFavorite)
            writeString(out, item.createdAtLabel)
            writeString(out, item.updatedAtLabel)
            out.writeLong(item.lastUsedEpochMs)
            writeString(out, item.secondaryIdentifier)
        }
        return baos.toByteArray()
    }

    fun decode(
        recordId: String,
        recordType: VaultItemType,
        plaintext: ByteArray,
    ): VaultItem {
        try {
            DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
                val schemaVersion = input.readInt()
                if (schemaVersion != PAYLOAD_SCHEMA_VERSION) {
                    throw UnsupportedFormatVersionException(schemaVersion)
                }
                val title = readString(input)
                val username = readString(input)
                val secret = readString(input)
                val website = readString(input)
                val normalizedDomain = readString(input)
                val notes = readString(input)
                val isFavorite = input.readBoolean()
                val createdAtLabel = readString(input)
                val updatedAtLabel = readString(input)
                val lastUsedEpochMs = input.readLong()
                val secondaryIdentifier = readString(input)

                return VaultItem(
                    id = recordId,
                    type = recordType,
                    title = title,
                    username = username,
                    secret = secret,
                    website = website,
                    normalizedDomain = normalizedDomain,
                    notes = notes,
                    isFavorite = isFavorite,
                    createdAtLabel = createdAtLabel,
                    updatedAtLabel = updatedAtLabel,
                    lastUsedEpochMs = lastUsedEpochMs,
                    secondaryIdentifier = secondaryIdentifier,
                )
            }
        } catch (e: VaultCryptoException) {
            throw e
        } catch (_: Exception) {
            throw TamperedDataException("Decrypted record payload framing is malformed.")
        }
    }

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val length = input.readInt()
        if (length < 0 || length > 1_048_576) {
            throw TamperedDataException("Invalid field length in decrypted payload.")
        }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}

/**
 * Format versioning and migration validation gate.
 * Explicitly rejects unsupported or future vault versions rather than failing silently.
 */
object VaultFormatMigrator {
    fun validateFormatVersion(version: Int) {
        if (version !in VaultFormatConstants.SUPPORTED_FORMAT_VERSIONS) {
            throw UnsupportedFormatVersionException(version)
        }
    }

    fun migrateHeaderToCurrent(header: VaultHeader): VaultHeader {
        validateFormatVersion(header.formatVersion)
        // v1 is current; future v1 -> v2 header transformations will execute here explicitly.
        return header
    }

    fun migrateRecordToCurrent(record: EncryptedVaultRecord): EncryptedVaultRecord {
        validateFormatVersion(record.formatVersion)
        return record
    }
}
