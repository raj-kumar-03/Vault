package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Native Android SQLiteOpenHelper vault database (schema v3).
 *
 * Schema history:
 * - v1: vault_headers, vault_records (encrypted blobs only), vault_preferences.
 * - v2: non-destructive ALTER TABLE adding is_deleted + record_revision tombstones.
 * - v3: Phase 4 biometric_envelopes table for Android Keystore AES-256-GCM wrapped VEKs.
 */
class VaultDatabase private constructor(
    context: Context,
    name: String?,
) : SQLiteOpenHelper(context, name, null, DATABASE_VERSION) {

    private val dao: VaultDao = SqliteVaultDao(this)

    fun vaultDao(): VaultDao = dao

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_HEADERS)
        db.execSQL(SQL_CREATE_RECORDS_V2)
        db.execSQL(SQL_CREATE_PREFERENCES)
        db.execSQL(SQL_CREATE_BIOMETRIC_ENVELOPES_V3)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE vault_records ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE vault_records ADD COLUMN record_revision INTEGER NOT NULL DEFAULT 1"
            )
        }
        if (oldVersion < 3) {
            db.execSQL(SQL_CREATE_BIOMETRIC_ENVELOPES_V3)
        }
        if (oldVersion < 4) {
            db.execSQL(
                "ALTER TABLE vault_preferences ADD COLUMN allow_insecure_http_autofill INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE vault_preferences ADD COLUMN auto_prompt_biometric_on_launch INTEGER NOT NULL DEFAULT 1"
            )
        }
    }

    companion object {
        const val DATABASE_NAME = "aran_encrypted_vault.db"
        const val DATABASE_VERSION = 4

        private const val SQL_CREATE_HEADERS = """
            CREATE TABLE IF NOT EXISTS vault_headers (
                singleton_id INTEGER NOT NULL PRIMARY KEY,
                format_version INTEGER NOT NULL,
                vault_id TEXT NOT NULL,
                kdf_memory_kib INTEGER NOT NULL,
                kdf_iterations INTEGER NOT NULL,
                kdf_parallelism INTEGER NOT NULL,
                kdf_salt_length INTEGER NOT NULL,
                kdf_hash_length INTEGER NOT NULL,
                master_salt BLOB NOT NULL,
                master_wrap_nonce BLOB NOT NULL,
                master_wrap_ciphertext BLOB NOT NULL,
                recovery_salt BLOB NOT NULL,
                recovery_wrap_nonce BLOB NOT NULL,
                recovery_wrap_ciphertext BLOB NOT NULL,
                key_rotation_epoch INTEGER NOT NULL,
                created_at_epoch_ms INTEGER NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL
            )
        """

        private const val SQL_CREATE_RECORDS_V2 = """
            CREATE TABLE IF NOT EXISTS vault_records (
                record_id TEXT NOT NULL PRIMARY KEY,
                vault_id TEXT NOT NULL,
                format_version INTEGER NOT NULL,
                record_type TEXT NOT NULL,
                nonce BLOB NOT NULL,
                ciphertext_and_tag BLOB NOT NULL,
                updated_at_epoch_ms INTEGER NOT NULL,
                is_deleted INTEGER NOT NULL DEFAULT 0,
                record_revision INTEGER NOT NULL DEFAULT 1
            )
        """

        private const val SQL_CREATE_PREFERENCES = """
            CREATE TABLE IF NOT EXISTS vault_preferences (
                singleton_id INTEGER NOT NULL PRIMARY KEY,
                auto_lock_interval TEXT NOT NULL,
                biometric_unlock_enabled INTEGER NOT NULL,
                clipboard_clear_interval TEXT NOT NULL,
                hide_app_preview_enabled INTEGER NOT NULL,
                theme_preference TEXT NOT NULL,
                allow_insecure_http_autofill INTEGER NOT NULL DEFAULT 0,
                auto_prompt_biometric_on_launch INTEGER NOT NULL DEFAULT 1
            )
        """

        private const val SQL_CREATE_BIOMETRIC_ENVELOPES_V3 = """
            CREATE TABLE IF NOT EXISTS biometric_envelopes (
                singleton_id INTEGER NOT NULL PRIMARY KEY,
                vault_id TEXT NOT NULL,
                key_alias TEXT NOT NULL,
                iv BLOB NOT NULL,
                ciphertext_and_tag BLOB NOT NULL,
                created_at_epoch_ms INTEGER NOT NULL
            )
        """

        @Volatile
        private var instance: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase {
            return instance ?: synchronized(this) {
                instance ?: VaultDatabase(context.applicationContext, DATABASE_NAME)
                    .also { instance = it }
            }
        }

        fun createInMemory(context: Context): VaultDatabase =
            VaultDatabase(context.applicationContext, null)

        fun openForTesting(context: Context, dbName: String): VaultDatabase =
            VaultDatabase(context.applicationContext, dbName)
    }
}

