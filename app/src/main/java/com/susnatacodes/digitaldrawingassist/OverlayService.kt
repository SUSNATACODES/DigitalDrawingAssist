package com.susnatacodes.digitaldrawingassist

import android.annotation.SuppressLint
import android.app.Service
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var imageLayout: View
    private lateinit var imageView: ImageView
    private lateinit var guideGrid: GuideGridView
    private lateinit var controlBtn: ImageView
    private lateinit var imageParams: WindowManager.LayoutParams
    private lateinit var btnParams: WindowManager.LayoutParams
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var panelView: View? = null
    private var dragEnabled = false
    private var zoomEnabled = false
    private var mirrorX = false
    private var grayscaleEnabled = false
    private var contrastEnabled = false
    private var invertEnabled = false
    private var borderEnabled = true
    private var guideMode = GuideGridView.MODE_OFF
    private var referenceOpacity = 82
    private var contentBackgroundOpacity = 0
    private var scaleFactor = 1f
    private var rotationAngle = 0f

    private data class PanelButtonSpec(
        val text: String,
        val filled: Boolean = false,
        val danger: Boolean = false,
        val action: () -> Unit
    )

    private data class VisualSnapshot(
        val opacity: Int,
        val backgroundOpacity: Int,
        val grayscale: Boolean,
        val contrast: Boolean,
        val invert: Boolean,
        val border: Boolean,
        val guide: Int
    )

    private var peekSnapshot: VisualSnapshot? = null

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        imageLayout = LayoutInflater.from(this).inflate(R.layout.popup_layout, FrameLayout(this), false)
        imageView = imageLayout.findViewById(R.id.imageView)
        guideGrid = imageLayout.findViewById(R.id.guideGrid)
        configureReferenceStage()
        setContentBackgroundOpacity(contentBackgroundOpacity)
        setReferenceOpacity(referenceOpacity)
        guideGrid.setGuideMode(guideMode)

        imageParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 240
        }

        imageLayout.alpha = 0f
        imageLayout.scaleX = 0.92f
        imageLayout.scaleY = 0.92f
        windowManager.addView(imageLayout, imageParams)

        controlBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_overlay_tune)
            setBackgroundResource(R.drawable.bg_floating_control)
            setColorFilter(Color.WHITE)
            setPadding(21.dp(), 21.dp(), 21.dp(), 21.dp())
            elevation = 18f
            contentDescription = getString(R.string.cp_title)
        }

        btnParams = WindowManager.LayoutParams(
            74.dp(),
            74.dp(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 26.dp()
            y = 86.dp()
        }

        windowManager.addView(controlBtn, btnParams)

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        setupControlButton()
        setupImageTouch()
        updateImageTouchability()
        playEntrance()
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        val uriString = intent?.getStringExtra(MainActivity.EXTRA_IMAGE_URI)
        if (uriString != null) {
            try {
                imageView.setImageURI(uriString.toUri())
                setContentBackgroundOpacity(contentBackgroundOpacity)
                setReferenceOpacity(referenceOpacity)
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.msg_image_load_error), Toast.LENGTH_SHORT).show()
            }
        }
        return START_STICKY
    }

    private fun playEntrance() {
        imageLayout.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(380)
            .setInterpolator(DecelerateInterpolator())
            .start()

        controlBtn.alpha = 0f
        controlBtn.scaleX = 0.72f
        controlBtn.scaleY = 0.72f
        controlBtn.rotation = -18f
        controlBtn.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .rotation(0f)
            .setStartDelay(160)
            .setDuration(420)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }

    private fun configureReferenceStage() {
        val metrics = resources.displayMetrics
        val maxByWidth = metrics.widthPixels - 36.dp()
        val maxByHeight = metrics.heightPixels - 180.dp()
        val stageSize = min(420.dp(), min(maxByWidth, maxByHeight))
            .coerceAtLeast(240.dp())

        imageView.layoutParams = FrameLayout.LayoutParams(stageSize, stageSize, Gravity.CENTER)
        guideGrid.layoutParams = FrameLayout.LayoutParams(stageSize, stageSize, Gravity.CENTER)
    }

    private fun updateImageTouchability() {
        val isInteractive = dragEnabled || zoomEnabled
        imageParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (!isInteractive) {
            imageParams.flags = imageParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (::imageLayout.isInitialized) {
            windowManager.updateViewLayout(imageLayout, imageParams)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlButton() {
        controlBtn.setOnClickListener {
            controlBtn.animate()
                .rotationBy(18f)
                .setDuration(120)
                .withEndAction { controlBtn.animate().rotation(0f).setDuration(120).start() }
                .start()
            showControlPanel()
        }

        controlBtn.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = btnParams.x
                        initialY = btnParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isMoving = false
                        v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) isMoving = true

                        btnParams.x = initialX + dx
                        btnParams.y = initialY + dy
                        windowManager.updateViewLayout(controlBtn, btnParams)
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (!isMoving && event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageTouch() {
        imageLayout.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (!(dragEnabled || zoomEnabled)) return false

                if (zoomEnabled) scaleGestureDetector.onTouchEvent(event)

                if (dragEnabled && event.pointerCount == 1) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = imageParams.x
                            initialY = imageParams.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            imageParams.x = initialX + (event.rawX - touchX).toInt()
                            imageParams.y = initialY + (event.rawY - touchY).toInt()
                            windowManager.updateViewLayout(imageLayout, imageParams)
                            return true
                        }
                    }
                }
                return true
            }
        })
    }

    private fun applyImageTransform() {
        imageView.scaleX = scaleFactor * if (mirrorX) -1f else 1f
        imageView.scaleY = scaleFactor
        imageView.rotation = rotationAngle
    }

    private fun setReferenceOpacity(value: Int) {
        referenceOpacity = value.coerceIn(0, 100)
        val alpha = (referenceOpacity * 255 / 100).coerceIn(0, 255)

        imageView.alpha = 1f
        imageView.imageAlpha = alpha
    }

    private fun setContentBackgroundOpacity(value: Int) {
        contentBackgroundOpacity = value.coerceIn(0, 100)
        applyOverlayShell()
        imageView.background = Color.TRANSPARENT.toDrawable()
    }

    private fun applyOverlayShell() {
        val fillAlpha = (contentBackgroundOpacity * 255 / 100).coerceIn(0, 255)
        imageLayout.background = roundedRect(
            Color.argb(fillAlpha, 255, 255, 255),
            8f,
            if (borderEnabled) 0x5520BFA9 else null,
            2f
        )
    }

    private fun applyImageFilter() {
        val hasFilter = grayscaleEnabled || contrastEnabled || invertEnabled
        if (!hasFilter) {
            imageView.colorFilter = null
            return
        }

        val matrix = ColorMatrix()
        if (grayscaleEnabled) {
            matrix.setSaturation(0f)
        }

        if (contrastEnabled) {
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.45f, 0f, 0f, 0f, -34f,
                        0f, 1.45f, 0f, 0f, -34f,
                        0f, 0f, 1.45f, 0f, -34f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        if (invertEnabled) {
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

        imageView.colorFilter = ColorMatrixColorFilter(matrix)
    }

    private fun resetImageTransform() {
        dragEnabled = false
        zoomEnabled = false
        scaleFactor = 1f
        rotationAngle = 0f
        mirrorX = false
        grayscaleEnabled = false
        contrastEnabled = false
        invertEnabled = false
        borderEnabled = true
        guideMode = GuideGridView.MODE_OFF
        setReferenceOpacity(82)
        setContentBackgroundOpacity(0)
        guideGrid.setGuideMode(guideMode)
        applyImageFilter()
        applyImageTransform()
        updateImageTouchability()
        centerOverlay()
    }

    private fun applySoftTracePreset() {
        setReferenceOpacity(55)
        setContentBackgroundOpacity(0)
        grayscaleEnabled = false
        contrastEnabled = false
        invertEnabled = false
        borderEnabled = true
        setGuideMode(GuideGridView.MODE_THIRDS)
        applyImageFilter()
        applyOverlayShell()
    }

    private fun applyLineArtPreset() {
        setReferenceOpacity(72)
        setContentBackgroundOpacity(0)
        grayscaleEnabled = true
        contrastEnabled = true
        invertEnabled = false
        borderEnabled = true
        setGuideMode(GuideGridView.MODE_GRID)
        applyImageFilter()
        applyOverlayShell()
    }

    private fun applyDarkCanvasPreset() {
        setReferenceOpacity(64)
        setContentBackgroundOpacity(0)
        grayscaleEnabled = false
        contrastEnabled = true
        invertEnabled = true
        borderEnabled = true
        setGuideMode(GuideGridView.MODE_DIAGONAL)
        applyImageFilter()
        applyOverlayShell()
    }

    private fun applyCleanPreset() {
        setReferenceOpacity(82)
        setContentBackgroundOpacity(0)
        grayscaleEnabled = false
        contrastEnabled = false
        invertEnabled = false
        borderEnabled = true
        setGuideMode(GuideGridView.MODE_OFF)
        applyImageFilter()
        applyOverlayShell()
    }

    private fun beginPeekOriginal() {
        if (peekSnapshot != null) return

        peekSnapshot = VisualSnapshot(
            opacity = referenceOpacity,
            backgroundOpacity = contentBackgroundOpacity,
            grayscale = grayscaleEnabled,
            contrast = contrastEnabled,
            invert = invertEnabled,
            border = borderEnabled,
            guide = guideMode
        )

        setReferenceOpacity(100)
        setContentBackgroundOpacity(0)
        grayscaleEnabled = false
        contrastEnabled = false
        invertEnabled = false
        setGuideMode(GuideGridView.MODE_OFF)
        applyImageFilter()

        imageLayout.animate()
            .scaleX(1.018f)
            .scaleY(1.018f)
            .setDuration(110)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun endPeekOriginal() {
        val snapshot = peekSnapshot ?: return
        peekSnapshot = null

        setReferenceOpacity(snapshot.opacity)
        setContentBackgroundOpacity(snapshot.backgroundOpacity)
        grayscaleEnabled = snapshot.grayscale
        contrastEnabled = snapshot.contrast
        invertEnabled = snapshot.invert
        borderEnabled = snapshot.border
        setGuideMode(snapshot.guide)
        applyImageFilter()
        applyOverlayShell()

        imageLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(140)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun enterDrawMode() {
        dragEnabled = false
        zoomEnabled = false
        updateImageTouchability()
        dismissPanel(animated = true)
        Toast.makeText(this, getString(R.string.msg_draw_mode), Toast.LENGTH_SHORT).show()
    }

    private fun centerOverlay() {
        imageLayout.post {
            val metrics = resources.displayMetrics
            imageParams.x = ((metrics.widthPixels - imageLayout.width) / 2).coerceAtLeast(0)
            imageParams.y = ((metrics.heightPixels - imageLayout.height) / 3).coerceAtLeast(0)
            windowManager.updateViewLayout(imageLayout, imageParams)
        }
    }

    private fun rotateOverlay(delta: Float) {
        rotationAngle += delta
        applyImageTransform()
    }

    private fun fitOverlayToScreen() {
        imageLayout.post {
            val metrics = resources.displayMetrics
            val sourceWidth = imageView.width.takeIf { it > 0 } ?: imageLayout.width
            val sourceHeight = imageView.height.takeIf { it > 0 } ?: imageLayout.height
            if (sourceWidth <= 0 || sourceHeight <= 0) return@post

            val maxWidth = metrics.widthPixels * 0.86f
            val maxHeight = metrics.heightPixels * 0.62f
            scaleFactor = min(maxWidth / sourceWidth, maxHeight / sourceHeight)
                .coerceIn(0.2f, 4f)
            applyImageTransform()
            centerOverlay()
        }
    }

    private fun setGuideMode(mode: Int) {
        guideMode = mode
        guideGrid.setGuideMode(guideMode)
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.2f, min(scaleFactor, 4.0f))
            applyImageTransform()
            return true
        }
    }

    private fun showControlPanel() {
        dismissPanel()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 18.dp(), 20.dp(), 16.dp())
            background = roundedRect(0xF51B1D27.toInt(), 8f, 0x4433E0CC, 1f)
        }

        val mode = TextView(this).apply {
            setTextColor(0xFFE8F7F3.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(11.dp(), 7.dp(), 11.dp(), 7.dp())
        }

        fun refreshModeChip() {
            val interactive = dragEnabled || zoomEnabled
            mode.text = if (interactive) {
                getString(R.string.cp_mode_edit)
            } else {
                getString(R.string.cp_mode_draw)
            }
            mode.background = roundedRect(
                if (interactive) 0x292DD4BF else 0x18FFFFFF,
                8f,
                if (interactive) 0x5533E0CC else 0x33FFFFFF,
                1f
            )
        }
        refreshModeChip()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(this).apply {
                text = getString(R.string.cp_title)
                setTextColor(Color.WHITE)
                textSize = 22f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(mode)
        container.addView(header, rowParams(bottom = 10))

        addSectionTitle(container, getString(R.string.cp_section_presets))
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_preset_trace), guideMode == GuideGridView.MODE_THIRDS) {
                    applySoftTracePreset()
                    Toast.makeText(this, getString(R.string.msg_preset_trace), Toast.LENGTH_SHORT).show()
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_preset_line_art), grayscaleEnabled && contrastEnabled && !invertEnabled) {
                    applyLineArtPreset()
                    Toast.makeText(this, getString(R.string.msg_preset_line_art), Toast.LENGTH_SHORT).show()
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_preset_dark_canvas), invertEnabled) {
                    applyDarkCanvasPreset()
                    Toast.makeText(this, getString(R.string.msg_preset_dark_canvas), Toast.LENGTH_SHORT).show()
                    showControlPanel()
                }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_preset_clean), guideMode == GuideGridView.MODE_OFF && !grayscaleEnabled && !contrastEnabled && !invertEnabled) {
                    applyCleanPreset()
                    Toast.makeText(this, getString(R.string.msg_preset_clean), Toast.LENGTH_SHORT).show()
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_quick_fit)) { fitOverlayToScreen() },
                PanelButtonSpec(getString(R.string.cp_quick_center)) { centerOverlay() }
            )
        )
        addHoldButton(
            container,
            getString(R.string.cp_peek_original),
            holdStart = { beginPeekOriginal() },
            holdEnd = { endPeekOriginal() }
        )

        addSectionTitle(container, getString(R.string.cp_section_place))
        addSwitchRow(container, getString(R.string.cp_drag), getString(R.string.cp_drag_sub), dragEnabled) {
            dragEnabled = it
            updateImageTouchability()
            refreshModeChip()
        }
        addSwitchRow(container, getString(R.string.cp_zoom), getString(R.string.cp_zoom_sub), zoomEnabled) {
            zoomEnabled = it
            updateImageTouchability()
            refreshModeChip()
        }

        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_rotate_left)) { rotateOverlay(-15f) },
                PanelButtonSpec(getString(R.string.cp_rotate_reset), rotationAngle == 0f) {
                    rotationAngle = 0f
                    applyImageTransform()
                },
                PanelButtonSpec(getString(R.string.cp_rotate_right)) { rotateOverlay(15f) }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_mirror), mirrorX) {
                    mirrorX = !mirrorX
                    applyImageTransform()
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_quick_reset)) {
                    resetImageTransform()
                    showControlPanel()
                }
            )
        )

        addSectionTitle(container, getString(R.string.cp_section_look))
        addSlider(
            container,
            getString(R.string.cp_transparency),
            referenceOpacity,
            0,
            100
        ) { value ->
            setReferenceOpacity(value)
        }

        addSlider(container, getString(R.string.cp_size), (scaleFactor * 100).toInt(), 20, 400) { value ->
            scaleFactor = value / 100f
            applyImageTransform()
        }

        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_grayscale), grayscaleEnabled) {
                    grayscaleEnabled = !grayscaleEnabled
                    applyImageFilter()
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_contrast), contrastEnabled) {
                    contrastEnabled = !contrastEnabled
                    applyImageFilter()
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_invert), invertEnabled) {
                    invertEnabled = !invertEnabled
                    applyImageFilter()
                    showControlPanel()
                }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_guide_off), guideMode == GuideGridView.MODE_OFF) {
                    setGuideMode(GuideGridView.MODE_OFF)
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_guide_grid), guideMode == GuideGridView.MODE_GRID) {
                    setGuideMode(GuideGridView.MODE_GRID)
                    showControlPanel()
                },
                PanelButtonSpec(getString(R.string.cp_guide_thirds), guideMode == GuideGridView.MODE_THIRDS) {
                    setGuideMode(GuideGridView.MODE_THIRDS)
                    showControlPanel()
                }
            )
        )

        addSectionTitle(container, getString(R.string.cp_section_finish))
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_draw_mode), true) { enterDrawMode() },
                PanelButtonSpec(getString(R.string.cp_done), true) { dismissPanel(animated = true) },
                PanelButtonSpec(getString(R.string.cp_close), danger = true) {
                    dismissPanel()
                    stopSelf()
                }
            ),
            bottom = 0
        )

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                container,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        showSystemPanel(scrollView)
    }

    private fun addSectionTitle(parent: LinearLayout, text: String) {
        parent.addView(
            TextView(this).apply {
                this.text = text
                setTextColor(0xFFFFD166.toInt())
                textSize = 12f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setPadding(2.dp(), 8.dp(), 2.dp(), 8.dp())
            },
            rowParams(top = 2, bottom = 2)
        )
    }

    private fun addButtonRow(
        parent: LinearLayout,
        buttons: List<PanelButtonSpec>,
        top: Int = 0,
        bottom: Int = 10
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            weightSum = buttons.size.toFloat()
        }

        buttons.forEachIndexed { index, spec ->
            val params = LinearLayout.LayoutParams(0, 48.dp(), 1f).apply {
                if (index > 0) marginStart = 5.dp()
                if (index < buttons.lastIndex) marginEnd = 5.dp()
            }
            row.addView(
                makePanelButton(spec.text, spec.filled, spec.danger, spec.action),
                params
            )
        }

        parent.addView(row, rowParams(top = top, bottom = bottom))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addHoldButton(
        parent: LinearLayout,
        text: String,
        holdStart: () -> Unit,
        holdEnd: () -> Unit,
        bottom: Int = 10
    ) {
        var holding = false
        val button = makePanelButton(text, filled = true) {}
        button.textSize = 15f
        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    holding = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    view.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .alpha(0.9f)
                        .setDuration(80)
                        .start()
                    holdStart()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (holding) {
                        holding = false
                        holdEnd()
                    }
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(120)
                        .start()
                    true
                }

                else -> true
            }
        }

        parent.addView(button, rowParams(bottom = bottom).apply { height = 50.dp() })
    }

    private fun addSwitchRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        var current = checked
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 11.dp(), 10.dp(), 11.dp())
            background = roundedRect(0x18FFFFFF, 8f, 0x24FFFFFF, 1f)
            isClickable = true
        }

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        copy.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        copy.addView(TextView(this).apply {
            text = subtitle
            setTextColor(0xBDECECF0.toInt())
            textSize = 12f
        })

        val toggle = TextView(this).apply {
            minWidth = 62.dp()
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(10.dp(), 8.dp(), 10.dp(), 8.dp())
        }

        fun renderToggle() {
            toggle.text = getString(if (current) R.string.cp_toggle_on else R.string.cp_toggle_off)
            toggle.setTextColor(if (current) 0xFF0F2F2B.toInt() else 0xFFECECF0.toInt())
            toggle.background = if (current) {
                roundedRect(0xFF2DD4BF.toInt(), 8f, 0xFFFFFFFF.toInt(), 1f)
            } else {
                roundedRect(0x22FFFFFF, 8f, 0x44FFFFFF, 1f)
            }
        }

        row.setOnClickListener {
            row.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            current = !current
            renderToggle()
            onChanged(current)
        }
        renderToggle()

        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(toggle)
        parent.addView(row, rowParams(bottom = 10))
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        initialValue: Int,
        minimum: Int,
        maximum: Int,
        onChanged: (Int) -> Unit
    ) {
        val safeInitial = initialValue.coerceIn(minimum, maximum)
        val section = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 12.dp(), 14.dp(), 10.dp())
            background = roundedRect(0x12FFFFFF, 8f, 0x22FFFFFF, 1f)
        }

        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val label = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val value = TextView(this).apply {
            text = getString(R.string.cp_percent_value, safeInitial)
            setTextColor(0xFFFFD166.toInt())
            textSize = 13f
        }

        labelRow.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        labelRow.addView(value)
        section.addView(labelRow)

        val slider = SeekBar(this).apply {
            max = maximum - minimum
            progress = safeInitial - minimum
            setPadding(0, 8.dp(), 0, 0)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val current = minimum + progress
                    value.text = getString(R.string.cp_percent_value, current)
                    onChanged(current)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        section.addView(slider)
        parent.addView(section, rowParams(bottom = 10))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun makePanelButton(
        text: String,
        filled: Boolean,
        danger: Boolean = false,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = if (text.length > 10) 12f else 13.5f
            setAllCaps(false)
            gravity = Gravity.CENTER
            minHeight = 0
            minWidth = 0
            maxLines = 2
            includeFontPadding = false
            setTextColor(Color.WHITE)
            setPadding(6.dp(), 0, 6.dp(), 0)
            background = when {
                danger -> roundedRect(0xFF3E2430.toInt(), 8f, 0xFFFF6B6B.toInt(), 1f)
                filled -> GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xFFFF6B6B.toInt(), 0xFF2DD4BF.toInt())
                ).apply { cornerRadius = 8f.dp() }
                else -> roundedRect(0x16FFFFFF, 8f, 0x38FFFFFF, 1f)
            }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate()
                        .scaleX(0.96f)
                        .scaleY(0.96f)
                        .alpha(0.86f)
                        .setDuration(70)
                        .start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(110)
                        .start()
                }
                false
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    private fun showSystemPanel(view: View) {
        dismissPanel()

        val metrics = resources.displayMetrics
        val panelWidth = (metrics.widthPixels - 24.dp()).coerceAtMost(430.dp())
        val maxPanelHeight = (metrics.heightPixels - 92.dp()).coerceAtLeast(280.dp())
        val panelHeight = min(maxPanelHeight, 650.dp())
        val params = WindowManager.LayoutParams(
            panelWidth,
            panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((metrics.widthPixels - panelWidth) / 2).coerceAtLeast(0)
            y = 46.dp()
        }

        panelView = view
        windowManager.addView(view, params)
        view.alpha = 0f
        view.scaleX = 0.97f
        view.scaleY = 0.97f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dismissPanel(animated: Boolean = false) {
        val view = panelView ?: return
        panelView = null

        fun removePanel() {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // The panel may already be detached while the overlay is closing.
            }
        }

        if (animated && view.isAttachedToWindow) {
            view.animate()
                .alpha(0f)
                .scaleX(0.96f)
                .scaleY(0.96f)
                .setDuration(140)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { removePanel() }
                .start()
        } else {
            removePanel()
        }
    }

    private fun rowParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = top.dp()
            bottomMargin = bottom.dp()
        }
    }

    private fun roundedRect(
        color: Int,
        radiusDp: Float,
        strokeColor: Int? = null,
        strokeWidthDp: Float = 0f
    ): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp.dp()
            if (strokeColor != null) setStroke(strokeWidthDp.dp().toInt(), strokeColor)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()
    private fun Float.dp(): Float = this * resources.displayMetrics.density

    override fun onDestroy() {
        super.onDestroy()
        dismissPanel()
        try {
            if (::imageLayout.isInitialized) windowManager.removeView(imageLayout)
            if (::controlBtn.isInitialized) windowManager.removeView(controlBtn)
        } catch (_: Exception) {
            // The window manager can already be detached during service shutdown.
        }
    }
}
