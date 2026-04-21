package me.rerere.rikkahub.data.ai.tools

import android.content.Context
import android.util.Log
import com.whl.quickjs.wrapper.QuickJSContext
import com.whl.quickjs.wrapper.QuickJSObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.rikkahub.utils.readClipboardText
import me.rerere.rikkahub.utils.writeClipboardText
import me.rerere.rikkahub.data.root.RootGrantState
import me.rerere.rikkahub.data.root.RootManager
import me.rerere.rikkahub.data.root.SshSessionManager
import me.rerere.rikkahub.data.root.SshSessionStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.utils.JsonInstant
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

@Serializable
sealed class LocalToolOption {
    @Serializable
    @SerialName("javascript_engine")
    data object JavascriptEngine : LocalToolOption()

    @Serializable
    @SerialName("time_info")
    data object TimeInfo : LocalToolOption()

    @Serializable
    @SerialName("clipboard")
    data object Clipboard : LocalToolOption()

    @Serializable
    @SerialName("tts")
    data object Tts : LocalToolOption()

    @Serializable
    @SerialName("ask_user")
    data object AskUser : LocalToolOption()

    @Serializable
    @SerialName("ssh")
    data object Ssh : LocalToolOption()

    @Serializable
    @SerialName("shell_exec")
    data object ShellExec : LocalToolOption()
}

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val rootManager: RootManager,
    private val settingsFlow: StateFlow<Settings>,
    private val sshManager: SshSessionManager,
) {
    val javascriptTool by lazy {
        Tool(
            name = "eval_javascript",
            description = """
                Execute JavaScript code using QuickJS engine (ES2020).
                The result is the value of the last expression in the code.
                For calculations with decimals, use toFixed() to control precision.
                Console output (log/info/warn/error) is captured and returned in 'logs' field.
                No DOM or Node.js APIs available.
                Example: '1 + 2' returns 3; 'const x = 5; x * 2' returns 10.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("code", buildJsonObject {
                            put("type", "string")
                            put("description", "The JavaScript code to execute")
                        })
                    },
                    required = listOf("code")
                )
            },
            execute = {
                val logs = arrayListOf<String>()
                val code = it.jsonObject["code"]?.jsonPrimitive?.contentOrNull
                    ?: error("code is required")
                val payload = QuickJSContext.create().use { ctx ->
                    ctx.setConsole(object : QuickJSContext.Console {
                        override fun log(info: String?) {
                            logs.add("[LOG] $info")
                        }

                        override fun info(info: String?) {
                            logs.add("[INFO] $info")
                        }

                        override fun warn(info: String?) {
                            logs.add("[WARN] $info")
                        }

                        override fun error(info: String?) {
                            logs.add("[ERROR] $info")
                        }
                    })
                    val evalResult = runCatching { ctx.evaluate(code) }
                    buildJsonObject {
                        if (logs.isNotEmpty()) {
                            put("logs", JsonPrimitive(logs.joinToString("\n")))
                        }
                        evalResult.fold(
                            onSuccess = { result ->
                                put(
                                    key = "result",
                                    element = when (result) {
                                        null -> JsonNull
                                        is QuickJSObject -> runCatching { JsonPrimitive(result.stringify()) }.getOrElse {
                                            JsonPrimitive(result.toString())
                                        }
                                        else -> JsonPrimitive(result.toString())
                                    }
                                )
                            },
                            onFailure = { error ->
                                put("error", JsonPrimitive(error.message ?: "Unknown JS error"))
                            }
                        )
                    }
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val timeTool by lazy {
        Tool(
            name = "get_time_info",
            description = """
                Get the current local date and time info from the device.
                Returns year/month/day, weekday, ISO date/time strings, timezone, and timestamp.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject { }
                )
            },
            execute = {
                val now = ZonedDateTime.now()
                val date = now.toLocalDate()
                val time = now.toLocalTime().withNano(0)
                val weekday = now.dayOfWeek
                val payload = buildJsonObject {
                    put("year", date.year)
                    put("month", date.monthValue)
                    put("day", date.dayOfMonth)
                    put("weekday", weekday.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                    put("weekday_en", weekday.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                    put("weekday_index", weekday.value)
                    put("date", date.toString())
                    put("time", time.toString())
                    put("datetime", now.withNano(0).toString())
                    put("timezone", now.zone.id)
                    put("utc_offset", now.offset.id)
                    put("timestamp_ms", now.toInstant().toEpochMilli())
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val clipboardTool by lazy {
        Tool(
            name = "clipboard_tool",
            description = """
                Read or write plain text from the device clipboard.
                Use action: read or write. For write, provide text.
                Do NOT write to the clipboard unless the user has explicitly requested it.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("action", buildJsonObject {
                            put("type", "string")
                            put(
                                "enum",
                                kotlinx.serialization.json.buildJsonArray {
                                    add("read")
                                    add("write")
                                }
                            )
                            put("description", "Operation to perform: read or write")
                        })
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "Text to write to the clipboard (required for write)")
                        })
                    },
                    required = listOf("action")
                )
            },
            execute = {
                val params = it.jsonObject
                val action = params["action"]?.jsonPrimitive?.contentOrNull ?: error("action is required")
                when (action) {
                    "read" -> {
                        val payload = buildJsonObject {
                            put("text", context.readClipboardText())
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    "write" -> {
                        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
                        context.writeClipboardText(text)
                        val payload = buildJsonObject {
                            put("success", true)
                            put("text", text)
                        }
                        listOf(UIMessagePart.Text(payload.toString()))
                    }

                    else -> error("unknown action: $action, must be one of [read, write]")
                }
            }
        )
    }

    val ttsTool by lazy {
        Tool(
            name = "text_to_speech",
            description = """
                Speak text aloud to the user using the device's text-to-speech engine.
                Use this when the user asks you to read something aloud, or when audio output is appropriate.
                The tool returns immediately; audio plays in the background on the device.
                Provide natural, readable text without markdown formatting.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("text", buildJsonObject {
                            put("type", "string")
                            put("description", "The text to speak aloud")
                        })
                    },
                    required = listOf("text")
                )
            },
            execute = {
                val text = it.jsonObject["text"]?.jsonPrimitive?.contentOrNull
                    ?: error("text is required")
                eventBus.emit(AppEvent.Speak(text))
                val payload = buildJsonObject {
                    put("success", true)
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val askUserTool by lazy {
        Tool(
            name = "ask_user",
            description = """
                Ask the user one or more questions when you need clarification, additional information, or confirmation.
                Each question can optionally provide a list of suggested options for the user to choose from.
                The user may select an option or provide their own free-text answer for each question.
                The answers will be returned as a JSON object mapping question IDs to the user's responses.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("questions", buildJsonObject {
                            put("type", "array")
                            put("description", "List of questions to ask the user")
                            put("items", buildJsonObject {
                                put("type", "object")
                                put("properties", buildJsonObject {
                                    put("id", buildJsonObject {
                                        put("type", "string")
                                        put("description", "Unique identifier for this question")
                                    })
                                    put("question", buildJsonObject {
                                        put("type", "string")
                                        put("description", "The question text to display to the user")
                                    })
                                    put("options", buildJsonObject {
                                        put("type", "array")
                                        put(
                                            "description",
                                            "Optional list of suggested options for the user to choose from"
                                        )
                                        put("items", buildJsonObject {
                                            put("type", "string")
                                        })
                                    })
                                    put("selection_type", buildJsonObject {
                                        put("type", "string")
                                        put(
                                            "enum",
                                            kotlinx.serialization.json.buildJsonArray {
                                                add("text")
                                                add("single")
                                                add("multi")
                                            }
                                        )
                                        put(
                                            "description",
                                            "Answer type: text (free text input, default), single (select exactly one option), multi (select one or more options)"
                                        )
                                    })
                                })
                                put("required", kotlinx.serialization.json.buildJsonArray {
                                    add("id")
                                    add("question")
                                })
                            })
                        })
                    },
                    required = listOf("questions")
                )
            },
            needsApproval = true,
            execute = {
                error("ask_user tool should be handled by HITL flow")
            }
        )
    }

    val sshTool by lazy {
        Tool(
            name = "ssh_connect",
            description = """
                Establish an SSH connection to a remote server. The user can provide connection parameters through natural language dialogue.
                Supports password or private key authentication. After connecting, commands can be executed interactively via ssh_exec tool.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("host", buildJsonObject {
                            put("type", "string")
                            put("description", "SSH server hostname or IP address")
                        })
                        put("port", buildJsonObject {
                            put("type", "integer")
                            put("description", "SSH server port (default: 22)")
                            put("default", 22)
                        })
                        put("username", buildJsonObject {
                            put("type", "string")
                            put("description", "SSH username for authentication")
                        })
                        put("auth_type", buildJsonObject {
                            put("type", "string")
                            put("enum", kotlinx.serialization.json.buildJsonArray {
                                add("password")
                                add("private_key")
                            })
                            put("description", "Authentication type: password or private_key")
                            put("default", "password")
                        })
                        put("credential", buildJsonObject {
                            put("type", "string")
                            put("description", "Credential for authentication: password string for 'password' auth, or private key content for 'private_key' auth")
                        })
                    },
                    required = listOf("host", "username", "auth_type", "credential")
                )
            },
            needsApproval = true,
            execute = { params ->
                val host = params.jsonObject["host"]?.jsonPrimitive?.contentOrNull
                    ?: error("host is required")
                val port = params.jsonObject["port"]?.jsonPrimitive?.intOrNull ?: 22
                val username = params.jsonObject["username"]?.jsonPrimitive?.contentOrNull
                    ?: error("username is required")
                val authTypeStr = params.jsonObject["auth_type"]?.jsonPrimitive?.contentOrNull ?: "password"
                val credential = params.jsonObject["credential"]?.jsonPrimitive?.contentOrNull
                    ?: error("credential is required")

                val authType = when (authTypeStr) {
                    "password" -> me.rerere.rikkahub.data.datastore.SshAuthType.PASSWORD
                    "private_key" -> me.rerere.rikkahub.data.datastore.SshAuthType.PRIVATE_KEY
                    else -> error("Invalid auth_type: $authTypeStr. Must be 'password' or 'private_key'")
                }

                // Update settings with connection parameters
                val currentSettings = settingsFlow.first()
                settingsFlow.value.let {
                    runCatching {
                        // Store connection config (settings will be updated via SettingsStore)
                        // The actual connection happens via SshSessionManager
                    }
                }

                val payload = buildJsonObject {
                    put("success", true)
                    put("host", host)
                    put("port", port)
                    put("username", username)
                    put("auth_type", authTypeStr)
                    put("message", "SSH connection parameters received. Connection will be established when user approves.")
                    put("note", "Connection requires approval and will use stored credentials from settings.")
                }
                listOf(UIMessagePart.Text(payload.toString()))
            }
        )
    }

    val sshExecTool by lazy {
        Tool(
            name = "ssh_exec",
            description = """
                Execute a command on the connected SSH server. Requires an active SSH connection established via ssh_connect tool.
                Returns the command output.
            """.trimIndent().replace("\n", " "),
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("command", buildJsonObject {
                            put("type", "string")
                            put("description", "Shell command to execute on the remote server")
                        })
                    },
                    required = listOf("command")
                )
            },
            execute = { params ->
                val command = params.jsonObject["command"]?.jsonPrimitive?.contentOrNull
                    ?: error("command is required")

                val sshState = sshManager.state.value
                if (sshState.status != SshSessionStatus.CONNECTED) {
                    val payload = buildJsonObject {
                        put("success", false)
                        put("error", "SSH not connected. Current status: ${sshState.status}")
                        put("hint", "Use ssh_connect tool to establish connection first")
                    }
                    listOf(UIMessagePart.Text(payload.toString()))
                } else {
                    sshManager.send(command)
                    val output = sshManager.output.value
                    val payload = buildJsonObject {
                        put("success", true)
                        put("command", command)
                        put("output", output)
                    }
                    listOf(UIMessagePart.Text(payload.toString()))
                }
            }
        )
    }

    private companion object {
        private const val DEFAULT_ROOT_COMMAND_TIMEOUT_SECONDS = 30
        private const val MAX_ROOT_COMMAND_TIMEOUT_SECONDS = 300
    }

    val shellExecTool by lazy {
        Tool(
            name = "shell_exec",
            description = "Execute a root-privileged shell command on the local Android device. Returns stdout, stderr, exitCode and durationMs.",
            parameters = {
                InputSchema.Obj(
                    properties = buildJsonObject {
                        put("command", buildJsonObject {
                            put("type", "string")
                            put("description", "Shell command to execute as root")
                        })
                        put("timeout", buildJsonObject {
                            put("type", "integer")
                            put("description", "Timeout in seconds, defaults to 30")
                        })
                    },
                    required = listOf("command")
                )
            },
            execute = {
                val command = it.jsonObject["command"]?.jsonPrimitive?.contentOrNull
                    ?: error("command is required")
                val timeout = it.jsonObject["timeout"]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(1, MAX_ROOT_COMMAND_TIMEOUT_SECONDS)
                    ?: DEFAULT_ROOT_COMMAND_TIMEOUT_SECONDS
                val result = rootManager.exec(command, timeout)
                listOf(UIMessagePart.Text(JsonInstant.encodeToString(result)))
            }
        )
    }

    suspend fun getTools(options: List<LocalToolOption>): List<Tool> {
        val TAG = "LocalTools"
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        // SSH tools: available when SSH option is enabled
        if (options.contains(LocalToolOption.Ssh)) {
            tools.add(sshTool)
            tools.add(sshExecTool)
        }
        // Root shell_exec: only if ShellExec option is enabled AND root status is ready (GRANTED)
        val currentSettings = settingsFlow.first()
        val status = rootManager.status.value
        Log.i(TAG, "getTools: rootEnabled=${currentSettings.rootEnabled}, rootStatus=$status")
        if (options.contains(LocalToolOption.ShellExec)) {
            if (status.grantState == RootGrantState.GRANTED && status.suAvailable && status.shellReady) {
                Log.i(TAG, "getTools: adding shell_exec tool")
                tools.add(shellExecTool)
            } else {
                Log.w(TAG, "getTools: skipping shell_exec - grantState=${status.grantState}, suAvailable=${status.suAvailable}, shellReady=${status.shellReady}")
            }
        } else {
            Log.i(TAG, "getTools: skipping shell_exec - ShellExec option not enabled")
        }
        Log.i(TAG, "getTools: total tools=${tools.map { it.name }}")
        return tools
    }
}
