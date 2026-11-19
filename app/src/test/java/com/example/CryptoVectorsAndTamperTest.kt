package com.example

import com.example.crypto.AadBuilder
import com.example.crypto.DefaultVaultCryptoEngine
import com.example.crypto.EncryptedPayload
import com.example.crypto.InvalidCredentialsException
import com.example.crypto.InvalidRecoveryKeyException
import com.example.crypto.KdfParameters
import com.example.crypto.RecoveryKeyCodec
import com.example.crypto.TamperedDataException
import com.example.crypto.UnsupportedFormatVersionException
import com.example.crypto.VaultBackupCodec
import com.example.crypto.VaultFormatConstants
import com.example.crypto.VaultFormatMigrator
import com.example.crypto.VaultLockedException
import com.example.crypto.VaultRecordCodec
import com.example.domain.model.VaultItem
import com.example.domain.model.VaultItemType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 2 Cryptographic Engine & Vault Format verification suite.
 *
 * Validates:
 * 1. Official RFC 9106 Section 5.3 Argon2id known-answer test vector.
 * 2. Official NIST SP 800-38D AES-256-GCM test cases 13, 14, and 16 (with & without AAD).
 * 3. Wrong master password and invalid/wrong recovery key rejection.
 * 4. Tamper detection across ciphertext, nonce, GCM tag, AAD, recordId, vaultId, and recordType.
 * 5. Nonce uniqueness across repeated encryptions of identical plaintext.
 * 6. Format version validation and migration gate.
 * 7. Master password change & recovery key reset without re-encrypting vault records.
 * 8. VEK wipe behavior on lock and absence of secrets in exception messages.
 */
class CryptoVectorsAndTamperTest {

    private val engine = DefaultVaultCryptoEngine()

    @Test
    fun `rfc 9106 section 5_3 argon2id known answer vector matches exactly`() {
        val passwordChars = CharArray(32) { 0x01.toChar() }
        val salt = ByteArray(16) { 0x02 }
        val secret = ByteArray(8) { 0x03 }
        val associatedData = ByteArray(12) { 0x04 }

        val params = KdfParameters(
            memoryKiB = 32,
            iterations = 3,
            parallelism = 4,
            saltLengthBytes = 16,
            hashLengthBytes = 32,
        )

        val derived = DefaultVaultCryptoEngine.deriveArgon2id(
            password = passwordChars,
            salt = salt,
            parameters = params,
            secret = secret,
            associatedData = associatedData,
        )

        val expectedHex = "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"
        assertEquals(expectedHex, derived.toHex())
    }

    @Test
    fun `nist sp 800-38d aes-256-gcm test cases 13 14 and 16 match exactly`() {
        // Test Case 13: 256-bit zero key, 96-bit zero IV, empty plaintext, empty AAD
        val zeroKey = ByteArray(32)
        val zeroIv = ByteArray(12)
        val tc13 = DefaultVaultCryptoEngine.aesGcmEncrypt(
            keyBytes = zeroKey,
            plaintext = ByteArray(0),
            aad = ByteArray(0),
            nonce = zeroIv,
        )
        assertEquals("530f8afbc74536b9a963b4f1c4cb738b", tc13.ciphertextAndTag.toHex())

        // Test Case 14: 256-bit zero key, 96-bit zero IV, 16-byte zero plaintext, empty AAD
        val tc14 = DefaultVaultCryptoEngine.aesGcmEncrypt(
            keyBytes = zeroKey,
            plaintext = ByteArray(16),
            aad = ByteArray(0),
            nonce = zeroIv,
        )
        assertEquals(
            "cea7403d4d606b6e074ec5d3baf39d18d0d1c8a799996bf0265b98b5d48ab919",
            tc14.ciphertextAndTag.toHex(),
        )

        // Test Case 16: 256-bit key, 96-bit IV, 60-byte plaintext, 20-byte AAD
        val key16 = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hexToBytes()
        val iv16 = "cafebabefacedbaddecaf888".hexToBytes()
        val pt16 = (
            "d9313225f88406e5a55909c5aff5269a" +
                "86a7a9531534f7da2e4c303d8a318a72" +
                "1c3c0c95956809532fcf0e2449a6b525" +
                "b16aedf5aa0de657ba637b39"
            ).hexToBytes()
        val aad16 = "feedfacedeadbeeffeedfacedeadbeefabaddad2".hexToBytes()

        val tc16 = DefaultVaultCryptoEngine.aesGcmEncrypt(
            keyBytes = key16,
            plaintext = pt16,
            aad = aad16,
            nonce = iv16,
        )
        val expectedCt16 = (
            "522dc1f099567d07f47f37a32a84427d" +
                "643a8cdcbfe5c0c97598a2bd2555d1aa" +
                "8cb08e48590dbb3da7b08b1056828838" +
                "c5f61e6393ba7a0abcc9f662"
            )
        val expectedTag16 = "76fc6ece0f4e1768cddf8853bb2d551b"
        assertEquals(expectedCt16 + expectedTag16, tc16.ciphertextAndTag.toHex())

        val decrypted16 = DefaultVaultCryptoEngine.aesGcmDecrypt(key16, tc16, aad16)
        assertArrayEquals(pt16, decrypted16)
    }

