package com.simplelauncher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimeWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 46f
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 0, 0, 0)
        textSize = 46f
    }

    private val handler = Handler(Looper.getMainLooper())
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var text = formatter.format(Date())

    private val ticker = object : Runnable {
        override fun run() {
            text = formatter.format(Date())
            invalidate()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText(text, 4f, 52f, shadowPaint)
        canvas.drawText(text, 2f, 50f, textPaint)
    }
}
