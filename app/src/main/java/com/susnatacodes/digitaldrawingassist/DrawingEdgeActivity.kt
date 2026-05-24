package com.susnatacodes.digitaldrawingassist

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import java.io.IOException

class DrawingEdgeActivity : AppCompatActivity() {

    private lateinit var canvasView: DrawingCanvasView
    private lateinit var statusText: TextView
    private lateinit var brushSizeLabel: TextView
    private lateinit var brushSizeSeek: SeekBar
    private lateinit var colorSwatches: LinearLayout
    private lateinit var customColorInput: EditText
    private lateinit var btnUndo: Button
    private lateinit var btnRedo: Button
    private lateinit var btnSmooth: Button
    private lateinit var btnStylus: Button
    private lateinit var btnTemplate: Button
    private lateinit var layerButtons: List<Button>
    private lateinit var toolButtons: Map<DrawingCanvasView.Tool, Button>

    private val coachTips = listOf(
        "Block the big silhouette first, then sharpen edges.",
        "Try a light pencil pass, then ink over only the confident lines.",
        "Use a grid template for symmetry, then turn it off before exporting.",
        "Drop opacity mentally: sketch the shadow shapes, not only outlines.",
        "Use a thicker brush for foreground lines and a thin pencil for details.",
        "Rotate the canvas with two fingers until your wrist feels natural."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing_edge)

        canvasView = findViewById(R.id.drawingCanvas)
        statusText = findViewById(R.id.drawingStatus)
        brushSizeLabel = findViewById(R.id.brushSizeLabel)
        brushSizeSeek = findViewById(R.id.brushSizeSeek)
        colorSwatches = findViewById(R.id.colorSwatches)
        customColorInput = findViewById(R.id.customColorInput)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnSmooth = findViewById(R.id.btnSmooth)
        btnStylus = findViewById(R.id.btnStylus)
        btnTemplate = findViewById(R.id.btnTemplate)

        toolButtons = mapOf(
            DrawingCanvasView.Tool.PENCIL to findViewById(R.id.toolPencil),
            DrawingCanvasView.Tool.BRUSH to findViewById(R.id.toolBrush),
            DrawingCanvasView.Tool.MARKER to findViewById(R.id.toolMarker),
            DrawingCanvasView.Tool.ERASER to findViewById(R.id.toolEraser),
            DrawingCanvasView.Tool.LINE to findViewById(R.id.toolLine),
            DrawingCanvasView.Tool.RECTANGLE to findViewById(R.id.toolRect),
            DrawingCanvasView.Tool.ELLIPSE to findViewById(R.id.toolEllipse)
        )
        layerButtons = listOf(
            findViewById(R.id.btnLayerOne),
            findViewById(R.id.btnLayerTwo),
            findViewById(R.id.btnLayerThree)
        )

        setupTools()
        setupBrushSize()
        setupColors()
        setupActions()

