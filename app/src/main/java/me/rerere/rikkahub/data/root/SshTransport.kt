package me.rerere.rikkahub.data.root

import android.util.Log
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.userauth.UserAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SshAuthType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "SshTransport"

data class SshConnectionConfig(
    val host: String,
    val port: Int,
    val username: String,
    val authType: SshAuthType,
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null,
)

sealed class SshConnectionResult {
    data class Success(val transport: SshTransport) : SshConnectionResult()
    data class Failure(val error: String, val exception: Throwable? = null) : SshConnectionResult()
}

class SshTransport private constructor(
    private val client: SSHClient,
    private val session: net.schmizz.sshj.connection.channel.direct.Session,
) {
    val inputStream: InputStream = session.getInputStream()
    val outputStream: OutputStream = session.getOutputStream()
    val errorStream: InputStream? = null // SSHJ Session doesn't have error stream

    val isConnected: Boolean
        get() = runCatching { client.isConnected && session.isOpen }.getOrDefault(false)

    fun close() {
        runCatching {
            session.close()
            client.disconnect()
        }.onFailure { e ->
            Log.w(TAG, "Error closing SSH transport", e)
        }
    }

    companion object {
        suspend fun connect(config: SshConnectionConfig): SshConnectionResult = withContext(Dispatchers.IO) {
            runCatching {
                val client = SSHClient()
                // Accept all host keys (user should verify in UI)
                client.addHostKeyVerifier(object : net.schmizz.sshj.transport.verification.HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: java.security.PublicKey?): Boolean = true
                    override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
                })

                Log.d(TAG, "Connecting to ${config.host}:${config.port}")
                client.connect(config.host, config.port)

                when (config.authType) {
                    SshAuthType.PASSWORD -> {
                        val password = config.password ?: throw IllegalArgumentException("Password required for password auth")
                        Log.d(TAG, "Authenticating with password for ${config.username}")
                        client.authPassword(config.username, password)
                    }
                    SshAuthType.PRIVATE_KEY -> {
                        val privateKey = config.privateKey ?: throw IllegalArgumentException("Private key required for key auth")
                        Log.d(TAG, "Authenticating with private key for ${config.username}")
                        val passphraseChars = config.passphrase?.toCharArray() ?: charArrayOf()
                        val keyProvider = loadKeyFromContent(client, privateKey, passphraseChars)
                        client.authPublickey(config.username, keyProvider)
                    }
                }

                Log.d(TAG, "Starting shell session")
                val session = client.startSession()
                session.allocatePTY("xterm-256color", 80, 24, 640, 480, emptyMap())
                session.startShell()

                SshTransport(client, session)
            }.fold(
                onSuccess = { transport -> SshConnectionResult.Success(transport) },
                onFailure = { e ->
                    val errorMsg = when (e) {
                        is TransportException -> "Transport error: ${e.message}"
                        is UserAuthException -> "Authentication failed: ${e.message}"
                        is IllegalArgumentException -> "Configuration error: ${e.message}"
                        else -> "Connection failed: ${e.message}"
                    }
                    Log.e(TAG, "SSH connection failed", e)
                    SshConnectionResult.Failure(errorMsg, e)
                }
            )
        }

        private fun loadKeyFromContent(client: SSHClient, content: String, passphrase: CharArray): net.schmizz.sshj.userauth.keyprovider.KeyProvider {
            // Write private key to temp file since SSHJ loadKeys requires file path
            val tempFile = File.createTempFile("ssh_key", ".tmp")
            tempFile.writeText(content)
            tempFile.deleteOnExit()
            return client.loadKeys(tempFile.absolutePath, passphrase)
        }
    }
}