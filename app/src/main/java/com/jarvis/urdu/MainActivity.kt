package com.jarvis.urdu

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var logView: TextView
    private lateinit var input: EditText
    private lateinit var cmds: Commands
    private var tts: TextToSpeech? = null

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    private fun btn(label: String, size: Float = 15f, action: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.textSize = size
        b.setOnClickListener { action() }
        return b
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cmds = Commands(this)
        tts = TextToSpeech(this, this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        input = EditText(this)
        input.hint = "یا یہاں لکھ کر حکم دیں"
        logView = TextView(this)
        logView.textSize = 16f

        root.addView(btn("🎤  بولیں", 24f) { listen() })
        root.addView(input)
        root.addView(btn("چلاؤ") {
            val s = input.text.toString()
            input.setText("")
            if (s.isNotBlank()) handle(s)
        })
        root.addView(btn("👂 'جارویس' سننا شروع / بند") { toggleWake() })
        root.addView(btn("سیٹنگز (AI کلید یہاں لگائیں)") { settingsDialog() })
        root.addView(btn("1) ایکسیسبیلیٹی آن کریں (واٹس ایپ)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(btn("2) دوسری ایپس کے اوپر دکھانے کی اجازت") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(btn("3) بیٹری کی پابندی ہٹائیں") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(logView)

        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)

        requestPermissions(
            arrayOf(
                Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE,
                Manifest.permission.SEND_SMS, Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS
            ), 1
        )
        log("جارویس تیار ہے۔ مائیک دبا کر بولیں۔")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("ur", "PK"))
    }

    private fun log(s: String) {
        logView.append(s + "\n\n")
    }

    private fun say(s: String) {
        log("جارویس: $s")
        tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    private fun toggleWake() {
        if (WakeService.active) {
            startService(Intent(this, WakeService::class.java).setAction("STOP"))
            say("سننا بند کر دیا")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
            log("مائیک کی اجازت دیں، پھر یہی بٹن دوبارہ دبائیں")
            return
        }
        val i = Intent(this, WakeService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        say("اب آپ مجھے بلا سکتے ہیں")
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "حکم دیں")
        try {
            startActivityForResult(i, 100)
        } catch (e: Exception) {
            say("آواز پہچاننے والی سروس نہیں ملی۔ Google ایپ اپڈیٹ کریں")
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) handle(text)
        }
    }

    private fun handle(text: String) {
        log("آپ: $text")
        val r = cmds.run(text)
        if (r != null) say(r) else Ai.ask(this, text) { say(it) }
    }

    private fun settingsDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(8))
        val k = EditText(this)
        k.hint = "OpenRouter API key (sk-or-...)"
        k.setText(prefs().getString("key", ""))
        val m = EditText(this)
        m.hint = "Model"
        m.setText(prefs().getString("model", Ai.DEFAULT_MODEL))
        box.addView(k)
        box.addView(m)
        AlertDialog.Builder(this)
            .setTitle("AI سیٹنگز")
            .setView(box)
            .setPositiveButton("محفوظ کریں") { _, _ ->
                prefs().edit().putString("key", k.text.toString().trim())
                    .putString("model", m.text.toString().trim()).apply()
                log("سیٹنگز محفوظ ہو گئیں")
            }
            .setNegativeButton("منسوخ", null)
            .show()
    }
}
