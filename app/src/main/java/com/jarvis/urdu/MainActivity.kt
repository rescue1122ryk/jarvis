package com.jarvis.urdu

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var logView: TextView
    private lateinit var input: EditText
    private lateinit var cmds: Commands
    private var tts: TextToSpeech? = null

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun prefs() = getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cmds = Commands(this)
        tts = TextToSpeech(this, this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        val btnMic = Button(this)
        btnMic.text = "🎤  بولیں"
        btnMic.textSize = 24f
        btnMic.setOnClickListener { listen() }

        input = EditText(this)
        input.hint = "یا یہاں لکھ کر حکم دیں"

        val btnRun = Button(this)
        btnRun.text = "چلاؤ"
        btnRun.setOnClickListener {
            val s = input.text.toString()
            input.setText("")
            if (s.isNotBlank()) handle(s)
        }

        val btnSet = Button(this)
        btnSet.text = "سیٹنگز (AI کلید)"
        btnSet.setOnClickListener { settingsDialog() }

        val btnAcc = Button(this)
        btnAcc.text = "ایکسیسبیلیٹی آن کریں (واٹس ایپ کے لیے)"
        btnAcc.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }

        logView = TextView(this)
        logView.textSize = 16f
        val sv = ScrollView(this)
        sv.addView(logView)

        root.addView(btnMic)
        root.addView(input)
        root.addView(btnRun)
        root.addView(btnSet)
        root.addView(btnAcc)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        requestPermissions(
            arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS), 1
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
        if (r != null) say(r) else askAI(text)
    }

    private fun askAI(q: String) {
        val key = prefs().getString("key", "") ?: ""
        val model = prefs().getString("model", "meta-llama/llama-3.3-70b-instruct:free") ?: ""
        if (key.isBlank()) {
            say("یہ حکم مجھے سمجھ نہیں آیا۔ AI کے لیے سیٹنگز میں OpenRouter کی کلید لگائیں")
            return
        }
        Thread {
            val reply = try {
                val c = URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 15000
                c.readTimeout = 40000
                c.doOutput = true
                c.setRequestProperty("Authorization", "Bearer $key")
                c.setRequestProperty("Content-Type", "application/json")
                val msgs = JSONArray()
                    .put(JSONObject().put("role", "system").put("content",
                        "You are Jarvis, a helpful assistant. Always reply in Urdu (Urdu script), briefly, in 1 to 3 sentences."))
                    .put(JSONObject().put("role", "user").put("content", q))
                val body = JSONObject().put("model", model).put("messages", msgs)
                c.outputStream.use { it.write(body.toString().toByteArray()) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val txt = stream?.bufferedReader()?.readText() ?: ""
                if (code in 200..299)
                    JSONObject(txt).getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content").trim()
                else "AI سے جواب نہیں ملا (کوڈ $code)۔ کلید یا ماڈل چیک کریں"
            } catch (e: Exception) {
                "انٹرنیٹ نہیں ہے یا AI سے رابطہ نہیں ہو سکا"
            }
            runOnUiThread { say(reply) }
        }.start()
    }

    private fun settingsDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(8), dp(16), dp(8))
        val k = EditText(this)
        k.hint = "OpenRouter API key (sk-or-...)"
        k.setText(prefs().getString("key", ""))
        val m = EditText(this)
        m.hint = "Model (:free wala)"
        m.setText(prefs().getString("model", "meta-llama/llama-3.3-70b-instruct:free"))
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
