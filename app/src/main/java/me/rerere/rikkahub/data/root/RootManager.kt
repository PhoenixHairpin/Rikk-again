package me.rerere.rikkahub.data.root

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RootManager(
    private val shellRuntime: ShellRuntime,
) {
    companion object { private const val TAG = "RootManager" }
    private val _status = MutableStateFlow(RootStatus())
    val status: StateFlow<RootStatus> = _status.asStateFlow()

    suspend fun refreshStatus() {
        Log.i(TAG, "refreshStatus: starting root check")
        val suAvailable = runCatching { shellRuntime.isSuAvailable() }.getOrDefault(false)
        Log.i(TAG, "refreshStatus: suAvailable=$suAvailable")
        if (!suAvailable) {
            _status.value = RootStatus(
                suAvailable = false,
                grantState = RootGrantState.ERROR,
                managerType = RootManagerType.UNKNOWN,
                shellReady = false,
                terminalSession = _status.value.terminalSession,
            )
            Log.w(TAG, "refreshStatus: su not available, status=ERROR")
            return
        }

        val probe = shellRuntime.exec("id && whoami", timeoutSeconds = 10)
        val output = probe.stdout + "\n" + probe.stderr
        Log.i(TAG, "refreshStatus: probe exitCode=${probe.exitCode}, stdout=${probe.stdout}, stderr=${probe.stderr}")
        _status.value = RootStatus(
            suAvailable = true,
            grantState = if (probe.exitCode == 0) RootGrantState.GRANTED else RootGrantState.DENIED,
            managerType = detectManagerType(output),
            shellReady = probe.exitCode == 0,
            terminalSession = _status.value.terminalSession,
        )
        Log.i(TAG, "refreshStatus: final status=${_status.value}")
    }

    suspend fun exec(command: String, timeoutSeconds: Int = 30): RootExecResult {
        val s = status.value
        Log.i(TAG, "exec: status=$s, command=$command")
        if (!s.suAvailable || s.grantState != RootGrantState.GRANTED || !s.shellReady) {
            Log.w(TAG, "exec: blocked - suAvailable=${s.suAvailable}, grantState=${s.grantState}, shellReady=${s.shellReady}")
            return RootExecResult(
                stdout = "",
                stderr = "su unavailable or not ready",
                exitCode = -1,
                durationMs = 0,
                root = true,
                errorType = when {
                    !s.suAvailable -> RootErrorType.SU_UNAVAILABLE
                    s.grantState != RootGrantState.GRANTED -> RootErrorType.PERMISSION_DENIED
                    else -> RootErrorType.SESSION_LOST
                },
                message = when {
                    !s.suAvailable -> "su executable is unavailable"
                    s.grantState != RootGrantState.GRANTED -> "Root authorization was denied"
                    else -> "Root shell session is unavailable"
                }
            )
        }
        return shellRuntime.exec(command, timeoutSeconds)
    }

    fun setTerminalSession(session: TerminalSessionState) {
        _status.value = _status.value.copy(terminalSession = session)
    }

    fun clearTerminalSession() {
        setTerminalSession(TerminalSessionState())
    }

    fun setTerminalSessionActive(active: Boolean) {
        val current = _status.value.terminalSession
        _status.value = _status.value.copy(
            terminalSession = if (active) {
                current.copy(isActive = true)
            } else {
                TerminalSessionState()
            }
        )
    }

    private fun detectManagerType(text: String): RootManagerType {
        val lower = text.lowercase()
        return when {
            "kernelsu" in lower -> RootManagerType.KERNEL_SU
            "apatch" in lower -> RootManagerType.APATCH
            "magisk" in lower -> RootManagerType.MAGISK
            else -> RootManagerType.UNKNOWN
        }
    }
}
