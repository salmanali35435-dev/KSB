package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.model.QuizCatalog
import com.example.model.QuizQuestion
import com.example.model.StudioSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class SlideRenderer(private val context: Context) {

    companion object {
        const val CW = 1280
        const val CH = 720
        const val U = CW / 100f // 12.8px

        private val rtlRegex = Regex("""[\u0590-\u08FF\uFB1D-\uFDFF\uFE70-\uFEFF]""")

        fun isRtl(text: String?): Boolean = !text.isNullOrEmpty() && rtlRegex.containsMatchIn(text)

        fun shadeColor(hex: String, factor: Float): Int {
            val c = Color.parseColor(hex)
            val r = (Color.red(c) * (1f - factor)).toInt().coerceIn(0, 255)
            val g = (Color.green(c) * (1f - factor)).toInt().coerceIn(0, 255)
            val b = (Color.blue(c) * (1f - factor)).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        }

        fun optFractions(q: QuizQuestion, optLabel: String): FloatArray {
            var s = "${q.q}. "
            val starts = FloatArray(4)
            val opts = listOf("A" to q.a, "B" to q.b, "C" to q.c, "D" to q.d)
            opts.forEachIndexed { i, (letter, txt) ->
                starts[i] = s.length.toFloat()
                s += "$optLabel $letter, $txt. "
            }
            val total = max(1, s.length).toFloat()
            return FloatArray(4) { starts[it] / total }
        }
    }

    data class FrameState(
        val question: QuizQuestion,
        val questionIndex: Int,
        val totalQuestions: Int,
        val reveal: Boolean,
        val progress: Float?, // null = hidden, 0f..1f = loading bar
        val animElapsed: Float = 10f, // seconds since question started
        val optTimes: FloatArray = floatArrayOf(0f, 0.4f, 0.8f, 1.2f),
        val revealElapsed: Float = 0f,
        val globalTime: Float = 0f
    )

    data class IOState(
        val text: String,
        val anim: String,
        val themeIndex: Int,
        val durationSec: Float,
        val elapsedSec: Float
    )

    private val typefaceCache = mutableMapOf<String, Typeface>()
    private val decoCache = mutableMapOf<String, Bitmap>()
    private var cachedCustomBgPath: String? = null
    private var cachedCustomBgBitmap: Bitmap? = null

    private fun typefaceFor(fontKey: String): Typeface {
        val key = fontKey.ifBlank { "default" }
        return typefaceCache.getOrPut(key) {
            val font = QuizCatalog.fontByKey(key)
            val loaded = font.fontRes?.let { resId ->
                runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull()
            }
            if (loaded != null) {
                Typeface.create(loaded, Typeface.BOLD)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
        }
    }

    private fun customBitmap(path: String?): Bitmap? {
        if (path.isNullOrBlank()) {
            cachedCustomBgPath = null
            cachedCustomBgBitmap = null
            return null
        }
        if (path == cachedCustomBgPath && cachedCustomBgBitmap != null) {
            return cachedCustomBgBitmap
        }
        val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        cachedCustomBgPath = path
        cachedCustomBgBitmap = bmp
        return bmp
    }

    private fun decoBitmap(design: String, bgHex: String, acHex: String): Bitmap {
        val key = "$design|$bgHex|$acHex"
        decoCache[key]?.let { return it }
        if (decoCache.size > 24) {
            decoCache.clear()
        }
        val bmp = Bitmap.createBitmap(CW, CH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Color.parseColor(bgHex)
        val bg2 = shadeColor(bgHex, 0.38f)
        val ac = Color.parseColor(acHex)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, CW.toFloat(), CH.toFloat(), bg, bg2, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, CW.toFloat(), CH.toFloat(), bgPaint)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        when (design) {
            "circles" -> {
                p.style = Paint.Style.STROKE
                p.color = ac
                p.strokeWidth = 3f * U
                c.drawCircle(CW - 2f * U, 4f * U, 7.5f * U, p)
                c.drawCircle(U, CH - 3f * U, 7.5f * U, p)
            }
            "rings" -> {
                p.style = Paint.Style.STROKE
                p.color = Color.argb(36, 255, 255, 255)
                p.strokeWidth = 0.35f * U
                for (i in 1..7) {
                    c.drawCircle(CW.toFloat(), 0f, i * 6f * U, p)
                    c.drawCircle(0f, CH.toFloat(), i * 6f * U, p)
                }
                p.color = ac
                p.alpha = 140
                p.strokeWidth = 0.5f * U
                c.drawCircle(CW.toFloat(), 0f, 24f * U, p)
                c.drawCircle(0f, CH.toFloat(), 24f * U, p)
            }
            "dots" -> {
                p.style = Paint.Style.FILL
                p.color = Color.argb(44, 255, 255, 255)
                var y = 1.2f * U
                while (y < CH) {
                    var x = 1.2f * U
                    while (x < CW) {
                        c.drawCircle(x, y, 0.2f * U, p)
                        x += 2.4f * U
                    }
                    y += 2.4f * U
                }
            }
            "stripes" -> {
                p.style = Paint.Style.STROKE
                p.color = Color.argb(18, 255, 255, 255)
                p.strokeWidth = 1.3f * U
                var x = -CH.toFloat()
                while (x < CW) {
                    c.drawLine(x, CH.toFloat(), x + CH, 0f, p)
                    x += 4f * U
                }
            }
            "grid" -> {
                p.style = Paint.Style.STROKE
                p.color = Color.argb(20, 255, 255, 255)
                p.strokeWidth = 1.2f
                var x = 0f
                while (x <= CW) {
                    c.drawLine(x, 0f, x, CH.toFloat(), p)
                    x += 5f * U
                }
                var y = 0f
                while (y <= CH) {
                    c.drawLine(0f, y, CW.toFloat(), y, p)
                    y += 5f * U
                }
            }
            "waves" -> {
                p.style = Paint.Style.FILL
                val waves = listOf(Triple(0.78f, Color.argb(20, 255, 255, 255), 255), Triple(0.86f, ac, 56))
                waves.forEachIndexed { idx, (yy, col, alpha) ->
                    p.color = col
                    p.alpha = alpha
                    val path = Path()
                    path.moveTo(0f, CH.toFloat())
                    path.lineTo(0f, CH * yy)
                    val amp = if (idx == 1) 0.07f else 0.1f
                    for (k in 0 until 4) {
                        val x0 = k * CW / 4f
                        path.quadTo(x0 + CW / 8f, CH * (yy - amp), x0 + CW / 4f, CH * yy)
                    }
                    path.lineTo(CW.toFloat(), CH.toFloat())
                    path.close()
                    c.drawPath(path, p)
                }
            }
            "bokeh" -> {
                p.style = Paint.Style.FILL
                var s = 7L
                fun rnd(): Float {
                    s = (s * 16807L) % 2147483647L
                    return s / 2147483647f
                }
                for (i in 0 until 18) {
                    val alpha = ((0.06f + rnd() * 0.10f) * 255).toInt()
                    p.color = if (i % 3 != 0) Color.WHITE else ac
                    p.alpha = alpha
                    val rx = rnd() * CW
                    val ry = rnd() * CH
                    val rad = (2f + rnd() * 8f) * U
                    c.drawCircle(rx, ry, rad, p)
                }
            }
            "spot" -> {
                p.style = Paint.Style.FILL
                p.shader = RadialGradient(
                    CW / 2f, CH * 0.4f, CW * 0.65f,
                    Color.argb(66, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP
                )
                c.drawRect(0f, 0f, CW.toFloat(), CH.toFloat(), p)
            }
        }
        decoCache[key] = bmp
        return bmp
    }

    private fun drawBackground(c: Canvas, design: String, bgHex: String, acHex: String, customBgPath: String?) {
        val custom = customBitmap(customBgPath)
        if (custom != null) {
            c.drawColor(Color.parseColor(bgHex))
            val scale = max(CW.toFloat() / custom.width, CH.toFloat() / custom.height)
            val w = custom.width * scale
            val h = custom.height * scale
            val dst = RectF((CW - w) / 2f, (CH - h) / 2f, (CW + w) / 2f, (CH + h) / 2f)
            c.drawBitmap(custom, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawColor(Color.argb(115, 0, 0, 0))
        } else {
            c.drawBitmap(decoBitmap(design, bgHex, acHex), 0f, 0f, null)
        }
    }

    private fun cl(x: Float): Float = x.coerceIn(0f, 1f)
    private fun ease(x: Float): Float = 1f - (1f - x).pow(3)
    private fun back(x: Float): Float {
        val a = 1.70158f
        val b = a + 1f
        return 1f + b * (x - 1f).pow(3) + a * (x - 1f).pow(2)
    }

    private fun bnc(xInput: Float): Float {
        var x = xInput
        val n = 7.5625f
        val d = 2.75f
        return when {
            x < 1f / d -> n * x * x
            x < 2f / d -> {
                x -= 1.5f / d
                n * x * x + 0.75f
            }
            x < 2.5f / d -> {
                x -= 2.25f / d
                n * x * x + 0.9375f
            }
            else -> {
                x -= 2.625f / d
                n * x * x + 0.984375f
            }
        }
    }

    private fun applyEntranceTransform(c: Canvas, cx: Float, cy: Float, style: String, pRaw: Float): Float {
        val p = cl(pRaw)
        c.translate(cx, cy)
        var alphaMul = 1f
        when (style) {
            "fade" -> alphaMul = cl(p * 1.4f)
            "slide" -> {
                c.translate((1f - ease(p)) * -CW * 0.28f, 0f)
                alphaMul = cl(p * 2f)
            }
            "zoom" -> {
                val s = 0.55f + 0.45f * back(p)
                c.scale(s, s)
                alphaMul = cl(p * 2.5f)
            }
            "bounce" -> {
                c.translate(0f, -(1f - bnc(p)) * CH * 0.35f)
                alphaMul = cl(p * 3f)
            }
        }
        c.translate(-cx, -cy)
        return alphaMul
    }

    fun drawQuizFrame(c: Canvas, settings: StudioSettings, customBgPath: String?, st: FrameState) {
        val (theme, design) = QuizCatalog.effectiveLook(settings, st.questionIndex)
        val acColor = Color.parseColor(theme.accentHex)
        val lang = QuizCatalog.langByKey(settings.lang)
        val animStyle = if (settings.qAnimOn) settings.qAnim.ifBlank { "fade" } else null
        val tf = typefaceFor(st.question.font.ifBlank { settings.font })

        drawBackground(c, design, theme.bgHex, theme.accentHex, customBgPath)

        val x0 = 7f * U
        val w = CW - 14f * U
        val r = st.question

        // Fit question text (up to 3 lines)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            color = Color.WHITE
        }
        var qpx = 3.8f * U
        var qLayout: StaticLayout
        val maxTextW = (w - 6f * U).toInt().coerceAtLeast(100)
        while (true) {
            tp.textSize = qpx
            qLayout = StaticLayout.Builder.obtain(r.q, 0, r.q.length, tp, maxTextW)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(false)
                .build()
            if (qLayout.lineCount <= 3 || qpx <= 2.3f * U) break
            qpx -= 0.2f * U
        }
        val qContentH = qLayout.height.toFloat().coerceAtLeast(qpx * 1.3f)
        val qh = 2.3f * U + qContentH + 2.3f * U
        var oh = 7.2f * U
        var g1 = 5f * U
        var total = qh + g1 + 2 * oh + 3f * U + 3.5f * U + 4.5f * U
        if (total > 47.2f * U) {
            oh = 6.4f * U
            g1 = 3.4f * U
            total = qh + g1 + 2 * oh + 3f * U + 3.5f * U + 4.5f * U
        }
        var y = max((CH - total) / 2f, 6.4f * U)

        // Draw Question Box
        c.save()
        var qAlpha = 1f
        if (animStyle != null) {
            qAlpha = applyEntranceTransform(c, CW / 2f, y + qh / 2f, animStyle, st.animElapsed / 0.8f)
        }
        val qRect = RectF(x0, y, x0 + w, y + qh)
        val boxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb((26 * qAlpha).toInt(), 255, 255, 255)
        }
        val boxStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = acColor
            alpha = (255 * qAlpha).toInt()
            strokeWidth = 0.25f * U
        }
        c.drawRoundRect(qRect, 2.4f * U, 2.4f * U, boxFill)
        c.drawRoundRect(qRect, 2.4f * U, 2.4f * U, boxStroke)

        c.save()
        c.translate(x0 + 3f * U, y + 2.3f * U)
        tp.alpha = (255 * qAlpha).toInt()
        qLayout.draw(c)
        c.restore()
        c.restore()

        // Draw Question Number Pill (top-left)
        val badgeText = "${st.questionIndex + 1} / ${st.totalQuestions}"
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            textSize = 2f * U
            textAlign = Paint.Align.CENTER
        }
        val bw = badgePaint.measureText(badgeText) + 3.4f * U
        val bh = 3.8f * U
        val bx = 3f * U
        val by = 1.6f * U
        val badgeRect = RectF(bx, by, bx + bw, by + bh)
        c.drawRoundRect(badgeRect, bh / 2f, bh / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(82, 0, 0, 0)
        })
        c.drawRoundRect(badgeRect, bh / 2f, bh / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = acColor
            strokeWidth = 0.25f * U
        })
        badgePaint.color = acColor
        val fmBadge = badgePaint.fontMetrics
        val badgeTextY = by + bh / 2f - (fmBadge.ascent + fmBadge.descent) / 2f
        c.drawText(badgeText, bx + bw / 2f, badgeTextY, badgePaint)

        // Draw 4 Options (2x2 grid)
        y += qh + g1
        val ow = (w - 2f * U) / 2f
        val keys = listOf("A", "B", "C", "D")
        val vals = listOf(r.a, r.b, r.c, r.d)
        val normalizedAns = r.normalizedAns()

        keys.forEachIndexed { i, k ->
            val ox = x0 + (i % 2) * (ow + 2f * U)
            val oy = y + (i / 2) * (oh + 3f * U)
            val isRight = st.reveal && normalizedAns == k

            c.save()
            var optAlpha = 1f
            if (animStyle != null) {
                if (st.reveal) {
                    val ra = st.revealElapsed
                    if (isRight) {
                        val sc = 1f + 0.045f * sin(ra * 7f) * max(0f, 1f - ra / 2.8f)
                        c.translate(ox + ow / 2f, oy + oh / 2f)
                        c.scale(sc, sc)
                        c.translate(-(ox + ow / 2f), -(oy + oh / 2f))
                    } else {
                        optAlpha = 0.45f
                    }
                } else {
                    val ot = st.optTimes.getOrElse(i) { i * 0.4f }
                    optAlpha = applyEntranceTransform(c, ox + ow / 2f, oy + oh / 2f, animStyle, (st.animElapsed - ot) / 0.5f)
                }
            }

            val optRect = RectF(ox, oy, ox + ow, oy + oh)
            if (isRight) {
                if (animStyle != null) {
                    val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor("#7DFF7D")
                        maskFilter = BlurMaskFilter(2f * U, BlurMaskFilter.Blur.OUTER)
                    }
                    c.drawRoundRect(optRect, 4f * U, 4f * U, glowPaint)
                }
                c.drawRoundRect(optRect, 4f * U, 4f * U, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor("#2E7D32")
                    alpha = (255 * optAlpha).toInt()
                })
            } else {
                c.drawRoundRect(optRect, 4f * U, 4f * U, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb((13 * optAlpha).toInt(), 255, 255, 255)
                })
            }

            c.drawRoundRect(optRect, 4f * U, 4f * U, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.25f * U
                color = if (isRight) Color.parseColor("#99FF99") else Color.WHITE
                alpha = (255 * optAlpha).toInt()
            })

            val optText = "$k.  ${vals[i]}"
            val rtl = isRtl(vals[i])
            val optPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                color = Color.WHITE
                alpha = (255 * optAlpha).toInt()
                textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
            }
            var px = 2.7f * U
            val maxOptW = ow - 5f * U
            while (px > 12f) {
                optPaint.textSize = px
                if (optPaint.measureText(optText) <= maxOptW) break
                px -= 1f
            }
            val fmOpt = optPaint.fontMetrics
            val ty = oy + oh / 2f - (fmOpt.ascent + fmOpt.descent) / 2f
            val tx = if (rtl) ox + ow - 2.4f * U else ox + 2.4f * U
            c.drawText(optText, tx, ty, optPaint)
            c.restore()
        }

        // Footer: Answer or Loading Bar
        y += 2 * oh + 3f * U + 3.5f * U
        if (st.reveal) {
            val ansText = "✔ ${lang.ansLabel} $normalizedAns: ${r.correctOptionText()}"
            val ansPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = tf
                color = acColor
                textAlign = Paint.Align.CENTER
            }
            var px = 2.8f * U
            while (px > 12f) {
                ansPaint.textSize = px
                if (ansPaint.measureText(ansText) <= w) break
                px -= 1f
            }
            c.save()
            var ansAlpha = 1f
            if (animStyle != null) {
                ansAlpha = applyEntranceTransform(c, CW / 2f, y + 2.6f * U, "zoom", st.revealElapsed / 0.5f)
            }
            ansPaint.alpha = (255 * ansAlpha).toInt()
            val fmAns = ansPaint.fontMetrics
            val ay = y + 2.6f * U - (fmAns.ascent + fmAns.descent) / 2f
            c.drawText(ansText, CW / 2f, ay, ansPaint)
            c.restore()
        } else if (st.progress != null) {
            drawBar(c, settings, x0, y, w, st.progress.coerceIn(0f, 1f), st.globalTime)
        }

        // Watermark
        if (settings.wm.isNotBlank()) {
            drawWatermark(c, settings, tf)
        }
    }

    private fun drawBar(c: Canvas, settings: StudioSettings, x: Float, fy: Float, w: Float, p: Float, t: Float) {
        val barColor = runCatching { Color.parseColor(settings.bar) }.getOrElse { Color.parseColor("#2979FF") }
        val st = settings.style
        val y = if (st == "thin") fy + 2f * U else fy + U
        val h = if (st == "thin") 0.5f * U else 2.5f * U
        val rad = if (st == "thin") U else 2f * U
        val outerRect = RectF(x, y, x + w, y + h)

        c.drawRoundRect(outerRect, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(69, 0, 0, 0)
        })

        var ix = x
        var iy = y
        var iw = w
        var ih = h
        var innerRad = 2f * U
        if (st == "round") {
            ix += 0.4f * U
            iy += 0.4f * U
            iw -= 0.8f * U
            ih -= 0.8f * U
            innerRad = 3f * U
        }
        val fw = max(0f, iw * p)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor }

        if (st == "glow") {
            if (fw > 1f) {
                val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = barColor
                    maskFilter = BlurMaskFilter(2f * U, BlurMaskFilter.Blur.NORMAL)
                }
                val fwRect = RectF(ix, iy, ix + fw, iy + ih)
                c.drawRoundRect(fwRect, 2f * U, 2f * U, glow)
                c.drawRoundRect(fwRect, 2f * U, 2f * U, fillPaint)
            }
        } else {
            c.save()
            val clipPath = Path().apply {
                addRoundRect(RectF(ix, iy, ix + iw, iy + ih), innerRad, innerRad, Path.Direction.CW)
            }
            c.clipPath(clipPath)
            when (st) {
                "seg" -> {
                    var sx = 0f
                    while (sx < fw) {
                        val sw = min(2.2f * U, fw - sx)
                        c.drawRect(ix + sx, iy, ix + sx + sw, iy + ih, fillPaint)
                        sx += 2.8f * U
                    }
                }
                "stripes" -> {
                    c.save()
                    c.clipRect(ix, iy, ix + fw, iy + ih)
                    c.drawRect(ix, iy, ix + fw, iy + ih, fillPaint)
                    val stripePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(72, 0, 0, 0)
                    }
                    val off = (t * 5.7f * U) % (3.4f * U)
                    var sx = -ih + off - 3.4f * U
                    while (sx < fw) {
                        val path = Path().apply {
                            moveTo(ix + sx, iy + ih)
                            lineTo(ix + sx + 1.7f * U, iy + ih)
                            lineTo(ix + sx + 1.7f * U + ih, iy)
                            lineTo(ix + sx + ih, iy)
                            close()
                        }
                        c.drawPath(path, stripePaint)
                        sx += 3.4f * U
                    }
                    c.restore()
                }
                "gradient" -> {
                    if (fw > 1f) {
                        fillPaint.shader = LinearGradient(
                            ix, 0f, ix + fw, 0f,
                            Color.argb(140, 255, 255, 255),
                            barColor,
                            Shader.TileMode.CLAMP
                        )
                        c.drawRect(ix, iy, ix + fw, iy + ih, fillPaint)
                    }
                }
                "dots" -> {
                    var sx = U
                    val rDot = min(ih * 0.4f, 0.85f * U)
                    while (sx < fw) {
                        c.drawCircle(ix + sx, iy + ih / 2f, rDot, fillPaint)
                        sx += 2f * U
                    }
                }
                else -> {
                    if (st == "neon") fillPaint.alpha = 204
                    c.drawRect(ix, iy, ix + fw, iy + ih, fillPaint)
                }
            }
            c.restore()
        }

        if (st != "thin") {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 0.25f * U
                color = if (st == "neon") barColor else Color.WHITE
                if (st == "neon") {
                    maskFilter = BlurMaskFilter(1.2f * U, BlurMaskFilter.Blur.NORMAL)
                }
            }
            c.drawRoundRect(outerRect, 2f * U, 2f * U, borderPaint)
        }
    }

    private fun drawWatermark(c: Canvas, settings: StudioSettings, tf: Typeface) {
        val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            color = Color.WHITE
            alpha = (settings.wmOp.coerceIn(0.03f, 1f) * 255).toInt()
            textSize = settings.wmSize.coerceIn(1f, 8f) * U
            setShadowLayer(0.3f * U, 0f, 0f, Color.BLACK)
        }
        val fm = wmPaint.fontMetrics
        val (align, x, y) = when (settings.wmPos) {
            "bl" -> Triple(Paint.Align.LEFT, 3f * U, CH - 3.2f * U)
            "tr" -> Triple(Paint.Align.RIGHT, CW - 3f * U, 3.2f * U - fm.ascent)
            "tl" -> Triple(Paint.Align.LEFT, 3f * U, 3.2f * U - fm.ascent)
            "c" -> Triple(Paint.Align.CENTER, CW / 2f, CH / 2f - (fm.ascent + fm.descent) / 2f)
            else -> Triple(Paint.Align.RIGHT, CW - 3f * U, CH - 3.2f * U)
        }
        wmPaint.textAlign = align
        c.drawText(settings.wm, x, y, wmPaint)
    }

    private data class WordItem(
        val word: String,
        val cx: Float,
        val cy: Float,
        val width: Float,
        val lineIdx: Int,
        val isRtlWord: Boolean
    )

    fun drawIOFrame(c: Canvas, settings: StudioSettings, customBgPath: String?, st: IOState) {
        val (theme, design) = QuizCatalog.effectiveLook(settings, st.themeIndex)
        val acColor = Color.parseColor(theme.accentHex)
        val anim = st.anim.ifBlank { "none" }
        val dur = max(st.durationSec, 0.5f)
        val el = st.elapsedSec
        val tf = typefaceFor(settings.font)

        drawBackground(c, design, theme.bgHex, theme.accentHex, customBgPath)

        val maxW = CW - 16f * U
        val maxH = CH * 0.7f
        val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = tf
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

        val paras = st.text.trim().split(Regex("""\n+""")).filter { it.isNotEmpty() }
        if (paras.isEmpty()) return

        var px = 7f * U
        var lines: List<String>
        while (true) {
            pText.textSize = px
            lines = buildList {
                for (p in paras) {
                    addAll(wrapWords(pText, p, maxW))
                }
            }
            if (lines.size * px * 1.4f <= maxH || px <= 2f * U) break
            px -= U / 4f
        }

        pText.textSize = px
        val lh = px * 1.4f
        val y0 = CH / 2f - (lines.size * lh) / 2f + lh / 2f
        val sp = pText.measureText(" ")
        val items = mutableListOf<WordItem>()

        lines.forEachIndexed { li, ln ->
            val ws = ln.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val wds = ws.map { pText.measureText(it) }
            val tot = wds.sum() + sp * max(0, ws.size - 1)
            val rtl = isRtl(ln)
            var x = if (rtl) CW / 2f + tot / 2f else CW / 2f - tot / 2f
            ws.forEachIndexed { i, w ->
                val cx = if (rtl) x - wds[i] / 2f else x + wds[i] / 2f
                items.add(WordItem(w, cx, y0 + li * lh, wds[i], li, isRtl(w) || rtl))
                x += if (rtl) -(wds[i] + sp) else wds[i] + sp
            }
        }
        if (items.isEmpty()) return

        // Top and bottom accent bars
        val top = items.first().cy - px * 0.95f
        val bot = items.last().cy + px * 0.95f
        val barAlpha = if (anim == "none") 1f else cl(el / 0.5f + 0.2f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = acColor
            alpha = (barAlpha * 255).toInt()
        }
        c.drawRoundRect(
            RectF(CW / 2f - 7f * U, top - 1.2f * U, CW / 2f + 7f * U, top - 0.6f * U),
            0.3f * U, 0.3f * U, barPaint
        )
        c.drawRoundRect(
            RectF(CW / 2f - 7f * U, bot + 0.6f * U, CW / 2f + 7f * U, bot + 1.2f * U),
            0.3f * U, 0.3f * U, barPaint
        )

        val n = items.size
        val prog = cl(el / (dur * 0.95f))
        val totalChars = items.sumOf { it.word.length }.coerceAtLeast(1)
        var seen = 0
        val fm = pText.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) / 2f

        items.forEachIndexed { i, o ->
            val ti = (i.toFloat() / n) * dur * 0.92f
            val lp = cl((el - ti) / 0.45f)
            c.save()
            val wordPaint = Paint(pText).apply {
                setShadowLayer(0.5f * U, 0f, 0f, Color.argb(115, 0, 0, 0))
            }
            var txt = o.word
            var drawIt = true
            when (anim) {
                "type" -> {
                    if (o.isRtlWord) {
                        if (el < ti) drawIt = false
                    } else {
                        val vis = (prog * totalChars).toInt() - seen
                        seen += o.word.length
                        if (vis <= 0) drawIt = false
                        else txt = o.word.take(vis)
                    }
                }
                "fade" -> {
                    val ls = (o.lineIdx.toFloat() / max(1, lines.size)) * dur * 0.9f
                    val p = ease(cl((el - ls) / 0.7f))
                    wordPaint.alpha = (p * 255).toInt()
                    c.translate(0f, (1f - p) * 3f * U)
                }
                "zoom" -> {
                    if (lp <= 0f) drawIt = false
                    else {
                        val s = back(lp)
                        wordPaint.alpha = (cl(lp * 3f) * 255).toInt()
                        c.translate(o.cx, o.cy)
                        c.scale(s, s)
                        c.translate(-o.cx, -o.cy)
                    }
                }
                "highlight" -> {
                    val isCur = el >= ti && el < ti + dur / n
                    val past = el >= ti + dur / n
                    wordPaint.alpha = if (past || isCur) 255 else 76
                    if (isCur) {
                        wordPaint.color = acColor
                        wordPaint.setShadowLayer(1.6f * U, 0f, 0f, acColor)
                        c.translate(o.cx, o.cy)
                        c.scale(1.12f, 1.12f)
                        c.translate(-o.cx, -o.cy)
                    }
                }
                "glow" -> {
                    if (lp <= 0f) drawIt = false
                    else {
                        wordPaint.alpha = (cl(lp * 3f) * 255).toInt()
                        c.translate(0f, -sin(lp * PI.toFloat()) * 1.8f * U * (1f - lp * 0.5f))
                        wordPaint.setShadowLayer(if (lp < 1f) 2.6f * U else 0.9f * U, 0f, 0f, acColor)
                    }
                }
            }
            if (drawIt) {
                if (anim == "type" && !o.isRtlWord) {
                    wordPaint.textAlign = Paint.Align.LEFT
                    c.drawText(txt, o.cx - o.width / 2f, o.cy + baselineOffset, wordPaint)
                } else {
                    wordPaint.textAlign = Paint.Align.CENTER
                    c.drawText(txt, o.cx, o.cy + baselineOffset, wordPaint)
                }
            }
            c.restore()
        }

        if (settings.wm.isNotBlank()) {
            drawWatermark(c, settings, tf)
        }
    }

    private fun wrapWords(p: Paint, text: String, maxW: Float): List<String> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (w in text.split(Regex("""\s+"""))) {
            if (w.isEmpty()) continue
            val t = if (cur.isEmpty()) w else "$cur $w"
            if (cur.isNotEmpty() && p.measureText(t) > maxW) {
                lines.add(cur)
                cur = w
            } else {
                cur = t
            }
        }
        if (cur.isNotEmpty()) lines.add(cur)
        return lines
    }
}
