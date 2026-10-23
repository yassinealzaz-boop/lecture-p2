package app.tartil

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var rec: SpeechRecognizer? = null
    private var on = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {
        @JavascriptInterface fun start() { runOnUiThread { begin() } }
        @JavascriptInterface fun stop() { runOnUiThread { end() } }
    }

    private fun js(code: String) = web.evaluateJavascript(code, null)

    private fun begin() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            js("onSpeechError('Reconnaissance vocale indisponible sur cet appareil.')"); return
        }
        on = true
        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        listen()
    }

    private fun listen() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        rec?.startListening(i)
    }

    private fun end() {
        on = false
        rec?.destroy()
        rec = null
    }

    private fun send(b: Bundle?, fin: Boolean) {
        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        js("onTranscript(${JSONObject.quote(t)},$fin)")
    }

    private fun again() { if (on) ui.postDelayed({ if (on) listen() }, 150) }

    private val listener = object : RecognitionListener {
        override fun onResults(r: Bundle?) { send(r, true); again() }
        override fun onPartialResults(r: Bundle?) { send(r, false) }
        override fun onError(e: Int) {
            if (e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) again()
            else { end(); js("onSpeechError('Erreur micro (code $e). Vérifie la connexion et la langue arabe.')") }
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, p: Bundle?) {}
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(c, p, g)
        if (g.firstOrNull() == PackageManager.PERMISSION_GRANTED) begin()
        else js("onSpeechError('Autorise le micro pour réciter.')")
    }

    override fun onDestroy() { end(); super.onDestroy() }
}
