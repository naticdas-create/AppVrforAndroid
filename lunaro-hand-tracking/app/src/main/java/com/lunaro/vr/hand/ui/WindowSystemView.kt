package com.lunaro.vr.hand.ui

import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.lunaro.vr.hand.hand.PokeDetector

/**
 * Interface espacial estilo Quest: janelas escuras e translúcidas em arco (Biblioteca, Loja,
 * Configurações rápidas) e barra de apps inferior. Tudo é acionado pelo toque do indicador.
 * As janelas laterais usam perspectiva real (Camera + Matrix) e o toque é testado na matriz inversa.
 */
class WindowSystemView(ctx: Context, private val cb: Callbacks) : View(ctx) {

    interface Callbacks {
        fun onStatus(text: String)
        fun onLaunch(id: String)
        fun onSetting(id: String, value: Float) // toggles: 0/1; sliders: 0..1
    }

    private enum class Kind { PILL, TILE, CARD, TOGGLE, SLIDER, CLOSE, MIN, ICON }
    private class El(val id: String, val label: String, val rect: RectF, val kind: Kind,
                     val color: Int = 0, val glyph: String = "")

    private class Win(val id: String, val title: String, val yaw: Float, val hasTitle: Boolean = true) {
        var cx = 0f; var cy = 0f; var w = 0f; var h = 0f
        var visible = true; var minimized = false
        val els = mutableListOf<El>()
        val m = Matrix(); val inv = Matrix()
        fun full() = RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
    }

    // Se o arco parecer invertido (bordas internas mais próximas), troque o sinal de ARC.
    private val arc = 24f
    private val lib = Win("lib", "Biblioteca", arc)
    private val store = Win("store", "Loja", 0f)
    private val quick = Win("quick", "Configurações rápidas", -arc)
    private val bar = Win("bar", "", 0f, hasTitle = false)
    private val order = listOf(lib, quick, store, bar)

    private var u = 1f
    private var category = 0
    private var selected = ""
    private val toggles = mutableMapOf("outline" to true, "quest" to false, "latency" to true)
    private val sliders = mutableMapOf("thick" to 0.5f, "opacity" to 0.85f)

    private var hover: El? = null
    private var active: El? = null
    private var activeWin: Win? = null

