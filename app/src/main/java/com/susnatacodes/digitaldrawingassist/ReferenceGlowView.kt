package com.susnatacodes.digitaldrawingassist

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

class ReferenceGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bandPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 255, 255, 255)
        strokeWidth = 1.4f.dp()
        style = Paint.Style.STROKE
    }

    private var bandHeight = 0f
    private var offset = 0f

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bandHeight = h * 0.22f
        bandPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            bandHeight,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(54, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = (offset % (height + bandHeight)) - bandHeight
        shaderMatrix.setTranslate(0f, top)
        bandPaint.shader?.setLocalMatrix(shaderMatrix)
        canvas.drawRect(0f, top, width.toFloat(), top + bandHeight, bandPaint)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        offset += 2.2f.dp()
        postInvalidateOnAnimation()
    }

    private fun Float.dp(): Float = this * resources.displayMetrics.density
}
