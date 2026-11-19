package com.example.crypto

import com.example.domain.model.VaultItemType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/**
 * Versioned, self-contained encrypted vault backup codec (.svbk v1).
 *
 * Binary layout:
 * - Magic: "SVBK" (4 bytes ASCII)
 * - BackupFormatVersion: Int32 (currently 1)
 * - ExportedAtEpochMs: Int64
 * - VaultHeader fields (formatVersion, vaultId, KDF params, salts, wrapped VEKs, timestamps)
 * - RecordCount: Int32
 * - EncryptedVaultRecord[RecordCount] (all records remain AES-256-GCM encrypted under the VEK)
 * - IntegrityDigest: 32-byte SHA-256 over all preceding bytes
 *
 * Security property: Exporting a backup NEVER decrypts credentials or weakens encryption.
 * Restoring validates file integrity and requires unlocking the backup's VEK before modifying local SQLite.
 */
object VaultBackupCodec {

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'V'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val CURRENT_BACKUP_VERSION: Int = 1
    private const val DIGEST_LENGTH = 32
    private const val MAX_BYTE_ARRAY_SIZE = 2 * 1024 * 1024
    private const val MAX_RECORD_COUNT = 100_000

    data class DecodedVaultBackup(
        val backupVersion: Int,
        val exportedAtEpochMs: Long,
        val header: VaultHeader,
        val records: List<EncryptedVaultRecord>,
    )

    data class BackupInspectionSummary(
        val backupVersion: Int,
        val vaultFormatVersion: Int,
        val vaultIdPreview: String,
        val recordCount: Int,
        val kdfSummary: String,
        val exportedAtEpochMs: Long,
    )

    fun encode(
        header: VaultHeader,
        records: List<EncryptedVaultRecord>,
        exportedAtEpochMs: Long = System.currentTimeMillis(),
    ): ByteArray {
        VaultFormatMigrator.validateFormatVersion(header.formatVersion)
        val bodyStream = ByteArrayOutputStream()
        DataOutputStream(bodyStream).use { out ->
            out.write(MAGIC)
            out.writeInt(CURRENT_BACKUP_VERSION)
            out.writeLong(exportedAtEpochMs)

            // VaultHeader
            out.writeInt(header.formatVersion)
            writeUtf8(out, header.vaultId)
            out.writeInt(header.kdfParameters.memoryKiB)
            out.writeInt(header.kdfParameters.iterations)
            out.writeInt(header.kdfParameters.parallelism)
            out.writeInt(header.kdfParameters.saltLengthBytes)
            out.writeInt(header.kdfParameters.hashLengthBytes)
            writeBytes(out, header.masterSalt)
            writeBytes(out, header.masterWrappedVek.nonce)
            writeBytes(out, header.masterWrappedVek.ciphertextAndTag)
            writeBytes(out, header.recoverySalt)
            writeBytes(out, header.recoveryWrappedVek.nonce)
            writeBytes(out, header.recoveryWrappedVek.ciphertextAndTag)
            out.writeInt(header.keyRotationEpoch)
            out.writeLong(header.createdAtEpochMs)
            out.writeLong(header.updatedAtEpochMs)

            // Encrypted records
            out.writeInt(records.size)
            for (record in records) {
                VaultFormatMigrator.validateFormatVersion(record.formatVersion)
                out.writeInt(record.formatVersion)
                writeUtf8(out, record.vaultId)
                writeUtf8(out, record.recordId)
                writeUtf8(out, record.recordType.name)
                writeBytes(out, record.payload.nonce)
                writeBytes(out, record.payload.ciphertextAndTag)
                out.writeLong(record.updatedAtEpochMs)
            }
        }

        val bodyBytes = bodyStream.toByteArray()
        val digest = sha256(bodyBytes)
        val finalOut = ByteArray(bodyBytes.size + DIGEST_LENGTH)
        System.arraycopy(bodyBytes, 0, finalOut, 0, bodyBytes.size)
        System.arraycopy(digest, 0, finalOut, bodyBytes.size, DIGEST_LENGTH)
        return finalOut
    }

    fun inspect(backupBytes: ByteArray): BackupInspectionSummary {
        val decoded = decode(backupBytes)
        return BackupInspectionSummary(
            backupVersion = decoded.backupVersion,
            vaultFormatVersion = decoded.header.formatVersion,
            vaultIdPreview = decoded.header.vaultId.take(8).uppercase(),
            recordCount = decoded.records.size,
            kdfSummary = decoded.header.kdfParameters.summaryLabel(),
            exportedAtEpochMs = decoded.exportedAtEpochMs,
        )
    }

