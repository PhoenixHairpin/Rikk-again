package me.rerere.tts.provider.providers

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.common.http.SseEvent
import me.rerere.common.http.sseFlow
import me.rerere.tts.model.AudioChunk
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val MIMO_SAMPLE_RATE = 24000
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val mimoJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class MiMoChunk(
    val choices: List<MiMoChoice> = emptyList()
)

@Serializable
private data class MiMoChoice(
    val delta: MiMoDelta? = null
)

@Serializable
private data class MiMoDelta(
    val audio: MiMoAudio? = null
)

@Serializable
private data class MiMoAudio(
    val data: String? = null
)

internal fun decodeMiMoAudioData(data: String): ByteArray? {
    val payload = data.trim()
    if (payload == "[DONE]") return null
    val chunk = mimoJson.decodeFromString<MiMoChunk>(payload)
    val encoded = chunk.choices.firstOrNull()?.delta?.audio?.data ?: return null
    if (encoded.isBlank()) return null
    return Base64.getDecoder().decode(encoded)
}

internal class MiMoSseProcessor(
    private val model: String,
    private val voice: String
) {
    private var hasAudio = false
    private val metadata = mapOf(
        "provider" to "mimo",
        "model" to model,
        "voice" to voice
    )

    fun process(event: SseEvent): AudioChunk? {
        return when (event) {
            is SseEvent.Open -> null
            is SseEvent.Event -> {
                val pcmData = decodeMiMoAudioData(event.data) ?: return null
                hasAudio = true
                AudioChunk(
                    data = pcmData,
                    format = AudioFormat.PCM,
                    sampleRate = MIMO_SAMPLE_RATE,
                    metadata = metadata
                )
            }
            is SseEvent.Closed -> {
                if (!hasAudio) {
                    throw IllegalStateException("MiMo TTS returned no audio chunks")
                }
                AudioChunk(
                    data = byteArrayOf(),
                    format = AudioFormat.PCM,
                    sampleRate = MIMO_SAMPLE_RATE,
                    isLast = true,
                    metadata = metadata
                )
            }
            is SseEvent.Failure -> throw event.throwable ?: Exception("MiMo TTS streaming failed")
        }
    }
}

class MiMoTTSProvider : TTSProvider<TTSProviderSetting.MiMo> {
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    override fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.MiMo,
        request: TTSRequest
    ): Flow<AudioChunk> = flow {
        val requestBody = buildJsonObject {
            put("model", providerSetting.model)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "assistant")
                    put("content", request.text)
                })
            })
            put("audio", buildJsonObject {
                put("format", "pcm16")
                put("voice", providerSetting.voice)
            })
            put("stream", true)
        }

        val httpRequest = Request.Builder()
            .url("${providerSetting.baseUrl}/chat/completions")
            .addHeader("api-key", providerSetting.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val processor = MiMoSseProcessor(
            model = providerSetting.model,
            voice = providerSetting.voice
        )

        httpClient.sseFlow(httpRequest).collect { event ->
            processor.process(event)?.let { emit(it) }
        }
    }
}