    @Test
    fun `wrong master password and invalid recovery keys fail safely without leaking secrets`() {
        val secretMaster = "SUPER-SECRET-MASTER-PHRASE-9981"
        val wrongMaster = "WRONG-CANDIDATE-PASS-7742"
        val bundle = engine.createVault(secretMaster.toCharArray(), KdfParameters.TEST_FAST)
        bundle.sessionKey.wipe()

        try {
            engine.unlockWithMasterPassword(bundle.header, wrongMaster.toCharArray())
            fail("Expected InvalidCredentialsException for wrong master password")
        } catch (e: InvalidCredentialsException) {
            val msg = e.message.orEmpty()
            assertFalse(msg.contains(secretMaster))
            assertFalse(msg.contains(wrongMaster))
        }

        // Corrupt one character in the recovery key checksum -> InvalidRecoveryKeyException
        val corruptedRecovery = bundle.formattedRecoveryKey.dropLast(1) +
            if (bundle.formattedRecoveryKey.last() == 'A') 'B' else 'A'
        try {
            engine.unlockWithRecoveryKey(bundle.header, corruptedRecovery)
            fail("Expected InvalidRecoveryKeyException for corrupted checksum")
        } catch (e: InvalidRecoveryKeyException) {
            assertFalse(e.message.orEmpty().contains(bundle.formattedRecoveryKey))
            assertFalse(e.message.orEmpty().contains(corruptedRecovery))
        }

        // Valid recovery key belonging to a different vault -> InvalidCredentialsException
        val otherRecoveryKey = RecoveryKeyCodec.generateFormattedRecoveryKey()
        try {
            engine.unlockWithRecoveryKey(bundle.header, otherRecoveryKey)
            fail("Expected InvalidCredentialsException for mismatched recovery key")
        } catch (e: InvalidCredentialsException) {
            assertFalse(e.message.orEmpty().contains(otherRecoveryKey))
        }
    }

