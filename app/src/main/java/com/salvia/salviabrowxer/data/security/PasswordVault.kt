package com.salvia.salviabrowxer.data.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

/**
 * Turns a password into something that can be checked without storing the password.
 *
 * What is stored is a PBKDF2 verifier: salt, iteration count and the derived key, all in one string.
 * Checking a password is deliberately slow — that is the whole point — so a stolen preferences file
 * cannot be turned back into the user's password in any useful amount of time.
 *
 * `PBKDF2WithHmacSHA1` rather than the SHA-256 variant on purpose: the SHA-256 PRF only exists from
 * API 26 and this app runs from API 24. HMAC-SHA1 as a PRF inside PBKDF2 is not the broken thing the
 * name suggests, and the iteration count is chosen for the platform, not for a 2010 phone.
 *
 * This is a *lock*, not encryption: files in the locked folder are stored in the app's private
 * directory, where other apps cannot read them, and this decides who sees them inside the app. The
 * difference is stated in the settings screen rather than implied.
 */
object PasswordVault {

    /** Shorter than this and the "lock" is a formality; the UI refuses rather than warns. */
    const val MIN_PASSWORD_CHARS: Int = 4

    /**
     * A middle ground: a few hundred milliseconds on the slowest phone the app supports, and enough
     * work that guessing a dictionary is not a phone-sized job.
     */
    const val ITERATIONS: Int = 120_000

    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private const val PREFIX = "pbkdf2-sha1"

    /** A fresh verifier for a new or changed password. Null when the password is too short. */
    fun create(password: String, iterations: Int = ITERATIONS): String? {
        if (!isAcceptable(password)) return null
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val key = derive(password, salt, iterations)
        return listOf(
            PREFIX,
            iterations.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(key, Base64.NO_WRAP)
        ).joinToString(":")
    }

    /**
     * Whether [password] is the one behind [encoded].
     *
     * A malformed or missing verifier is never a match: an unreadable preference file must lock the
     * folder, not open it. The comparison itself is constant-time.
     */
    fun verify(password: String, encoded: String?): Boolean {
        val parts = encoded?.trim()?.split(':') ?: return false
        if (parts.size != 4 || parts[0] != PREFIX) return false
        val iterations = parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return false
        val salt = decode(parts[2]) ?: return false
        val expected = decode(parts[3]) ?: return false
        if (salt.isEmpty() || expected.isEmpty()) return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    /** Whether the stored value is a verifier this object wrote, rather than anything else. */
    fun isVerifier(value: String?): Boolean {
        val parts = value?.trim()?.split(':') ?: return false
        return parts.size == 4 && parts[0] == PREFIX && (parts[1].toIntOrNull() ?: 0) > 0 &&
            !decode(parts[2]).isNullOrEmpty() && !decode(parts[3]).isNullOrEmpty()
    }

    fun isAcceptable(password: String): Boolean = password.trim().length >= MIN_PASSWORD_CHARS

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun decode(value: String): ByteArray? = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
}