    fun decode(backupBytes: ByteArray): DecodedVaultBackup {
        if (backupBytes.size < MAGIC.size + 4 + DIGEST_LENGTH) {
            throw TamperedDataException("Backup file is truncated or invalid.")
        }

        val bodyLength = backupBytes.size - DIGEST_LENGTH
        val bodyBytes = backupBytes.copyOfRange(0, bodyLength)
        val storedDigest = backupBytes.copyOfRange(bodyLength, backupBytes.size)
        val computedDigest = sha256(bodyBytes)
        if (!MessageDigest.isEqual(storedDigest, computedDigest)) {
            throw TamperedDataException("Backup file integrity check failed; file may be corrupted or modified.")
        }

        try {
            DataInputStream(ByteArrayInputStream(bodyBytes)).use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC)) {
                    throw TamperedDataException("Not a valid SecureVault backup file.")
                }

                val backupVersion = input.readInt()
                if (backupVersion != CURRENT_BACKUP_VERSION) {
                    throw UnsupportedFormatVersionException(backupVersion)
                }

                val exportedAtEpochMs = input.readLong()
                val vaultFormatVersion = input.readInt()
                VaultFormatMigrator.validateFormatVersion(vaultFormatVersion)

                val vaultId = readUtf8(input)
                val kdfParams = KdfParameters(
                    memoryKiB = input.readInt(),
                    iterations = input.readInt(),
                    parallelism = input.readInt(),
                    saltLengthBytes = input.readInt(),
                    hashLengthBytes = input.readInt(),
                )
                val masterSalt = readBytes(input)
                val masterWrapNonce = readBytes(input)
                val masterWrapCiphertext = readBytes(input)
                val recoverySalt = readBytes(input)
                val recoveryWrapNonce = readBytes(input)
                val recoveryWrapCiphertext = readBytes(input)
                val keyRotationEpoch = input.readInt()
                val createdAtEpochMs = input.readLong()
                val updatedAtEpochMs = input.readLong()

                val header = VaultHeader(
                    formatVersion = vaultFormatVersion,
                    vaultId = vaultId,
                    kdfParameters = kdfParams,
                    masterSalt = masterSalt,
                    masterWrappedVek = EncryptedPayload(masterWrapNonce, masterWrapCiphertext),
                    recoverySalt = recoverySalt,
                    recoveryWrappedVek = EncryptedPayload(recoveryWrapNonce, recoveryWrapCiphertext),
                    keyRotationEpoch = keyRotationEpoch,
                    createdAtEpochMs = createdAtEpochMs,
                    updatedAtEpochMs = updatedAtEpochMs,
                )

                val recordCount = input.readInt()
                if (recordCount < 0 || recordCount > MAX_RECORD_COUNT) {
                    throw TamperedDataException("Invalid record count in backup file.")
                }

                val records = ArrayList<EncryptedVaultRecord>(recordCount)
                repeat(recordCount) {
                    val recVersion = input.readInt()
                    VaultFormatMigrator.validateFormatVersion(recVersion)
                    val recVaultId = readUtf8(input)
                    if (recVaultId != vaultId) {
                        throw TamperedDataException("Backup contains a record belonging to a different vault.")
                    }
                    val recordId = readUtf8(input)
                    val recordTypeName = readUtf8(input)
                    val recordType = runCatching { VaultItemType.valueOf(recordTypeName) }.getOrNull()
                        ?: throw TamperedDataException("Backup contains an unknown record type.")
                    val nonce = readBytes(input)
                    val ciphertextAndTag = readBytes(input)
                    val recUpdatedAt = input.readLong()

                    records.add(
                        EncryptedVaultRecord(
                            formatVersion = recVersion,
                            vaultId = recVaultId,
                            recordId = recordId,
                            recordType = recordType,
                            payload = EncryptedPayload(nonce, ciphertextAndTag),
                            updatedAtEpochMs = recUpdatedAt,
                        )
                    )
                }

                if (input.available() != 0) {
                    throw TamperedDataException("Backup file contains unexpected trailing bytes.")
                }

                return DecodedVaultBackup(
                    backupVersion = backupVersion,
                    exportedAtEpochMs = exportedAtEpochMs,
                    header = header,
                    records = records,
                )
            }
        } catch (e: VaultCryptoException) {
            throw e
        } catch (_: Exception) {
            throw TamperedDataException("Backup file structure is malformed.")
        }
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun writeUtf8(out: DataOutputStream, text: String) {
        writeBytes(out, text.toByteArray(Charsets.UTF_8))
    }

    private fun writeBytes(out: DataOutputStream, bytes: ByteArray) {
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readUtf8(input: DataInputStream): String =
        String(readBytes(input), Charsets.UTF_8)

    private fun readBytes(input: DataInputStream): ByteArray {
        val len = input.readInt()
        if (len < 0 || len > MAX_BYTE_ARRAY_SIZE) {
            throw TamperedDataException("Backup field size out of bounds.")
        }
        val out = ByteArray(len)
        input.readFully(out)
        return out
    }
}
