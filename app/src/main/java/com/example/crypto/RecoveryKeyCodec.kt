package com.example.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Generates, formats, validates, and derives a key-encryption key from an independent
 * 256-bit random Recovery Key.
 *
 * Format:
 * - 32 random secret bytes (256 bits) + 2-byte SHA-256 checksum = 34 bytes (272 bits).
 * - Encoded using Crockford Base32 (no ambiguous I/L/O/U characters) into 55 characters
 *   grouped in 5-character blocks prefixed with "SVRK-".
 * - Because the recovery secret already has 256 bits of cryptographic entropy, its KEK is
 *   derived via RFC 5869 HKDF-SHA256 with domain separation and per-vault recoverySalt.
 */
object RecoveryKeyCodec {

    private const val PREFIX = "SVRK"
    private const val SECRET_BYTE_LENGTH = 32
    private const val CHECKSUM_BYTE_LENGTH = 2
    private const val TOTAL_BYTE_LENGTH = SECRET_BYTE_LENGTH + CHECKSUM_BYTE_LENGTH
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val DECODE_TABLE: IntArray = IntArray(128) { -1 }.apply {
        ALPHABET.forEachIndexed { idx, c ->
            this[c.code] = idx
            this[c.lowercaseChar().code] = idx
        }
        // Crockford human-typo tolerances
        this['O'.code] = 0; this['o'.code] = 0
        this['I'.code] = 1; this['i'.code] = 1
        this['L'.code] = 1; this['l'.code] = 1
    }

    fun generateFormattedRecoveryKey(secureRandom: SecureRandom = SecureRandom()): String {
        val secret = ByteArray(SECRET_BYTE_LENGTH)
        try {
            secureRandom.nextBytes(secret)
            return encode(secret)
        } finally {
            secret.fill(0)
        }
    }

    fun encode(rawSecret: ByteArray): String {
        require(rawSecret.size == SECRET_BYTE_LENGTH) {
            "Recovery secret must be exactly $SECRET_BYTE_LENGTH bytes."
        }
        val checksum = computeChecksum(rawSecret)
        val combined = ByteArray(TOTAL_BYTE_LENGTH)
        try {
            System.arraycopy(rawSecret, 0, combined, 0, SECRET_BYTE_LENGTH)
            System.arraycopy(checksum, 0, combined, SECRET_BYTE_LENGTH, CHECKSUM_BYTE_LENGTH)
            val base32 = encodeBase32(combined)
            val grouped = base32.chunked(5).joinToString("-")
            return "$PREFIX-$grouped"
        } finally {
            combined.fill(0)
            checksum.fill(0)
        }
    }

    /**
     * Parses and validates checksum of a user-entered recovery key.
     * Caller MUST zeroize the returned ByteArray after use.
     */
    fun decode(formatted: String): ByteArray {
        val cleaned = formatted
            .trim()
            .uppercase()
            .removePrefix("$PREFIX-")
            .replace("-", "")
            .replace(" ", "")

        val expectedChars = ((TOTAL_BYTE_LENGTH * 8) + 4) / 5 // 55 chars
        if (cleaned.length != expectedChars) {
            throw InvalidRecoveryKeyException("Recovery key length is invalid.")
        }

        val combined = decodeBase32(cleaned, TOTAL_BYTE_LENGTH)
        try {
            val secret = combined.copyOfRange(0, SECRET_BYTE_LENGTH)
            val providedChecksum = combined.copyOfRange(SECRET_BYTE_LENGTH, TOTAL_BYTE_LENGTH)
            val expectedChecksum = computeChecksum(secret)
            val valid = MessageDigest.isEqual(providedChecksum, expectedChecksum)
            providedChecksum.fill(0)
            expectedChecksum.fill(0)
            if (!valid) {
                secret.fill(0)
                throw InvalidRecoveryKeyException("Recovery key checksum verification failed.")
            }
            return secret
        } finally {
            combined.fill(0)
        }
    }

    /**
     * Derives a 32-byte AES-256 Recovery KEK using RFC 5869 HKDF-SHA256.
     * Caller MUST zeroize the returned ByteArray after use.
     */
    fun deriveRecoveryKek(
        rawRecoverySecret: ByteArray,
        recoverySalt: ByteArray,
        vaultId: String,
    ): ByteArray {
        require(rawRecoverySecret.size == SECRET_BYTE_LENGTH) { "Invalid recovery secret length." }
        require(recoverySalt.size >= 16) { "Invalid recovery salt length." }

        val info = "SecureVault-Recovery-KEK-v1:$vaultId".toByteArray(Charsets.UTF_8)
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(rawRecoverySecret, recoverySalt, info))
        val derived = ByteArray(32)
        hkdf.generateBytes(derived, 0, derived.size)
        return derived
    }

    private fun computeChecksum(secret: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("SVRK-CHK-v1".toByteArray(Charsets.UTF_8))
        digest.update(secret)
        val full = digest.digest()
        val out = full.copyOfRange(0, CHECKSUM_BYTE_LENGTH)
        full.fill(0)
        return out
    }

    private fun encodeBase32(bytes: ByteArray): String {
        val sb = StringBuilder(((bytes.size * 8) + 4) / 5)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(ALPHABET[(buffer ushr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    private fun decodeBase32(encoded: String, expectedByteLength: Int): ByteArray {
        val out = ByteArray(expectedByteLength)
        var outIndex = 0
        var buffer = 0
        var bitsLeft = 0
        for (ch in encoded) {
            val code = ch.code
            if (code >= DECODE_TABLE.size || DECODE_TABLE[code] < 0) {
                out.fill(0)
                throw InvalidRecoveryKeyException("Recovery key contains invalid characters.")
            }
            buffer = (buffer shl 5) or DECODE_TABLE[code]
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                if (outIndex >= expectedByteLength) {
                    out.fill(0)
                    throw InvalidRecoveryKeyException("Recovery key payload overflow.")
                }
                out[outIndex++] = ((buffer ushr bitsLeft) and 0xFF).toByte()
            }
        }
        if (outIndex != expectedByteLength) {
            out.fill(0)
            throw InvalidRecoveryKeyException("Recovery key decoded length mismatch.")
        }
        if (bitsLeft > 0 && (buffer and ((1 shl bitsLeft) - 1)) != 0) {
            out.fill(0)
            throw InvalidRecoveryKeyException("Recovery key trailing padding bits are invalid.")
        }
        return out
    }
}
