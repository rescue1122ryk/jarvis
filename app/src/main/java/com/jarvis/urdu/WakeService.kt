package com.jarvis.urdu

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

class WakeService : Service() {
    companion object {
        @Volatile var active = false
    }

    private val h = Handler(Looper.getMainLooper())
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var cmds: Commands
    private var running = false
    private var awaitUntil = 0L

    private val wake = listOf("جارویس", "جاروس", "جارویز", "جاروز", "جارس", "jarvis", "jarwis", "jarves")
    private val fillers = setOf("ہے", "ارے", "ہیلو", "hey", "hello", "اے", "او")

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        cmds = Commands(this)
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("ur", "PK")) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }
        startFg()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        active = true
        if (!running) {
            running = true
            listen()
        }
        return START_STICKY
    }

    private fun startFg() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            nm.createNotificationChannel(NotificationChannel("jarvis", "Jarvis", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, WakeService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "jarvis") else Notification.Builder(this)
        b.setContentTitle("Jarvis sun raha hai")
            .setContentText("Jarvis kah kar bulayen")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_delete, "Band karein", stop)
        val n = b.build()
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        else startForeground(1, n)
    }

    private fun again(d: Long = 300) {
        h.postDelayed({
            if (running) {
                if (tts?.isSpeaking == true) again(500) else listen()
            }
        }, d)
    }

    private fun listen() {
        if (!running) return
        try {
            sr?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            sr = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(b: Bundle?) {}
                override fun onEvent(t: Int, b: Bundle?) {}
                override fun onError(e: Int) {
                    if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stopSelf()
                        return
                    }
                    again(
                        when (e) {
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER,
                            SpeechRecognizer.ERROR_CLIENT,
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2500L
                            else -> 300L
                        }
                    )
                }

                override fun onResults(b: Bundle?) {
                    onHeard(b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                    again()
                }
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            r.startListening(i)
        } catch (e: Exception) {
            again(3000)
        }
    }

    private fun onHeard(list: ArrayList<String>?) {
        if (list.isNullOrEmpty()) return
        val now = System.currentTimeMillis()
        if (now < awaitUntil) {
            awaitUntil = 0
            runCmd(list[0])
            return
        }
        for (s in list) {
            val n = s.lowercase()
            val w = wake.firstOrNull { n.contains(it) } ?: continue
            val rest = n.replace(w, " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
            if (rest.isNotEmpty() && rest[0] in fillers) rest.removeAt(0)
            val cmd = rest.joinToString(" ")
            if (cmd.isBlank()) {
                awaitUntil = now + 10000
                say("جی؟")
            } else runCmd(cmd)
            return
        }
    }

    private fun runCmd(t: String) {
        val r = cmds.run(t)
        if (r != null) say(r) else Ai.ask(this, t) { say(it) }
    }

    private fun say(s: String) {
        tts?.speak(s, TextToSpeech.QUEUE_FLUSH, null, "w")
    }

    override fun onDestroy() {
        running = false
        active = false
        h.removeCallbacksAndMessages(null)
        try { sr?.destroy() } catch (e: Exception) {}
        tts?.shutdown()
        super.onDestroy()
    }
}
