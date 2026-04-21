package me.rerere.rikkahub.data.root

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RootGrantState {
    @SerialName("checking")
    CHECKING,

    @SerialName("granted")
    GRANTED,

    @SerialName("denied")
    DENIED,

    @SerialName("error")
    ERROR,
}

@Serializable
enum class RootManagerType {
    @SerialName("magisk")
    MAGISK,

    @SerialName("kernelsu")
    KERNEL_SU,

    @SerialName("apatch")
    APATCH,

    @SerialName("unknown")
    UNKNOWN,
}

@Serializable
enum class RootErrorType {
    @SerialName("su_unavailable")
    SU_UNAVAILABLE,

    @SerialName("permission_denied")
    PERMISSION_DENIED,

    @SerialName("timeout")
    TIMEOUT,

    @SerialName("session_lost")
    SESSION_LOST,

    @SerialName("exec_failed")
    EXEC_FAILED,
}

@Serializable
enum class TerminalSessionTransport {
    @SerialName("local_root")
    LOCAL_ROOT,

    @SerialName("ssh")
    SSH,
}

@Serializable
data class TerminalSessionState(
    val isActive: Boolean = false,
    val transport: TerminalSessionTransport? = null,
    val title: String = "",
)

@Serializable
data class RootExecResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val root: Boolean,
    val errorType: RootErrorType? = null,
    val message: String? = null,
)

@Serializable
data class RootStatus(
    val suAvailable: Boolean = false,
    val grantState: RootGrantState = RootGrantState.CHECKING,
    val managerType: RootManagerType = RootManagerType.UNKNOWN,
    val shellReady: Boolean = false,
    val terminalSession: TerminalSessionState = TerminalSessionState(),
) {
    val activeTerminalSession: Boolean
        get() = terminalSession.isActive
}
