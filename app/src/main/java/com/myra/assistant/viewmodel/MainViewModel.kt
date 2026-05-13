package com.myra.assistant.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.myra.assistant.model.AppCommand
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val commandResult = MutableLiveData<String?>()

    private val appMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "settings" to "com.android.settings",
        "calculator" to "com.android.calculator2",
        "camera" to "com.android.camera"
    )

    fun executeCommand(command: AppCommand, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            when (command.type) {
                "OPEN_APP" -> openApp(command.params["app_name"] ?: "", context)
                "CLOSE_APP" -> closeApp(context)
                "CALL" -> makeCall(command.params["name"] ?: "", context)
                "PRIME_CALL" -> primecall(command.params["index"]?.toInt() ?: 0, context)
                "SMS" -> sendSms(command.params["name"] ?: "", context)
                "VOLUME_UP" -> changeVolume(true, context)
                "VOLUME_DOWN" -> changeVolume(false, context)
            }
        }
    }

    private fun openApp(appName: String, context: Context) {
        val packageName = appMap[appName.lowercase()]
        val intent = if (packageName != null) {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } else {
            context.packageManager.getInstalledApplications(0)
                .firstOrNull { pm ->
                    context.packageManager.getApplicationLabel(pm)
                        .toString().lowercase().contains(appName.lowercase())
                }?.packageName?.let {
                    context.packageManager.getLaunchIntentForPackage(it)
                }
        }
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            commandResult.postValue("$appName khol diya ✅")
        } else {
            commandResult.postValue("$appName nahi mila ❌")
        }
    }

    private fun closeApp(context: Context) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        commandResult.postValue("App band kar diya ✅")
    }

    private fun makeCall(name: String, context: Context) {
        val number = lookupContact(name, context) ?: name
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        commandResult.postValue("$name ko call kar raha hoon ✅")
    }

    private fun primecall(index: Int, context: Context) {
        val prefs = context.getSharedPreferences("myra_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("prime_contacts_json", null)
        if (json != null) {
            val arr = JSONArray(json)
            if (index < arr.length()) {
                val contact = arr.getJSONObject(index)
                val number = contact.getString("number")
                val name = contact.getString("name")
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                commandResult.postValue("$name ko call kar raha hoon ✅")
            }
        }
    }

    private fun sendSms(name: String, context: Context) {
        val number = lookupContact(name, context) ?: name
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("smsto:$number")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        commandResult.postValue("$name ko message bhej raha hoon ✅")
    }

    private fun changeVolume(up: Boolean, context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction,
            AudioManager.FLAG_SHOW_UI)
        commandResult.postValue(if (up) "Volume badha diya ✅" else "Volume kam kar diya ✅")
    }

    private fun lookupContact(name: String, context: Context): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val contactName = it.getString(nameIdx) ?: continue
                if (contactName.lowercase().contains(name.lowercase())) {
                    return it.getString(numIdx)
                }
            }
        }
        return null
    }
}
