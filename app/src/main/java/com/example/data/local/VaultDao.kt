package com.example.data.local

/**
 * Pure Kotlin DAO contract backed by native Android [android.database.sqlite.SQLiteOpenHelper].
 * Eliminates KSP/Room annotation-processing overhead while preserving atomic transactions,
 * metadata minimization, soft-delete tombstones, and Phase 4 biometric envelope storage.
 */
interface VaultDao {

    suspend fun getHeader(): VaultHeaderEntity?

    suspend fun upsertHeader(header: VaultHeaderEntity)

    suspend fun touchHeaderUpdatedAt(updatedAtEpochMs: Long)

    suspend fun getActiveRecords(vaultId: String): List<VaultRecordEntity>

    suspend fun getRecordRevision(recordId: String): Long?

    suspend fun upsertRecord(record: VaultRecordEntity)

    suspend fun upsertRecords(records: List<VaultRecordEntity>)

    suspend fun deleteAllRecords()

    suspend fun softDeleteRecord(recordId: String, deletedAtEpochMs: Long)

    suspend fun replaceVaultAtomically(
        header: VaultHeaderEntity,
        records: List<VaultRecordEntity>,
    )

    suspend fun upsertRecordAndTouchHeader(
        record: VaultRecordEntity,
        updatedAtEpochMs: Long,
    )

    suspend fun softDeleteRecordAndTouchHeader(
        recordId: String,
        deletedAtEpochMs: Long,
    )

    suspend fun getPreferences(): VaultPreferencesEntity?

    suspend fun upsertPreferences(preferences: VaultPreferencesEntity)

    suspend fun getBiometricEnvelope(): BiometricEnvelopeEntity?

    suspend fun upsertBiometricEnvelope(envelope: BiometricEnvelopeEntity)

    suspend fun deleteBiometricEnvelope()
}
