package me.rerere.rikkahub.data.root

import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.system.measureTimeMillis

class LibsuShellRuntime : ShellRuntime {
    private var shell: Shell? = null

    private fun getOrCreateShell(): Shell {
        val current = shell
        if (current != null && current.isAlive) {
            return current
        }
        val newShell = Shell.Builder.create().build()
        shell = newShell
        return newShell
    }

    override suspend fun isSuAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val s = getOrCreateShell()
            s.isRoot
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exec(command: String, timeoutSeconds: Int): RootExecResult = withContext(Dispatchers.IO) {
        val stdout = mutableListOf<String>()
        val stderr = mutableListOf<String>()
        var code = -1
        var timedOut = false
        val duration = measureTimeMillis {
            try {
                withTimeout(timeoutSeconds * 1000L) {
                    val shell = getOrCreateShell()
                    val result = shell.newJob().add(command).to(stdout, stderr).exec()
                    code = result.code
                }
            } catch (e: TimeoutCancellationException) {
                timedOut = true
                stderr.add("Command timed out after ${timeoutSeconds}s")
            } catch (e: Exception) {
                stderr.add(e.message ?: "Unknown error")
                code = -1
            }
        }
        RootExecResult(
            stdout = stdout.joinToString("\n"),
            stderr = stderr.joinToString("\n"),
            exitCode = code,
            durationMs = duration,
            root = true,
            errorType = when {
                code == 0 -> null
                timedOut -> RootErrorType.TIMEOUT
                else -> RootErrorType.EXEC_FAILED
            },
            message = when {
                code == 0 -> null
                timedOut -> "Command timed out after ${timeoutSeconds}s"
                else -> "Command exited with code $code"
            }
        )
    }
}