    @Test
    fun `tampered ciphertext nonce tag or bound AAD metadata is strictly rejected`() {
        val bundle = engine.createVault("cedar-harbor-test-key".toCharArray(), KdfParameters.TEST_FAST)
        val secretPlaintext = "TOP-SECRET-CREDENTIAL-PAYLOAD-42".toByteArray(Charsets.UTF_8)
        val encrypted = engine.encryptRecord(
            sessionKey = bundle.sessionKey,
            recordId = "rec-1",
            recordType = VaultItemType.LOGIN,
            plaintext = secretPlaintext,
        )

        // Happy path
        assertArrayEquals(secretPlaintext, engine.decryptRecord(bundle.sessionKey, encrypted))

        // 1. Flip a ciphertext byte
        val flippedCt = encrypted.payload.ciphertextAndTag.copyOf().apply { this[0] = (this[0].toInt() xor 0x01).toByte() }
        assertTamperRejected(
            record = encrypted.copy(payload = EncryptedPayload(encrypted.payload.nonce.copyOf(), flippedCt)),
            sessionKey = bundle.sessionKey,
            forbiddenSecret = "TOP-SECRET-CREDENTIAL-PAYLOAD-42",
        )

        // 2. Flip a GCM authentication tag byte (last byte)
        val flippedTag = encrypted.payload.ciphertextAndTag.copyOf().apply {
            this[lastIndex] = (this[lastIndex].toInt() xor 0x80).toByte()
        }
        assertTamperRejected(
            record = encrypted.copy(payload = EncryptedPayload(encrypted.payload.nonce.copyOf(), flippedTag)),
            sessionKey = bundle.sessionKey,
            forbiddenSecret = "TOP-SECRET-CREDENTIAL-PAYLOAD-42",
        )

        // 3. Flip a nonce byte
        val flippedNonce = encrypted.payload.nonce.copyOf().apply { this[0] = (this[0].toInt() xor 0x04).toByte() }
        assertTamperRejected(
            record = encrypted.copy(payload = EncryptedPayload(flippedNonce, encrypted.payload.ciphertextAndTag.copyOf())),
            sessionKey = bundle.sessionKey,
            forbiddenSecret = "TOP-SECRET-CREDENTIAL-PAYLOAD-42",
        )

        // 4. Tamper recordId (record-swapping attack)
        assertTamperRejected(
            record = encrypted.copy(recordId = "rec-2"),
            sessionKey = bundle.sessionKey,
            forbiddenSecret = "TOP-SECRET-CREDENTIAL-PAYLOAD-42",
        )

        // 5. Tamper recordType
        assertTamperRejected(
            record = encrypted.copy(recordType = VaultItemType.SECURE_NOTE),
            sessionKey = bundle.sessionKey,
            forbiddenSecret = "TOP-SECRET-CREDENTIAL-PAYLOAD-42",
        )

        // 6. Tamper vaultId
        assertTamperRejected(
            record = encrypted.copy(vaultId = "00000000-0000-0000-0000-000000000000"),
            sessionKey = bundle.sessionKey,
            forbiddenSecret = "TOP-SECRET-CREDENTIAL-PAYLOAD-42",
        )

        bundle.sessionKey.wipe()
    }

    @Test
    fun `nonce generation produces unique nonces and distinct ciphertexts across 1000 encryptions`() {
        val bundle = engine.createVault("nonce-uniqueness-pass".toCharArray(), KdfParameters.TEST_FAST)
        val plaintext = "identical-plaintext".toByteArray(Charsets.UTF_8)
        val seenNonces = HashSet<String>()
        val seenCiphertexts = HashSet<String>()

        repeat(1_000) { idx ->
            val rec = engine.encryptRecord(
                sessionKey = bundle.sessionKey,
                recordId = "rec-$idx",
                recordType = VaultItemType.LOGIN,
                plaintext = plaintext,
            )
            assertTrue("Duplicate nonce detected", seenNonces.add(rec.payload.nonce.toHex()))
            assertTrue("Duplicate ciphertext detected", seenCiphertexts.add(rec.payload.ciphertextAndTag.toHex()))
        }

        assertEquals(1_000, seenNonces.size)
        assertEquals(1_000, seenCiphertexts.size)
        bundle.sessionKey.wipe()
    }

