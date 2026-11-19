package com.example.crypto

/**
 * Argon2id key derivation parameters (RFC 9106, version 0x13).
 *
 * Parameter selection rationale (OWASP Password Storage Cheat Sheet & mobile constraints):
 * - OWASP recommends Argon2id with at least m=19 MiB (19,456 KiB), t=2, p=1 for constrained/mobile
 *   interactive environments, or m=46 MiB (47,104 KiB), t=1..2, p=1 where higher RAM is available.
 * - Because Phase 2 uses pure-Java Bouncy Castle for cross-ABI portability across Android API 24+,
 *   OWASP_MOBILE_BALANCED (19 MiB, t=2, p=1) provides strong memory-hard resistance without OOMing
 *   budget Android devices or stalling interactive unlock.
 * - All parameters are stored in the unencrypted VaultHeader so devices can re-derive keys reliably
 *   and upgrade parameters during password changes without breaking existing vaults.
 */
data class KdfParameters(
    val memoryKiB: Int,
    val iterations: Int,
    val parallelism: Int,
    val saltLengthBytes: Int = DEFAULT_SALT_LENGTH_BYTES,
    val hashLengthBytes: Int = DEFAULT_KEY_LENGTH_BYTES,
) {
    init {
        require(memoryKiB >= MIN_MEMORY_KIB) {
            "Argon2id memoryKiB must be at least $MIN_MEMORY_KIB KiB."
        }
        require(iterations >= 1) {
            "Argon2id iterations must be at least 1."
        }
        require(parallelism in 1..16) {
            "Argon2id parallelism must be within 1..16."
        }
        require(saltLengthBytes >= 16) {
            "Salt length must be at least 16 bytes."
        }
        require(hashLengthBytes == DEFAULT_KEY_LENGTH_BYTES) {
            "Derived KEK length must be $DEFAULT_KEY_LENGTH_BYTES bytes (AES-256)."
        }
    }

    fun summaryLabel(): String {
        val mib = memoryKiB / 1024
        return "Argon2id • ${mib} MiB • t=$iterations • p=$parallelism"
    }

    companion object {
        const val MIN_MEMORY_KIB = 32
        const val DEFAULT_SALT_LENGTH_BYTES = 16
        const val DEFAULT_KEY_LENGTH_BYTES = 32

        /**
         * Default interactive mobile profile aligned with OWASP Argon2id guidance
         * (19 MiB memory, 2 iterations, 1 lane).
         */
        val OWASP_MOBILE_BALANCED = KdfParameters(
            memoryKiB = 19_456,
            iterations = 2,
            parallelism = 1,
        )

        /**
         * Higher-memory profile aligned with OWASP 46 MiB guidance for higher-RAM devices.
         */
        val OWASP_HIGH_MEMORY = KdfParameters(
            memoryKiB = 47_104,
            iterations = 2,
            parallelism = 1,
        )

        /**
         * Fast profile strictly for deterministic unit/UI test suites where full mobile
         * KDF latency would slow down non-KDF assertions. Never used as production default.
         */
        internal val TEST_FAST = KdfParameters(
            memoryKiB = 1_024,
            iterations = 1,
            parallelism = 1,
        )
    }
}

data class KdfBenchmarkResult(
    val parameters: KdfParameters,
    val durationMs: Long,
)
