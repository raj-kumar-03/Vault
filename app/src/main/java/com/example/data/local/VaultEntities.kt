package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.crypto.EncryptedPayload
import com.example.crypto.EncryptedVaultRecord
import com.example.crypto.KdfParameters
import com.example.crypto.VaultHeader
import com.example.domain.model.AutoLockInterval
import com.example.domain.model.ClipboardClearInterval
import com.example.domain.model.SecuritySettings
import com.example.domain.model.ThemePreference
import com.example.domain.model.VaultItemType

@Entity(tableName = "vault_headers")
data class VaultHeaderEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 0,
    @ColumnInfo(name = "format_version")
    val formatVersion: Int,
    @ColumnInfo(name = "vault_id")
    val vaultId: String,
    @ColumnInfo(name = "kdf_memory_kib")
    val kdfMemoryKiB: Int,
    @ColumnInfo(name = "kdf_iterations")
    val kdfIterations: Int,
    @ColumnInfo(name = "kdf_parallelism")
    val kdfParallelism: Int,
    @ColumnInfo(name = "kdf_salt_length")
    val kdfSaltLength: Int,
    @ColumnInfo(name = "kdf_hash_length")
    val kdfHashLength: Int,
    @ColumnInfo(name = "master_salt", typeAffinity = ColumnInfo.BLOB)
    val masterSalt: ByteArray,
    @ColumnInfo(name = "master_wrap_nonce", typeAffinity = ColumnInfo.BLOB)
    val masterWrapNonce: ByteArray,
    @ColumnInfo(name = "master_wrap_ciphertext", typeAffinity = ColumnInfo.BLOB)
    val masterWrapCiphertext: ByteArray,
    @ColumnInfo(name = "recovery_salt", typeAffinity = ColumnInfo.BLOB)
    val recoverySalt: ByteArray,
    @ColumnInfo(name = "recovery_wrap_nonce", typeAffinity = ColumnInfo.BLOB)
    val recoveryWrapNonce: ByteArray,
    @ColumnInfo(name = "recovery_wrap_ciphertext", typeAffinity = ColumnInfo.BLOB)
    val recoveryWrapCiphertext: ByteArray,
    @ColumnInfo(name = "key_rotation_epoch")
    val keyRotationEpoch: Int,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
) {
    fun toDomain(): VaultHeader = VaultHeader(
        formatVersion = formatVersion,
        vaultId = vaultId,
        kdfParameters = KdfParameters(
            memoryKiB = kdfMemoryKiB,
            iterations = kdfIterations,
            parallelism = kdfParallelism,
            saltLengthBytes = kdfSaltLength,
            hashLengthBytes = kdfHashLength,
        ),
        masterSalt = masterSalt.copyOf(),
        masterWrappedVek = EncryptedPayload(
            nonce = masterWrapNonce.copyOf(),
            ciphertextAndTag = masterWrapCiphertext.copyOf(),
        ),
        recoverySalt = recoverySalt.copyOf(),
        recoveryWrappedVek = EncryptedPayload(
            nonce = recoveryWrapNonce.copyOf(),
            ciphertextAndTag = recoveryWrapCiphertext.copyOf(),
        ),
        keyRotationEpoch = keyRotationEpoch,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    companion object {
        fun fromDomain(header: VaultHeader): VaultHeaderEntity = VaultHeaderEntity(
            singletonId = 0,
            formatVersion = header.formatVersion,
            vaultId = header.vaultId,
            kdfMemoryKiB = header.kdfParameters.memoryKiB,
            kdfIterations = header.kdfParameters.iterations,
            kdfParallelism = header.kdfParameters.parallelism,
            kdfSaltLength = header.kdfParameters.saltLengthBytes,
            kdfHashLength = header.kdfParameters.hashLengthBytes,
            masterSalt = header.masterSalt.copyOf(),
            masterWrapNonce = header.masterWrappedVek.nonce.copyOf(),
            masterWrapCiphertext = header.masterWrappedVek.ciphertextAndTag.copyOf(),
            recoverySalt = header.recoverySalt.copyOf(),
            recoveryWrapNonce = header.recoveryWrappedVek.nonce.copyOf(),
            recoveryWrapCiphertext = header.recoveryWrappedVek.ciphertextAndTag.copyOf(),
            keyRotationEpoch = header.keyRotationEpoch,
            createdAtEpochMs = header.createdAtEpochMs,
            updatedAtEpochMs = header.updatedAtEpochMs,
        )
    }
}

@Entity(tableName = "vault_records")
data class VaultRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "record_id")
    val recordId: String,
    @ColumnInfo(name = "vault_id")
    val vaultId: String,
    @ColumnInfo(name = "format_version")
    val formatVersion: Int,
    @ColumnInfo(name = "record_type")
    val recordType: String,
    @ColumnInfo(name = "nonce", typeAffinity = ColumnInfo.BLOB)
    val nonce: ByteArray,
    @ColumnInfo(name = "ciphertext_and_tag", typeAffinity = ColumnInfo.BLOB)
    val ciphertextAndTag: ByteArray,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long,
    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "record_revision", defaultValue = "1")
    val recordRevision: Long = 1L,
) {
    fun toDomainOrNull(): EncryptedVaultRecord? = runCatching {
        EncryptedVaultRecord(
            formatVersion = formatVersion,
            vaultId = vaultId,
            recordId = recordId,
            recordType = VaultItemType.valueOf(recordType),
            payload = EncryptedPayload(
                nonce = nonce.copyOf(),
                ciphertextAndTag = ciphertextAndTag.copyOf(),
            ),
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }.getOrNull()

    companion object {
        fun fromDomain(
            record: EncryptedVaultRecord,
            isDeleted: Boolean,
            revision: Long,
        ): VaultRecordEntity = VaultRecordEntity(
            recordId = record.recordId,
            vaultId = record.vaultId,
            formatVersion = record.formatVersion,
            recordType = record.recordType.name,
            nonce = record.payload.nonce.copyOf(),
            ciphertextAndTag = record.payload.ciphertextAndTag.copyOf(),
            updatedAtEpochMs = record.updatedAtEpochMs,
            isDeleted = isDeleted,
            recordRevision = revision,
        )
    }
}

@Entity(tableName = "vault_preferences")
data class VaultPreferencesEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 0,
    @ColumnInfo(name = "auto_lock_interval")
    val autoLockInterval: String,
    @ColumnInfo(name = "biometric_unlock_enabled")
    val biometricUnlockEnabled: Boolean,
    @ColumnInfo(name = "clipboard_clear_interval")
    val clipboardClearInterval: String,
    @ColumnInfo(name = "hide_app_preview_enabled")
    val hideAppPreviewEnabled: Boolean,
    @ColumnInfo(name = "theme_preference")
    val themePreference: String,
) {
    fun toDomain(): SecuritySettings = SecuritySettings(
        autoLockInterval = runCatching { AutoLockInterval.valueOf(autoLockInterval) }
            .getOrDefault(AutoLockInterval.FIVE_MINUTES),
        biometricUnlockEnabled = biometricUnlockEnabled,
        clipboardClearInterval = runCatching { ClipboardClearInterval.valueOf(clipboardClearInterval) }
            .getOrDefault(ClipboardClearInterval.THIRTY_SECONDS),
        hideAppPreviewEnabled = hideAppPreviewEnabled,
        themePreference = runCatching { ThemePreference.valueOf(themePreference) }
            .getOrDefault(ThemePreference.DARK),
    )

    companion object {
        fun fromDomain(settings: SecuritySettings): VaultPreferencesEntity = VaultPreferencesEntity(
            singletonId = 0,
            autoLockInterval = settings.autoLockInterval.name,
            biometricUnlockEnabled = settings.biometricUnlockEnabled,
            clipboardClearInterval = settings.clipboardClearInterval.name,
            hideAppPreviewEnabled = settings.hideAppPreviewEnabled,
            themePreference = settings.themePreference.name,
        )
    }
}
