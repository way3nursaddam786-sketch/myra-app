package com.myra.assistant

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val chatList = ArrayList<String>()
        chatList.add("Hello! I am Myra. How can I help you?")

        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, chatList)
        val listView = findViewById<ListView>(R.id.chatListView)
        listView.adapter = adapter
        
        val micButton = findViewById<Button>(R.id.micButton)
        micButton.setOnClickListener {
            // ভয়েস সিস্টেম পরে যোগ করা হবে
        }
    }
}

