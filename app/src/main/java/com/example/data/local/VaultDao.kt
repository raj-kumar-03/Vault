package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface VaultDao {

    @Query("SELECT * FROM vault_headers WHERE singleton_id = 0 LIMIT 1")
    suspend fun getHeader(): VaultHeaderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHeader(header: VaultHeaderEntity)

    @Query("UPDATE vault_headers SET updated_at_epoch_ms = :updatedAtEpochMs WHERE singleton_id = 0")
    suspend fun touchHeaderUpdatedAt(updatedAtEpochMs: Long)

    @Query("SELECT * FROM vault_records WHERE vault_id = :vaultId AND is_deleted = 0")
    suspend fun getActiveRecords(vaultId: String): List<VaultRecordEntity>

    @Query("SELECT record_revision FROM vault_records WHERE record_id = :recordId LIMIT 1")
    suspend fun getRecordRevision(recordId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: VaultRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(records: List<VaultRecordEntity>)

    @Query("DELETE FROM vault_records")
    suspend fun deleteAllRecords()

    @Query(
        "UPDATE vault_records SET is_deleted = 1, record_revision = record_revision + 1, " +
            "updated_at_epoch_ms = :deletedAtEpochMs WHERE record_id = :recordId"
    )
    suspend fun softDeleteRecord(recordId: String, deletedAtEpochMs: Long)

    @Transaction
    suspend fun replaceVaultAtomically(
        header: VaultHeaderEntity,
        records: List<VaultRecordEntity>,
    ) {
        deleteAllRecords()
        upsertHeader(header)
        if (records.isNotEmpty()) {
            upsertRecords(records)
        }
    }

    @Transaction
    suspend fun upsertRecordAndTouchHeader(
        record: VaultRecordEntity,
        updatedAtEpochMs: Long,
    ) {
        upsertRecord(record)
        touchHeaderUpdatedAt(updatedAtEpochMs)
    }

    @Transaction
    suspend fun softDeleteRecordAndTouchHeader(
        recordId: String,
        deletedAtEpochMs: Long,
    ) {
        softDeleteRecord(recordId, deletedAtEpochMs)
        touchHeaderUpdatedAt(deletedAtEpochMs)
    }

    @Query("SELECT * FROM vault_preferences WHERE singleton_id = 0 LIMIT 1")
    suspend fun getPreferences(): VaultPreferencesEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(preferences: VaultPreferencesEntity)
}
