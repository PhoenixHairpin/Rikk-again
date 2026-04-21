package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.root.SshCredentialState
import me.rerere.rikkahub.data.root.SshCredentialStore

class SettingVM(
    private val settingsStore: SettingsStore,
    private val sshCredentialStore: SshCredentialStore,
    private val mcpManager: McpManager
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    val sshCredentials: StateFlow<SshCredentialState> = sshCredentialStore.state
        .stateIn(viewModelScope, SharingStarted.Lazily, sshCredentialStore.state.value)

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun updateSshPassword(password: String) {
        if (password.isBlank()) {
            sshCredentialStore.clearPassword()
        } else {
            sshCredentialStore.savePassword(password)
        }
    }

    fun updateSshPrivateKey(privateKey: String) {
        if (privateKey.isBlank()) {
            sshCredentialStore.clearPrivateKey()
        } else {
            sshCredentialStore.savePrivateKey(privateKey)
        }
    }

    fun updateSshPassphrase(passphrase: String) {
        if (passphrase.isBlank()) {
            sshCredentialStore.clearPassphrase()
        } else {
            sshCredentialStore.savePassphrase(passphrase)
        }
    }
}

