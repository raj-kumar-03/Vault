package com.example.security

import com.example.domain.model.AutoLockInterval

enum class LockReason(val bannerMessage: String?) {
    NONE(null),
    MANUAL("Vault locked • VEK wiped from memory"),
    AUTO_LOCK_BACKGROUND("Auto-locked after returning from background"),
    AUTO_LOCK_INACTIVITY("Auto-locked after inactivity timeout"),
    COLD_START("Vault locked • Unlock required after app restart"),
}

/**
 * Pure monotonic-clock auto-lock engine (Phase 4).
 *
 * Uses monotonic elapsed milliseconds (e.g. SystemClock.elapsedRealtime()) rather than wall-clock
 * time so changing the system date/time cannot bypass auto-lock deadlines.
 */
class VaultAutoLockPolicy(
    private val elapsedClockMs: () -> Long,
) {
    private var backgroundedAtElapsedMs: Long? = null
    private var lastActivityElapsedMs: Long = elapsedClockMs()

    fun resetActivity(nowElapsedMs: Long = elapsedClockMs()) {
        lastActivityElapsedMs = nowElapsedMs
        backgroundedAtElapsedMs = null
    }

    /**
     * Records app backgrounding (ON_STOP).
     * Returns true if the vault must lock immediately before backgrounding completes.
     */
    fun onAppBackgrounded(
        interval: AutoLockInterval,
        nowElapsedMs: Long = elapsedClockMs(),
    ): Boolean {
        if (interval == AutoLockInterval.NEVER) {
            backgroundedAtElapsedMs = null
            return false
        }
        backgroundedAtElapsedMs = nowElapsedMs
        return false
    }

    /**
     * Evaluates whether elapsed background duration exceeded the configured auto-lock interval.
     * Clears background timestamp after evaluation.
     */
    fun shouldLockOnForeground(
        interval: AutoLockInterval,
        nowElapsedMs: Long = elapsedClockMs(),
    ): Boolean {
        val bgTime = backgroundedAtElapsedMs ?: return false
        backgroundedAtElapsedMs = null
        val timeoutMs = interval.durationMs() ?: return false
        val elapsed = nowElapsedMs - bgTime
        // Fail closed if clock moved unexpectedly backwards.
        if (elapsed < 0L) return true
        return elapsed >= timeoutMs
    }

    /**
     * Evaluates whether foreground inactivity exceeded the configured auto-lock interval.
     */
    fun shouldLockForInactivity(
        interval: AutoLockInterval,
        nowElapsedMs: Long = elapsedClockMs(),
    ): Boolean {
        val timeoutMs = interval.durationMs() ?: return false
        val idleMs = nowElapsedMs - lastActivityElapsedMs
        if (idleMs < 0L) return true
        return idleMs >= timeoutMs
    }

    companion object {
        fun AutoLockInterval.durationMs(): Long? = when (this) {
            AutoLockInterval.ONE_MINUTE -> 60_000L
            AutoLockInterval.FIVE_MINUTES -> 300_000L
            AutoLockInterval.FIFTEEN_MINUTES -> 900_000L
            AutoLockInterval.NEVER -> null
        }
    }
}
