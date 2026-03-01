package com.simplelauncher.ui.draw

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Path
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import com.simplelauncher.perf.PerformanceConfig
import java.util.Locale
import kotlin.math.min

object DrawableFactory {
    fun createRootBackground(context: Context, themeColor: Int): Drawable {
        if (PerformanceConfig.simpleBackground(context)) {
            return ColorDrawable(Color.rgb(12, 12, 12))
        }

        val gradientLayer = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.argb(250, 22, 22, 24),
                Color.argb(242, 12, 12, 14)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
        }

        if (!PerformanceConfig.glassmorphism(context)) {
            return gradientLayer
        }

        val accentVeil = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                applyAlpha(themeColor, 0.18f),
                Color.argb(0, 0, 0, 0)
            )
        )

        return LayerDrawable(
            arrayOf(
                gradientLayer,
                accentVeil,
                NoiseOverlayDrawable()
            )
        )
    }

    fun createCardDrawable(context: Context, themeColor: Int, focused: Boolean): Drawable {
        val cardRadius = dp(context, 16f)
        val frameRadius = dp(context, 8f)
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cardRadius
            setColor(Color.argb(if (PerformanceConfig.glassmorphism(context)) 172 else 238, 20, 20, 22))
            setStroke(dpInt(context, 1f), applyAlpha(themeColor, if (focused) 0.65f else 0.32f))
        }

        if (!focused) {
            return base
        }

        val focusFrame = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = frameRadius
            setColor(Color.TRANSPARENT)
            setStroke(dpInt(context, 2f), Color.WHITE)
        }

        return LayerDrawable(arrayOf(base, focusFrame)).apply {
            val inset = dpInt(context, 4f)
            setLayerInset(1, inset, inset, inset, inset)
        }
    }

    fun createActionButtonDrawable(context: Context, themeColor: Int, focused: Boolean): Drawable {
        val radius = dp(context, 12f)
        val strokeColor = if (focused) Color.WHITE else applyAlpha(themeColor, 0.7f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.argb(140, 18, 18, 20))
            setStroke(dpInt(context, if (focused) 2f else 1f), strokeColor)
        }
    }

    fun createMonogramIconDrawable(label: String, themeColor: Int): Drawable {
        return MonogramIconDrawable(label = label, themeColor = themeColor)
    }

    fun createFolderIconDrawable(themeColor: Int): Drawable {
        return FolderIconDrawable(themeColor)
    }

    fun createShortcutIconDrawable(themeColor: Int): Drawable {
        return ShortcutIconDrawable(themeColor)
    }

    private fun applyAlpha(color: Int, alpha: Float): Int {
        val resolved = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(resolved, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun dp(context: Context, value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        )
    }

    private fun dpInt(context: Context, value: Float): Int {
        return dp(context, value).toInt().coerceAtLeast(1)
    }
}

private class FolderIconDrawable(
    themeColor: Int
) : Drawable() {
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(240, 44, 44, 50)
    }

    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(240, 62, 62, 70)
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(224, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
    }

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        val body = RectF(rect.left + 4f, rect.top + rect.height() * 0.28f, rect.right - 4f, rect.bottom - 4f)
        val tab = RectF(rect.left + 8f, rect.top + 6f, rect.left + rect.width() * 0.54f, rect.top + rect.height() * 0.45f)
        canvas.drawRoundRect(body, 10f, 10f, bodyPaint)
        canvas.drawRoundRect(tab, 8f, 8f, tabPaint)
        canvas.drawRoundRect(body, 10f, 10f, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        bodyPaint.alpha = alpha
        tabPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bodyPaint.colorFilter = colorFilter
        tabPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class ShortcutIconDrawable(
    themeColor: Int
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.argb(232, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(150, 36, 36, 40)
    }

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        val body = RectF(rect.left + 8f, rect.top + 8f, rect.right - 8f, rect.bottom - 8f)
        canvas.drawRoundRect(body, 12f, 12f, fillPaint)
        canvas.drawRoundRect(body, 12f, 12f, paint)

        val path = Path().apply {
            moveTo(body.centerX() - 8f, body.centerY())
            lineTo(body.centerX() + 8f, body.centerY())
            moveTo(body.centerX() + 3f, body.centerY() - 5f)
            lineTo(body.centerX() + 8f, body.centerY())
            lineTo(body.centerX() + 3f, body.centerY() + 5f)
        }
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        fillPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class NoiseOverlayDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: BitmapShader? = null
    private var shaderMatrix = Matrix()

    override fun draw(canvas: Canvas) {
        ensureShader()
        val localShader = shader ?: return
        localShader.setLocalMatrix(shaderMatrix)
        paint.shader = localShader
        canvas.drawRect(bounds, paint)
    }

    private fun ensureShader() {
        if (shader != null) {
            return
        }
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(73L)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val noise = 28 + random.nextInt(48)
                val alpha = if ((x + y) % 5 == 0) 24 else 16
                bitmap.setPixel(x, y, Color.argb(alpha, noise, noise, noise))
            }
        }
        shader = BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        shaderMatrix = Matrix()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class MonogramIconDrawable(
    label: String,
    themeColor: Int
) : Drawable() {
    private val initial = label.trim().firstOrNull()?.toString()?.toUpperCase(Locale.getDefault()) ?: "A"

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 34, 34, 38)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(180, Color.red(themeColor), Color.green(themeColor), Color.blue(themeColor))
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(48, 0, 0, 0)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(240, 242, 242, 242)
        textAlign = Paint.Align.CENTER
        setShadowLayer(1f, 0f, 1f, Color.argb(110, 0, 0, 0))
    }

    private val textBounds = android.graphics.Rect()

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        if (rect.isEmpty) return

        val size = min(rect.width(), rect.height())
        val iconRect = RectF(
            rect.left + (rect.width() - size) / 2f,
            rect.top + (rect.height() - size) / 2f,
            rect.left + (rect.width() + size) / 2f,
            rect.top + (rect.height() + size) / 2f
        )
        val radius = size * 0.26f

        val shadowRect = RectF(iconRect)
        shadowRect.offset(0f, 1f)
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)
        canvas.drawRoundRect(iconRect, radius, radius, backgroundPaint)
        canvas.drawRoundRect(iconRect, radius, radius, borderPaint)

        textPaint.textSize = size * 0.42f
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val baseLine = iconRect.centerY() + textBounds.height() * 0.35f
        canvas.drawText(initial, iconRect.centerX(), baseLine, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        backgroundPaint.alpha = alpha
        borderPaint.alpha = alpha
        textPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        backgroundPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
