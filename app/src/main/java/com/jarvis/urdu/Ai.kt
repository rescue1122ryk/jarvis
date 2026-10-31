package com.jarvis.urdu

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Ai {
    const val DEFAULT_MODEL = "openrouter/free"

    fun ask(ctx: Context, q: String, cb: (String) -> Unit) {
        val p = ctx.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val key = p.getString("key", "") ?: ""
        val model = (p.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL).ifBlank { DEFAULT_MODEL }
        val main = Handler(Looper.getMainLooper())
        if (key.isBlank()) {
            cb("یہ حکم مجھے سمجھ نہیں آیا۔ AI کے لیے سیٹنگز میں OpenRouter کی کلید لگائیں")
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
            main.post { cb(reply) }
        }.start()
    }
}
