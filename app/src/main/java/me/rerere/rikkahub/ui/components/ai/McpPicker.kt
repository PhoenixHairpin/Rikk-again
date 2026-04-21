package me.rerere.rikkahub.ui.components.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Alert01
import me.rerere.hugeicons.stroke.Code
import me.rerere.hugeicons.stroke.Earth
import me.rerere.hugeicons.stroke.Icon1stBracket
import me.rerere.hugeicons.stroke.McpServer
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpServerConfig
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.ai.tools.LocalToolOption
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.root.RootGrantState
import me.rerere.rikkahub.data.root.RootManager
import me.rerere.rikkahub.data.root.SshSessionManager
import me.rerere.rikkahub.data.root.SshSessionStatus
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.ToggleSurface
import org.koin.compose.koinInject

@Composable
fun McpPickerButton(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showMcpPicker by remember { mutableStateOf(false) }
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    val enabledServers = servers.fastFilter {
        it.commonOptions.enable && assistant.mcpServers.contains(it.id)
    }

    // Calculate status summary
    val connectedCount = enabledServers.count { s ->
        status[s.id] == McpStatus.Connected
    }
    val connectingCount = enabledServers.count { s ->
        status[s.id] == McpStatus.Connecting || status[s.id] is McpStatus.Reconnecting
    }
    val errorCount = enabledServers.count { s ->
        status[s.id] is McpStatus.Error
    }

    ToggleSurface(
        modifier = modifier,
        checked = assistant.mcpServers.isNotEmpty(),
        onClick = {
            showMcpPicker = true
        }
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        imageVector = HugeIcons.McpServer,
                        contentDescription = stringResource(R.string.mcp_picker_title),
                    )
                }
            }

            // Show status summary with colored dots
            if (enabledServers.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Connected indicator (green dot)
                    if (connectedCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    // Connecting indicator (amber dot)
                    if (connectingCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                    }
                    // Error indicator (red dot)
                    if (errorCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }
        }
    }
    if (showMcpPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMcpPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.mcp_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                AnimatedVisibility(loading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        LinearWavyProgressIndicator()
                        Text(
                            text = stringResource(id = R.string.mcp_picker_syncing),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                McpPicker(
                    assistant = assistant,
                    servers = servers,
                    onUpdateAssistant = {
                        onUpdateAssistant(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun McpPicker(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    val mcpManager = koinInject<McpManager>()
    val rootManager = koinInject<RootManager>()
    val sshManager = koinInject<SshSessionManager>()

    val rootStatus by rootManager.status.collectAsStateWithLifecycle()
    val sshStatus by sshManager.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Local Tools section header
        item {
            Text(
                text = stringResource(R.string.mcp_picker_local_tools),
                style = MaterialTheme.typography.labelLarge,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        // SSH local tool item
        item {
            val sshEnabled = assistant.localTools.contains(LocalToolOption.Ssh)
            Card {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.Earth, null)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.mcp_picker_ssh),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = when (sshStatus.status) {
                                SshSessionStatus.DISABLED -> stringResource(R.string.mcp_picker_ssh_disconnected)
                                SshSessionStatus.INCOMPLETE_CONFIG -> stringResource(R.string.mcp_picker_ssh_disconnected)
                                SshSessionStatus.READY -> stringResource(R.string.mcp_picker_ssh_disconnected)
                                SshSessionStatus.CONNECTING -> stringResource(R.string.mcp_picker_ssh_connecting)
                                SshSessionStatus.CONNECTED -> stringResource(R.string.mcp_picker_ssh_connected)
                                SshSessionStatus.ERROR -> stringResource(R.string.mcp_picker_ssh_error, sshStatus.error ?: "")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = sshEnabled,
                        onCheckedChange = { enabled ->
                            val newLocalTools = if (enabled) {
                                assistant.localTools + LocalToolOption.Ssh
                            } else {
                                assistant.localTools - LocalToolOption.Ssh
                            }
                            onUpdateAssistant(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            }
        }

        // ShellExec (Root Shell) local tool item
        item {
            val shellExecEnabled = assistant.localTools.contains(LocalToolOption.ShellExec)
            val rootAvailable = rootStatus.grantState == RootGrantState.GRANTED && rootStatus.suAvailable && rootStatus.shellReady
            Card {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HugeIcons.Code, null)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.mcp_picker_shell_exec),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = if (rootAvailable) {
                                stringResource(R.string.mcp_picker_shell_exec_available)
                            } else {
                                stringResource(R.string.mcp_picker_shell_exec_unavailable)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 2
                        )
                    }
                    Switch(
                        checked = shellExecEnabled && rootAvailable,
                        enabled = rootAvailable,
                        onCheckedChange = { enabled ->
                            val newLocalTools = if (enabled) {
                                assistant.localTools + LocalToolOption.ShellExec
                            } else {
                                assistant.localTools - LocalToolOption.ShellExec
                            }
                            onUpdateAssistant(assistant.copy(localTools = newLocalTools))
                        }
                    )
                }
            }
        }

        // MCP Servers section header
        if (servers.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.mcp_picker_servers),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalContentColor.current.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        items(servers.fastFilter { it.commonOptions.enable }) { server ->
            val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
            Card {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (status) {
                        McpStatus.Idle -> Icon(HugeIcons.Icon1stBracket, null)
                        McpStatus.Connecting -> CircularProgressIndicator(
                            modifier = Modifier.size(
                                24.dp
                            )
                        )

                        McpStatus.Connected -> Icon(HugeIcons.McpServer, null)
                        is McpStatus.Reconnecting -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                        is McpStatus.Error -> Icon(HugeIcons.Alert01, null)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = server.commonOptions.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = when (val s = status) {
                                is McpStatus.Idle -> stringResource(R.string.mcp_status_idle)
                                is McpStatus.Connecting -> stringResource(R.string.mcp_status_connecting)
                                is McpStatus.Connected -> stringResource(R.string.mcp_status_connected)
                                is McpStatus.Reconnecting -> stringResource(R.string.mcp_status_reconnecting, s.attempt, s.maxAttempts)
                                is McpStatus.Error -> stringResource(R.string.mcp_status_error, s.message)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.8f),
                            maxLines = 5
                        )
                        if (status == McpStatus.Connected) {
                            val tools = server.commonOptions.tools
                            val enabledTools = tools.fastFilter { it.enable }
                            Tag(
                                type = TagType.INFO
                            ) {
                                Text(stringResource(R.string.mcp_tools_count, enabledTools.size, tools.size))
                            }
                        }
                    }
                    Switch(
                        checked = server.id in assistant.mcpServers,
                        onCheckedChange = {
                            if (it) {
                                val newServers = assistant.mcpServers.toMutableSet()
                                newServers.add(server.id)
                                newServers.removeIf { servers.none { s -> s.id == server.id } } // remove invalid servers
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.toSet()
                                    )
                                )
                            } else {
                                val newServers = assistant.mcpServers.toMutableSet()
                                newServers.remove(server.id)
                                newServers.removeIf { servers.none { s -> s.id == server.id } } //  remove invalid servers
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.toSet()
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
