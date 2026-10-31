package com.jarvis.urdu

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Commands(private val a: Context) {

    private val msgRe = Regex(
        "^(.+?)\\s+(?:کو|ko)\\s+(?:.*?\\s+)?(?:کہ|کے|keh|ke)\\s+(.+)$",
        RegexOption.DOT_MATCHES_ALL
    )
    private val nameRe = Regex("^(.+?)\\s+(?:کو|ko)\\s")
    private val aliases = linkedMapOf(
        "واٹس ایپ" to "whatsapp", "واٹس اپ" to "whatsapp", "واٹساپ" to "whatsapp", "whatsapp" to "whatsapp",
        "یوٹیوب" to "youtube", "youtube" to "youtube", "کیمرہ" to "camera", "کیمرا" to "camera",
        "فیس بک" to "facebook", "کروم" to "chrome", "سیٹنگ" to "settings", "گیلری" to "gallery",
        "کیلکولیٹر" to "calculator", "ٹیلیگرام" to "telegram", "انسٹاگرام" to "instagram",
        "جی میل" to "gmail", "میپس" to "maps", "نقشہ" to "maps", "پلے اسٹور" to "play store",
        "پلے سٹور" to "play store", "ٹک ٹاک" to "tiktok"
    )
    private val openWords = setOf(
        "کھولو", "کھول", "کھولیں", "دو", "کر", "کرو", "کریں", "دیں", "khol", "kholo",
        "open", "app", "ایپ", "کو", "please", "پلیز"
    )
    private val callWords = setOf("کال", "کرو", "کریں", "call", "karo", "کر", "دو", "فون", "ملاؤ", "ملا")
    private val searchWords = setOf(
        "گوگل", "پر", "سرچ", "کرو", "کریں", "تلاش", "ڈھونڈو", "ڈھونڈ", "search", "karo",
        "google", "on", "یوٹیوب", "youtube", "چلاؤ", "کر", "دو", "میں"
    )

    private fun norm(s: String): String {
        val sb = StringBuilder()
        for (c in s.trim()) {
            sb.append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c.lowercaseChar()
                }
            )
        }
        return sb.toString().replace("ي", "ی").replace("ك", "ک")
    }

    private fun go(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }

    private fun has(t: String, vararg w: String) = w.any { t.contains(it) }

    private fun strip(t: String, words: Set<String>) =
        t.split(Regex("\\s+")).filter { it.isNotBlank() && it !in words }.joinToString(" ")

    fun run(raw: String): String? {
        val t = norm(raw)
        if (t.isEmpty()) return null
        return try {
            dispatch(t)
        } catch (e: Exception) {
            "یہ کام نہیں ہو سکا: ${e.message}"
        }
    }

    private fun dispatch(t: String): String? {
        val m = msgRe.find(t)
        if (has(t, "الارم", "alarm")) return alarm(t)
        if (has(t, "ٹائمر", "timer")) return timer(t)
        if (has(t, "ٹارچ", "torch", "flashlight", "فلیش")) return torch(!has(t, "بند", "band", "off"))
        if (has(t, "بیٹری", "battery")) return battery()
        if (has(t, "تاریخ", "date")) return "آج کی تاریخ " + SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()) + " ہے"
        if (has(t, "کتنے بجے", "وقت", "time", "kitne baje")) return time()
        if (has(t, "آواز", "والیوم", "وولیم", "volume") &&
            has(t, "بڑھ", "زیادہ", "اونچ", "کم", "badh", "up", "down", "ghata", "increase", "decrease", "low", "high")
        ) return volume(has(t, "بڑھ", "زیادہ", "اونچ", "badh", "up", "increase", "high"))
        if (m != null && has(t, "واٹس", "whatsapp")) return whatsapp(m.groupValues[1].trim(), m.groupValues[2].trim())
        if (m != null && has(t, "میسج", "پیغام", "sms", "message", "ایس ایم ایس")) return sms(m.groupValues[1].trim(), m.groupValues[2].trim())
        if (has(t, "کال", "call", "فون کر", "فون ملا")) {
            val name = nameRe.find(t)?.groupValues?.get(1) ?: strip(t, callWords)
            return call(name)
        }
        if (has(t, "سرچ", "search", "تلاش", "ڈھونڈ", "dhoond")) {
            val q = strip(t, searchWords)
            if (q.isBlank()) return "کیا سرچ کروں؟"
            val url = if (has(t, "یوٹیوب", "youtube"))
                "https://www.youtube.com/results?search_query=" + Uri.encode(q)
            else "https://www.google.com/search?q=" + Uri.encode(q)
            go(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            return "$q سرچ کر رہا ہوں"
        }
        if (has(t, "کھول", "khol", "open")) return openApp(t)
        return null
    }

    private fun number(name: String): String? {
        val n = name.trim()
        if (n.matches(Regex("[+0-9 ]{6,}"))) return n.replace(" ", "")
        if (a.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val p = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        a.contentResolver.query(
            p, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$n%"), null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    private fun intl(n: String): String {
        val d = n.filter { it.isDigit() }
        return when {
            d.startsWith("00") -> d.substring(2)
            d.startsWith("0") -> "92" + d.substring(1)
            else -> d
        }
    }

    private fun call(name: String): String {
        val num = number(name) ?: return "$name کا نمبر نہیں ملا"
        val ok = a.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        go(Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(num))))
        return "$name کو کال کر رہا ہوں"
    }

    private fun sms(name: String, msg: String): String {
        val num = number(name) ?: return "$name کا نمبر نہیں ملا"
        if (a.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)
            return "ایس ایم ایس کی اجازت نہیں ہے"
        val sm = if (android.os.Build.VERSION.SDK_INT >= 31) a.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
        sm.sendMultipartTextMessage(num, null, sm.divideMessage(msg), null, null)
        return "$name کو پیغام بھیج دیا"
    }

    private fun whatsapp(name: String, msg: String): String {
        val num = number(name) ?: return "$name کا نمبر نہیں ملا"
        val uri = Uri.parse("https://wa.me/" + intl(num) + "?text=" + Uri.encode(msg))
        val on = Settings.Secure.getString(a.contentResolver, "enabled_accessibility_services")
            ?.contains(a.packageName) == true
        if (on) JarvisAccessibility.arm()
        try {
            go(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp"))
        } catch (e: Exception) {
            go(Intent(Intent.ACTION_VIEW, uri))
        }
        return if (on) "$name کو واٹس ایپ پر پیغام بھیج رہا ہوں"
        else "چیٹ کھول دی ہے۔ ایکسیسبیلیٹی آن نہیں ہے، اس لیے بھیجیں کا بٹن آپ دبائیں"
    }

    private fun openApp(t: String): String {
        val q = (aliases.entries.firstOrNull { t.contains(it.key) }?.value ?: strip(t, openWords)).lowercase()
        if (q.isBlank()) return "کون سی ایپ کھولوں؟"
        val pm = a.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
        val li = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            ?: return "$q نام کی ایپ نہیں ملی"
        go(li)
        return "$q کھول رہا ہوں"
    }

    private fun alarm(t: String): String {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?").find(t)
            ?: return "الارم کس وقت کا لگاؤں؟ مثلاً: صبح 6 بجے کا الارم لگاؤ"
        var h = m.groupValues[1].toInt()
        var min = m.groupValues[2].toIntOrNull() ?: 0
        if (has(t, "ساڑھے")) min = 30
        if (has(t, "شام", "رات", "دوپہر", "pm", "evening", "night") && h < 12) h += 12
        if (h > 23 || min > 59) return "وقت صحیح نہیں ہے"
        go(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, h)
                .putExtra(AlarmClock.EXTRA_MINUTES, min)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "الارم %02d:%02d کے لیے لگا دیا".format(h, min)
    }

    private fun timer(t: String): String {
        val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toIntOrNull()
            ?: return "ٹائمر کتنی دیر کا لگاؤں؟"
        val sec = when {
            has(t, "سیکنڈ", "second") -> n
            has(t, "گھنٹ", "hour") -> n * 3600
            else -> n * 60
        }
        go(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, sec)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        return "ٹائمر لگا دیا"
    }

    private fun torch(on: Boolean): String {
        val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.setTorchMode(cm.cameraIdList.first(), on)
        return if (on) "ٹارچ آن کر دی" else "ٹارچ بند کر دی"
    }

    private fun battery(): String {
        val bm = a.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return "بیٹری " + bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) + " فیصد ہے"
    }

    private fun time(): String {
        val c = Calendar.getInstance()
        var h = c.get(Calendar.HOUR)
        if (h == 0) h = 12
        return "اس وقت $h بج کر " + c.get(Calendar.MINUTE) + " منٹ ہوئے ہیں"
    }

    private fun volume(up: Boolean): String {
        val am = a.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        repeat(3) {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                AudioManager.FLAG_SHOW_UI
            )
        }
        return if (up) "آواز بڑھا دی" else "آواز کم کر دی"
    }
}
