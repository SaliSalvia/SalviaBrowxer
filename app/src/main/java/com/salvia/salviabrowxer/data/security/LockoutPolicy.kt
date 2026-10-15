package com.salvia.salviabrowxer.data.security

/**
 * Progressive delay between password attempts on the locked folder.
 *
 * [PasswordVault]'s PBKDF2 verifier is deliberately slow, but "slow" is a *rate*, not a *stop*: an
 * unattended phone and a patient guesser still get unlimited tries at a few hundred milliseconds
 * each. This policy adds the stop — after [maxAttempts] failures in a row the next attempt has to
 * wait, and the wait doubles with every further failure up to [maxDelayMillis].
 *
 * The clock is injected by the caller (`now` on every call) rather than read here, so the whole
 * behaviour is deterministic in tests and the caller can stay on whatever thread it is already on.
 * Nothing is persisted: a lockout that survives a process restart would need a file the attacker
 * controls, and the delay's job is only to make guessing through the UI pointless.
 */
class LockoutPolicy(
    private val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 2_000L,
    private val maxDelayMillis: Long = 60_000L
) {
    private var failures = 0
    private var lockedUntil = 0L

    /** True when an attempt at [now] must be refused without consulting the verifier. */
    fun isLockedOut(now: Long): Boolean = now < lockedUntil

    /** How much longer the lockout lasts, for the message that tells the user when to try again. */
    fun remainingMillis(now: Long): Long = (lockedUntil - now).coerceAtLeast(0L)

    /**
     * Records a failed attempt. Returns the delay the *next* attempt must wait, which is zero until
     * the [maxAttempts]-th failure and doubles from there.
     */
    fun onFailure(now: Long): Long {
        failures += 1
        if (failures < maxAttempts) return 0L
        val exponent = (failures - maxAttempts).coerceAtMost(MAX_EXPONENT)
        val delay = (baseDelayMillis shl exponent).coerceAtMost(maxDelayMillis)
        lockedUntil = now + delay
        return delay
    }

    /** A correct password ends the streak; the folder stays as unlocked as it was before. */
    fun onSuccess() {
        failures = 0
        lockedUntil = 0L
    }

    private companion object {
        /** `shl` is bounded so a pathological failure count cannot overflow back to zero. */
        const val MAX_EXPONENT = 12
    }
}
