package com.susnatacodes.digitaldrawingassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GuideGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 0, 0, 0)
        strokeWidth = 2.5f.dp()
        style = Paint.Style.STROKE
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 255, 255, 255)
        strokeWidth = 1.2f.dp()
        style = Paint.Style.STROKE
    }

    var guideMode: Int = MODE_OFF
        private set

    fun setGuideMode(mode: Int) {
        guideMode = mode.coerceIn(MODE_OFF, MODE_DIAGONAL)
        visibility = if (guideMode == MODE_OFF) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (guideMode == MODE_OFF) return

        when (guideMode) {
            MODE_GRID -> drawGrid(canvas, 4)
            MODE_THIRDS -> drawGrid(canvas, 3)
            MODE_DIAGONAL -> drawDiagonal(canvas)
        }
    }

    private fun drawGrid(canvas: Canvas, divisions: Int) {
        val w = width.toFloat()
        val h = height.toFloat()

        for (i in 1 until divisions) {
            val x = w * i / divisions
            drawLine(canvas, x, 0f, x, h)
        }

        for (i in 1 until divisions) {
            val y = h * i / divisions
            drawLine(canvas, 0f, y, w, y)
        }
    }

    private fun drawDiagonal(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        drawLine(canvas, 0f, 0f, w, h)
        drawLine(canvas, w, 0f, 0f, h)
        drawLine(canvas, w / 2f, 0f, w / 2f, h)
        drawLine(canvas, 0f, h / 2f, w, h / 2f)
    }

    private fun drawLine(canvas: Canvas, startX: Float, startY: Float, stopX: Float, stopY: Float) {
        canvas.drawLine(startX, startY, stopX, stopY, shadowPaint)
        canvas.drawLine(startX, startY, stopX, stopY, guidePaint)
    }

    private fun Float.dp(): Float = this * resources.displayMetrics.density

    companion object {
        const val MODE_OFF = 0
        const val MODE_GRID = 1
        const val MODE_THIRDS = 2
        const val MODE_DIAGONAL = 3
    }
}
