package me.rerere.rikkahub.data.root

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.rerere.rikkahub.AppScope

private const val TAG = "UnifiedTerminal"

sealed class TerminalKind {
    data object LocalRoot : TerminalKind()
    data class Ssh(val endpoint: String) : TerminalKind()
}

data class UnifiedTerminalState(
    val kind: TerminalKind? = null,
    val isActive: Boolean = false,
    val title: String = "",
    val error: String? = null,
) {
    val label: String
        get() = when (kind) {
            is TerminalKind.LocalRoot -> "Local Root"
            is TerminalKind.Ssh -> "SSH: ${kind.endpoint}"
            null -> "No session"
        }
}

class UnifiedTerminalManager(
    private val rootTerminal: RootTerminalSessionManager,
    private val sshSessionManager: SshSessionManager,
    private val rootManager: RootManager,
    private val appScope: AppScope,
) {
    private val _state = MutableStateFlow(UnifiedTerminalState())
    val state: StateFlow<UnifiedTerminalState> = _state.asStateFlow()

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private var outputObserverJob: Job? = null

    init {
        outputObserverJob = appScope.launch(Dispatchers.Main) {
            combine(
                rootTerminal.output,
                sshSessionManager.output,
                rootManager.status,
            ) { rootOutput, sshOutput, rootStatus ->
                val session = rootStatus.terminalSession
                when {
                    session.isActive && session.transport == TerminalSessionTransport.SSH -> sshOutput
                    session.isActive && session.transport == TerminalSessionTransport.LOCAL_ROOT -> rootOutput
                    else -> ""
                }
            }.distinctUntilChanged().collect { combined ->
                _output.value = combined
            }
        }
    }

    suspend fun startSession() {
        val sshState = sshSessionManager.state.value
        if (sshState.isActive && sshState.status == SshSessionStatus.CONNECTED) {
            Log.i(TAG, "SSH session already active, skipping start")
            _state.value = UnifiedTerminalState(
                kind = TerminalKind.Ssh(sshState.endpointLabel),
                isActive = true,
                title = sshState.title,
            )
            return
        }
        rootTerminal.startSession()
        val rootStatus = rootManager.status.value
        if (rootStatus.terminalSession.isActive) {
            _state.value = UnifiedTerminalState(
                kind = TerminalKind.LocalRoot,
                isActive = true,
                title = rootStatus.terminalSession.title.ifBlank { "Root terminal" },
            )
        }
    }

    suspend fun connectSsh(): Boolean {
        val sshState = sshSessionManager.state.value
        if (!sshState.enabled || !sshState.configValid) {
            Log.w(TAG, "SSH not configured properly")
            _state.value = UnifiedTerminalState(
                error = "SSH not configured",
            )
            return false
        }
        if (sshState.status == SshSessionStatus.CONNECTED) {
            Log.i(TAG, "SSH already connected")
            _state.value = UnifiedTerminalState(
                kind = TerminalKind.Ssh(sshState.endpointLabel),
                isActive = true,
                title = sshState.title,
            )
            return true
        }
        val result = sshSessionManager.connect()
        val newState = sshSessionManager.state.value
        if (result) {
            _state.value = UnifiedTerminalState(
                kind = TerminalKind.Ssh(newState.endpointLabel),
                isActive = true,
                title = newState.title,
            )
        } else {
            _state.value = UnifiedTerminalState(
                kind = TerminalKind.Ssh(newState.endpointLabel),
                isActive = false,
                error = newState.error ?: "SSH connection failed",
            )
        }
        return result
    }

    suspend fun send(command: String) {
        val current = _state.value
        when (current.kind) {
            is TerminalKind.LocalRoot -> rootTerminal.send(command)
            is TerminalKind.Ssh -> sshSessionManager.send(command)
            null -> Log.w(TAG, "No active session, cannot send command")
        }
    }

    fun clear() {
        val current = _state.value
        when (current.kind) {
            is TerminalKind.LocalRoot -> rootTerminal.clear()
            is TerminalKind.Ssh -> {}
            null -> {}
        }
    }

    fun stopSession() {
        val current = _state.value
        when (current.kind) {
            is TerminalKind.LocalRoot -> rootTerminal.stopSession()
            is TerminalKind.Ssh -> sshSessionManager.disconnect()
            null -> {}
        }
        _state.value = UnifiedTerminalState()
    }

    fun disconnectSsh() {
        sshSessionManager.disconnect()
        _state.value = UnifiedTerminalState()
    }
}