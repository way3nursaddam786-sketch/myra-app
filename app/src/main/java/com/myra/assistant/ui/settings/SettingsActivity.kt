package com.myra.assistant.ui.settings

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.myra.assistant.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)

        val apiKeyInput = findViewById<EditText>(R.id.apiKeyInput)
        val nameInput = findViewById<EditText>(R.id.nameInput)
        val modelSpinner = findViewById<Spinner>(R.id.modelSpinner)
        val voiceSpinner = findViewById<Spinner>(R.id.voiceSpinner)
        val saveBtn = findViewById<Button>(R.id.saveBtn)

        // Models
        val models = arrayOf(
            "Native Audio (Default)",
            "Flash Live (Fast)",
            "Pro Audio Dialog"
        )
        modelSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, models)

        // Voices
        val voices = arrayOf(
            "Aoede (Female)",
            "Charon (Male)",
            "Kore (Female)",
            "Fenrir (Male)",
            "Puck (Male)",
            "Leda (Female)",
            "Orus (Male)",
            "Zephyr (Female)"
        )
        voiceSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, voices)

        // Load saved values
        apiKeyInput.setText(prefs.getString("api_key", ""))
        nameInput.setText(prefs.getString("user_name", ""))

        // Save button
        saveBtn.setOnClickListener {
            val voiceNames = arrayOf("Aoede","Charon","Kore",
                "Fenrir","Puck","Leda","Orus","Zephyr")
            val modelStrings = arrayOf(
                "models/gemini-2.5-flash-native-audio-preview-12-2025",
                "models/gemini-2.0-flash-live-001",
                "models/gemini-2.5-flash-preview-native-audio-dialog"
            )
            prefs.edit().apply {
                putString("api_key", apiKeyInput.text.toString())
                putString("user_name", nameInput.text.toString())
                putString("gemini_model", modelStrings[modelSpinner.selectedItemPosition])
                putString("gemini_voice", voiceNames[voiceSpinner.selectedItemPosition])
                apply()
            }
            Toast.makeText(this,
                "Saved! App restart karein ✅", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
