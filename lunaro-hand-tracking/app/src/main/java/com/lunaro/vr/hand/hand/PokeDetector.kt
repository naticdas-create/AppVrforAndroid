package com.lunaro.vr.hand.hand

import android.graphics.PointF
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

/**
 * Detecta o "toque" do indicador em uma superfície virtual.
 *
 * Posição (x, y) vem da ponta do indicador (landmark 8). A profundidade vem de z do MediaPipe,
 * que é RELATIVA ao pulso e estimada por uma câmera só: por isso usamos a diferença ponta-vs-nó
 * do dedo (landmark 5), normalizada pelo tamanho da mão, com histerese (entrar/sair), filtro
 * One Euro e cooldown para evitar clique duplo. Os limiares precisam de ajuste em aparelho real.
 */
class PokeDetector(
    var enterDepth: Float = 0.35f,
    var exitDepth: Float = 0.18f,
    private val cooldownMs: Long = 220L
) {
    enum class Phase { HOVER, DOWN, MOVE, UP, LOST }
    data class Event(val phase: Phase, val x: Float, val y: Float)

    var lastDepth = 0f
        private set

    private val fx = OneEuroFilter()
    private val fy = OneEuroFilter()
    private val fd = OneEuroFilter(minCutoff = 2.0f, beta = 0.02f)
    private var pressed = false
    private var tracking = false
    private var lastUpAt = 0L

    fun update(hand: List<NormalizedLandmark>?, nowMs: Long, map: (Float, Float) -> PointF): Event? {
        if (hand == null || !isPointing(hand)) {
            val wasPressed = pressed
            val wasTracking = tracking
            pressed = false; tracking = false
            fx.reset(); fy.reset(); fd.reset()
            return when {
                wasPressed -> Event(Phase.UP, 0f, 0f).also { lastUpAt = nowMs }
                wasTracking -> Event(Phase.LOST, 0f, 0f)
                else -> null
            }
        }
        tracking = true
        val tip = hand[8]
        val p = map(tip.x(), tip.y())
        val x = fx.filter(p.x, nowMs)
        val y = fy.filter(p.y, nowMs)
        val size = hypot(hand[0].x() - hand[9].x(), hand[0].y() - hand[9].y()).coerceAtLeast(1e-3f)
        val depth = fd.filter((hand[5].z() - tip.z()) / size, nowMs)
        lastDepth = depth

        return if (!pressed) {
            if (depth > enterDepth && nowMs - lastUpAt > cooldownMs) {
                pressed = true
                Event(Phase.DOWN, x, y) // ação executada no instante do toque
            } else Event(Phase.HOVER, x, y)
        } else {
            if (depth < exitDepth) {
                pressed = false; lastUpAt = nowMs
                Event(Phase.UP, x, y)
            } else Event(Phase.MOVE, x, y)
        }
    }

    companion object {
        private fun d(a: NormalizedLandmark, b: NormalizedLandmark) =
            hypot(a.x() - b.x(), a.y() - b.y())

        /** Indicador estendido e médio/anelar/mindinho recolhidos. */
        fun isPointing(h: List<NormalizedLandmark>): Boolean {
            val w = h[0]
            val indexOut = d(h[8], w) > d(h[6], w) * 1.1f
            val othersCurled = d(h[12], w) < d(h[10], w) * 1.05f &&
                d(h[16], w) < d(h[14], w) * 1.05f &&
                d(h[20], w) < d(h[18], w) * 1.05f
            return indexOut && othersCurled
        }
    }
}
