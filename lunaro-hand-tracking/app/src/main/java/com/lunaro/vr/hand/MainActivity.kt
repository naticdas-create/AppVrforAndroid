package com.lunaro.vr.hand

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.lunaro.vr.hand.hand.HandTracker
import com.lunaro.vr.hand.hand.PokeDetector
import com.lunaro.vr.hand.ui.HandOverlayView
import com.lunaro.vr.hand.ui.WindowSystemView

class MainActivity : ComponentActivity(), WindowSystemView.Callbacks {
    private lateinit var preview: PreviewView
    private lateinit var overlay: HandOverlayView
    private lateinit var windows: WindowSystemView
    private lateinit var action: TextView
    private lateinit var status: TextView
    private var tracker: HandTracker? = null
    private var showLatency = true
    private val poke = PokeDetector()

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startTracking() else status.text = "Sem permissão de câmera não há tracking de mãos."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        fun label(g: Int) = TextView(this).apply {
            setTextColor(Color.WHITE); setBackgroundColor(0x99000000.toInt()); setPadding(24, 10, 24, 10); gravity = g
        }
        status = label(Gravity.START).apply { text = "Estenda o indicador, feche os outros dedos e empurre a ponta em direção a um botão." }
        action = label(Gravity.CENTER).apply { text = "LUNARO" }
        windows = WindowSystemView(this, this)
        overlay = HandOverlayView(this).apply { outlineWidth = 6f + 22f * 0.5f }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))
        root.addView(windows, FrameLayout.LayoutParams(-1, -1))
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        root.addView(action, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        setContentView(root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startTracking() else permission.launch(Manifest.permission.CAMERA)
    }

    // ---- Callbacks da interface ----
    override fun onStatus(text: String) { action.text = text }

    override fun onSetting(id: String, value: Float) {
        when (id) {
            "outline" -> overlay.visibleOutline = value > 0.5f
            "quest" -> overlay.questStyle = value > 0.5f
            "latency" -> { showLatency = value > 0.5f; if (!showLatency) status.text = "" }
            "thick" -> overlay.outlineWidth = 6f + 22f * value
            "opacity" -> {} // tratado dentro da própria interface
        }
    }

    override fun onLaunch(id: String) {
        val pkg: String?; val url: String?
        when (id) {
            "youtube" -> { pkg = "com.google.android.youtube"; url = "https://www.youtube.com" }
            "netflix" -> { pkg = "com.netflix.mediaclient"; url = "https://www.netflix.com" }
            "roblox" -> { pkg = "com.roblox.client"; url = "https://www.roblox.com" }
            "spotify" -> { pkg = "com.spotify.music"; url = "https://open.spotify.com" }
            "browser" -> { pkg = null; url = "https://www.google.com" }
            else -> { pkg = null; url = null }
        }
        val app = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
        val intent = when {
            id == "settings" -> Intent(Settings.ACTION_SETTINGS)
            app != null -> app
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(url ?: "https://www.google.com"))
        }
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            action.text = if (app != null) "Abrindo o app ($id)" else "Abrindo $id no navegador/sistema"
        } catch (e: Exception) {
            action.text = "Não foi possível abrir $id"
        }
    }

    private fun startTracking() {
        tracker = HandTracker(this, this, preview) { r -> runOnUiThread { onFrame(r) } }.also { it.start() }
    }

    private fun onFrame(r: HandTracker.FrameResult) {
        overlay.update(r.hands, r.imgW, r.imgH)
        val hand = r.hands.firstOrNull { PokeDetector.isPointing(it) }
        val ev = poke.update(hand, SystemClock.uptimeMillis()) { x, y -> overlay.map(x, y) }
        if (ev != null) {
            windows.onPointer(ev)
            val pressed = ev.phase == PokeDetector.Phase.DOWN || ev.phase == PokeDetector.Phase.MOVE
            val gone = ev.phase == PokeDetector.Phase.LOST || (ev.phase == PokeDetector.Phase.UP && ev.x == 0f)
            overlay.setTip(if (gone) null else PointF(ev.x, ev.y), pressed)
        }
        if (!showLatency) return
        status.text = if (r.hands.isEmpty()) "Nenhuma mão detectada (verifique luz e enquadramento)."
        else "Latência ${r.latencyMs} ms · profundidade %.2f (toque > %.2f)".format(poke.lastDepth, poke.enterDepth)
    }

    override fun onDestroy() {
        tracker?.stop()
        super.onDestroy()
    }
}