        canvasView.onStateChanged = { updateState() }
        updateState()
        UiEffects.playStaggeredEntrance(
            listOf(
                findViewById(R.id.drawingHeader),
                findViewById(R.id.drawingCanvasShell),
                findViewById(R.id.toolPanel),
                findViewById(R.id.actionPanel)
            )
        )
    }

    private fun setupTools() {
        toolButtons.forEach { (tool, button) ->
            button.setOnClickListener {
                canvasView.activeTool = tool
                updateState()
            }
        }
    }

    private fun setupBrushSize() {
        brushSizeSeek.max = 78
        brushSizeSeek.progress = 8
        brushSizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                canvasView.brushSize = progress + 2f
                updateState()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun setupColors() {
        val colors = listOf(
            0xFF17181F.toInt(),
            0xFFE85757.toInt(),
            0xFFFFB703.toInt(),
            0xFF20BFA9.toInt(),
            0xFF3A86FF.toInt(),
            0xFF8338EC.toInt(),
            0xFFFFFFFF.toInt()
        )

        colors.forEach { color ->
            val swatch = TextView(this).apply {
                gravity = Gravity.CENTER
                text = if (color == Color.WHITE) "W" else ""
                setTextColor(0xFF17181F.toInt())
                background = swatchDrawable(color, color == canvasView.activeColor)
                setOnClickListener {
                    canvasView.activeColor = color
                    updateColorSwatches()
                }
            }
            colorSwatches.addView(
                swatch,
                LinearLayout.LayoutParams(36.dp(), 36.dp()).apply { marginEnd = 8.dp() }
            )
        }

        findViewById<Button>(R.id.btnApplyColor).setOnClickListener {
            val color = parseColorInput(customColorInput.text.toString())
            if (color == null) {
                Toast.makeText(this, getString(R.string.drawing_invalid_color), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            canvasView.activeColor = color
            updateColorSwatches()
        }
    }

    private fun setupActions() {
        findViewById<Button>(R.id.btnBackHome).setOnClickListener { finish() }
        btnUndo.setOnClickListener { canvasView.undo() }
        btnRedo.setOnClickListener { canvasView.redo() }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            canvasView.clearCanvas()
            Toast.makeText(this, getString(R.string.drawing_canvas_cleared), Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnFitCanvas).setOnClickListener { canvasView.fitCanvas() }
        findViewById<Button>(R.id.btnSaveDrawing).setOnClickListener { saveDrawing() }
        findViewById<Button>(R.id.btnCoach).setOnClickListener { showCoachSuggestion() }

        btnSmooth.setOnClickListener {
            canvasView.smoothMode = !canvasView.smoothMode
            updateState()
        }

        btnStylus.setOnClickListener {
            canvasView.stylusOnly = !canvasView.stylusOnly
            updateState()
        }

        btnTemplate.setOnClickListener {
            canvasView.cycleTemplate()
            updateState()
        }

        layerButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                canvasView.activeLayer = index
                updateState()
            }
            button.setOnLongClickListener {
                canvasView.toggleLayerVisibility(index)
                updateState()
                true
            }
        }
    }

    private fun updateState() {
        statusText.text = getString(
            R.string.drawing_status,
            canvasView.toolLabel(),
            canvasView.layerLabel(),
            canvasView.templateLabel()
        )
        brushSizeLabel.text = getString(R.string.drawing_brush_size_value, canvasView.brushSize.toInt())
        btnUndo.isEnabled = canvasView.canUndo()
        btnRedo.isEnabled = canvasView.canRedo()
        btnSmooth.text = getString(
            if (canvasView.smoothMode) R.string.drawing_smooth_on else R.string.drawing_smooth_off
        )
        btnStylus.text = getString(
            if (canvasView.stylusOnly) R.string.drawing_stylus_on else R.string.drawing_stylus_off
        )
        btnTemplate.text = getString(R.string.drawing_template_button, canvasView.templateLabel())

        toolButtons.forEach { (tool, button) ->
            button.background = chipDrawable(active = tool == canvasView.activeTool)
        }
        layerButtons.forEachIndexed { index, button ->
            button.text = getString(
                R.string.drawing_layer_button,
                index + 1,
                if (canvasView.isLayerVisible(index)) "" else " off"
            )
            button.background = chipDrawable(active = index == canvasView.activeLayer)
        }
        updateColorSwatches()
    }

    private fun updateColorSwatches() {
        val swatchColors = listOf(
            0xFF17181F.toInt(),
            0xFFE85757.toInt(),
            0xFFFFB703.toInt(),
            0xFF20BFA9.toInt(),
            0xFF3A86FF.toInt(),
            0xFF8338EC.toInt(),
            0xFFFFFFFF.toInt()
        )
        swatchColors.forEachIndexed { index, color ->
            colorSwatches.getChildAt(index).background = swatchDrawable(color, color == canvasView.activeColor)
        }
    }

    private fun showCoachSuggestion() {
        val tip = coachTips.random()
        statusText.text = tip
        Toast.makeText(this, tip, Toast.LENGTH_LONG).show()
    }

    private fun saveDrawing() {
        val bitmap = canvasView.renderBitmap()
        val fileName = "DrawingEdge_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DigitalDrawingAssist")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(this, getString(R.string.drawing_save_error), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            contentResolver.openOutputStream(uri)?.use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    throw IOException("Bitmap compression failed")
                }
            } ?: throw IOException("Unable to open export stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            Toast.makeText(this, getString(R.string.drawing_saved), Toast.LENGTH_LONG).show()
        } catch (_: IOException) {
            contentResolver.delete(uri, null, null)
            Toast.makeText(this, getString(R.string.drawing_save_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseColorInput(value: String): Int? {
        val normalized = value.trim().removePrefix("#")
        if (normalized.length != 6) return null
        return try {
            "#$normalized".toColorInt()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun chipDrawable(active: Boolean): GradientDrawable {
        return if (active) {
            GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0xFFFF6B6B.toInt(), 0xFF20BFA9.toInt())
            ).apply { cornerRadius = 8f.dp() }
        } else {
            GradientDrawable().apply {
                setColor(0x18FFFFFF)
                cornerRadius = 8f.dp()
                setStroke(1.dp(), 0x22FFFFFF)
            }
        }
    }

    private fun swatchDrawable(color: Int, active: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(if (active) 4.dp() else 1.dp(), if (active) 0xFFFFD166.toInt() else 0x55FFFFFF)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
    private fun Float.dp(): Float = this * resources.displayMetrics.density
}