    @Test
    fun `format version validation rejects unknown past and future versions`() {
        VaultFormatMigrator.validateFormatVersion(VaultFormatConstants.CURRENT_FORMAT_VERSION)

        try {
            VaultFormatMigrator.validateFormatVersion(0)
            fail("Expected UnsupportedFormatVersionException for v0")
        } catch (e: UnsupportedFormatVersionException) {
            assertEquals(0, e.version)
        }

        try {
            VaultFormatMigrator.validateFormatVersion(99)
            fail("Expected UnsupportedFormatVersionException for v99")
        } catch (e: UnsupportedFormatVersionException) {
            assertEquals(99, e.version)
        }

        // Canonical AAD differs across format versions
        val aadV1 = AadBuilder.forRecord(1, "v-1", "r-1", VaultItemType.LOGIN)
        val aadV2 = AadBuilder.forRecord(2, "v-1", "r-1", VaultItemType.LOGIN)
        assertFalse(aadV1.contentEquals(aadV2))
    }

    @Test
    fun `password change and recovery re-wrap VEK without re-encrypting existing records`() {
        val initialPassword = "first-master-passphrase"
        val secondPassword = "second-master-passphrase"
        val recoveredPassword = "recovered-master-passphrase"

        val bundle = engine.createVault(initialPassword.toCharArray(), KdfParameters.TEST_FAST)
        val sampleItem = VaultItem(
            id = "rec-42",
            type = VaultItemType.LOGIN,
            title = "CodeForge Git",
            username = "alex.chen@example.com",
            secret = "Keep-Exact-Secret-Space ",
            website = "https://codeforge.example.com",
            normalizedDomain = "codeforge.example.com",
            notes = "Must survive password rotation untouched.",
            isFavorite = true,
            createdAtLabel = "Today",
            updatedAtLabel = "Today",
            lastUsedEpochMs = 123456L,
        )
        val encryptedRecord = engine.encryptRecord(
            sessionKey = bundle.sessionKey,
            recordId = sampleItem.id,
            recordType = sampleItem.type,
            plaintext = VaultRecordCodec.encode(sampleItem),
        )
        bundle.sessionKey.wipe()

        // 1. Change master password: ONLY header changes; encryptedRecord is untouched
        val rotatedHeader = engine.changeMasterPassword(
            header = bundle.header,
            currentMasterPassword = initialPassword.toCharArray(),
            newMasterPassword = secondPassword.toCharArray(),
            newKdfParameters = KdfParameters.TEST_FAST,
        )
        assertFalse(bundle.header.masterSalt.contentEquals(rotatedHeader.masterSalt))
        assertNotEquals(bundle.header.masterWrappedVek, rotatedHeader.masterWrappedVek)

        // Old password must fail
        try {
            engine.unlockWithMasterPassword(rotatedHeader, initialPassword.toCharArray())
            fail("Old master password should no longer unwrap VEK")
        } catch (_: InvalidCredentialsException) {
            // expected
        }

        // New password unwraps the SAME VEK and decrypts the untouched encryptedRecord
        val sessionAfterChange = engine.unlockWithMasterPassword(rotatedHeader, secondPassword.toCharArray())
        val decryptedAfterChange = VaultRecordCodec.decode(
            recordId = encryptedRecord.recordId,
            recordType = encryptedRecord.recordType,
            plaintext = engine.decryptRecord(sessionAfterChange, encryptedRecord),
        )
        assertEquals(sampleItem, decryptedAfterChange)
        sessionAfterChange.wipe()

        // 2. Recover with independent recovery key and set a third password (still without touching encryptedRecord)
        val (recoveredHeader, recoveredSession) = engine.recoverAndResetMasterPassword(
            header = rotatedHeader,
            formattedRecoveryKey = bundle.formattedRecoveryKey,
            newMasterPassword = recoveredPassword.toCharArray(),
            newKdfParameters = KdfParameters.TEST_FAST,
        )
        val decryptedAfterRecovery = VaultRecordCodec.decode(
            recordId = encryptedRecord.recordId,
            recordType = encryptedRecord.recordType,
            plaintext = engine.decryptRecord(recoveredSession, encryptedRecord),
        )
        assertEquals(sampleItem, decryptedAfterRecovery)
        recoveredSession.wipe()

        // Verify third password works independently
        val finalSession = engine.unlockWithMasterPassword(recoveredHeader, recoveredPassword.toCharArray())
        assertTrue(finalSession.isActive)
        finalSession.wipe()

        // Wiped session key must refuse further crypto operations
        try {
            engine.decryptRecord(finalSession, encryptedRecord)
            fail("Expected VaultLockedException after sessionKey.wipe()")
        } catch (_: VaultLockedException) {
            // expected
        }
    }

