package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.root.RootExecResult
import me.rerere.rikkahub.data.root.RootGrantState
import me.rerere.rikkahub.data.root.RootManager
import me.rerere.rikkahub.data.root.ShellRuntime
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.kotlin.mock

private class FakeShellRuntime(private val available: Boolean = true) : ShellRuntime {
    override suspend fun isSuAvailable(): Boolean = available
    override suspend fun exec(command: String, timeoutSeconds: Int): RootExecResult =
        RootExecResult("root", "", 0, 12, true)
}

class LocalToolsRootTest {
    @Test
    fun `getTools should include shell_exec when rootEnabled and status granted`() = runTest {
        val manager = RootManager(FakeShellRuntime())
        manager.refreshStatus()

        val settingsFlow = MutableStateFlow(Settings.dummy().copy(rootEnabled = true))
        val localTools = LocalTools(
            context = mock(),
            eventBus = mock(),
            rootManager = manager,
            settingsFlow = settingsFlow
        )

        val tools = localTools.getTools(emptyList())
        val shellExec = tools.find { it.name == "shell_exec" }
        assertTrue("shell_exec should be present when root is enabled and granted", shellExec != null)
    }

    @Test
    fun `getTools should NOT include shell_exec when rootEnabled is false`() = runTest {
        val manager = RootManager(FakeShellRuntime())
        manager.refreshStatus()

        val settingsFlow = MutableStateFlow(Settings.dummy().copy(rootEnabled = false))
        val localTools = LocalTools(
            context = mock(),
            eventBus = mock(),
            rootManager = manager,
            settingsFlow = settingsFlow
        )

        val tools = localTools.getTools(emptyList())
        val shellExec = tools.find { it.name == "shell_exec" }
        assertFalse("shell_exec should NOT be present when rootEnabled is false", shellExec != null)
    }
}
