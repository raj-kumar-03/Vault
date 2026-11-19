package com.example.crypto

/**
 * Base exception for vault cryptographic operations.
 *
 * Security invariant: Exception messages and causes MUST NEVER contain master passwords,
 * recovery keys, raw key bytes, nonces, or plaintext credential data.
 */
sealed class VaultCryptoException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class InvalidCredentialsException(
    message: String = "Unable to unlock vault with the provided credentials.",
) : VaultCryptoException(message)

class TamperedDataException(
    message: String = "Authenticated decryption failed; ciphertext or metadata was modified.",
) : VaultCryptoException(message)

class UnsupportedFormatVersionException(
    val version: Int,
) : VaultCryptoException("Unsupported vault format version: $version.")

class InvalidRecoveryKeyException(
    message: String = "Recovery key format or checksum is invalid.",
) : VaultCryptoException(message)

class VaultLockedException(
    message: String = "Vault session key is not available or has been wiped.",
) : VaultCryptoException(message)

class NonceGenerationException(
    message: String = "Failed to generate a valid unique cryptographic nonce.",
) : VaultCryptoException(message)