    @Test
    fun `vault backup codec v1 roundtrips encrypted vault and rejects tampering or truncation`() {
        val bundle = engine.createVault("backup-codec-master-pass".toCharArray(), KdfParameters.TEST_FAST)
        val item = VaultItem(
            id = "rec-passkey-1",
            type = VaultItemType.PASSKEY,
            title = "CodeForge Offline Passkey",
            username = "alex@example.com",
            secret = "private-p256-envelope-bytes",
            website = "https://codeforge.example.com",
            normalizedDomain = "codeforge.example.com",
            notes = "Offline passkey envelope",
            isFavorite = true,
            createdAtLabel = "Today",
            updatedAtLabel = "Today",
            lastUsedEpochMs = 999L,
        )
        val encrypted = engine.encryptRecord(
            sessionKey = bundle.sessionKey,
            recordId = item.id,
            recordType = item.type,
            plaintext = VaultRecordCodec.encode(item),
        )
        bundle.sessionKey.wipe()

        val encoded = VaultBackupCodec.encode(
            header = bundle.header,
            records = listOf(encrypted),
            exportedAtEpochMs = 1_730_000_000_000L,
        )

        // Ensure plaintext secrets never appear inside encoded .svbk bytes
        val encodedText = String(encoded, Charsets.ISO_8859_1)
        assertFalse(encodedText.contains("private-p256-envelope-bytes"))
        assertFalse(encodedText.contains("CodeForge Offline Passkey"))

        val summary = VaultBackupCodec.inspect(encoded)
        assertEquals(VaultBackupCodec.CURRENT_BACKUP_VERSION, summary.backupVersion)
        assertEquals(VaultFormatConstants.CURRENT_FORMAT_VERSION, summary.vaultFormatVersion)
        assertEquals(1, summary.recordCount)

        val decoded = VaultBackupCodec.decode(encoded)
        assertEquals(bundle.header.vaultId, decoded.header.vaultId)
        assertEquals(1, decoded.records.size)
        assertEquals(VaultItemType.PASSKEY, decoded.records.first().recordType)

        // Flip any byte in body -> SHA-256 digest mismatch must reject
        val tamperedBody = encoded.copyOf().apply { this[10] = (this[10].toInt() xor 0xFF).toByte() }
        try {
            VaultBackupCodec.decode(tamperedBody)
            fail("Expected TamperedDataException on flipped body byte")
        } catch (_: TamperedDataException) {
            // expected
        }

        // Flip a byte in trailing SHA-256 digest -> must reject
        val tamperedDigest = encoded.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 0x01).toByte() }
        try {
            VaultBackupCodec.decode(tamperedDigest)
            fail("Expected TamperedDataException on flipped digest byte")
        } catch (_: TamperedDataException) {
            // expected
        }

        // Truncated file -> must reject
        try {
            VaultBackupCodec.decode(encoded.copyOf(24))
            fail("Expected TamperedDataException on truncated backup")
        } catch (_: TamperedDataException) {
            // expected
        }
    }

    private fun assertTamperRejected(
        record: com.example.crypto.EncryptedVaultRecord,
        sessionKey: com.example.crypto.VaultSessionKey,
        forbiddenSecret: String,
    ) {
        try {
            engine.decryptRecord(sessionKey, record)
            fail("Expected TamperedDataException")
        } catch (e: TamperedDataException) {
            assertFalse(e.message.orEmpty().contains(forbiddenSecret))
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        check(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
