package com.example.dailydigest.data.preferences

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "daily_digest_prefs")

class PreferencesManager(private val context: Context) {

    private val KEY_STORE_ALIAS = "DailyDigestGeminiKey"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"

    private object PreferencesKeys {
        val ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val DIGEST_HOUR = intPreferencesKey("digest_hour")
        val DIGEST_MINUTE = intPreferencesKey("digest_minute")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val LAST_DIGEST_DATE = stringPreferencesKey("last_digest_date")
        val BATTERY_OPTIMIZATION_PROMPTED = booleanPreferencesKey("battery_prompted")
    }

    // Default configuration values
    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val DEFAULT_HOUR = 6
        const val DEFAULT_MINUTE = 0
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_STORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_STORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
        return (keyStore.getEntry(KEY_STORE_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encryptString(plainText: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback base64 encoding if keystore is unavailable in edge environments
            Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun decryptString(encryptedBase64: String): String {
        if (encryptedBase64.isBlank()) return ""
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmIvLength = 12 // Standard GCM IV length
            if (combined.size <= gcmIvLength) return ""
            val spec = GCMParameterSpec(128, combined, 0, gcmIvLength)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            val original = cipher.doFinal(combined, gcmIvLength, combined.size - gcmIvLength)
            String(original, Charsets.UTF_8)
        } catch (e: Exception) {
            try {
                String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
            } catch (ex: Exception) {
                ""
            }
        }
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        val encrypted = prefs[PreferencesKeys.ENCRYPTED_API_KEY] ?: ""
        if (encrypted.isNotBlank()) {
            decryptString(encrypted)
        } else {
            // Fallback to BuildConfig if provided via AI Studio secrets
            val buildConfigKey = try {
                BuildConfig.GEMINI_API_KEY
            } catch (e: Throwable) {
                ""
            }
            if (buildConfigKey == "PLACEHOLDER" || buildConfigKey == "MY_GEMINI_API_KEY") {
                ""
            } else {
                buildConfigKey
            }
        }
    }

    suspend fun saveApiKey(apiKey: String) {
        val encrypted = encryptString(apiKey.trim())
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.ENCRYPTED_API_KEY] = encrypted
        }
    }

    val selectedModelFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.GEMINI_MODEL] ?: DEFAULT_MODEL
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.GEMINI_MODEL] = model.trim()
        }
    }

    val digestHourFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DIGEST_HOUR] ?: DEFAULT_HOUR
    }

    val digestMinuteFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.DIGEST_MINUTE] ?: DEFAULT_MINUTE
    }

    suspend fun saveDigestTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.DIGEST_HOUR] = hour
            prefs[PreferencesKeys.DIGEST_MINUTE] = minute
        }
    }

    val notificationsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    val lastDigestDateFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.LAST_DIGEST_DATE]
    }

    suspend fun setLastDigestDate(date: String) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.LAST_DIGEST_DATE] = date
        }
    }

    val batteryPromptedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PreferencesKeys.BATTERY_OPTIMIZATION_PROMPTED] ?: false
    }

    suspend fun setBatteryPrompted(prompted: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.BATTERY_OPTIMIZATION_PROMPTED] = prompted
        }
    }

    suspend fun clearAllPreferences() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
