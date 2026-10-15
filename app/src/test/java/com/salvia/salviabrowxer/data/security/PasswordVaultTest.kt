package com.salvia.salviabrowxer.data.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The password verifier behind the locked folder.
 *
 * Robolectric rather than a plain JVM test because the encoding is done with `android.util.Base64` —
 * the platform class, available on every device the app supports, and a different implementation on
 * the test classpath unless the real framework is the one running.
 *
 * The iteration count is raised here only through the `iterations` parameter of [PasswordVault.create],
 * so the stored format — which includes the count — is exercised as it really is; the default is left
 * at the production value where it is read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PasswordVaultTest {

    private val password = "correct horse battery"

    @Test
    fun `a stored verifier accepts its own password and nothing else`() {
        val encoded = PasswordVault.create(password)!!

        assertTrue(PasswordVault.verify(password, encoded))
        assertFalse(PasswordVault.verify("correct horse batterz", encoded))
        assertFalse(PasswordVault.verify("", encoded))
        assertFalse(PasswordVault.verify(password.uppercase(), encoded))
    }

    @Test
    fun `the password itself is not in the stored value`() {
        val encoded = PasswordVault.create(password)!!

        assertFalse("the verifier must not contain the password", encoded.contains(password))
        assertTrue(PasswordVault.isVerifier(encoded))
    }

    @Test
    fun `the same password twice gives two different verifiers`() {
        val first = PasswordVault.create(password)!!
        val second = PasswordVault.create(password)!!

        assertNotEquals("a fixed salt would make the verifiers identical", first, second)
        assertTrue(PasswordVault.verify(password, first))
        assertTrue(PasswordVault.verify(password, second))
    }

    @Test
    fun `nothing unreadable is ever accepted`() {
        // A locked folder that opens when its verifier is missing or damaged is not a lock.
        assertFalse(PasswordVault.verify(password, null))
        assertFalse(PasswordVault.verify(password, ""))
        assertFalse(PasswordVault.verify(password, "not-a-verifier"))
        assertFalse(PasswordVault.verify(password, "pbkdf2-sha1:120000:not-base64:also-not"))
        assertFalse(PasswordVault.verify(password, "pbkdf2-sha1:0:AAAA:AAAA"))
        assertFalse(PasswordVault.verify(password, "md5:1:AAAA:AAAA"))
        assertFalse(PasswordVault.isVerifier("pbkdf2-sha1:120000"))
        assertFalse(PasswordVault.isVerifier(null))
    }

    @Test
    fun `a password that is too short is refused before it is stored`() {
        assertFalse(PasswordVault.isAcceptable("abcde"))
        assertNull(PasswordVault.create("abcde"))
        assertTrue(PasswordVault.isAcceptable("abcdef"))
    }

    @Test
    fun `a verifier made when the minimum was four still verifies a four-character password`() {
        // Built by hand in the stored format, exactly as a build whose minimum was four would have
        // written it: raising the bar for *new* passwords must never re-judge an existing folder.
        val salt = ByteArray(16) { (it * 7).toByte() }
        val spec = javax.crypto.spec.PBEKeySpec("abcd".toCharArray(), salt, 1_000, 256)
        val key = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        val legacy = listOf(
            "pbkdf2-sha1", "1000",
            android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP),
            android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
        ).joinToString(":")
        assertTrue(PasswordVault.isVerifier(legacy))
        assertTrue(PasswordVault.verify("abcd", legacy))
        assertFalse(PasswordVault.verify("abce", legacy))
    }

    @Test
    fun `an old verifier still opens the folder after the iteration count changes`() {
        // The count lives in the stored string, so a verifier written by an earlier build keeps working.
        val old = PasswordVault.create(password, iterations = 1_000)!!

        assertTrue(PasswordVault.verify(password, old))
        assertFalse(PasswordVault.verify("something else", old))
    }
}
