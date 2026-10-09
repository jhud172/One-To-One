package uk.ac.cardiff.trainerhub.data.remote

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import uk.ac.cardiff.trainerhub.BuildConfig
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureSessionStore(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("one_to_one_mobile_session", Context.MODE_PRIVATE)

    fun token(): String? {
        val encrypted = preferences.getString(KEY_TOKEN, null) ?: return null
        if (preferences.getString(KEY_TOKEN_SERVER, null) != baseUrl()) {
            clearSession()
            return null
        }
        return try {
            decrypt(encrypted)
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    fun saveSession(token: String, user: MobileUser) {
        preferences.edit()
            .putString(KEY_TOKEN, encrypt(token))
            .putString(KEY_TOKEN_SERVER, baseUrl())
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_ROLE, user.role.name)
            .putBoolean(KEY_TRAINER_VERIFIED, user.trainerVerified)
            .apply()
    }

    fun cachedUser(): MobileUser? {
        val id = preferences.getLong(KEY_USER_ID, -1L)
        if (id <= 0) return null
        return MobileUser(
            id = id,
            email = preferences.getString(KEY_EMAIL, "").orEmpty(),
            username = preferences.getString(KEY_USERNAME, "").orEmpty(),
            fullName = preferences.getString(KEY_FULL_NAME, "One To One").orEmpty(),
            role = cachedRole(),
            trainerVerified = preferences.getBoolean(KEY_TRAINER_VERIFIED, false),
        )
    }

    fun clearSession() {
        preferences.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_TOKEN_SERVER)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_USERNAME)
            .remove(KEY_FULL_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_TRAINER_VERIFIED)
            .apply()
    }

    fun baseUrl(): String {
        val configuredUrl = BuildConfig.ONE_TO_ONE_BASE_URL.trimEnd('/')
        val savedUrl = preferences.getString(KEY_BASE_URL, null).orEmpty().trimEnd('/')
        val selectedUrl = when {
            savedUrl.isBlank() -> configuredUrl
            savedUrl.isLocalDevelopmentUrl() -> configuredUrl
            else -> savedUrl
        }
        return runCatching { validatedApiBaseUrl(selectedUrl, BuildConfig.DEBUG) }
            .getOrElse { validatedApiBaseUrl(configuredUrl, BuildConfig.DEBUG) }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val payload = Base64.decode(value, Base64.NO_WRAP)
        val iv = payload.copyOfRange(0, IV_LENGTH)
        val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun cachedRole(): MobileRole {
        val roleName = preferences.getString(KEY_ROLE, "UNKNOWN").orEmpty()
        return try {
            MobileRole.valueOf(roleName)
        } catch (_: Exception) {
            MobileRole.UNKNOWN
        }
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val KEY_ALIAS = "one_to_one_mobile_session_key"
        private const val KEY_TOKEN = "token"
        private const val KEY_TOKEN_SERVER = "token_server"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_USERNAME = "username"
        private const val KEY_FULL_NAME = "full_name"
        private const val KEY_ROLE = "role"
        private const val KEY_TRAINER_VERIFIED = "trainer_verified"
        private const val KEY_BASE_URL = "base_url"
    }
}

private fun String.isLocalDevelopmentUrl(): Boolean =
    runCatching { java.net.URI(this).host?.lowercase(java.util.Locale.ROOT) in setOf("10.0.2.2", "localhost", "127.0.0.1", "[::1]") }.getOrDefault(false)

internal fun validatedApiBaseUrl(value: String, allowLocalDevelopment: Boolean): String {
    val endpoint = java.net.URI(value.trim().trimEnd('/')).normalize()
    val scheme = endpoint.scheme?.lowercase(java.util.Locale.ROOT)
    require(!endpoint.host.isNullOrBlank() && endpoint.userInfo == null && endpoint.query == null && endpoint.fragment == null) {
        "Use a server URL without credentials, query parameters or fragments."
    }
    require(scheme == "https" || (scheme == "http" && allowLocalDevelopment && endpoint.toString().isLocalDevelopmentUrl())) {
        "Use HTTPS for the server. Local HTTP is available only in debug builds."
    }
    return endpoint.toString().trimEnd('/')
}
