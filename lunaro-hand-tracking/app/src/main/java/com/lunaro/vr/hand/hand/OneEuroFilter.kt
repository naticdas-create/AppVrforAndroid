package com.lunaro.vr.hand.hand

import kotlin.math.PI
import kotlin.math.abs

/** Filtro One Euro: remove jitter com pouca latência (suaviza parado, segue rápido em movimento). */
class OneEuroFilter(
    private val minCutoff: Float = 1.2f,
    private val beta: Float = 0.05f,
    private val dCutoff: Float = 1.0f
) {
    private var xPrev = 0f
    private var dxPrev = 0f
    private var tPrev = 0L
    private var init = false

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    fun reset() { init = false }

    fun filter(x: Float, tMs: Long): Float {
        if (!init) { init = true; xPrev = x; dxPrev = 0f; tPrev = tMs; return x }
        val dt = ((tMs - tPrev).coerceAtLeast(1L)) / 1000f
        tPrev = tMs
        val dx = (x - xPrev) / dt
        val aD = alpha(dCutoff, dt)
        val dxHat = aD * dx + (1 - aD) * dxPrev
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(cutoff, dt)
        val xHat = a * x + (1 - a) * xPrev
        xPrev = xHat; dxPrev = dxHat
        return xHat
    }
}
