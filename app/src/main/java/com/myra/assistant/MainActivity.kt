package com.myra.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.myra.assistant.ai.AudioEngine
import com.myra.assistant.ai.CommandParser
import com.myra.assistant.ai.GeminiLiveClient
import com.myra.assistant.ui.settings.SettingsActivity
import com.myra.assistant.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var geminiLive: GeminiLiveClient
    private lateinit var audioEngine: AudioEngine
    private val commandParser = CommandParser()

    private lateinit var statusText: TextView
    private lateinit var micButton: ImageButton
    private lateinit var settingsBtn: ImageButton
    private lateinit var chatRecycler: RecyclerView

    private var isMuted = false
    private val chatMessages = mutableListOf<String>()
    private lateinit var chatAdapter: ArrayAdapter<String>

    private var inputBuffer = StringBuilder()
    private var outputBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        statusText = findViewById(R.id.statusText)
        micButton = findViewById(R.id.micButton)
        settingsBtn = findViewById(R.id.settingsBtn)
        chatRecycler = findViewById(R.id.chatRecycler)

        // Chat setup
        chatAdapter = ArrayAdapter(this,
            android.R.layout.simple_list_item_1, chatMessages)
        chatRecycler.adapter = chatAdapter
        chatRecycler.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        // Settings button
        settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Mic button
        micButton.setOnClickListener {
            isMuted = !isMuted
            audioEngine.setMuted(isMuted)
            micButton.setImageResource(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on)
            statusText.text = if (isMuted) "Muted 🔇" else "Sun rahi hoon... 👂"
        }

        micButton.setOnLongClickListener {
            audioEngine.clearQueue()
            geminiLive.interrupt()
            statusText.text = "Ruk gayi ✋"
            true
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                    PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            initGeminiLive()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) initGeminiLive()
    }

    private fun initGeminiLive() {
        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        val userName = prefs.getString("user_name", "Dost") ?: "Dost"
        val model = prefs.getString("gemini_model",
            "models/gemini-2.5-flash-native-audio-preview-12-2025") ?: ""
        val voice = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"

        if (apiKey.isEmpty()) {
            statusText.text = "Settings mein API Key daalo! ⚙️"
            return
        }

        val systemPrompt = """
            Tum MYRA ho — $userName ki AI assistant.
            Hinglish mein baat karo — warm aur caring style mein.
            Max 2-3 sentences mein jawab do.
            Aaj ki date: ${java.util.Date()}
        """.trimIndent()

        audioEngine = AudioEngine()
        geminiLive = GeminiLiveClient(apiKey, model, voice, systemPrompt)

        geminiLive.onConnected = {
            runOnUiThread {
                statusText.text = "Connect ho gayi! 🟢"
            }
            audioEngine.startRecording()
            audioEngine.startPlayback()
            Thread.sleep(600)
            geminiLive.sendText("Hey $userName! Main MYRA hoon. Kaise help karun?")
        }

        audioEngine.onAudioChunk = { chunk ->
            if (!audioEngine.isSpeaking) {
                geminiLive.sendAudio(chunk)
            }
        }

        audioEngine.onSpeakingStarted = {
            runOnUiThread { statusText.text = "Bol rahi hoon... 💬" }
        }

        audioEngine.onSpeakingStopped = {
            runOnUiThread { statusText.text = "Sun rahi hoon... 👂" }
        }

        geminiLive.onInputTranscript = { text ->
            inputBuffer.append(text)
        }

        geminiLive.onOutputTranscript = { text ->
            outputBuffer.append(text)
        }

        geminiLive.onAudioReceived = { pcm ->
            audioEngine.queueAudio(pcm)
        }

        geminiLive.onTurnComplete = {
            val userText = inputBuffer.toString().trim()
            val myraText = outputBuffer.toString().trim()

            if (userText.isNotEmpty()) {
                runOnUiThread {
                    chatMessages.add("Tum: $userText")
                    chatAdapter.notifyDataSetChanged()
                    chatRecycler.scrollToPosition(chatMessages.size - 1)
                }
                val command = commandParser.parse(userText)
                command?.let { viewModel.executeCommand(it, this) }
            }

            if (myraText.isNotEmpty()) {
                runOnUiThread {
                    chatMessages.add("MYRA: $myraText")
                    chatAdapter.notifyDataSetChanged()
                    chatRecycler.scrollToPosition(chatMessages.size - 1)
                }
            }

            inputBuffer.clear()
            outputBuffer.clear()
        }

        viewModel.commandResult.observe(this) { result ->
            result?.let { geminiLive.sendText(it) }
        }

        geminiLive.connect()
    }

    override fun onDestroy() {
        super.onDestroy()
        geminiLive.disconnect()
        audioEngine.release()
    }
}