    private val cats = listOf("Início", "Jogos", "Viagens", "Esportes", "Música")
    private val items = listOf(
        listOf("Órbita", "Neon Blocks", "Mar Aberto"),
        listOf("Space Run", "Neon Blocks", "Torre de Vidro"),
        listOf("Alpes 360", "Kyoto Walk", "Deserto Azul"),
        listOf("Quadra Virtual", "Remo VR", "Trilha"),
        listOf("Show Lunar", "Sala de Jazz", "Batida Zero")
    )
    private val palette = intArrayOf(0xFF3B5BDB.toInt(), 0xFF9C36B5.toInt(), 0xFF0CA678.toInt())

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFFE6E9EF.toInt() }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        if (w == 0 || h == 0) return
        val W = w.toFloat(); val H = h.toFloat(); u = H / 1080f
        lib.cx = 0.17f * W; lib.cy = 0.42f * H; lib.w = 0.25f * W; lib.h = 0.56f * H
        store.cx = 0.50f * W; store.cy = 0.42f * H; store.w = 0.44f * W; store.h = 0.60f * H
        quick.cx = 0.83f * W; quick.cy = 0.42f * H; quick.w = 0.25f * W; quick.h = 0.62f * H
        bar.cx = 0.50f * W; bar.cy = 0.90f * H; bar.w = 0.30f * W; bar.h = 0.11f * H
        rebuild()
    }

    private fun updateMatrix(w: Win) {
        val cam = Camera()
        cam.save(); cam.setLocation(0f, 0f, -14f); cam.rotateY(w.yaw); cam.getMatrix(w.m); cam.restore()
        w.m.preTranslate(-w.cx, -w.cy); w.m.postTranslate(w.cx, w.cy)
        w.m.invert(w.inv)
    }

    private fun rebuild() {
        for (w in order) { w.els.clear(); updateMatrix(w) }
        buildStore(); buildLibrary(); buildQuick(); buildBar()
        invalidate()
    }

    private val tb get() = 64f * u

    private fun titleButtons(w: Win) {
        val r = w.full(); val s = 40f * u
        val y = r.top + (tb - s) / 2
        w.els += El("close:${w.id}", "×", RectF(r.right - s - 16 * u, y, r.right - 16 * u, y + s), Kind.CLOSE)
        w.els += El("min:${w.id}", "–", RectF(r.right - 2 * s - 28 * u, y, r.right - s - 28 * u, y + s), Kind.MIN)
    }

    private fun buildStore() {
        val r = store.full(); titleButtons(store)
        if (store.minimized) return
        val pad = 24f * u; val gap = 12f * u
        val pw = (store.w - 2 * pad - 4 * gap) / 5f
        var y = r.top + tb + 12 * u
        cats.forEachIndexed { i, c ->
            val l = r.left + pad + i * (pw + gap)
            store.els += El("cat:$i", c, RectF(l, y, l + pw, y + 52 * u), Kind.PILL)
        }
        y += 52 * u + 24 * u
        val cw = (store.w - 2 * pad - 2 * gap) / 3f; val ch = store.h * 0.50f
        items[category].forEachIndexed { i, n ->
            val l = r.left + pad + i * (cw + gap)
            store.els += El("item:$n", n, RectF(l, y, l + cw, y + ch), Kind.CARD, palette[i])
        }
    }

    private fun buildLibrary() {
        val r = lib.full(); titleButtons(lib)
        if (lib.minimized) return
        val apps = listOf(
            Triple("youtube", "YouTube", 0xFFE53935.toInt()), Triple("netflix", "Netflix", 0xFFB71C1C.toInt()),
            Triple("roblox", "Roblox", 0xFF455A64.toInt()), Triple("spotify", "Spotify", 0xFF1DB954.toInt()),
            Triple("browser", "Navegador", 0xFF3B5BDB.toInt()), Triple("settings", "Ajustes", 0xFF6C7A89.toInt())
        )
        val pad = 28f * u; val s = (lib.w - 2 * pad - 24 * u) / 2f
        apps.forEachIndexed { i, a ->
            val col = i % 2; val row = i / 2
            val l = r.left + pad + col * (s + 24 * u); val t = r.top + tb + 20 * u + row * (s + 56 * u)
            lib.els += El("app:${a.first}", a.second, RectF(l, t, l + s, t + s), Kind.TILE, a.third, a.second.take(1))
        }
    }

    private fun buildQuick() {
        val r = quick.full(); titleButtons(quick)
        if (quick.minimized) return
        val pad = 28f * u; var y = r.top + tb + 16 * u
        listOf("outline" to "Contorno das mãos", "quest" to "Estilo claro (Quest)", "latency" to "Mostrar latência").forEach { (id, l) ->
            quick.els += El("tg:$id", l, RectF(r.left + pad, y, r.right - pad, y + 64 * u), Kind.TOGGLE)
            y += 76 * u
        }
        y += 8 * u
        listOf("thick" to "Espessura do contorno", "opacity" to "Opacidade das janelas").forEach { (id, l) ->
            quick.els += El("sl:$id", l, RectF(r.left + pad, y, r.right - pad, y + 84 * u), Kind.SLIDER)
            y += 100 * u
        }
    }

    private fun buildBar() {
        val r = bar.full(); val s = 0.7f * bar.h; val gap = 28f * u
        val total = 3 * s + 2 * gap; var l = r.centerX() - total / 2; val t = r.centerY() - s / 2
        listOf(Triple("store", "Loja", 0xFFF59F00.toInt()), Triple("lib", "Biblioteca", 0xFF9C36B5.toInt()),
            Triple("quick", "Config.", 0xFF1C7ED6.toInt())).forEach { (id, n, c) ->
            bar.els += El("bar:$id", n, RectF(l, t, l + s, t + s), Kind.ICON, c, n.take(1)); l += s + gap
        }
    }

    private fun win(id: String) = order.first { it.id == id }

    // ---------- Toque ----------
    private fun hit(x: Float, y: Float): Pair<Win, El>? {
        val pt = FloatArray(2)
        for (w in order.asReversed()) {
            if (!w.visible) continue
            pt[0] = x; pt[1] = y; w.inv.mapPoints(pt)
            val e = w.els.lastOrNull { it.rect.contains(pt[0], pt[1]) } ?: continue
            return w to e
        }
        return null
    }

    private fun localX(w: Win, x: Float, y: Float): Float {
        val pt = floatArrayOf(x, y); w.inv.mapPoints(pt); return pt[0]
    }

    private fun setSlider(e: El, w: Win, x: Float) {
        val id = e.id.removePrefix("sl:"); val pad = 20f * u
        val v = ((localX(w, x, e.rect.centerY()) - e.rect.left - pad) / (e.rect.width() - 2 * pad)).coerceIn(0f, 1f)
        sliders[id] = v; cb.onSetting(id, v)
    }

    fun onPointer(ev: PokeDetector.Event) {
        when (ev.phase) {
            PokeDetector.Phase.LOST -> { hover = null; active = null; activeWin = null }
            PokeDetector.Phase.HOVER -> hover = hit(ev.x, ev.y)?.second
            PokeDetector.Phase.DOWN -> hit(ev.x, ev.y)?.let { (w, e) -> hover = e; active = e; activeWin = w; click(w, e, ev.x) }
            PokeDetector.Phase.MOVE -> { val e = active; val w = activeWin; if (e != null && w != null && e.kind == Kind.SLIDER) setSlider(e, w, ev.x) }
            PokeDetector.Phase.UP -> { active = null; activeWin = null }
        }
        invalidate()
    }

    private fun click(w: Win, e: El, x: Float) {
        when {
            e.kind == Kind.CLOSE -> { win(e.id.removePrefix("close:")).visible = false }
            e.kind == Kind.MIN -> { val t = win(e.id.removePrefix("min:")); t.minimized = !t.minimized; rebuild() }
            e.id.startsWith("cat:") -> { category = e.id.removePrefix("cat:").toInt(); rebuild() }
            e.id.startsWith("item:") -> { selected = e.label; cb.onStatus("Loja: ${e.label} selecionado") }
            e.id.startsWith("app:") -> cb.onLaunch(e.id.removePrefix("app:"))
            e.id.startsWith("tg:") -> {
                val id = e.id.removePrefix("tg:"); val v = !(toggles[id] ?: false); toggles[id] = v
                cb.onSetting(id, if (v) 1f else 0f)
            }
            e.kind == Kind.SLIDER -> setSlider(e, w, x)
            e.id.startsWith("bar:") -> {
                val t = win(e.id.removePrefix("bar:"))
                if (!t.visible) { t.visible = true; t.minimized = false } else t.visible = false
                rebuild()
            }
        }
    }

    // ---------- Desenho ----------
    private val winAlpha get() = (255 * (0.35f + 0.65f * (sliders["opacity"] ?: 0.85f))).toInt()

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL; p.color = 0x55000000; c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        for (w in order) {
            if (!w.visible) continue
            c.save(); c.concat(w.m)
            drawWin(c, w)
            c.restore()
        }
    }

    private fun drawWin(c: Canvas, w: Win) {
        val r = w.full()
        if (w.hasTitle && w.minimized) r.bottom = r.top + tb
        val rad = 28f * u
        p.style = Paint.Style.FILL
        p.color = if (w.hasTitle) 0xFF1E2430.toInt() else 0xFF10141C.toInt()
        p.alpha = winAlpha
        c.drawRoundRect(r, if (w.hasTitle) rad else r.height() / 2, if (w.hasTitle) rad else r.height() / 2, p)
        p.alpha = 255
        if (w.hasTitle) { tp.textSize = 26f * u; tp.color = 0xFFE6E9EF.toInt(); c.drawText(w.title, r.centerX(), r.top + tb / 2 + 9 * u, tp) }
        for (e in w.els) drawEl(c, w, e)
        if (w === store && !store.minimized && selected.isNotEmpty()) {
            tp.textSize = 24f * u; tp.color = 0xFFB8C0D0.toInt(); c.drawText("Selecionado: $selected", r.centerX(), r.bottom - 22 * u, tp)
        }
    }

    private fun base(e: El) = when {
        e === active -> 0xFF2F6FEB.toInt()
        e === hover -> 0xFF465066.toInt()
        else -> 0xFF303846.toInt()
    }

    private fun drawEl(c: Canvas, w: Win, e: El) {
        val r = e.rect; p.style = Paint.Style.FILL
        when (e.kind) {
            Kind.PILL -> {
                val sel = e.id == "cat:$category"
                p.color = if (sel) 0xFFE6E9EF.toInt() else base(e)
                c.drawRoundRect(r, r.height() / 2, r.height() / 2, p)
                tp.textSize = 22f * u; tp.color = if (sel) 0xFF1E2430.toInt() else 0xFFE6E9EF.toInt()
                c.drawText(e.label, r.centerX(), r.centerY() + 8 * u, tp)
            }
            Kind.CARD -> {
                p.color = e.color; c.drawRoundRect(r, 22 * u, 22 * u, p)
                if (e === hover || e === active) { p.color = 0x33FFFFFF; c.drawRoundRect(r, 22 * u, 22 * u, p) }
                if (e.label == selected) { p.style = Paint.Style.STROKE; p.strokeWidth = 5 * u; p.color = 0xFFFFFFFF.toInt(); c.drawRoundRect(r, 22 * u, 22 * u, p); p.style = Paint.Style.FILL }
                tp.textSize = 26f * u; tp.color = 0xFFFFFFFF.toInt(); c.drawText(e.label, r.centerX(), r.bottom - 24 * u, tp)
            }
            Kind.TILE, Kind.ICON -> {
                p.color = e.color; c.drawRoundRect(r, 22 * u, 22 * u, p)
                if (e === hover || e === active) { p.color = 0x44FFFFFF; c.drawRoundRect(r, 22 * u, 22 * u, p) }
                tp.textSize = r.height() * 0.5f; tp.color = 0xFFFFFFFF.toInt(); c.drawText(e.glyph, r.centerX(), r.centerY() + r.height() * 0.18f, tp)
                if (e.kind == Kind.TILE) { tp.textSize = 22f * u; tp.color = 0xFFE6E9EF.toInt(); c.drawText(e.label, r.centerX(), r.bottom + 32 * u, tp) }
                else if (win(e.id.removePrefix("bar:")).visible) { p.color = 0xFFFFFFFF.toInt(); c.drawCircle(r.centerX(), r.bottom + 10 * u, 4 * u, p) }
            }
            Kind.CLOSE, Kind.MIN -> {
                p.color = base(e); c.drawCircle(r.centerX(), r.centerY(), r.width() / 2, p)
                tp.textSize = 30f * u; tp.color = 0xFFE6E9EF.toInt(); c.drawText(e.label, r.centerX(), r.centerY() + 10 * u, tp)
            }
            Kind.TOGGLE -> {
                val on = toggles[e.id.removePrefix("tg:")] ?: false
                p.color = base(e); c.drawRoundRect(r, 18 * u, 18 * u, p)
                tp.textAlign = Paint.Align.LEFT; tp.textSize = 22f * u; tp.color = 0xFFE6E9EF.toInt()
                c.drawText(e.label, r.left + 18 * u, r.centerY() + 8 * u, tp); tp.textAlign = Paint.Align.CENTER
                val sw = RectF(r.right - 88 * u, r.centerY() - 20 * u, r.right - 16 * u, r.centerY() + 20 * u)
                p.color = if (on) 0xFF2F6FEB.toInt() else 0xFF5B6478.toInt(); c.drawRoundRect(sw, 20 * u, 20 * u, p)
                p.color = 0xFFFFFFFF.toInt(); c.drawCircle(if (on) sw.right - 20 * u else sw.left + 20 * u, sw.centerY(), 15 * u, p)
            }
            Kind.SLIDER -> {
                val v = sliders[e.id.removePrefix("sl:")] ?: 0f
                tp.textAlign = Paint.Align.LEFT; tp.textSize = 22f * u; tp.color = 0xFFE6E9EF.toInt()
                c.drawText(e.label, r.left, r.top + 24 * u, tp); tp.textAlign = Paint.Align.CENTER
                val x0 = r.left + 20 * u; val x1 = r.right - 20 * u; val cy = r.bottom - 20 * u
                p.color = 0xFF5B6478.toInt(); c.drawRoundRect(x0, cy - 5 * u, x1, cy + 5 * u, 5 * u, 5 * u, p)
                p.color = 0xFF2F6FEB.toInt(); c.drawRoundRect(x0, cy - 5 * u, x0 + (x1 - x0) * v, cy + 5 * u, 5 * u, 5 * u, p)
                p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x0 + (x1 - x0) * v, cy, (if (e === active) 17 else 14) * u, p)
            }
        }
    }
}
