package me.rerere.rikkahub.data.root

interface ShellRuntime {
    suspend fun isSuAvailable(): Boolean
    suspend fun exec(command: String, timeoutSeconds: Int = 30): RootExecResult
}
