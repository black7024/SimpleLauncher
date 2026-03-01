package com.simplelauncher.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class WeatherWidgetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var temperature: String = "--°C"
    private var weatherCode: Int = -1

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 238, 238, 238)
        textSize = 28f
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(232, 238, 238, 238)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 238, 238, 238)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var fetchTask: FetchWeatherTask? = null

    private val refresher = object : Runnable {
        override fun run() {
            fetchTask?.cancel(true)
            fetchTask = FetchWeatherTask().also { it.execute() }
            handler.postDelayed(this, 30 * 60 * 1000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(refresher)
        handler.post(refresher)
    }

    override fun onDetachedFromWindow() {
        fetchTask?.cancel(true)
        fetchTask = null
        handler.removeCallbacks(refresher)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawWeatherIcon(canvas, 16f, 4f, 34f)
        canvas.drawText(temperature, 62f, 34f, textPaint)
    }

    private fun drawWeatherIcon(canvas: Canvas, x: Float, y: Float, size: Float) {
        if (weatherCode in 0..1) {
            canvas.drawCircle(x + size / 2f, y + size / 2f, size * 0.25f, fillPaint)
            return
        }
        val cloud = RectF(x + 4f, y + 14f, x + size, y + size)
        canvas.drawRoundRect(cloud, 10f, 10f, fillPaint)
        if (weatherCode in 51..67 || weatherCode in 80..82) {
            val line = min(size * 0.16f, 5f)
            canvas.drawLine(x + 10f, y + size + 2f, x + 6f, y + size + 8f, iconPaint)
            canvas.drawLine(x + 20f, y + size + 2f, x + 16f, y + size + 8f, iconPaint)
            canvas.drawLine(x + 30f, y + size + 2f, x + 26f, y + size + 8f, iconPaint)
            iconPaint.strokeWidth = line
            canvas.drawPoint(x + 10f, y + size + 9f, iconPaint)
            canvas.drawPoint(x + 20f, y + size + 9f, iconPaint)
            canvas.drawPoint(x + 30f, y + size + 9f, iconPaint)
            iconPaint.strokeWidth = 2f
        }
    }

    private inner class FetchWeatherTask : AsyncTask<Unit, Unit, Pair<String, Int>?>() {
        override fun doInBackground(vararg params: Unit?): Pair<String, Int>? {
            var connection: HttpURLConnection? = null
            return try {
                if (isCancelled) return null
                val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=39.90&longitude=116.40&current=temperature_2m,weather_code"
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.requestMethod = "GET"
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                val current = root.getJSONObject("current")
                val temp = current.optDouble("temperature_2m", 0.0)
                val code = current.optInt("weather_code", -1)
                String.format("%.0f°C", temp) to code
            } catch (_: Throwable) {
                null
            } finally {
                connection?.disconnect()
            }
        }

        override fun onPostExecute(result: Pair<String, Int>?) {
            if (isCancelled) return
            if (result == null) return
            temperature = result.first
            weatherCode = result.second
            invalidate()
        }
    }
}
