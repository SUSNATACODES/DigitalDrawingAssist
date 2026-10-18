package com.susnatacodes.digitaldrawingassist

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
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
import androidx.core.content.ContextCompat
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
    private var resizeEnabled = false
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

    companion object {
        private const val RESIZE_NONE = 0
        private const val RESIZE_RIGHT = 1
        private const val RESIZE_BOTTOM = 2
        private const val RESIZE_LEFT = 3
        private const val RESIZE_TOP = 4
        private const val RESIZE_TOP_LEFT = 5
        private const val RESIZE_TOP_RIGHT = 6
        private const val RESIZE_BOTTOM_LEFT = 7
        private const val RESIZE_BOTTOM_RIGHT = 8
        private const val RESIZE_ANY = 9
    }

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

    override fun onBind(intent: Intent?): IBinder? = null

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        val imgParams = imageView.layoutParams as FrameLayout.LayoutParams
        imgParams.width = stageSize
        imgParams.height = stageSize
        imgParams.gravity = Gravity.CENTER
        imageView.layoutParams = imgParams

        val gridParams = guideGrid.layoutParams as FrameLayout.LayoutParams
        gridParams.width = stageSize
        gridParams.height = stageSize
        gridParams.gravity = Gravity.CENTER
        guideGrid.layoutParams = gridParams
    }

    private fun updateResizeHandlesVisibility() {
        if (!::imageLayout.isInitialized) return
        val container = imageLayout.findViewById<View>(R.id.resizeHandlesContainer)
        container?.visibility = if (resizeEnabled) View.VISIBLE else View.GONE
    }

    private fun updateImageTouchability() {
        val isInteractive = dragEnabled || zoomEnabled || resizeEnabled
        imageParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (!isInteractive) {
            imageParams.flags = imageParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (::imageLayout.isInitialized) {
            windowManager.updateViewLayout(imageLayout, imageParams)
        }
        updateResizeHandlesVisibility()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlButton() {
        controlBtn.setOnClickListener {
            controlBtn.animate()
                .scaleX(1.18f)
                .scaleY(1.18f)
                .rotationBy(25f)
                .setDuration(120)
                .withEndAction {
                    controlBtn.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotation(0f)
                        .setDuration(160)
                        .setInterpolator(OvershootInterpolator(1.4f))
                        .start()
                }
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
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (abs(dx) > 12 || abs(dy) > 12) {
                            isMoving = true
                        }

                        if (isMoving) {
                            btnParams.x = initialX + dx
                            btnParams.y = initialY + dy
                            windowManager.updateViewLayout(controlBtn, btnParams)
                        }
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()

                        if (!isMoving && event.actionMasked == MotionEvent.ACTION_UP) {
                            v.performClick()
                        }
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

            private var startRawX = 0f
            private var startRawY = 0f
            private var initialWidth = 0
            private var initialHeight = 0
            private var resizeMode = RESIZE_NONE

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (!(dragEnabled || zoomEnabled || resizeEnabled)) return false

                if (zoomEnabled) scaleGestureDetector.onTouchEvent(event)

                if (event.pointerCount == 1) {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = imageParams.x
                            initialY = imageParams.y
                            touchX = event.rawX
                            touchY = event.rawY
                            startRawX = event.rawX
                            startRawY = event.rawY

                            initialWidth = imageView.width.takeIf { it > 0 } ?: 420.dp()
                            initialHeight = imageView.height.takeIf { it > 0 } ?: 420.dp()

                            if (resizeEnabled) {
                                val localX = event.x
                                val localY = event.y
                                val w = v.width.toFloat()
                                val h = v.height.toFloat()
                                val edgeThreshold = 48.dp().toFloat()

                                val isLeft = localX < edgeThreshold
                                val isRight = localX > w - edgeThreshold
                                val isTop = localY < edgeThreshold
                                val isBottom = localY > h - edgeThreshold

                                resizeMode = when {
                                    isTop && isLeft -> RESIZE_TOP_LEFT
                                    isTop && isRight -> RESIZE_TOP_RIGHT
                                    isBottom && isLeft -> RESIZE_BOTTOM_LEFT
                                    isBottom && isRight -> RESIZE_BOTTOM_RIGHT
                                    isLeft -> RESIZE_LEFT
                                    isRight -> RESIZE_RIGHT
                                    isTop -> RESIZE_TOP
                                    isBottom -> RESIZE_BOTTOM
                                    else -> RESIZE_ANY
                                }
                            } else {
                                resizeMode = RESIZE_NONE
                            }
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - startRawX).toInt()
                            val dy = (event.rawY - startRawY).toInt()

                            if (resizeEnabled && resizeMode != RESIZE_NONE) {
                                val minSize = 120.dp()
                                val metrics = resources.displayMetrics
                                val maxWidth = metrics.widthPixels - 20.dp()
                                val maxHeight = metrics.heightPixels - 40.dp()

                                var newW = initialWidth
                                var newH = initialHeight
                                var newX = initialX
                                var newY = initialY

                                when (resizeMode) {
                                    RESIZE_RIGHT -> {
                                        newW = (initialWidth + dx).coerceIn(minSize, maxWidth)
                                    }
                                    RESIZE_BOTTOM -> {
                                        newH = (initialHeight + dy).coerceIn(minSize, maxHeight)
                                    }
                                    RESIZE_BOTTOM_RIGHT, RESIZE_ANY -> {
                                        newW = (initialWidth + dx).coerceIn(minSize, maxWidth)
                                        newH = (initialHeight + dy).coerceIn(minSize, maxHeight)
                                    }
                                    RESIZE_LEFT -> {
                                        newW = (initialWidth - dx).coerceIn(minSize, maxWidth)
                                        newX = initialX + (initialWidth - newW)
                                    }
                                    RESIZE_TOP -> {
                                        newH = (initialHeight - dy).coerceIn(minSize, maxHeight)
                                        newY = initialY + (initialHeight - newH)
                                    }
                                    RESIZE_TOP_LEFT -> {
                                        newW = (initialWidth - dx).coerceIn(minSize, maxWidth)
                                        newH = (initialHeight - dy).coerceIn(minSize, maxHeight)
                                        newX = initialX + (initialWidth - newW)
                                        newY = initialY + (initialHeight - newH)
                                    }
                                    RESIZE_TOP_RIGHT -> {
                                        newW = (initialWidth + dx).coerceIn(minSize, maxWidth)
                                        newH = (initialHeight - dy).coerceIn(minSize, maxHeight)
                                        newY = initialY + (initialHeight - newH)
                                    }
                                    RESIZE_BOTTOM_LEFT -> {
                                        newW = (initialWidth - dx).coerceIn(minSize, maxWidth)
                                        newH = (initialHeight + dy).coerceIn(minSize, maxHeight)
                                        newX = initialX + (initialWidth - newW)
                                    }
                                }

                                val imgParams = imageView.layoutParams as FrameLayout.LayoutParams
                                imgParams.width = newW
                                imgParams.height = newH
                                imageView.layoutParams = imgParams

                                val gridParams = guideGrid.layoutParams as FrameLayout.LayoutParams
                                gridParams.width = newW
                                gridParams.height = newH
                                guideGrid.layoutParams = gridParams

                                imageParams.x = newX
                                imageParams.y = newY
                                windowManager.updateViewLayout(imageLayout, imageParams)
                                return true
                            }

                            if (dragEnabled) {
                                imageParams.x = initialX + (event.rawX - touchX).toInt()
                                imageParams.y = initialY + (event.rawY - touchY).toInt()
                                windowManager.updateViewLayout(imageLayout, imageParams)
                                return true
                            }
                        }

                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            resizeMode = RESIZE_NONE
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
        resizeEnabled = false
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
        configureReferenceStage()
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
        resizeEnabled = false
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showControlPanel() {
        dismissPanel()

        val panelLayout = LayoutInflater.from(this).inflate(R.layout.popup_control_panel, null)

        val tvModeBadge = panelLayout.findViewById<TextView>(R.id.tvModeBadge)
        fun refreshModeChip() {
            val interactive = dragEnabled || zoomEnabled || resizeEnabled
            tvModeBadge.text = if (interactive) {
                getString(R.string.cp_mode_edit)
            } else {
                getString(R.string.cp_mode_draw)
            }
            tvModeBadge.background = roundedRect(
                if (interactive) 0x292DD4BF else 0x18FFFFFF,
                8f,
                if (interactive) 0x5533E0CC else 0x33FFFFFF,
                1f
            )
        }
        refreshModeChip()

        fun setupPanelButton(
            button: Button?,
            selected: Boolean = false,
            danger: Boolean = false,
            onClick: () -> Unit
        ) {
            val btn = button ?: return
            btn.setTextColor(Color.BLACK)
            if (danger) {
                btn.background = ContextCompat.getDrawable(this, R.drawable.brutal_button_pink)
            } else if (selected) {
                btn.background = ContextCompat.getDrawable(this, R.drawable.brutal_button_yellow)
            } else {
                btn.background = ContextCompat.getDrawable(this, R.drawable.button_secondary)
            }

            UiEffects.applyPressAnimation(btn)
            btn.setOnClickListener {
                btn.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }

        // Section: CREATIVE MODES
        val btnPresetTrace = panelLayout.findViewById<Button>(R.id.btnPresetTrace)
        val btnPresetLineArt = panelLayout.findViewById<Button>(R.id.btnPresetLineArt)
        val btnPresetDarkCanvas = panelLayout.findViewById<Button>(R.id.btnPresetDarkCanvas)
        val btnPresetClean = panelLayout.findViewById<Button>(R.id.btnPresetClean)
        val btnQuickFit = panelLayout.findViewById<Button>(R.id.btnQuickFit)
        val btnQuickCenter = panelLayout.findViewById<Button>(R.id.btnQuickCenter)
        val btnPeekOriginal = panelLayout.findViewById<Button>(R.id.btnPeekOriginal)

        setupPanelButton(btnPresetTrace, guideMode == GuideGridView.MODE_THIRDS) {
            applySoftTracePreset()
            Toast.makeText(this, getString(R.string.msg_preset_trace), Toast.LENGTH_SHORT).show()
            showControlPanel()
        }
        setupPanelButton(btnPresetLineArt, grayscaleEnabled && contrastEnabled && !invertEnabled) {
            applyLineArtPreset()
            Toast.makeText(this, getString(R.string.msg_preset_line_art), Toast.LENGTH_SHORT).show()
            showControlPanel()
        }
        setupPanelButton(btnPresetDarkCanvas, invertEnabled) {
            applyDarkCanvasPreset()
            Toast.makeText(this, getString(R.string.msg_preset_dark_canvas), Toast.LENGTH_SHORT).show()
            showControlPanel()
        }
        setupPanelButton(
            btnPresetClean,
            guideMode == GuideGridView.MODE_OFF && !grayscaleEnabled && !contrastEnabled && !invertEnabled
        ) {
            applyCleanPreset()
            Toast.makeText(this, getString(R.string.msg_preset_clean), Toast.LENGTH_SHORT).show()
            showControlPanel()
        }
        setupPanelButton(btnQuickFit) { fitOverlayToScreen() }
        setupPanelButton(btnQuickCenter) { centerOverlay() }

        var holdingPeek = false
        setupPanelButton(btnPeekOriginal, selected = true) {}
        btnPeekOriginal.textSize = 15f
        btnPeekOriginal.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    holdingPeek = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    view.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .alpha(0.9f)
                        .setDuration(80)
                        .start()
                    beginPeekOriginal()
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (holdingPeek) {
                        holdingPeek = false
                        endPeekOriginal()
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

        // Section: PLACE
        fun setupSwitchRow(
            row: View,
            toggleText: TextView,
            getChecked: () -> Boolean,
            setChecked: (Boolean) -> Unit
        ) {
            fun renderToggle() {
                val checked = getChecked()
                toggleText.text = getString(if (checked) R.string.cp_toggle_on else R.string.cp_toggle_off)
                toggleText.setTextColor(if (checked) 0xFF0F2F2B.toInt() else 0xFFECECF0.toInt())
                toggleText.background = if (checked) {
                    roundedRect(0xFF2DD4BF.toInt(), 8f, 0xFFFFFFFF.toInt(), 1f)
                } else {
                    roundedRect(0x22FFFFFF, 8f, 0x44FFFFFF, 1f)
                }
            }
            renderToggle()
            row.setOnClickListener {
                row.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val newChecked = !getChecked()
                setChecked(newChecked)
                renderToggle()
                updateImageTouchability()
                refreshModeChip()
            }
        }

        val rowDrag = panelLayout.findViewById<View>(R.id.rowDrag)
        val btnToggleDrag = panelLayout.findViewById<TextView>(R.id.btnToggleDrag)
        setupSwitchRow(rowDrag, btnToggleDrag, { dragEnabled }) { dragEnabled = it }

        val rowZoom = panelLayout.findViewById<View>(R.id.rowZoom)
        val btnToggleZoom = panelLayout.findViewById<TextView>(R.id.btnToggleZoom)
        setupSwitchRow(rowZoom, btnToggleZoom, { zoomEnabled }) { zoomEnabled = it }

        val rowResize = panelLayout.findViewById<View>(R.id.rowResize)
        val btnToggleResize = panelLayout.findViewById<TextView>(R.id.btnToggleResize)
        setupSwitchRow(rowResize, btnToggleResize, { resizeEnabled }) { resizeEnabled = it }

        val btnRotateLeft = panelLayout.findViewById<Button>(R.id.btnRotateLeft)
        val btnRotateReset = panelLayout.findViewById<Button>(R.id.btnRotateReset)
        val btnRotateRight = panelLayout.findViewById<Button>(R.id.btnRotateRight)
        val btnMirror = panelLayout.findViewById<Button>(R.id.btnMirror)
        val btnQuickReset = panelLayout.findViewById<Button>(R.id.btnQuickReset)

        setupPanelButton(btnRotateLeft) { rotateOverlay(-15f) }
        setupPanelButton(btnRotateReset, rotationAngle == 0f) {
            rotationAngle = 0f
            applyImageTransform()
        }
        setupPanelButton(btnRotateRight) { rotateOverlay(15f) }
        setupPanelButton(btnMirror, mirrorX) {
            mirrorX = !mirrorX
            applyImageTransform()
            showControlPanel()
        }
        setupPanelButton(btnQuickReset) {
            resetImageTransform()
            showControlPanel()
        }

        // Section: LOOK
        val tvOpacityValue = panelLayout.findViewById<TextView>(R.id.tvOpacityValue)
        val sbOpacity = panelLayout.findViewById<SeekBar>(R.id.sbOpacity)
        val safeOpacity = referenceOpacity.coerceIn(0, 100)
        tvOpacityValue.text = getString(R.string.cp_percent_value, safeOpacity)
        sbOpacity.max = 100
        sbOpacity.progress = safeOpacity
        sbOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacityValue.text = getString(R.string.cp_percent_value, progress)
                setReferenceOpacity(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val tvSizeValue = panelLayout.findViewById<TextView>(R.id.tvSizeValue)
        val sbSize = panelLayout.findViewById<SeekBar>(R.id.sbSize)
        val safeScale = (scaleFactor * 100).toInt().coerceIn(20, 400)
        tvSizeValue.text = getString(R.string.cp_percent_value, safeScale)
        sbSize.max = 380
        sbSize.progress = safeScale - 20
        sbSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val current = 20 + progress
                tvSizeValue.text = getString(R.string.cp_percent_value, current)
                scaleFactor = current / 100f
                applyImageTransform()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val btnGrayscale = panelLayout.findViewById<Button>(R.id.btnGrayscale)
        val btnContrast = panelLayout.findViewById<Button>(R.id.btnContrast)
        val btnInvert = panelLayout.findViewById<Button>(R.id.btnInvert)

        setupPanelButton(btnGrayscale, grayscaleEnabled) {
            grayscaleEnabled = !grayscaleEnabled
            applyImageFilter()
            showControlPanel()
        }
        setupPanelButton(btnContrast, contrastEnabled) {
            contrastEnabled = !contrastEnabled
            applyImageFilter()
            showControlPanel()
        }
        setupPanelButton(btnInvert, invertEnabled) {
            invertEnabled = !invertEnabled
            applyImageFilter()
            showControlPanel()
        }

        val btnGuideOff = panelLayout.findViewById<Button>(R.id.btnGuideOff)
        val btnGuideGrid = panelLayout.findViewById<Button>(R.id.btnGuideGrid)
        val btnGuideThirds = panelLayout.findViewById<Button>(R.id.btnGuideThirds)

        setupPanelButton(btnGuideOff, guideMode == GuideGridView.MODE_OFF) {
            setGuideMode(GuideGridView.MODE_OFF)
            showControlPanel()
        }
        setupPanelButton(btnGuideGrid, guideMode == GuideGridView.MODE_GRID) {
            setGuideMode(GuideGridView.MODE_GRID)
            showControlPanel()
        }
        setupPanelButton(btnGuideThirds, guideMode == GuideGridView.MODE_THIRDS) {
            setGuideMode(GuideGridView.MODE_THIRDS)
            showControlPanel()
        }

        // Section: FINISH
        val btnDrawMode = panelLayout.findViewById<Button>(R.id.btnDrawMode)
        val btnDone = panelLayout.findViewById<Button>(R.id.btnDone)
        val btnClose = panelLayout.findViewById<Button>(R.id.btnClose)

        setupPanelButton(btnDrawMode, selected = true) { enterDrawMode() }
        setupPanelButton(btnDone, selected = true) { dismissPanel(animated = true) }
        setupPanelButton(btnClose, danger = true) {
            dismissPanel()
            stopSelf()
        }

        showSystemPanel(panelLayout)
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
        view.scaleX = 0.90f
        view.scaleY = 0.90f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280)
            .setInterpolator(OvershootInterpolator(1.2f))
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
