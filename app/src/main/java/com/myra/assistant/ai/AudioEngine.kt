package com.myra.assistant.ai

import android.media.*
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

class AudioEngine {
    private val SAMPLE_RATE_IN = 16000
    private val SAMPLE_RATE_OUT = 24000
    private val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private val FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isRecording = false
    private var isPlaying = false
    private var isMuted = false
    var isSpeaking = false

    var onAudioChunk: ((ByteArray) -> Unit)? = null
    var onAmplitudeChanged: ((Float) -> Unit)? = null
    var onSpeakingStarted: (() -> Unit)? = null
    var onSpeakingStopped: (() -> Unit)? = null

    fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_IN, CHANNEL_IN, FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_IN, CHANNEL_IN, FORMAT, bufferSize)
        audioRecord?.startRecording()
        isRecording = true

        scope.launch {
            val buffer = ByteArray(1024)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0 && !isMuted && !isSpeaking) {
                    val chunk = buffer.copyOf(read)
                    val rms = calculateRMS(chunk)
                    onAmplitudeChanged?.invoke(rms)
                    onAudioChunk?.invoke(chunk)
                }
            }
        }
    }

    fun startPlayback() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_OUT, CHANNEL_OUT, FORMAT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_OUT)
                .setChannelMask(CHANNEL_OUT)
                .setEncoding(FORMAT)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        isPlaying = true

        scope.launch {
            while (isPlaying) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    if (!isSpeaking) {
                        isSpeaking = true
                        withContext(Dispatchers.Main) {
                            onSpeakingStarted?.invoke()
                        }
                    }
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (isSpeaking) {
                        isSpeaking = false
                        withContext(Dispatchers.Main) {
                            onSpeakingStopped?.invoke()
                        }
                    }
                    delay(10)
                }
            }
        }
    }

    fun queueAudio(pcmBytes: ByteArray) {
        audioQueue.offer(pcmBytes)
    }

    fun clearQueue() {
        audioQueue.clear()
        audioTrack?.flush()
        isSpeaking = false
        onSpeakingStopped?.invoke()
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    private fun calculateRMS(buffer: ByteArray): Float {
        var sum = 0.0
        for (i in buffer.indices step 2) {
            if (i + 1 < buffer.size) {
                val sample = (buffer[i + 1].toInt() shl 8) or
                        (buffer[i].toInt() and 0xFF)
                sum += sample * sample
            }
        }
        val rms = Math.sqrt(sum / (buffer.size / 2))
        return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
    }

    fun release() {
        isRecording = false
        isPlaying = false
        audioRecord?.stop()
        audioRecord?.release()
        audioTrack?.stop()
        audioTrack?.release()
        scope.cancel()
    }
}
