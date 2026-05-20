package com.susnatacodes.digitaldrawingassist

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Service
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.atan2
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

    private var panelDialog: AlertDialog? = null
    private var dragEnabled = false
    private var zoomEnabled = false
    private var rotateEnabled = false
    private var mirrorX = false
    private var grayscaleEnabled = false
    private var contrastEnabled = false
    private var invertEnabled = false
    private var keepScreenOn = false
    private var guideMode = GuideGridView.MODE_OFF
    private var scaleFactor = 1f
    private var rotationAngle = 0f
    private var lastAngle = 0f

    private data class PanelButtonSpec(
        val text: String,
        val filled: Boolean = false,
        val danger: Boolean = false,
        val action: () -> Unit
    )

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        imageLayout = LayoutInflater.from(this).inflate(R.layout.popup_layout, null)
        imageView = imageLayout.findViewById(R.id.imageView)
        guideGrid = imageLayout.findViewById(R.id.guideGrid)
        imageView.alpha = 0.82f
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
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundResource(R.drawable.bg_floating_control)
            setColorFilter(Color.WHITE)
            setPadding(32.dp(), 32.dp(), 32.dp(), 32.dp())
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
                imageView.setImageURI(Uri.parse(uriString))
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
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun updateImageTouchability() {
        val isInteractive = dragEnabled || zoomEnabled || rotateEnabled
        imageParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        if (!isInteractive) {
            imageParams.flags = imageParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (keepScreenOn) {
            imageParams.flags = imageParams.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
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
                if (!(dragEnabled || zoomEnabled || rotateEnabled)) return false

                if (zoomEnabled) scaleGestureDetector.onTouchEvent(event)

                if (rotateEnabled && event.pointerCount == 2) {
                    val deltaX = event.getX(1) - event.getX(0)
                    val deltaY = event.getY(1) - event.getY(0)
                    val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

                    if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        lastAngle = angle
                    } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        rotationAngle += angle - lastAngle
                        lastAngle = angle
                        applyImageTransform()
                    }
                }

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
        scaleFactor = 1f
        rotationAngle = 0f
        mirrorX = false
        grayscaleEnabled = false
        contrastEnabled = false
        invertEnabled = false
        keepScreenOn = false
        guideMode = GuideGridView.MODE_OFF
        imageView.alpha = 0.82f
        guideGrid.setGuideMode(guideMode)
        applyImageFilter()
        applyImageTransform()
        updateImageTouchability()
        centerOverlay()
    }

    private fun centerOverlay() {
        imageLayout.post {
            val metrics = resources.displayMetrics
            imageParams.x = ((metrics.widthPixels - imageLayout.width) / 2).coerceAtLeast(0)
            imageParams.y = ((metrics.heightPixels - imageLayout.height) / 3).coerceAtLeast(0)
            windowManager.updateViewLayout(imageLayout, imageParams)
        }
    }

    private fun nudgeOverlay(deltaX: Int, deltaY: Int) {
        imageParams.x += deltaX.dp()
        imageParams.y += deltaY.dp()
        windowManager.updateViewLayout(imageLayout, imageParams)
    }

    private fun rotateOverlay(delta: Float) {
        rotationAngle += delta
        applyImageTransform()
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
        panelDialog?.dismiss()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 20.dp(), 22.dp(), 18.dp())
            background = roundedRect(0xF21B1D27.toInt(), 8f, 0x33FFFFFF, 1f)
        }

        val title = TextView(this).apply {
            text = getString(R.string.cp_title)
            setTextColor(Color.WHITE)
            textSize = 22f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(title)

        val mode = TextView(this).apply {
            text = if (dragEnabled || zoomEnabled || rotateEnabled) {
                getString(R.string.cp_mode_edit)
            } else {
                getString(R.string.cp_mode_draw)
            }
            setTextColor(0xFFE8F7F3.toInt())
            textSize = 13f
            setPadding(12.dp(), 8.dp(), 12.dp(), 8.dp())
            background = roundedRect(0x292DD4BF, 8f, 0x5533E0CC, 1f)
        }
        val modeParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8.dp()
            bottomMargin = 14.dp()
        }
        container.addView(mode, modeParams)

        addSectionTitle(container, getString(R.string.cp_section_editing))
        addSwitchRow(container, getString(R.string.cp_drag), getString(R.string.cp_drag_sub), dragEnabled) {
            dragEnabled = it
            updateImageTouchability()
        }
        addSwitchRow(container, getString(R.string.cp_zoom), getString(R.string.cp_zoom_sub), zoomEnabled) {
            zoomEnabled = it
            updateImageTouchability()
        }
        addSwitchRow(container, getString(R.string.cp_rotate), getString(R.string.cp_rotate_sub), rotateEnabled) {
            rotateEnabled = it
            updateImageTouchability()
        }
        addSwitchRow(container, getString(R.string.cp_mirror), getString(R.string.cp_mirror_sub), mirrorX) {
            mirrorX = it
            applyImageTransform()
        }
        addSwitchRow(container, getString(R.string.cp_keep_screen), getString(R.string.cp_keep_screen_sub), keepScreenOn) {
            keepScreenOn = it
            updateImageTouchability()
        }

        addSlider(
            container,
            getString(R.string.cp_transparency),
            (imageView.alpha * 100).toInt(),
            15,
            100
        ) { value ->
            imageView.alpha = value / 100f
        }

        addSlider(container, getString(R.string.cp_size), (scaleFactor * 100).toInt(), 20, 400) { value ->
            scaleFactor = value / 100f
            applyImageTransform()
        }

        addSectionTitle(container, getString(R.string.cp_section_guides))
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_guide_off), guideMode == GuideGridView.MODE_OFF) {
                    setGuideMode(GuideGridView.MODE_OFF)
                },
                PanelButtonSpec(getString(R.string.cp_guide_grid), guideMode == GuideGridView.MODE_GRID) {
                    setGuideMode(GuideGridView.MODE_GRID)
                }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_guide_thirds), guideMode == GuideGridView.MODE_THIRDS) {
                    setGuideMode(GuideGridView.MODE_THIRDS)
                },
                PanelButtonSpec(getString(R.string.cp_guide_diagonal), guideMode == GuideGridView.MODE_DIAGONAL) {
                    setGuideMode(GuideGridView.MODE_DIAGONAL)
                }
            )
        )

        addSectionTitle(container, getString(R.string.cp_section_filters))
        addSwitchRow(container, getString(R.string.cp_grayscale), getString(R.string.cp_grayscale_sub), grayscaleEnabled) {
            grayscaleEnabled = it
            applyImageFilter()
        }
        addSwitchRow(container, getString(R.string.cp_contrast), getString(R.string.cp_contrast_sub), contrastEnabled) {
            contrastEnabled = it
            applyImageFilter()
        }
        addSwitchRow(container, getString(R.string.cp_invert), getString(R.string.cp_invert_sub), invertEnabled) {
            invertEnabled = it
            applyImageFilter()
        }

        addSectionTitle(container, getString(R.string.cp_section_precision))
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_size_small)) {
                    scaleFactor = 0.65f
                    applyImageTransform()
                },
                PanelButtonSpec(getString(R.string.cp_size_normal)) {
                    scaleFactor = 1f
                    applyImageTransform()
                },
                PanelButtonSpec(getString(R.string.cp_size_large)) {
                    scaleFactor = 1.45f
                    applyImageTransform()
                }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_rotate_left)) { rotateOverlay(-15f) },
                PanelButtonSpec(getString(R.string.cp_rotate_right)) { rotateOverlay(15f) }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_nudge_left)) { nudgeOverlay(-12, 0) },
                PanelButtonSpec(getString(R.string.cp_nudge_up)) { nudgeOverlay(0, -12) },
                PanelButtonSpec(getString(R.string.cp_nudge_down)) { nudgeOverlay(0, 12) },
                PanelButtonSpec(getString(R.string.cp_nudge_right)) { nudgeOverlay(12, 0) }
            )
        )

        addSectionTitle(container, getString(R.string.cp_section_actions))
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_draw_mode), true) {
                    dragEnabled = false
                    zoomEnabled = false
                    rotateEnabled = false
                    updateImageTouchability()
                    panelDialog?.dismiss()
                    Toast.makeText(this, getString(R.string.msg_draw_mode), Toast.LENGTH_SHORT).show()
                },
                PanelButtonSpec(getString(R.string.cp_center)) { centerOverlay() }
            )
        )
        addButtonRow(
            container,
            listOf(
                PanelButtonSpec(getString(R.string.cp_reset)) { resetImageTransform() },
                PanelButtonSpec(getString(R.string.cp_done), true) { panelDialog?.dismiss() },
                PanelButtonSpec(getString(R.string.cp_close), danger = true) {
                    panelDialog?.dismiss()
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

        showSystemDialog(scrollView)
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
            weightSum = buttons.size.toFloat()
        }

        buttons.forEachIndexed { index, spec ->
            val params = LinearLayout.LayoutParams(0, 46.dp(), 1f).apply {
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

    private fun addSwitchRow(
        parent: LinearLayout,
        title: String,
        subtitle: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 11.dp(), 10.dp(), 11.dp())
            background = roundedRect(0x18FFFFFF, 8f, 0x24FFFFFF, 1f)
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

        val switch = Switch(this).apply {
            isChecked = checked
            showText = false
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }

        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(switch)
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
            text = "$safeInitial%"
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
                    value.text = "$current%"
                    onChanged(current)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        section.addView(slider)
        parent.addView(section, rowParams(bottom = 10))
    }

    private fun makePanelButton(
        text: String,
        filled: Boolean,
        danger: Boolean = false,
        onClick: () -> Unit
    ): Button {
        return Button(this).apply {
            this.text = text
            textSize = 14f
            setAllCaps(false)
            minHeight = 0
            minWidth = 0
            includeFontPadding = false
            setTextColor(Color.WHITE)
            background = when {
                danger -> roundedRect(0xFF3E2430.toInt(), 8f, 0xFFFF6B6B.toInt(), 1f)
                filled -> GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(0xFFFF6B6B.toInt(), 0xFF2DD4BF.toInt())
                ).apply { cornerRadius = 8f.dp() }
                else -> roundedRect(0x16FFFFFF, 8f, 0x38FFFFFF, 1f)
            }
            setOnClickListener { onClick() }
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
                false
            }
        }
    }

    private fun showSystemDialog(view: View) {
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnDismissListener {
            if (panelDialog == dialog) panelDialog = null
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        panelDialog = dialog
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

    private fun rowParams(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = top.dp()
            bottomMargin = bottom.dp()
        }
    }

    private fun actionParams(start: Int = 0, end: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, 46.dp(), 1f).apply {
            marginStart = start.dp()
            marginEnd = end.dp()
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
        panelDialog?.dismiss()
        try {
            if (::imageLayout.isInitialized) windowManager.removeView(imageLayout)
            if (::controlBtn.isInitialized) windowManager.removeView(controlBtn)
        } catch (_: Exception) {
            // The window manager can already be detached during service shutdown.
        }
    }
}
