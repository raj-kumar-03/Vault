package com.example.data.local

import com.example.crypto.EncryptedPayload
import com.example.crypto.EncryptedVaultRecord
import com.example.crypto.KdfParameters
import com.example.crypto.VaultHeader
import com.example.domain.model.AutoLockInterval
import com.example.domain.model.ClipboardClearInterval
import com.example.domain.model.SecuritySettings
import com.example.domain.model.ThemePreference
import com.example.domain.model.VaultItemType

data class VaultHeaderEntity(
    val singletonId: Int = 0,
    val formatVersion: Int,
    val vaultId: String,
    val kdfMemoryKiB: Int,
    val kdfIterations: Int,
    val kdfParallelism: Int,
    val kdfSaltLength: Int,
    val kdfHashLength: Int,
    val masterSalt: ByteArray,
    val masterWrapNonce: ByteArray,
    val masterWrapCiphertext: ByteArray,
    val recoverySalt: ByteArray,
    val recoveryWrapNonce: ByteArray,
    val recoveryWrapCiphertext: ByteArray,
    val keyRotationEpoch: Int,
    val createdAtEpochMs: Long,
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

data class VaultRecordEntity(
    val recordId: String,
    val vaultId: String,
    val formatVersion: Int,
    val recordType: String,
    val nonce: ByteArray,
    val ciphertextAndTag: ByteArray,
    val updatedAtEpochMs: Long,
    val isDeleted: Boolean = false,
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

data class VaultPreferencesEntity(
    val singletonId: Int = 0,
    val autoLockInterval: String,
    val biometricUnlockEnabled: Boolean,
    val clipboardClearInterval: String,
    val hideAppPreviewEnabled: Boolean,
    val themePreference: String,
    val allowInsecureHttpAutofill: Boolean = false,
    val autoPromptBiometricOnLaunch: Boolean = true,
) {
    fun toDomain(): SecuritySettings = SecuritySettings(
        autoLockInterval = runCatching { AutoLockInterval.valueOf(autoLockInterval) }
            .getOrDefault(AutoLockInterval.FIVE_MINUTES),
        biometricUnlockEnabled = biometricUnlockEnabled,
        autoPromptBiometricOnLaunch = autoPromptBiometricOnLaunch,
        clipboardClearInterval = runCatching { ClipboardClearInterval.valueOf(clipboardClearInterval) }
            .getOrDefault(ClipboardClearInterval.THIRTY_SECONDS),
        hideAppPreviewEnabled = hideAppPreviewEnabled,
        allowInsecureHttpAutofill = allowInsecureHttpAutofill,
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
            allowInsecureHttpAutofill = settings.allowInsecureHttpAutofill,
            autoPromptBiometricOnLaunch = settings.autoPromptBiometricOnLaunch,
        )
    }
}

/**
 * Phase 4 device-bound biometric envelope storing the VEK encrypted under a hardware-backed
 * Android Keystore AES-256-GCM key that requires strong biometric authentication per operation.
 */
data class BiometricEnvelopeEntity(
    val singletonId: Int = 0,
    val vaultId: String,
    val keyAlias: String,
    val iv: ByteArray,
    val ciphertextAndTag: ByteArray,
    val createdAtEpochMs: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BiometricEnvelopeEntity) return false
        return singletonId == other.singletonId &&
            vaultId == other.vaultId &&
            keyAlias == other.keyAlias &&
            iv.contentEquals(other.iv) &&
            ciphertextAndTag.contentEquals(other.ciphertextAndTag) &&
            createdAtEpochMs == other.createdAtEpochMs
    }

    override fun hashCode(): Int {
        var result = singletonId
        result = 31 * result + vaultId.hashCode()
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertextAndTag.contentHashCode()
        result = 31 * result + createdAtEpochMs.hashCode()
        return result
    }
}
