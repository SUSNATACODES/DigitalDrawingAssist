package com.susnatacodes.digitaldrawingassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class SketchBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(24, 23, 24, 31)
        strokeWidth = 1f.dp()
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 32, 191, 169)
        strokeWidth = 2f.dp()
        style = Paint.Style.STROKE
    }

    private val coralPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(82, 255, 95, 109)
        strokeWidth = 2.4f.dp()
        style = Paint.Style.STROKE
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 209, 102)
        style = Paint.Style.FILL
    }

    private val path = Path()
    private var phase = 0f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSoftGrid(canvas)
        drawSketchCurves(canvas)
        drawFloatingDots(canvas)
        phase += 0.018f
        postInvalidateOnAnimation()
    }

    private fun drawSoftGrid(canvas: Canvas) {
        val step = 46f.dp()
        var x = -step
        while (x < width + step) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += step
        }

        var y = -step
        while (y < height + step) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += step
        }
    }

    private fun drawSketchCurves(canvas: Canvas) {
        drawWave(canvas, height * 0.18f, accentPaint, 0.8f)
        drawWave(canvas, height * 0.72f, coralPaint, 1.25f)
    }

    private fun drawWave(canvas: Canvas, centerY: Float, paint: Paint, speed: Float) {
        path.reset()
        val amplitude = 18f.dp()
        val waveLength = 165f.dp()
        path.moveTo(0f, centerY)
        var x = 0f
        while (x <= width + 8f.dp()) {
            val y = centerY + sin((x / waveLength) + phase * speed) * amplitude
            path.lineTo(x, y)
            x += 10f.dp()
        }
        canvas.drawPath(path, paint)
    }

    private fun drawFloatingDots(canvas: Canvas) {
        val count = 9
        for (i in 0 until count) {
            val baseX = width * ((i + 1f) / (count + 1f))
            val drift = sin(phase * 1.8f + i) * 12f.dp()
            val y = height * (0.2f + ((i * 23) % 60) / 100f)
            canvas.drawCircle(baseX + drift, y, (2.2f + (i % 3)).dp(), dotPaint)
        }
    }

    private fun Float.dp(): Float = this * resources.displayMetrics.density
}
