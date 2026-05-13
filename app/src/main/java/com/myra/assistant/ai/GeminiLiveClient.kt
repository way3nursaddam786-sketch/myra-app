package com.myra.assistant.ai

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

class GeminiLiveClient(
    private val apiKey: String,
    private val model: String = "models/gemini-2.5-flash-native-audio-preview-12-2025",
    private val voiceName: String = "Aoede",
    private val systemPrompt: String = ""
) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onConnected: (() -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                sendSetup(ws)
            }
            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("MYRA", "WebSocket error: ${t.message}")
                onDisconnected?.invoke()
                scope.launch {
                    delay(3000)
                    connect()
                }
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                onDisconnected?.invoke()
            }
        })
    }

    private fun sendSetup(ws: WebSocket) {
        val setup = JSONObject().put("setup", JSONObject().apply {
            put("model", model)
            put("system_instruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", systemPrompt))))
            put("generation_config", JSONObject().apply {
                put("response_modalities", JSONArray().put("AUDIO"))
                put("speech_config", JSONObject().put("voice_config",
                    JSONObject().put("prebuilt_voice_config",
                        JSONObject().put("voice_name", voiceName))))
                put("temperature", 0.9)
            })
            put("output_audio_transcription", JSONObject())
            put("input_audio_transcription", JSONObject())
        })
        ws.send(setup.toString())
        onConnected?.invoke()
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val content = json.optJSONObject("serverContent") ?: return
            content.optJSONObject("modelTurn")
                ?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    parts.getJSONObject(i)
                        .optJSONObject("inlineData")
                        ?.optString("data")?.let { b64 ->
                        onAudioReceived?.invoke(Base64.decode(b64, Base64.DEFAULT))
                    }
                }
            }
            content.optJSONObject("outputTranscription")
                ?.optString("text")?.let { onOutputTranscript?.invoke(it) }
            content.optJSONObject("inputTranscription")
                ?.optString("text")?.let { onInputTranscript?.invoke(it) }
            if (content.optBoolean("turnComplete"))
                onTurnComplete?.invoke()
        } catch (e: Exception) {
            Log.e("MYRA", "Parse error: ${e.message}")
        }
    }

    fun sendAudio(pcmBytes: ByteArray) {
        val msg = JSONObject().put("realtime_input", JSONObject().put("media_chunks",
            JSONArray().put(JSONObject().apply {
                put("mime_type", "audio/pcm;rate=16000")
                put("data", Base64.encodeToString(pcmBytes, Base64.NO_WRAP))
            })))
        webSocket?.send(msg.toString())
    }

    fun sendText(text: String) {
        val msg = JSONObject().put("client_content", JSONObject().apply {
            put("turns", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().put("text", text)))
            }))
            put("turn_complete", true)
        })
        webSocket?.send(msg.toString())
    }

    fun interrupt() {
        val msg = JSONObject().put("client_content", JSONObject().apply {
            put("turns", JSONArray())
            put("turn_complete", true)
        })
        webSocket?.send(msg.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Bye")
        scope.cancel()
    }
}
