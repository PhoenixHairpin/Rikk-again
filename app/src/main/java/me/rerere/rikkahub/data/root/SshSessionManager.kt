package me.rerere.rikkahub.data.root

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.SshAuthType
import me.rerere.rikkahub.service.RootTerminalService

private const val TAG = "SshSessionManager"

enum class SshSessionStatus {
    DISABLED,
    INCOMPLETE_CONFIG,
    READY,
    CONNECTING,
    CONNECTED,
    ERROR,
}

data class SshSessionState(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 22,
    val username: String = "",
    val authType: SshAuthType = SshAuthType.PASSWORD,
    val configValid: Boolean = false,
    val storageReady: Boolean = false,
    val hasCredential: Boolean = false,
    val status: SshSessionStatus = SshSessionStatus.DISABLED,
    val isActive: Boolean = false,
    val title: String = "",
    val error: String? = null,
) {
    val endpointLabel: String
        get() = when {
            username.isNotBlank() && host.isNotBlank() -> "$username@$host:$port"
            host.isNotBlank() -> "$host:$port"
            else -> ""
        }

    fun defaultTitle(): String {
        return when {
            username.isNotBlank() && host.isNotBlank() -> "SSH $username@$host"
            host.isNotBlank() -> "SSH $host"
            else -> "SSH session"
        }
    }
}

