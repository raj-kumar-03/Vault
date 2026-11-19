package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.DefaultVaultCryptoEngine
import com.example.crypto.KdfParameters
import com.example.crypto.VaultRecordCodec
import com.example.data.PersistentVaultRepository
import com.example.data.local.VaultDatabase
import com.example.domain.model.ThemePreference
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 3 Local SQLite Persistence, Migration, Corruption Quarantine, and Backup/Restore Tests.
 *
 * Verifies every Phase 3 gate requirement:
 * 1. Offline encrypted CRUD & persistence across simulated cold app restarts.
 * 2. Metadata minimization: zero plaintext titles, usernames, domains, notes, or secrets in SQLite.
 * 3. Non-destructive schema migration (v1 -> v2) preserving existing encrypted vault records.
 * 4. Per-record corruption isolation without crashing or losing intact entries.
 * 5. Versioned .svbk backup export, integrity verification, non-destructive failure handling,
 *    and verified restore via both Master Password and Recovery Key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistentVaultStorageTest {

    private lateinit var context: Context
    private lateinit var database: VaultDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VaultDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newRepository(db: VaultDatabase = database): PersistentVaultRepository =
        PersistentVaultRepository(
            vaultDao = db.vaultDao(),
            kdfParameters = KdfParameters.TEST_FAST,
            ioDispatcher = Dispatchers.Unconfined,
            cryptoDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `encrypted records and preferences persist across simulated cold restart with zero plaintext in sqlite`() = runTest {
        val repo1 = newRepository()
        repo1.initializeFromDisk()
        assertFalse(repo1.hasVault.value)

        val masterPass = "emerald-sqlite-master-pass-91"
        repo1.createVault(masterPass.toCharArray())

        val secretTitle = "UltraSecretBankPortal"
        val secretUser = "alice.private@example.net"
        val secretPassword = "S3cr3t!P@ssword#2026"
        val secretDomain = "vault.privatebank.example"
        val secretNotes = "Never store this note in plaintext"

        val savedLogin = repo1.upsertItem(
            VaultItem(
                id = "",
                type = VaultItemType.LOGIN,
                title = secretTitle,
                username = secretUser,
                secret = secretPassword,
                website = "https://$secretDomain/login",
                normalizedDomain = secretDomain,
                notes = secretNotes,
                isFavorite = true,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 1_000L,
            )
        )
        val savedPasskey = repo1.upsertItem(
            VaultItem(
                id = "",
                type = VaultItemType.PASSKEY,
                title = "CodeForge Offline Passkey",
                username = secretUser,
                secret = "p256-offline-key-envelope",
                website = "https://codeforge.example.com",
                normalizedDomain = "codeforge.example.com",
                notes = "Ready for Phase 5 CredentialProviderService",
                isFavorite = false,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 2_000L,
            )
        )
        repo1.updateSettings { it.copy(themePreference = ThemePreference.LIGHT) }

        // Metadata minimization audit: inspect raw SQLite rows directly
        val rawDb = database.readableDatabase
        rawDb.rawQuery("SELECT record_id, vault_id, record_type, nonce, ciphertext_and_tag FROM vault_records", null).use { cursor ->
            assertEquals(2, cursor.count)
            while (cursor.moveToNext()) {
                val recordId = cursor.getString(0)
                val vaultId = cursor.getString(1)
                val recordType = cursor.getString(2)
                val nonce = cursor.getBlob(3)
                val ciphertext = cursor.getBlob(4)
                val combinedRaw = "$recordId|$vaultId|$recordType|${String(nonce, Charsets.ISO_8859_1)}|${String(ciphertext, Charsets.ISO_8859_1)}"

                assertFalse("Title leaked in SQLite!", combinedRaw.contains(secretTitle))
                assertFalse("Username leaked in SQLite!", combinedRaw.contains(secretUser))
                assertFalse("Password leaked in SQLite!", combinedRaw.contains(secretPassword))
                assertFalse("Domain leaked in SQLite!", combinedRaw.contains(secretDomain))
                assertFalse("Notes leaked in SQLite!", combinedRaw.contains(secretNotes))
            }
        }

        // Lock and simulate cold app restart with a fresh repository instance on the same DB
        repo1.lockVault()
        assertTrue(repo1.items.value.isEmpty())

        val repo2 = newRepository()
        repo2.initializeFromDisk()
        assertTrue(repo2.hasVault.value)
        assertFalse(repo2.isUnlocked.value)
        assertTrue(repo2.items.value.isEmpty())
        assertEquals(ThemePreference.LIGHT, repo2.settings.value.themePreference)

        // Unlock on fresh instance and verify all decrypted fields & types survived
        assertTrue(repo2.unlockWithMasterPassword(masterPass.toCharArray()))
        assertEquals(2, repo2.items.value.size)

        val restoredLogin = repo2.items.value.first { it.id == savedLogin.id }
        assertEquals(secretTitle, restoredLogin.title)
        assertEquals(secretUser, restoredLogin.username)
        assertEquals(secretPassword, restoredLogin.secret)
        assertEquals(secretDomain, restoredLogin.normalizedDomain)
        assertTrue(restoredLogin.isFavorite)

        val restoredPasskey = repo2.items.value.first { it.id == savedPasskey.id }
        assertEquals(VaultItemType.PASSKEY, restoredPasskey.type)
        assertEquals("p256-offline-key-envelope", restoredPasskey.secret)

        // Soft-delete marks tombstone in SQLite and hides record from active list
        repo2.deleteItem(savedPasskey.id)
        assertEquals(1, repo2.items.value.size)
        rawDb.rawQuery("SELECT is_deleted, record_revision FROM vault_records WHERE record_id = ?", arrayOf(savedPasskey.id)).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals(2L, c.getLong(1))
        }
    }

    @Test
    fun `corrupted sqlite record is quarantined without crashing or hiding intact vault records`() = runTest {
        val repo = newRepository()
        repo.initializeFromDisk()
        val masterPass = "quarantine-integrity-pass"
        repo.createVault(masterPass.toCharArray())

        val intactItem = repo.upsertItem(
            VaultItem(
                id = "rec-intact",
                type = VaultItemType.LOGIN,
                title = "Intact Service",
                username = "ok@example.com",
                secret = "Intact-Secret-11!",
                website = "https://intact.example.com",
                normalizedDomain = "intact.example.com",
                notes = "",
                isFavorite = false,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 100L,
            )
        )
        val victimItem = repo.upsertItem(
            VaultItem(
                id = "rec-corrupt",
                type = VaultItemType.LOGIN,
                title = "Corrupted Service",
                username = "bad@example.com",
                secret = "Victim-Secret-22!",
                website = "https://victim.example.com",
                normalizedDomain = "victim.example.com",
                notes = "",
                isFavorite = false,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 200L,
            )
        )

        // Flip one byte directly inside the SQLite ciphertext blob of victimItem
        val writable = database.writableDatabase
        var corruptedBlob: ByteArray? = null
        writable.rawQuery("SELECT ciphertext_and_tag FROM vault_records WHERE record_id = ?", arrayOf(victimItem.id)).use { c ->
            assertTrue(c.moveToFirst())
            val orig = c.getBlob(0)
            orig[0] = (orig[0].toInt() xor 0xFF).toByte()
            corruptedBlob = orig
        }
        assertNotNull(corruptedBlob)
        writable.execSQL(
            "UPDATE vault_records SET ciphertext_and_tag = ? WHERE record_id = ?",
            arrayOf<Any>(corruptedBlob!!, victimItem.id),
        )

        // Lock and re-unlock: must NOT crash, must isolate victimItem, must keep intactItem readable
        repo.lockVault()
        assertTrue(repo.unlockWithMasterPassword(masterPass.toCharArray()))
        assertEquals(1, repo.items.value.size)
        assertEquals(intactItem.id, repo.items.value.first().id)
        assertEquals("Intact-Secret-11!", repo.items.value.first().secret)
        assertEquals(listOf(victimItem.id), repo.cryptoMetadata.value.corruptedRecordIds)
    }

    @Test
    fun `versioned svbk backup restore is non-destructive on failure and succeeds with master password or recovery key`() = runTest {
        val repo = newRepository()
        repo.initializeFromDisk()

        val originalPass = "backup-origin-passphrase-1"
        val recoveryKey = repo.createVault(originalPass.toCharArray())
        repo.upsertItem(
            VaultItem(
                id = "rec-alpha",
                type = VaultItemType.LOGIN,
                title = "Alpha Vault Entry",
                username = "alpha@example.com",
                secret = "Alpha-Secret-9000",
                website = "https://alpha.example.com",
                normalizedDomain = "alpha.example.com",
                notes = "Exported into .svbk v1",
                isFavorite = true,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 500L,
            )
        )

        val backupBytes = repo.exportEncryptedBackup()
        val summary = repo.inspectBackup(backupBytes)
        assertEquals(1, summary.recordCount)
        assertEquals(1, summary.backupVersion)

        // Now create a completely separate local vault in SQLite
        val currentPass = "current-local-vault-pass-2"
        repo.createVault(currentPass.toCharArray())
        repo.upsertItem(
            VaultItem(
                id = "rec-local",
                type = VaultItemType.SECURE_NOTE,
                title = "Current Local Note",
                username = "",
                secret = "Must-Not-Be-Wiped-On-Failed-Restore",
                website = "",
                normalizedDomain = "",
                notes = "",
                isFavorite = false,
                createdAtLabel = "Now",
                updatedAtLabel = "Now",
                lastUsedEpochMs = 600L,
            )
        )

        // 1. Wrong password restore MUST fail and leave current SQLite vault completely intact!
        assertFalse(repo.restoreBackupWithMasterPassword(backupBytes, "wrong-backup-password".toCharArray()))
        assertEquals(1, repo.items.value.size)
        assertEquals("rec-local", repo.items.value.first().id)

        // 2. Tampered backup MUST fail and leave current SQLite vault intact!
        val tampered = backupBytes.copyOf().apply { this[20] = (this[20].toInt() xor 0x0F).toByte() }
        assertFalse(repo.restoreBackupWithMasterPassword(tampered, originalPass.toCharArray()))
        assertEquals("rec-local", repo.items.value.first().id)

        // 3. Correct master password restores backup atomically
        assertTrue(repo.restoreBackupWithMasterPassword(backupBytes, originalPass.toCharArray()))
        assertEquals(1, repo.items.value.size)
        assertEquals("rec-alpha", repo.items.value.first().id)
        assertEquals("Alpha-Secret-9000", repo.items.value.first().secret)

        // 4. Restore via independent Recovery Key with a brand-new master password
        val newRecoveredPass = "restored-via-recovery-key-3"
        assertTrue(repo.restoreBackupWithRecoveryKey(backupBytes, recoveryKey, newRecoveredPass.toCharArray()))
        assertEquals("Alpha-Secret-9000", repo.items.value.first().secret)

        // Verify persisted vault unlocks with the newly set password
        repo.lockVault()
        assertFalse(repo.unlockWithMasterPassword(originalPass.toCharArray()))
        assertTrue(repo.unlockWithMasterPassword(newRecoveredPass.toCharArray()))
        assertEquals("Alpha-Secret-9000", repo.items.value.first().secret)
    }

    @Test
    fun `migration 1 to 2 upgrades v1 sqlite schema non-destructively and preserves encrypted records`() = runTest {
        val dbName = "migration_test_v1_to_v2.db"
        context.deleteDatabase(dbName)
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // Create a real v1 SQLite database manually with an AES-256-GCM encrypted header + record
        val engine = DefaultVaultCryptoEngine()
        val masterPass = "migration-v1-master-pass"
        val bundle = engine.createVault(masterPass.toCharArray(), KdfParameters.TEST_FAST)
        val sampleItem = VaultItem(
            id = "rec-v1-legacy",
            type = VaultItemType.LOGIN,
            title = "Legacy v1 Login",
            username = "legacy@example.com",
            secret = "Survived-Migration-1-to-2!",
            website = "https://legacy.example.com",
            normalizedDomain = "legacy.example.com",
            notes = "Must decrypt cleanly after schema migration.",
            isFavorite = true,
            createdAtLabel = "v1",
            updatedAtLabel = "v1",
            lastUsedEpochMs = 42L,
        )
        val encrypted = engine.encryptRecord(
            sessionKey = bundle.sessionKey,
            recordId = sampleItem.id,
            recordType = sampleItem.type,
            plaintext = VaultRecordCodec.encode(sampleItem),
        )
        bundle.sessionKey.wipe()

        val rawV1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            rawV1.version = 1
            rawV1.execSQL(
                """
                CREATE TABLE vault_headers (
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
                """.trimIndent()
            )
            rawV1.execSQL(
                """
                CREATE TABLE vault_records (
                    record_id TEXT NOT NULL PRIMARY KEY,
                    vault_id TEXT NOT NULL,
                    format_version INTEGER NOT NULL,
                    record_type TEXT NOT NULL,
                    nonce BLOB NOT NULL,
                    ciphertext_and_tag BLOB NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawV1.execSQL(
                """
                CREATE TABLE vault_preferences (
                    singleton_id INTEGER NOT NULL PRIMARY KEY,
                    auto_lock_interval TEXT NOT NULL,
                    biometric_unlock_enabled INTEGER NOT NULL,
                    clipboard_clear_interval TEXT NOT NULL,
                    hide_app_preview_enabled INTEGER NOT NULL,
                    theme_preference TEXT NOT NULL
                )
                """.trimIndent()
            )

            val h = bundle.header
            rawV1.execSQL(
                """
                INSERT INTO vault_headers VALUES (
                    0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """.trimIndent(),
                arrayOf<Any>(
                    h.formatVersion,
                    h.vaultId,
                    h.kdfParameters.memoryKiB,
                    h.kdfParameters.iterations,
                    h.kdfParameters.parallelism,
                    h.kdfParameters.saltLengthBytes,
                    h.kdfParameters.hashLengthBytes,
                    h.masterSalt,
                    h.masterWrappedVek.nonce,
                    h.masterWrappedVek.ciphertextAndTag,
                    h.recoverySalt,
                    h.recoveryWrappedVek.nonce,
                    h.recoveryWrappedVek.ciphertextAndTag,
                    h.keyRotationEpoch,
                    h.createdAtEpochMs,
                    h.updatedAtEpochMs,
                ),
            )
            rawV1.execSQL(
                "INSERT INTO vault_records VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(
                    encrypted.recordId,
                    encrypted.vaultId,
                    encrypted.formatVersion,
                    encrypted.recordType.name,
                    encrypted.payload.nonce,
                    encrypted.payload.ciphertextAndTag,
                    encrypted.updatedAtEpochMs,
                ),
            )
        } finally {
            rawV1.close()
        }

        // Now open the v1 database with native SQLiteOpenHelper v3 (v1 -> v2 -> v3) and verify decryption!
        val migratedDb = VaultDatabase.openForTesting(context, dbName)
        try {
            val repo = newRepository(migratedDb)
            repo.initializeFromDisk()
            assertTrue(repo.hasVault.value)
            assertTrue(repo.unlockWithMasterPassword(masterPass.toCharArray()))
            assertEquals(1, repo.items.value.size)
            assertEquals("Legacy v1 Login", repo.items.value.first().title)
            assertEquals("Survived-Migration-1-to-2!", repo.items.value.first().secret)
            // Verify v3 biometric_envelopes table was created cleanly during upgrade
            migratedDb.readableDatabase.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='biometric_envelopes'",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            migratedDb.close()
            context.deleteDatabase(dbName)
        }
    }
}
