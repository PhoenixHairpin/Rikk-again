package me.rerere.rikkahub.ui.pages.root

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.root.SshSessionStatus
import me.rerere.rikkahub.data.root.SshSessionManager
import me.rerere.rikkahub.data.root.UnifiedTerminalManager
import me.rerere.rikkahub.data.root.UnifiedTerminalState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.koinInject

@Composable
fun RootTerminalPage() {
    val unified: UnifiedTerminalManager = koinInject()
    val sshManager: SshSessionManager = koinInject()
    val terminalState by unified.state.collectAsStateWithLifecycle()
    val sshState by sshManager.state.collectAsStateWithLifecycle()
    val output by unified.output.collectAsStateWithLifecycle(initialValue = "")
    var input by remember { mutableStateOf("") }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(sshState.status) {
        if (sshState.enabled && sshState.configValid && sshState.status == SshSessionStatus.READY) {
            unified.connectSsh()
        } else if (!sshState.enabled || !sshState.configValid) {
            unified.startSession()
        }
    }

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(terminalState.title.ifBlank { "Terminal" }) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = terminalState.error?.let { "# Error: $it\n$output" } ?: output,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                fontFamily = FontFamily.Monospace,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text(
                                "$ ",
                                color = Color.Gray,
                            )
                        }
                        inner()
                    }
                )
                Button(
                    onClick = {
                        val command = input
                        input = ""
                        scope.launch {
                            unified.send(command)
                        }
                    },
                    enabled = input.isNotBlank() && terminalState.isActive,
                ) {
                    Text("Run")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { unified.clear() }) {
                    Text("Clear")
                }
                OutlinedButton(onClick = { unified.stopSession() }) {
                    Text("Close")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        unified.stopSession()
                        if (sshState.enabled && sshState.configValid) {
                            unified.connectSsh()
                        } else {
                            unified.startSession()
                        }
                    }
                }) {
                    Text("Restart")
                }
            }
        }
    }
}