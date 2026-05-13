package com.myra.assistant.ai

import com.myra.assistant.model.AppCommand

class CommandParser {
    fun parse(text: String): AppCommand? {
        val t = text.lowercase().trim()
        return when {
            // App Open
            t.contains("kholo") || t.contains("open") || t.contains("chalu") ->
                AppCommand("OPEN_APP", mapOf("app_name" to extractAppName(t)))
            // App Close
            t.contains("band karo") || t.contains("close") || t.contains("band kar") ->
                AppCommand("CLOSE_APP", mapOf("app_name" to extractAppName(t)))
            // Prime Call
            t.contains("close friend") || t.contains("meri jaan") ||
            t.contains("mere yaar") || t.contains("best friend") ->
                AppCommand("PRIME_CALL", mapOf("index" to "0"))
            // Call
            t.contains("call karo") || t.contains("call kar") ||
            t.contains("ko call") || t.contains("phone karo") ->
                AppCommand("CALL", mapOf("name" to extractName(t)))
            // SMS
            t.contains("sms") || t.contains("message karo") ||
            t.contains("msg karo") || t.contains("text karo") ->
                AppCommand("SMS", mapOf("name" to extractName(t)))
            // Volume
            t.contains("volume badhao") || t.contains("volume up") ->
                AppCommand("VOLUME_UP", emptyMap())
            t.contains("volume kam") || t.contains("volume down") ->
                AppCommand("VOLUME_DOWN", emptyMap())
            // Flashlight
            t.contains("torch on") || t.contains("flashlight on") ->
                AppCommand("FLASHLIGHT_ON", emptyMap())
            t.contains("torch off") || t.contains("flashlight off") ->
                AppCommand("FLASHLIGHT_OFF", emptyMap())
            else -> null
        }
    }

    private fun extractAppName(text: String): String {
        val apps = listOf("youtube", "whatsapp", "instagram", "facebook",
            "chrome", "gmail", "spotify", "netflix", "telegram",
            "snapchat", "settings", "calculator", "maps")
        return apps.firstOrNull { text.contains(it) } ?: ""
    }

    private fun extractName(text: String): String {
        val words = text.split(" ")
        val keywords = listOf("call", "karo", "ko", "sms", "msg",
            "message", "phone", "kar")
        return words.filterNot { it in keywords }.joinToString(" ").trim()
    }
}