class SshSessionManager(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val credentialStore: SshCredentialStore,
    private val appScope: AppScope,
    private val rootManager: RootManager,
) {
    private val _state = MutableStateFlow(SshSessionState())
    val state: StateFlow<SshSessionState> = _state.asStateFlow()

    private var transport: SshTransport? = null
    private var connectionJob: Job? = null
    private var readJob: Job? = null

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    init {
        appScope.launch {
            combine(settingsStore.settingsFlow, credentialStore.state, rootManager.status) { settings, credentials, rootStatus ->
                buildState(settings, credentials, rootStatus, _state.value)
            }.collect { nextState ->
                _state.value = nextState
            }
        }
    }

    suspend fun connect(): Boolean {
        val current = _state.value
        if (!current.enabled || !current.configValid) {
            Log.w(TAG, "Cannot connect: invalid config")
            return false
        }
        if (current.status == SshSessionStatus.CONNECTING || current.status == SshSessionStatus.CONNECTED) {
            Log.w(TAG, "Already connecting or connected")
            return true
        }

        markConnecting()
        connectionJob = appScope.launch {
            val config = SshConnectionConfig(
                host = current.host,
                port = current.port,
                username = current.username,
                authType = current.authType,
                password = credentialStore.getPassword(),
                privateKey = credentialStore.getPrivateKey(),
                passphrase = credentialStore.getPassphrase(),
            )

            val result = SshTransport.connect(config)
            when (result) {
                is SshConnectionResult.Success -> {
                    transport = result.transport
                    markConnected()
                    startReaderLoop()
                    Log.i(TAG, "SSH connected successfully to ${current.endpointLabel}")
                }
                is SshConnectionResult.Failure -> {
                    markDisconnected(result.error)
                    Log.e(TAG, "SSH connection failed: ${result.error}")
                }
            }
        }
        connectionJob?.join()
        return _state.value.status == SshSessionStatus.CONNECTED
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
        readJob?.cancel()
        readJob = null
        transport?.close()
        transport = null
        markDisconnected()
        _output.value = ""
        Log.i(TAG, "SSH disconnected")
    }

    suspend fun send(command: String) {
        val t = transport ?: run {
            appendOutput("# SSH session not active")
            return
        }
        val sanitized = command.trim()
        if (sanitized.isEmpty()) return
        if (!t.isConnected) {
            appendOutput("# SSH connection lost")
            disconnect()
            return
        }
        withContext(Dispatchers.IO) {
            runCatching {
                t.outputStream.write("$sanitized\n".toByteArray())
                t.outputStream.flush()
            }.onFailure { e ->
                Log.e(TAG, "Failed to send command", e)
                appendOutput("# Failed to send: ${e.message}")
            }
        }
    }

    private fun startReaderLoop() {
        val t = transport ?: return
        readJob = appScope.launch {
            withContext(Dispatchers.IO) {
                val buffer = ByteArray(4096)
                while (isActive && t.isConnected) {
                    runCatching {
                        val read = t.inputStream.read(buffer)
                        if (read > 0) {
                            val text = String(buffer, 0, read)
                            withContext(Dispatchers.Main) {
                                appendOutput(text)
                            }
                        }
                    }.onFailure { e ->
                        if (isActive) {
                            Log.w(TAG, "Read error", e)
                            withContext(Dispatchers.Main) {
                                disconnect()
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    private fun appendOutput(text: String) {
        val current = _output.value
        val updated = current + text
        _output.value = updated.trimToMaxLines(MAX_OUTPUT_LINES)
    }

    private companion object {
        private const val MAX_OUTPUT_LINES = 5000
    }

    private fun String.trimToMaxLines(maxLines: Int): String {
        var value = this
        while (value.lineCount() > maxLines) {
            val firstNl = value.indexOf('\n')
            if (firstNl < 0) return value
            value = value.substring(firstNl + 1)
        }
        return value
    }

    private fun String.lineCount(): Int {
        if (isEmpty()) return 0
        var count = 1
        for (i in indices) {
            if (this[i] == '\n') count++
        }
        return count
    }

    fun markConnecting() {
        val current = _state.value
        if (!current.enabled || !current.configValid) return
        _state.value = current.copy(status = SshSessionStatus.CONNECTING, error = null)
    }

    fun markConnected(title: String = _state.value.defaultTitle()) {
        val current = _state.value
        val resolvedTitle = title.ifBlank { current.defaultTitle() }
        rootManager.setTerminalSession(
            TerminalSessionState(
                isActive = true,
                transport = TerminalSessionTransport.SSH,
                title = resolvedTitle,
            )
        )
        startKeepAliveService()
        _state.value = current.copy(
            status = SshSessionStatus.CONNECTED,
            isActive = true,
            title = resolvedTitle,
            error = null,
        )
    }

    fun markDisconnected(error: String? = null) {
        val wasActiveSsh = rootManager.status.value.terminalSession.let {
            it.isActive && it.transport == TerminalSessionTransport.SSH
        }
        if (wasActiveSsh) {
            rootManager.clearTerminalSession()
            stopKeepAliveService()
        }
        val current = _state.value
        val nextStatus = if (error.isNullOrBlank()) fallbackStatus(current) else SshSessionStatus.ERROR
        _state.value = current.copy(
            status = nextStatus,
            isActive = false,
            title = current.defaultTitle(),
            error = error?.takeIf { nextStatus == SshSessionStatus.ERROR },
        )
    }

    fun clearError() {
        val current = _state.value
        _state.value = current.copy(
            status = fallbackStatus(current),
            error = null,
        )
    }

    private fun buildState(
        settings: me.rerere.rikkahub.data.datastore.Settings,
        credentials: SshCredentialState,
        rootStatus: RootStatus,
        previous: SshSessionState,
    ): SshSessionState {
        val enabled = settings.sshEnabled
        val host = settings.sshHost.trim()
        val port = settings.sshPort
        val username = settings.sshUsername.trim()
        val authType = settings.sshAuthType
        val activeSession = rootStatus.terminalSession.takeIf {
            it.isActive && it.transport == TerminalSessionTransport.SSH
        }
        val hasCredential = when (authType) {
            SshAuthType.PASSWORD -> credentials.hasPassword
            SshAuthType.PRIVATE_KEY -> credentials.hasPrivateKey
        }
        val configValid = enabled &&
            host.isNotBlank() &&
            username.isNotBlank() &&
            port in 1..65535 &&
            credentials.storageReady &&
            hasCredential
        val shouldDropActiveSession = activeSession != null && (!enabled || !configValid)
        if (shouldDropActiveSession) {
            rootManager.clearTerminalSession()
            stopKeepAliveService()
        }
        val effectiveActiveSession = if (shouldDropActiveSession) null else activeSession
        val configChanged = previous.enabled != enabled ||
            previous.host != host ||
            previous.port != port ||
            previous.username != username ||
            previous.authType != authType ||
            previous.storageReady != credentials.storageReady ||
            previous.hasCredential != hasCredential
        val title = effectiveActiveSession?.title?.ifBlank {
            previous.copy(host = host, port = port, username = username).defaultTitle()
        } ?: previous.copy(host = host, port = port, username = username).defaultTitle()
        val nextStatus = when {
            effectiveActiveSession != null -> SshSessionStatus.CONNECTED
            !enabled -> SshSessionStatus.DISABLED
            !configValid -> SshSessionStatus.INCOMPLETE_CONFIG
            previous.status == SshSessionStatus.CONNECTING -> SshSessionStatus.CONNECTING
            previous.status == SshSessionStatus.ERROR && !configChanged && !previous.error.isNullOrBlank() -> SshSessionStatus.ERROR
            else -> SshSessionStatus.READY
        }
        return previous.copy(
            enabled = enabled,
            host = host,
            port = port,
            username = username,
            authType = authType,
            configValid = configValid,
            storageReady = credentials.storageReady,
            hasCredential = hasCredential,
            status = nextStatus,
            isActive = effectiveActiveSession != null,
            title = title,
            error = previous.error.takeIf { nextStatus == SshSessionStatus.ERROR },
        )
    }

    private fun fallbackStatus(state: SshSessionState): SshSessionStatus {
        return when {
            !state.enabled -> SshSessionStatus.DISABLED
            !state.configValid -> SshSessionStatus.INCOMPLETE_CONFIG
            else -> SshSessionStatus.READY
        }
    }

    private fun startKeepAliveService() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RootTerminalService::class.java).apply {
                action = RootTerminalService.ACTION_START
            }
        )
    }

    private fun stopKeepAliveService() {
        context.stopService(Intent(context, RootTerminalService::class.java))
    }
}
