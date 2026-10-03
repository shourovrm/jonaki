package app.jonaki.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Names of the API keys the user can save. */
enum class SecretName {
    OPENROUTER,
    DEEPSEEK,
    GEMINI,
    GLM,
    MIMO,
    OPENAI,
    TAVILY,

    /** One Ollama account key serves both Ollama Cloud chat and Ollama web search. */
    OLLAMA,
    EXA,
    MINIMAX,
    QWEN,
}

/**
 * API keys encrypted with an AES key that lives in Android Keystore and never
 * leaves it; only the ciphertext is stored in app preferences.
 */
class SecretStore(context: Context) {
    private val preferences = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    private val savedNames = MutableStateFlow(readSavedNames())

    /** Which keys are saved; the UI shows "set" without ever reading a key back. */
    val names: StateFlow<Set<SecretName>> = savedNames.asStateFlow()

    private val savedPreviews = MutableStateFlow(readPreviews())

    /** For each saved key, its first three characters and a mask (D-028); never the whole key. */
    val previews: StateFlow<Map<SecretName, String>> = savedPreviews.asStateFlow()

    fun read(name: SecretName): String? = decrypt(name.name)

    fun save(name: SecretName, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            remove(name)
            return
        }
        encrypt(name.name, trimmed)
        savedNames.value = readSavedNames()
        savedPreviews.value = savedPreviews.value + (name to maskedKeyPreview(trimmed))
    }

    fun remove(name: SecretName) {
        preferences.edit().remove(name.name).apply()
        savedNames.value = readSavedNames()
        savedPreviews.value = savedPreviews.value - name
    }

    /**
     * Secrets whose names are made at run time, such as the header of each
     * MCP server (D-104). They are encrypted like the API keys but are not
     * part of [names] or [previews].
     */
    fun readRuntimeSecret(name: String): String? =
        runCatching { decrypt(RUNTIME_PREFIX + name) }.getOrNull()

    /** Checks without decrypting, so a screen can ask on every frame. */
    fun hasRuntimeSecret(name: String): Boolean = preferences.contains(RUNTIME_PREFIX + name)

    fun saveRuntimeSecret(name: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            removeRuntimeSecret(name)
            return
        }
        encrypt(RUNTIME_PREFIX + name, trimmed)
    }

    fun removeRuntimeSecret(name: String) {
        preferences.edit().remove(RUNTIME_PREFIX + name).apply()
    }

    private fun decrypt(preferenceKey: String): String? {
        val stored = preferences.getString(preferenceKey, null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val initializationVector = bytes.copyOfRange(0, IV_LENGTH)
        val cipherText = bytes.copyOfRange(IV_LENGTH, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(TAG_BITS, initializationVector))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    private fun encrypt(preferenceKey: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val stored = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(preferenceKey, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
    }

    private fun readSavedNames(): Set<SecretName> =
        SecretName.entries.filter { name -> preferences.contains(name.name) }.toSet()

    private fun readPreviews(): Map<SecretName, String> =
        readSavedNames().mapNotNull { name ->
            // A key that no longer decrypts (for example after a Keystore reset) shows as not saved.
            val key = runCatching { read(name) }.getOrNull() ?: return@mapNotNull null
            name to maskedKeyPreview(key)
        }.toMap()

    private fun keystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "jonaki-api-keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128

        /** Keeps run-time names apart from the [SecretName] entries in the same preferences file. */
        const val RUNTIME_PREFIX = "runtime:"
    }
}
