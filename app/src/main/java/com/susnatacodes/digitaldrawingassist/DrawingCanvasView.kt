package com.susnatacodes.digitaldrawingassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withMatrix
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DrawingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Tool {
        PENCIL,
        BRUSH,
        MARKER,
        ERASER,
        LINE,
        RECTANGLE,
        ELLIPSE
    }

    enum class Template {
        BLANK,
        GRID,
        DOTS,
        RULED,
        DARK
    }

    private sealed class DrawingElement(open val layer: Int) {
        data class Stroke(
            val path: Path,
            val paint: Paint,
            override val layer: Int
        ) : DrawingElement(layer)

        data class Shape(
            val tool: Tool,
            val startX: Float,
            val startY: Float,
            val endX: Float,
            val endY: Float,
            val paint: Paint,
            override val layer: Int
        ) : DrawingElement(layer)
    }

    var activeTool: Tool = Tool.PENCIL
        set(value) {
            field = value
            invalidate()
            onStateChanged?.invoke()
        }

    var activeColor: Int = Color.rgb(23, 24, 31)
        set(value) {
            field = value
            invalidate()
            onStateChanged?.invoke()
        }

    var brushSize: Float = 10f
        set(value) {
            field = value.coerceIn(2f, 80f)
            onStateChanged?.invoke()
        }

    var activeLayer: Int = 0
        set(value) {
            field = value.coerceIn(0, LAYER_COUNT - 1)
            onStateChanged?.invoke()
        }

    var smoothMode: Boolean = true
        set(value) {
            field = value
            onStateChanged?.invoke()
        }

    var stylusOnly: Boolean = false
        set(value) {
            field = value
            onStateChanged?.invoke()
        }

    var template: Template = Template.BLANK
        private set

    var onStateChanged: (() -> Unit)? = null

    private val elements = mutableListOf<DrawingElement>()
    private val redoElements = mutableListOf<DrawingElement>()
    private val layerVisible = BooleanArray(LAYER_COUNT) { true }
    private val contentMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val templatePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var currentPath: Path? = null
    private var currentPaint: Paint? = null
    private var shapeStartX = 0f
    private var shapeStartY = 0f
    private var shapeEndX = 0f
    private var shapeEndY = 0f
    private var lastDrawX = 0f
    private var lastDrawY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private var canvasScale = 0.42f
    private var canvasRotation = 0f
    private var canvasOffsetX = 0f
    private var canvasOffsetY = 0f
    private var transformActive = false
    private var lastTransformDistance = 0f
    private var lastTransformAngle = 0f
    private var lastTransformCenterX = 0f
    private var lastTransformCenterY = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitCanvas()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        updateContentMatrix()

        canvas.withMatrix(contentMatrix) {
            drawPaper(this)
            elements.forEach { element ->
                if (layerVisible[element.layer]) drawElement(this, element)
            }
            drawPreview(this)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) {
            cancelCurrentGesture()
            handleCanvasTransform(event)
            return true
        }

        if (transformActive && event.actionMasked == MotionEvent.ACTION_UP) {
            transformActive = false
            return true
        }

        if (stylusOnly && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return true
        }

        val pointerIndex = when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_UP -> event.actionIndex
            else -> event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: 0
        }
        val point = toCanvasPoint(event.getX(pointerIndex), event.getY(pointerIndex))

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                startDrawing(point[0], point[1])
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                updateDrawing(point[0], point[1])
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                performClick()
                finishDrawing(point[0], point[1])
                parent?.requestDisallowInterceptTouchEvent(false)
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun undo() {
        if (elements.isEmpty()) return
        redoElements.add(elements.removeAt(elements.lastIndex))
        invalidate()
        onStateChanged?.invoke()
    }

    fun redo() {
        if (redoElements.isEmpty()) return
        elements.add(redoElements.removeAt(redoElements.lastIndex))
        invalidate()
        onStateChanged?.invoke()
    }

    fun clearCanvas() {
        elements.clear()
        redoElements.clear()
        currentPath = null
        currentPaint = null
        invalidate()
        onStateChanged?.invoke()
    }

    fun canUndo(): Boolean = elements.isNotEmpty()

    fun canRedo(): Boolean = redoElements.isNotEmpty()

    fun cycleTemplate() {
        template = Template.entries[(template.ordinal + 1) % Template.entries.size]
        invalidate()
        onStateChanged?.invoke()
    }

    fun toggleLayerVisibility(layer: Int) {
        if (layer !in 0 until LAYER_COUNT) return
        layerVisible[layer] = !layerVisible[layer]
        invalidate()
        onStateChanged?.invoke()
    }

    fun isLayerVisible(layer: Int): Boolean = layerVisible.getOrNull(layer) ?: false

    fun fitCanvas() {
        if (width == 0 || height == 0) return
        val horizontalScale = width / (PAPER_WIDTH * 1.15f)
        val verticalScale = height / (PAPER_HEIGHT * 1.12f)
        canvasScale = min(horizontalScale, verticalScale).coerceIn(0.18f, 2.6f)
        canvasRotation = 0f
        canvasOffsetX = 0f
        canvasOffsetY = 0f
        invalidate()
        onStateChanged?.invoke()
    }

    fun renderBitmap(): Bitmap {
        val bitmap = createBitmap(PAPER_WIDTH.toInt(), PAPER_HEIGHT.toInt())
        val exportCanvas = Canvas(bitmap)
        drawPaper(exportCanvas)
        elements.forEach { element ->
            if (layerVisible[element.layer]) drawElement(exportCanvas, element)
        }
        return bitmap
    }

    fun toolLabel(): String {
        return when (activeTool) {
            Tool.PENCIL -> "Pencil"
            Tool.BRUSH -> "Brush"
            Tool.MARKER -> "Marker"
            Tool.ERASER -> "Eraser"
            Tool.LINE -> "Line"
            Tool.RECTANGLE -> "Rect"
            Tool.ELLIPSE -> "Circle"
        }
    }

    fun templateLabel(): String {
        return when (template) {
            Template.BLANK -> "Blank"
            Template.GRID -> "Grid"
            Template.DOTS -> "Dots"
            Template.RULED -> "Ruled"
            Template.DARK -> "Dark"
        }
    }

    fun layerLabel(): String = "L${activeLayer + 1}"

    private fun startDrawing(x: Float, y: Float) {
        redoElements.clear()
        shapeStartX = x
        shapeStartY = y
        shapeEndX = x
        shapeEndY = y
        lastDrawX = x
        lastDrawY = y

        if (activeTool.isFreehand()) {
            currentPath = Path().apply { moveTo(x, y) }
            currentPaint = buildPaint()
        } else {
            currentPaint = buildPaint()
        }
        invalidate()
    }

    private fun updateDrawing(x: Float, y: Float) {
        if (activeTool.isFreehand()) {
            val path = currentPath ?: return
            if (smoothMode) {
                val midX = (x + lastDrawX) / 2f
                val midY = (y + lastDrawY) / 2f
                path.quadTo(lastDrawX, lastDrawY, midX, midY)
            } else {
                path.lineTo(x, y)
            }
            lastDrawX = x
            lastDrawY = y
        } else {
            shapeEndX = x
            shapeEndY = y
        }
        invalidate()
    }

    private fun finishDrawing(x: Float, y: Float) {
        if (activeTool.isFreehand()) {
            val path = currentPath ?: return
            val paint = currentPaint ?: return
            path.lineTo(x, y)
            elements.add(DrawingElement.Stroke(Path(path), Paint(paint), activeLayer))
        } else {
            val paint = currentPaint ?: return
            shapeEndX = x
            shapeEndY = y
            elements.add(
                DrawingElement.Shape(
                    activeTool,
                    shapeStartX,
                    shapeStartY,
                    shapeEndX,
                    shapeEndY,
                    Paint(paint),
                    activeLayer
                )
            )
        }

        currentPath = null
        currentPaint = null
        invalidate()
        onStateChanged?.invoke()
    }

    private fun cancelCurrentGesture() {
        currentPath = null
        currentPaint = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
    }

    private fun handleCanvasTransform(event: MotionEvent) {
        if (event.pointerCount < 2) return

        val distance = pointerDistance(event)
        val angle = pointerAngle(event)
        val centerX = (event.getX(0) + event.getX(1)) / 2f
        val centerY = (event.getY(0) + event.getY(1)) / 2f

        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_DOWN -> {
                transformActive = true
                lastTransformDistance = distance
                lastTransformAngle = angle
                lastTransformCenterX = centerX
                lastTransformCenterY = centerY
            }

            MotionEvent.ACTION_MOVE -> {
                if (!transformActive) {
                    transformActive = true
                    lastTransformDistance = distance
                    lastTransformAngle = angle
                    lastTransformCenterX = centerX
                    lastTransformCenterY = centerY
                    return
                }

                if (lastTransformDistance > 0f && distance > 0f) {
                    canvasScale = (canvasScale * (distance / lastTransformDistance)).coerceIn(0.16f, 4f)
                }
                canvasRotation += angle - lastTransformAngle
                canvasOffsetX += centerX - lastTransformCenterX
                canvasOffsetY += centerY - lastTransformCenterY

                lastTransformDistance = distance
                lastTransformAngle = angle
                lastTransformCenterX = centerX
                lastTransformCenterY = centerY
                invalidate()
                onStateChanged?.invoke()
            }

            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                transformActive = false
            }
        }
    }

    private fun updateContentMatrix() {
        contentMatrix.reset()
        contentMatrix.postTranslate(-PAPER_WIDTH / 2f, -PAPER_HEIGHT / 2f)
        contentMatrix.postScale(canvasScale, canvasScale)
        contentMatrix.postRotate(canvasRotation)
        contentMatrix.postTranslate(width / 2f + canvasOffsetX, height / 2f + canvasOffsetY)
        contentMatrix.invert(inverseMatrix)
    }

    private fun toCanvasPoint(x: Float, y: Float): FloatArray {
        updateContentMatrix()
        return floatArrayOf(x, y).also { inverseMatrix.mapPoints(it) }
    }

    private fun buildPaint(): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = when (activeTool) {
                Tool.PENCIL -> max(2f, brushSize * 0.55f)
                Tool.BRUSH -> brushSize
                Tool.MARKER -> brushSize * 1.7f
                Tool.ERASER -> brushSize * 2f
                Tool.LINE, Tool.RECTANGLE, Tool.ELLIPSE -> brushSize
            }
            color = when (activeTool) {
                Tool.ERASER -> paperColor()
                Tool.MARKER -> activeColor.withAlpha(150)
                Tool.PENCIL -> activeColor.withAlpha(215)
                else -> activeColor
            }
        }
    }

    private fun drawElement(canvas: Canvas, element: DrawingElement) {
        when (element) {
            is DrawingElement.Stroke -> canvas.drawPath(element.path, element.paint)
            is DrawingElement.Shape -> drawShape(canvas, element.tool, element.startX, element.startY, element.endX, element.endY, element.paint)
        }
    }

    private fun drawPreview(canvas: Canvas) {
        val paint = currentPaint ?: return
        if (activeTool.isFreehand()) {
            currentPath?.let { canvas.drawPath(it, paint) }
        } else {
            drawShape(canvas, activeTool, shapeStartX, shapeStartY, shapeEndX, shapeEndY, paint)
        }
    }

    private fun drawShape(canvas: Canvas, tool: Tool, startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
        when (tool) {
            Tool.LINE -> canvas.drawLine(startX, startY, endX, endY, paint)
            Tool.RECTANGLE -> canvas.drawRoundRect(normalizedRect(startX, startY, endX, endY), 14f, 14f, paint)
            Tool.ELLIPSE -> canvas.drawOval(normalizedRect(startX, startY, endX, endY), paint)
            else -> Unit
        }
    }

    private fun drawPaper(canvas: Canvas) {
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.color = paperColor()
        canvas.drawRect(0f, 0f, PAPER_WIDTH, PAPER_HEIGHT, backgroundPaint)

        when (template) {
            Template.BLANK -> Unit
            Template.GRID -> drawGridTemplate(canvas)
            Template.DOTS -> drawDotTemplate(canvas)
            Template.RULED -> drawRuledTemplate(canvas)
            Template.DARK -> drawDarkTemplate(canvas)
        }
    }

    private fun drawGridTemplate(canvas: Canvas) {
        templatePaint.style = Paint.Style.STROKE
        templatePaint.strokeWidth = 1f
        templatePaint.color = Color.argb(38, 20, 30, 42)
        var x = 0f
        while (x <= PAPER_WIDTH) {
            canvas.drawLine(x, 0f, x, PAPER_HEIGHT, templatePaint)
            x += 80f
        }
        var y = 0f
        while (y <= PAPER_HEIGHT) {
            canvas.drawLine(0f, y, PAPER_WIDTH, y, templatePaint)
            y += 80f
        }
    }

    private fun drawDotTemplate(canvas: Canvas) {
        templatePaint.style = Paint.Style.FILL
        templatePaint.color = Color.argb(52, 20, 30, 42)
        var x = 60f
        while (x <= PAPER_WIDTH) {
            var y = 60f
            while (y <= PAPER_HEIGHT) {
                canvas.drawCircle(x, y, 3.5f, templatePaint)
                y += 86f
            }
            x += 86f
        }
    }

    private fun drawRuledTemplate(canvas: Canvas) {
        templatePaint.style = Paint.Style.STROKE
        templatePaint.strokeWidth = 2f
        templatePaint.color = Color.argb(42, 30, 60, 90)
        var y = 120f
        while (y <= PAPER_HEIGHT) {
            canvas.drawLine(80f, y, PAPER_WIDTH - 80f, y, templatePaint)
            y += 92f
        }
    }

    private fun drawDarkTemplate(canvas: Canvas) {
        templatePaint.style = Paint.Style.STROKE
        templatePaint.strokeWidth = 1.2f
        templatePaint.color = Color.argb(52, 255, 255, 255)
        var x = 0f
        while (x <= PAPER_WIDTH) {
            canvas.drawLine(x, 0f, x, PAPER_HEIGHT, templatePaint)
            x += 96f
        }
        var y = 0f
        while (y <= PAPER_HEIGHT) {
            canvas.drawLine(0f, y, PAPER_WIDTH, y, templatePaint)
            y += 96f
        }
    }

    private fun paperColor(): Int {
        return if (template == Template.DARK) Color.rgb(19, 21, 29) else Color.rgb(252, 250, 246)
    }

    private fun pointerDistance(event: MotionEvent): Float {
        return hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
    }

    private fun pointerAngle(event: MotionEvent): Float {
        return Math.toDegrees(
            atan2(
                event.getY(1) - event.getY(0),
                event.getX(1) - event.getX(0)
            ).toDouble()
        ).toFloat()
    }

    private fun normalizedRect(startX: Float, startY: Float, endX: Float, endY: Float): RectF {
        return RectF(min(startX, endX), min(startY, endY), max(startX, endX), max(startY, endY))
    }

    private fun Tool.isFreehand(): Boolean {
        return this == Tool.PENCIL || this == Tool.BRUSH || this == Tool.MARKER || this == Tool.ERASER
    }

    private fun Int.withAlpha(alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(this), Color.green(this), Color.blue(this))
    }

    companion object {
        private const val LAYER_COUNT = 3
        private const val PAPER_WIDTH = 1600f
        private const val PAPER_HEIGHT = 2200f
    }
}
