package com.salvia.salviabrowxer.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the locked folder keeps its password verifier.
 *
 * The file is the app's own, and it never contains the password — only a PBKDF2 verifier (see
 * [PasswordVault]) plus one flag. It is written through `EncryptedSharedPreferences`, so the verifier
 * is encrypted with a key that lives in the Android keystore rather than sitting in plain sight in
 * the app's data directory.
 *
 * If the keystore cannot be opened — a device with a broken or reset keystore, which does happen —
 * this falls back to the app's private preferences rather than failing. The verifier is still not the
 * password, and [isSecured] tells the truth about which of the two happened so the settings screen can
 * say it instead of pretending.
 */
class LockedFolderStore(private val context: Context) {

    private val encrypted: SharedPreferences? = openEncrypted(context)

    private val prefs: SharedPreferences = encrypted
        ?: context.getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)

    /** Whether the verifier is additionally protected by the keystore-backed file. */
    val isSecured: Boolean get() = encrypted != null

    /** Whether a password has been set. */
    fun isConfigured(): Boolean = PasswordVault.isVerifier(prefs.getString(KEY_VERIFIER, null))

    /**
     * Sets or replaces the password. False when it is too short to be one, or when the verifier could
     * not be written — the caller reports it rather than leaving the user believing in a lock that is
     * not there.
     */
    fun setPassword(password: String): Boolean {
        val verifier = PasswordVault.create(password) ?: return false
        return runCatching { prefs.edit().putString(KEY_VERIFIER, verifier).commit() }.getOrDefault(false)
    }

    /** Whether [password] opens the folder. A malformed or missing verifier never does. */
    fun verify(password: String): Boolean =
        PasswordVault.verify(password, prefs.getString(KEY_VERIFIER, null))

    /** Removes the password. The caller unlocks every file: nothing stays hidden behind a lock that is gone. */
    fun clear() {
        runCatching { prefs.edit().remove(KEY_VERIFIER).commit() }
    }

    private companion object {
        const val ENCRYPTED_FILE = "salvia_locked_folder"
        const val FALLBACK_FILE = "salvia_locked_folder_fallback"
        const val KEY_VERIFIER = "password_verifier"

        fun openEncrypted(context: Context): SharedPreferences? = runCatching {
            val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }
}