private class SqliteVaultDao(
    private val dbHelper: VaultDatabase,
) : VaultDao {

    private val dbLock = Any()

    override suspend fun getHeader(): VaultHeaderEntity? = synchronized(dbLock) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """
            SELECT singleton_id, format_version, vault_id,
                   kdf_memory_kib, kdf_iterations, kdf_parallelism, kdf_salt_length, kdf_hash_length,
                   master_salt, master_wrap_nonce, master_wrap_ciphertext,
                   recovery_salt, recovery_wrap_nonce, recovery_wrap_ciphertext,
                   key_rotation_epoch, created_at_epoch_ms, updated_at_epoch_ms
            FROM vault_headers WHERE singleton_id = 0 LIMIT 1
            """.trimIndent(),
            null,
        ).use { c ->
            if (!c.moveToFirst()) return@synchronized null
            VaultHeaderEntity(
                singletonId = c.getInt(0),
                formatVersion = c.getInt(1),
                vaultId = c.getString(2),
                kdfMemoryKiB = c.getInt(3),
                kdfIterations = c.getInt(4),
                kdfParallelism = c.getInt(5),
                kdfSaltLength = c.getInt(6),
                kdfHashLength = c.getInt(7),
                masterSalt = c.getBlob(8),
                masterWrapNonce = c.getBlob(9),
                masterWrapCiphertext = c.getBlob(10),
                recoverySalt = c.getBlob(11),
                recoveryWrapNonce = c.getBlob(12),
                recoveryWrapCiphertext = c.getBlob(13),
                keyRotationEpoch = c.getInt(14),
                createdAtEpochMs = c.getLong(15),
                updatedAtEpochMs = c.getLong(16),
            )
        }
    }

    override suspend fun upsertHeader(header: VaultHeaderEntity) = synchronized(dbLock) {
        insertHeaderLocked(dbHelper.writableDatabase, header)
    }

    override suspend fun touchHeaderUpdatedAt(updatedAtEpochMs: Long) = synchronized(dbLock) {
        touchHeaderLocked(dbHelper.writableDatabase, updatedAtEpochMs)
    }

    override suspend fun getActiveRecords(vaultId: String): List<VaultRecordEntity> = synchronized(dbLock) {
        val db = dbHelper.readableDatabase
        val results = ArrayList<VaultRecordEntity>()
        db.rawQuery(
            """
            SELECT record_id, vault_id, format_version, record_type,
                   nonce, ciphertext_and_tag, updated_at_epoch_ms, is_deleted, record_revision
            FROM vault_records WHERE vault_id = ? AND is_deleted = 0
            """.trimIndent(),
            arrayOf(vaultId),
        ).use { c ->
            while (c.moveToNext()) {
                results.add(
                    VaultRecordEntity(
                        recordId = c.getString(0),
                        vaultId = c.getString(1),
                        formatVersion = c.getInt(2),
                        recordType = c.getString(3),
                        nonce = c.getBlob(4),
                        ciphertextAndTag = c.getBlob(5),
                        updatedAtEpochMs = c.getLong(6),
                        isDeleted = c.getInt(7) != 0,
                        recordRevision = c.getLong(8),
                    )
                )
            }
        }
        results
    }

    override suspend fun getRecordRevision(recordId: String): Long? = synchronized(dbLock) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            "SELECT record_revision FROM vault_records WHERE record_id = ? LIMIT 1",
            arrayOf(recordId),
        ).use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    override suspend fun upsertRecord(record: VaultRecordEntity) = synchronized(dbLock) {
        insertRecordLocked(dbHelper.writableDatabase, record)
    }

    override suspend fun upsertRecords(records: List<VaultRecordEntity>) = synchronized(dbLock) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (record in records) {
                insertRecordLocked(db, record)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun deleteAllRecords() = synchronized(dbLock) {
        dbHelper.writableDatabase.delete("vault_records", null, null)
        Unit
    }

    override suspend fun softDeleteRecord(recordId: String, deletedAtEpochMs: Long) = synchronized(dbLock) {
        softDeleteLocked(dbHelper.writableDatabase, recordId, deletedAtEpochMs)
    }

    override suspend fun replaceVaultAtomically(
        header: VaultHeaderEntity,
        records: List<VaultRecordEntity>,
    ) = synchronized(dbLock) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("vault_records", null, null)
            db.delete("biometric_envelopes", null, null)
            insertHeaderLocked(db, header)
            for (record in records) {
                insertRecordLocked(db, record)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun upsertRecordAndTouchHeader(
        record: VaultRecordEntity,
        updatedAtEpochMs: Long,
    ) = synchronized(dbLock) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            insertRecordLocked(db, record)
            touchHeaderLocked(db, updatedAtEpochMs)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun softDeleteRecordAndTouchHeader(
        recordId: String,
        deletedAtEpochMs: Long,
    ) = synchronized(dbLock) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            softDeleteLocked(db, recordId, deletedAtEpochMs)
            touchHeaderLocked(db, deletedAtEpochMs)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun getPreferences(): VaultPreferencesEntity? = synchronized(dbLock) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """
            SELECT singleton_id, auto_lock_interval, biometric_unlock_enabled,
                   clipboard_clear_interval, hide_app_preview_enabled, theme_preference,
                   allow_insecure_http_autofill, auto_prompt_biometric_on_launch
            FROM vault_preferences WHERE singleton_id = 0 LIMIT 1
            """.trimIndent(),
            null,
        ).use { c ->
            if (!c.moveToFirst()) return@synchronized null
            VaultPreferencesEntity(
                singletonId = c.getInt(0),
                autoLockInterval = c.getString(1),
                biometricUnlockEnabled = c.getInt(2) != 0,
                clipboardClearInterval = c.getString(3),
                hideAppPreviewEnabled = c.getInt(4) != 0,
                themePreference = c.getString(5),
                allowInsecureHttpAutofill = c.getInt(6) != 0,
                autoPromptBiometricOnLaunch = c.getInt(7) != 0,
            )
        }
    }

    override suspend fun upsertPreferences(preferences: VaultPreferencesEntity) = synchronized(dbLock) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("singleton_id", preferences.singletonId)
            put("auto_lock_interval", preferences.autoLockInterval)
            put("biometric_unlock_enabled", if (preferences.biometricUnlockEnabled) 1 else 0)
            put("clipboard_clear_interval", preferences.clipboardClearInterval)
            put("hide_app_preview_enabled", if (preferences.hideAppPreviewEnabled) 1 else 0)
            put("theme_preference", preferences.themePreference)
            put("allow_insecure_http_autofill", if (preferences.allowInsecureHttpAutofill) 1 else 0)
            put("auto_prompt_biometric_on_launch", if (preferences.autoPromptBiometricOnLaunch) 1 else 0)
        }
        db.insertWithOnConflict("vault_preferences", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    override suspend fun getBiometricEnvelope(): BiometricEnvelopeEntity? = synchronized(dbLock) {
        val db = dbHelper.readableDatabase
        db.rawQuery(
            """
            SELECT singleton_id, vault_id, key_alias, iv, ciphertext_and_tag, created_at_epoch_ms
            FROM biometric_envelopes WHERE singleton_id = 0 LIMIT 1
            """.trimIndent(),
            null,
        ).use { c ->
            if (!c.moveToFirst()) return@synchronized null
            BiometricEnvelopeEntity(
                singletonId = c.getInt(0),
                vaultId = c.getString(1),
                keyAlias = c.getString(2),
                iv = c.getBlob(3),
                ciphertextAndTag = c.getBlob(4),
                createdAtEpochMs = c.getLong(5),
            )
        }
    }

    override suspend fun upsertBiometricEnvelope(envelope: BiometricEnvelopeEntity) = synchronized(dbLock) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("singleton_id", envelope.singletonId)
            put("vault_id", envelope.vaultId)
            put("key_alias", envelope.keyAlias)
            put("iv", envelope.iv)
            put("ciphertext_and_tag", envelope.ciphertextAndTag)
            put("created_at_epoch_ms", envelope.createdAtEpochMs)
        }
        db.insertWithOnConflict("biometric_envelopes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    override suspend fun deleteBiometricEnvelope() = synchronized(dbLock) {
        dbHelper.writableDatabase.delete("biometric_envelopes", null, null)
        Unit
    }

    private fun insertHeaderLocked(db: SQLiteDatabase, header: VaultHeaderEntity) {
        val values = ContentValues().apply {
            put("singleton_id", header.singletonId)
            put("format_version", header.formatVersion)
            put("vault_id", header.vaultId)
            put("kdf_memory_kib", header.kdfMemoryKiB)
            put("kdf_iterations", header.kdfIterations)
            put("kdf_parallelism", header.kdfParallelism)
            put("kdf_salt_length", header.kdfSaltLength)
            put("kdf_hash_length", header.kdfHashLength)
            put("master_salt", header.masterSalt)
            put("master_wrap_nonce", header.masterWrapNonce)
            put("master_wrap_ciphertext", header.masterWrapCiphertext)
            put("recovery_salt", header.recoverySalt)
            put("recovery_wrap_nonce", header.recoveryWrapNonce)
            put("recovery_wrap_ciphertext", header.recoveryWrapCiphertext)
            put("key_rotation_epoch", header.keyRotationEpoch)
            put("created_at_epoch_ms", header.createdAtEpochMs)
            put("updated_at_epoch_ms", header.updatedAtEpochMs)
        }
        db.insertWithOnConflict("vault_headers", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun insertRecordLocked(db: SQLiteDatabase, record: VaultRecordEntity) {
        val values = ContentValues().apply {
            put("record_id", record.recordId)
            put("vault_id", record.vaultId)
            put("format_version", record.formatVersion)
            put("record_type", record.recordType)
            put("nonce", record.nonce)
            put("ciphertext_and_tag", record.ciphertextAndTag)
            put("updated_at_epoch_ms", record.updatedAtEpochMs)
            put("is_deleted", if (record.isDeleted) 1 else 0)
            put("record_revision", record.recordRevision)
        }
        db.insertWithOnConflict("vault_records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun touchHeaderLocked(db: SQLiteDatabase, updatedAtEpochMs: Long) {
        val values = ContentValues().apply {
            put("updated_at_epoch_ms", updatedAtEpochMs)
        }
        db.update("vault_headers", values, "singleton_id = 0", null)
    }

    private fun softDeleteLocked(db: SQLiteDatabase, recordId: String, deletedAtEpochMs: Long) {
        db.execSQL(
            "UPDATE vault_records SET is_deleted = 1, record_revision = record_revision + 1, " +
                "updated_at_epoch_ms = ? WHERE record_id = ?",
            arrayOf<Any>(deletedAtEpochMs, recordId),
        )
    }
}
