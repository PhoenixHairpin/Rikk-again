package me.rerere.rikkahub.ui.pages.setting

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.SshAuthType
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.root.RootManager
import me.rerere.rikkahub.data.root.SshSessionManager
import me.rerere.rikkahub.data.root.SshSessionStatus
import me.rerere.rikkahub.data.root.TerminalSessionTransport
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.CustomColors
import org.koin.compose.viewmodel.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingRootPage(vm: SettingVM = koinViewModel()) {
    val rootManager: RootManager = koinInject()
    val sshSessionManager: SshSessionManager = koinInject()
    val settingsStore: SettingsStore = koinInject()
    val status by rootManager.status.collectAsStateWithLifecycle()
    val sshState by sshSessionManager.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val credentialState by vm.sshCredentials.collectAsStateWithLifecycle()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var sshHostText by remember(settings.sshHost) {
        mutableStateOf(settings.sshHost)
    }
    var sshPortText by remember(settings.sshPort) {
        mutableStateOf(settings.sshPort.toString())
    }
    var sshUsernameText by remember(settings.sshUsername) {
        mutableStateOf(settings.sshUsername)
    }
    var sshPasswordText by remember { mutableStateOf("") }
    var sshPrivateKeyText by remember { mutableStateOf("") }
    var sshPassphraseText by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passphraseVisible by remember { mutableStateOf(false) }
    LaunchedEffect(credentialState.storageReady, credentialState.hasPassword) {
        if (!credentialState.hasPassword && sshPasswordText.isNotEmpty()) {
            sshPasswordText = ""
        }
    }
    LaunchedEffect(credentialState.storageReady, credentialState.hasPrivateKey) {
        if (!credentialState.hasPrivateKey && sshPrivateKeyText.isNotEmpty()) {
            sshPrivateKeyText = ""
        }
    }
    LaunchedEffect(credentialState.storageReady, credentialState.hasPassphrase) {
        if (!credentialState.hasPassphrase && sshPassphraseText.isNotEmpty()) {
            sshPassphraseText = ""
        }
    }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_page_root)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_root_enable)) },
                        supportingContent = { Text(stringResource(R.string.setting_root_enable_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.rootEnabled,
                                onCheckedChange = { checked ->
                                    vm.updateSettings(settings.copy(rootEnabled = checked))
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_root_status)) },
                        supportingContent = {
                            Text("su=${status.suAvailable}, grant=${status.grantState}, shell=${status.shellReady}")
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_root_manager)) },
                        supportingContent = { Text(status.managerType.name) }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_root_active_session)) },
                        supportingContent = {
                            Text(
                                if (!status.terminalSession.isActive) {
                                    stringResource(R.string.setting_root_no_session)
                                } else {
                                    val transport = when (status.terminalSession.transport) {
                                        TerminalSessionTransport.LOCAL_ROOT -> "local_root"
                                        TerminalSessionTransport.SSH -> "ssh"
                                        null -> "unknown"
                                    }
                                    "transport=$transport, title=${status.terminalSession.title.ifBlank { "unnamed" }}"
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_keep_alive_enable)) },
                        supportingContent = { Text(stringResource(R.string.setting_keep_alive_enable_desc)) },
                    )
                }
            }
            item {
                CardGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_enable)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_enable_desc)) },
                        trailingContent = {
                            Switch(
                                checked = settings.sshEnabled,
                                onCheckedChange = { checked ->
                                    vm.updateSettings(settings.copy(sshEnabled = checked))
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_host)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_host_desc)) },
                        trailingContent = {
                            TextField(
                                value = sshHostText,
                                onValueChange = { value ->
                                    sshHostText = value
                                    vm.updateSettings(settings.copy(sshHost = value.trim()))
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp),
                                enabled = settings.sshEnabled,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_port)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_port_desc)) },
                        trailingContent = {
                            TextField(
                                value = sshPortText,
                                onValueChange = { value ->
                                    sshPortText = value.filter { it.isDigit() }
                                    val port = sshPortText.toIntOrNull()
                                    if (port != null && port in 1..65535) {
                                        vm.updateSettings(settings.copy(sshPort = port))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                isError = sshPortText.toIntOrNull()?.let { it !in 1..65535 } ?: true,
                                modifier = Modifier.width(110.dp),
                                enabled = settings.sshEnabled,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_username)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_username_desc)) },
                        trailingContent = {
                            TextField(
                                value = sshUsernameText,
                                onValueChange = { value ->
                                    sshUsernameText = value
                                    vm.updateSettings(settings.copy(sshUsername = value.trim()))
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp),
                                enabled = settings.sshEnabled,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_auth)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_auth_desc)) },
                        trailingContent = {
                            Button(
                                onClick = {
                                    vm.updateSettings(
                                        settings.copy(
                                            sshAuthType = when (settings.sshAuthType) {
                                                SshAuthType.PASSWORD -> SshAuthType.PRIVATE_KEY
                                                SshAuthType.PRIVATE_KEY -> SshAuthType.PASSWORD
                                            }
                                        )
                                    )
                                },
                                modifier = Modifier.width(140.dp),
                                enabled = settings.sshEnabled,
                            ) {
                                Text(
                                    when (settings.sshAuthType) {
                                        SshAuthType.PASSWORD -> stringResource(R.string.setting_ssh_auth_password)
                                        SshAuthType.PRIVATE_KEY -> stringResource(R.string.setting_ssh_auth_private_key)
                                    }
                                )
                            }
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_credentials)) },
                        supportingContent = {
                            Text(
                                when {
                                    !credentialState.storageReady -> credentialState.error ?: stringResource(R.string.setting_ssh_session_storage_unavailable)
                                    settings.sshAuthType == SshAuthType.PASSWORD && credentialState.hasPassword -> stringResource(R.string.setting_ssh_password_saved)
                                    settings.sshAuthType == SshAuthType.PRIVATE_KEY && credentialState.hasPrivateKey && credentialState.hasPassphrase -> stringResource(R.string.setting_ssh_private_key_saved)
                                    settings.sshAuthType == SshAuthType.PRIVATE_KEY && credentialState.hasPrivateKey -> stringResource(R.string.setting_ssh_private_key_saved)
                                    else -> stringResource(R.string.setting_ssh_no_credential)
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_password)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_password_desc)) },
                        trailingContent = {
                            TextField(
                                value = sshPasswordText,
                                onValueChange = { value ->
                                    sshPasswordText = value
                                    vm.updateSshPassword(value)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = if (passwordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                placeholder = {
                                    if (credentialState.hasPassword) {
                                        Text(stringResource(R.string.setting_ssh_saved_securely))
                                    }
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) HugeIcons.ViewOff else HugeIcons.View,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp),
                                enabled = settings.sshEnabled && settings.sshAuthType == SshAuthType.PASSWORD && credentialState.storageReady,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_private_key)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_private_key_desc)) },
                        trailingContent = {
                            Column(
                                modifier = Modifier.width(220.dp)
                            ) {
                                TextField(
                                    value = sshPrivateKeyText,
                                    onValueChange = { value ->
                                        sshPrivateKeyText = value
                                        vm.updateSshPrivateKey(value)
                                    },
                                    placeholder = {
                                        if (credentialState.hasPrivateKey) {
                                            Text(stringResource(R.string.setting_ssh_saved_securely))
                                        }
                                    },
                                    minLines = 4,
                                    maxLines = 8,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp),
                                    enabled = settings.sshEnabled && settings.sshAuthType == SshAuthType.PRIVATE_KEY && credentialState.storageReady,
                                    shape = CircleShape,
                                    colors = TextFieldDefaults.colors(
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        errorIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                    ),
                                )
                            }
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_passphrase)) },
                        supportingContent = { Text(stringResource(R.string.setting_ssh_passphrase_desc)) },
                        trailingContent = {
                            TextField(
                                value = sshPassphraseText,
                                onValueChange = { value ->
                                    sshPassphraseText = value
                                    vm.updateSshPassphrase(value)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = if (passphraseVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                placeholder = {
                                    if (credentialState.hasPassphrase) {
                                        Text(stringResource(R.string.setting_ssh_saved_securely))
                                    }
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                        Icon(
                                            imageVector = if (passphraseVisible) HugeIcons.ViewOff else HugeIcons.View,
                                            contentDescription = null,
                                        )
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.width(180.dp),
                                enabled = settings.sshEnabled && settings.sshAuthType == SshAuthType.PRIVATE_KEY && credentialState.storageReady,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_endpoint)) },
                        supportingContent = {
                            Text(
                                sshState.endpointLabel.ifBlank {
                                    if (settings.sshEnabled) stringResource(R.string.setting_ssh_endpoint_host_missing) else stringResource(R.string.setting_ssh_endpoint_disabled)
                                }
                            )
                        }
                    )
                    item(
                        headlineContent = { Text(stringResource(R.string.setting_ssh_session)) },
                        supportingContent = {
                            Text(
                                when (sshState.status) {
                                    SshSessionStatus.DISABLED -> stringResource(R.string.setting_ssh_session_disabled)
                                    SshSessionStatus.INCOMPLETE_CONFIG -> when {
                                        !sshState.storageReady -> sshState.error ?: stringResource(R.string.setting_ssh_session_storage_unavailable)
                                        !sshState.hasCredential -> stringResource(R.string.setting_ssh_session_credential_missing)
                                        else -> stringResource(R.string.setting_ssh_session_config_incomplete)
                                    }
                                    SshSessionStatus.READY -> stringResource(R.string.setting_ssh_session_ready) + ": ${sshState.endpointLabel.ifBlank { "missing endpoint" }}"
                                    SshSessionStatus.CONNECTING -> stringResource(R.string.setting_ssh_session_connecting) + ": ${sshState.endpointLabel.ifBlank { "missing endpoint" }}"
                                    SshSessionStatus.CONNECTED -> "transport=ssh, title=${sshState.title.ifBlank { "unnamed" }}"
                                    SshSessionStatus.ERROR -> sshState.error ?: stringResource(R.string.setting_ssh_session_error)
                                }
                            )
                        }
                    )
                }
            }
            item {
                val sshSessionActive = status.terminalSession.isActive &&
                    status.terminalSession.transport == TerminalSessionTransport.SSH
                val sshReady = sshState.status == SshSessionStatus.READY ||
                    sshState.status == SshSessionStatus.CONNECTED ||
                    sshState.status == SshSessionStatus.CONNECTING
                Button(
                    onClick = { nav.navigate(Screen.RootTerminal) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    enabled = !sshSessionActive || sshState.enabled,
                ) {
                    Text(
                        when {
                            sshSessionActive -> stringResource(R.string.setting_ssh_terminal_button_active)
                            settings.sshEnabled && sshReady -> stringResource(R.string.setting_ssh_terminal_button_open)
                            settings.sshEnabled -> stringResource(R.string.setting_ssh_terminal_button_pending)
                            else -> stringResource(R.string.setting_ssh_terminal_button_local)
                        }
                    )
                }
            }
        }
    }
}
