package com.lunaro.vr.hand.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.min

/** Mãos estilo esqueleto 3D minimalista: contorno preto, dedos cinza, articulações visíveis. */
class HandOverlayView(ctx: Context) : View(ctx) {
    private var hands: List<List<NormalizedLandmark>> = emptyList()
    private var imgW = 1
    private var imgH = 1
    private var tip: PointF? = null
    private var tipPressed = false

    // Ajustáveis (espessura/cor/transparência) pela tela de Configurações no app completo.
    var outlineWidth = 16f
    var fingerWidth = 8f
    var visibleOutline = true
    var questStyle = false // true: contorno fino e claro, como nas referências do Quest

    private val density = ctx.resources.displayMetrics.density
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val gray = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF9A9A9A.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val jointFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB5B5B5.toInt() }
    private val jointRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f * density
    }
    private val tipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val palm = setOf(0, 1, 5, 9, 13, 17)

    fun update(h: List<List<NormalizedLandmark>>, w: Int, hh: Int) {
        hands = h; imgW = w.coerceAtLeast(1); imgH = hh.coerceAtLeast(1)
        invalidate()
    }

    fun setTip(p: PointF?, pressed: Boolean) { tip = p; tipPressed = pressed }

    /** Mapeia coordenadas normalizadas da imagem para pixels da tela (mesmo ajuste FIT do preview). */
    fun map(nx: Float, ny: Float): PointF {
        val s = min(width / imgW.toFloat(), height / imgH.toFloat())
        val dw = imgW * s
        val dh = imgH * s
        return PointF((width - dw) / 2f + nx * dw, (height - dh) / 2f + ny * dh)
    }

    override fun onDraw(c: Canvas) {
        black.strokeWidth = outlineWidth
        gray.strokeWidth = fingerWidth
        if (visibleOutline) for (hand in hands) {
            if (questStyle) {
                val pts = hand.map { map(it.x(), it.y()) }
                gray.color = 0xDDFFFFFF.toInt(); gray.strokeWidth = outlineWidth * 0.3f
                for (conn in HandLandmarker.HAND_CONNECTIONS) {
                    val a = pts[conn.start()]; val b = pts[conn.end()]
                    c.drawLine(a.x, a.y, b.x, b.y, gray)
                }
                gray.color = 0xFF9A9A9A.toInt()
                continue
            }
            val pts = hand.map { map(it.x(), it.y()) }
            for (conn in HandLandmarker.HAND_CONNECTIONS) {
                val a = pts[conn.start()]; val b = pts[conn.end()]
                c.drawLine(a.x, a.y, b.x, b.y, black)
            }
            for (conn in HandLandmarker.HAND_CONNECTIONS) {
                if (conn.start() in palm && conn.end() in palm) continue // palma fica só no contorno preto
                val a = pts[conn.start()]; val b = pts[conn.end()]
                c.drawLine(a.x, a.y, b.x, b.y, gray)
            }
            for (p in pts) {
                c.drawCircle(p.x, p.y, 5f * density, jointFill)
                c.drawCircle(p.x, p.y, 5f * density, jointRing)
            }
        }
        tip?.let {
            tipPaint.color = if (tipPressed) 0xFF2F6FEB.toInt() else 0x88FFFFFF.toInt()
            c.drawCircle(it.x, it.y, (if (tipPressed) 14f else 9f) * density, tipPaint)
        }
    }
}
