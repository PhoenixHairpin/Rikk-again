package me.rerere.rikkahub.data.root

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.rerere.rikkahub.service.RootTerminalService

class RootTerminalSessionManager(
    private val context: Context,
    private val rootManager: RootManager,
) {
    companion object {
        private const val MAX_OUTPUT_LINES = 5000
        private const val ROOT_TERMINAL_TITLE = "Root terminal"
    }

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    fun markActive(active: Boolean) {
        if (active) {
            rootManager.setTerminalSession(
                TerminalSessionState(
                    isActive = true,
                    transport = TerminalSessionTransport.LOCAL_ROOT,
                    title = ROOT_TERMINAL_TITLE,
                )
            )
        } else {
            rootManager.clearTerminalSession()
        }
    }

    suspend fun startSession() {
        rootManager.refreshStatus()
        val status = rootManager.status.value
        if (!status.suAvailable || status.grantState != RootGrantState.GRANTED || !status.shellReady) {
            appendLine("# root session unavailable")
            return
        }
        val session = status.terminalSession
        if (session.isActive && session.transport == TerminalSessionTransport.LOCAL_ROOT) return
        if (session.isActive) {
            appendLine("# another terminal session is already active")
            return
        }
        markActive(true)
        startKeepAliveService()
        appendLine("# root session started")
    }

    suspend fun send(command: String) {
        val sanitizedCommand = command.trim()
        if (sanitizedCommand.isEmpty()) return
        val session = rootManager.status.value.terminalSession
        if (!session.isActive || session.transport != TerminalSessionTransport.LOCAL_ROOT) {
            appendLine("# root session is not active")
            return
        }
        appendLine("$ $sanitizedCommand")
        val result = rootManager.exec(sanitizedCommand, 30)
        if (result.stdout.isNotBlank()) appendLine(result.stdout)
        if (result.stderr.isNotBlank()) appendLine("stderr: " + result.stderr)
        appendLine("# exit code: ${result.exitCode}")
    }

    fun clear() {
        _output.value = ""
    }

    fun stopSession() {
        val session = rootManager.status.value.terminalSession
        if (!session.isActive || session.transport != TerminalSessionTransport.LOCAL_ROOT) return
        appendLine("# root session closed")
        markActive(false)
        stopKeepAliveService()
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

    private fun appendLine(line: String) {
        val updated = buildString {
            if (_output.value.isNotBlank()) {
                append(_output.value)
                append('\n')
            }
            append(line)
        }
        _output.value = updated.trimToMaxLines(MAX_OUTPUT_LINES)
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
}
