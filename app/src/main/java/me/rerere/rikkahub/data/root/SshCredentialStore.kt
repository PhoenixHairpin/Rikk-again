package me.rerere.rikkahub.data.root

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val SSH_CREDENTIAL_PREFS = "ssh_credentials"
private const val SSH_PASSWORD_KEY = "password"
private const val SSH_PRIVATE_KEY_KEY = "private_key"
private const val SSH_PASSPHRASE_KEY = "passphrase"

data class SshCredentialState(
    val storageReady: Boolean = false,
    val hasPassword: Boolean = false,
    val hasPrivateKey: Boolean = false,
    val hasPassphrase: Boolean = false,
    val error: String? = null,
)

class SshCredentialStore(context: Context) {
    private val prefsResult = runCatching {
        createEncryptedPreferences(context.applicationContext)
    }
    private val prefs = prefsResult.getOrNull()
    private var lastError = prefsResult.exceptionOrNull()?.message

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<SshCredentialState> = _state.asStateFlow()

    fun getPassword(): String? = readSecret(SSH_PASSWORD_KEY)

    fun getPrivateKey(): String? = readSecret(SSH_PRIVATE_KEY_KEY)

    fun getPassphrase(): String? = readSecret(SSH_PASSPHRASE_KEY)

    fun savePassword(password: String) {
        writeSecret(SSH_PASSWORD_KEY, password)
    }

    fun clearPassword() {
        writeSecret(SSH_PASSWORD_KEY, null)
    }

    fun savePrivateKey(privateKey: String) {
        writeSecret(SSH_PRIVATE_KEY_KEY, privateKey)
    }

    fun clearPrivateKey() {
        writeSecret(SSH_PRIVATE_KEY_KEY, null)
    }

    fun savePassphrase(passphrase: String) {
        writeSecret(SSH_PASSPHRASE_KEY, passphrase)
    }

    fun clearPassphrase() {
        writeSecret(SSH_PASSPHRASE_KEY, null)
    }

    private fun buildState(): SshCredentialState {
        return if (prefs == null) {
            SshCredentialState(
                storageReady = false,
                error = lastError ?: "Secure credential storage unavailable",
            )
        } else {
            SshCredentialState(
                storageReady = true,
                hasPassword = readSecret(SSH_PASSWORD_KEY) != null,
                hasPrivateKey = readSecret(SSH_PRIVATE_KEY_KEY) != null,
                hasPassphrase = readSecret(SSH_PASSPHRASE_KEY) != null,
                error = lastError,
            )
        }
    }

    private fun readSecret(key: String): String? {
        return prefs?.getString(key, null)?.takeIf { it.isNotEmpty() }
    }

    private fun writeSecret(key: String, value: String?) {
        val securePrefs = prefs ?: run {
            _state.value = buildState()
            return
        }
        runCatching {
            securePrefs.edit().apply {
                if (value.isNullOrEmpty()) {
                    remove(key)
                } else {
                    putString(key, value)
                }
            }.apply()
            lastError = null
        }.onFailure {
            lastError = it.message ?: "Failed to update secure credential storage"
        }
        _state.value = buildState()
    }

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            SSH_CREDENTIAL_PREFS,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
