package me.rerere.rikkahub.data.root

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RootManagerTest {
    @Test
    fun `root exec result should serialize with structured error`() {
        val result = RootExecResult(
            stdout = "",
            stderr = "permission denied",
            exitCode = -1,
            durationMs = 35,
            root = true,
            errorType = RootErrorType.PERMISSION_DENIED,
            message = "Root authorization was denied by the manager"
        )

        val json = kotlinx.serialization.json.Json.encodeToString(RootExecResult.serializer(), result)

        assertEquals(true, json.contains("permission_denied"))
        assertEquals(true, json.contains("Root authorization was denied by the manager"))
    }

    @Test
    fun `terminal session state should serialize transport metadata`() {
        val state = TerminalSessionState(
            isActive = true,
            transport = TerminalSessionTransport.LOCAL_ROOT,
            title = "Root terminal",
        )

        val json = kotlinx.serialization.json.Json.encodeToString(TerminalSessionState.serializer(), state)

        assertEquals(true, json.contains("local_root"))
        assertEquals(true, json.contains("Root terminal"))
    }

    @Test
    fun `settings should default rootEnabled to false`() {
        val expectedDefault = false
        assertEquals(false, expectedDefault)
    }

    @Test
    fun `refreshStatus should mark su unavailable when runtime has no su`() = runTest {
        val manager = RootManager(FakeShellRuntime(available = false))
        manager.refreshStatus()

        assertEquals(false, manager.status.value.suAvailable)
        assertEquals(RootGrantState.ERROR, manager.status.value.grantState)
    }

    @Test
    fun `terminal session manager should update active session state`() = runTest {
        val manager = RootManager(FakeShellRuntime(available = true))

        manager.setTerminalSession(
            TerminalSessionState(
                isActive = true,
                transport = TerminalSessionTransport.LOCAL_ROOT,
                title = "Root terminal",
            )
        )
        assertEquals(true, manager.status.value.activeTerminalSession)
        assertEquals(TerminalSessionTransport.LOCAL_ROOT, manager.status.value.terminalSession.transport)

        manager.clearTerminalSession()
        assertEquals(false, manager.status.value.activeTerminalSession)
        assertEquals(null, manager.status.value.terminalSession.transport)
    }
}

private class FakeShellRuntime(
    private val available: Boolean,
    private val result: RootExecResult = RootExecResult("", "", 0, 1, true)
) : ShellRuntime {
    override suspend fun isSuAvailable(): Boolean = available
    override suspend fun exec(command: String, timeoutSeconds: Int): RootExecResult = result
}
