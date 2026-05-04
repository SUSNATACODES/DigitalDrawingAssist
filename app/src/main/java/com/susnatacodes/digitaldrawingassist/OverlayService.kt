package com.susnatacodes.digitaldrawingassist

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.IBinder
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager

    // Views
    private lateinit var imageLayout: View
    private lateinit var imageView: ImageView
    private lateinit var controlBtn: ImageView

    // Layout Params
    private lateinit var imageParams: WindowManager.LayoutParams
    private lateinit var btnParams: WindowManager.LayoutParams

    // State Flags
    private var dragEnabled = false
    private var zoomEnabled = false
    private var rotateEnabled = false

    // Gesture Math
    private var scaleFactor = 1f
    private var rotationAngle = 0f
    private var lastAngle = 0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // --- 1. SETUP IMAGE OVERLAY ---
        imageLayout = LayoutInflater.from(this).inflate(R.layout.popup_layout, null)
        imageView = imageLayout.findViewById(R.id.imageView)

        imageParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Android 8.0+ requirement
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or     // Allow touch outside
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        imageParams.gravity = Gravity.TOP or Gravity.START
        imageParams.x = 100
        imageParams.y = 300

        windowManager.addView(imageLayout, imageParams)

        // --- 2. SETUP FLOATING CONTROL BUTTON ---
        controlBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_edit)
            setBackgroundResource(R.drawable.ic_launcher_background) // Or just a color
            setBackgroundColor(0xCC000000.toInt()) // Dark semi-transparent
            setPadding(30, 30, 30, 30)
        }

        btnParams = WindowManager.LayoutParams(
            150, 150,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        btnParams.gravity = Gravity.TOP or Gravity.START
        btnParams.x = 50
        btnParams.y = 100

        windowManager.addView(controlBtn, btnParams)

        // --- 3. INIT GESTURES ---
        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())
        setupControlButton()
        setupImageTouch()

        // Initial State: Locked (Click-through)
        updateImageTouchability()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uriString = intent?.getStringExtra("imageUri")
        if (uriString != null) {
            try {
                imageView.setImageURI(Uri.parse(uriString))
            } catch (e: Exception) {
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
            }
        }
        return START_STICKY
    }

    /**
     * Logic: If ALL controls are OFF, make the image "untouchable" so user
     * can draw on the app behind it.
     */
    private fun updateImageTouchability() {
        val isInteractive = dragEnabled || zoomEnabled || rotateEnabled

        if (isInteractive) {
            // Remove NOT_TOUCHABLE flag -> We can interact
            imageParams.flags = imageParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            // Add NOT_TOUCHABLE flag -> Clicks pass through to app behind
            imageParams.flags = imageParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        if (::imageLayout.isInitialized && ::imageParams.isInitialized) {
            windowManager.updateViewLayout(imageLayout, imageParams)
        }
    }

    // -----------------------------------------------------------
    // CONTROL BUTTON TOUCH (Always Draggable)
    // -----------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlButton() {
        controlBtn.setOnClickListener { showControlPanel() }

        controlBtn.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            var isMoving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = btnParams.x
                        initialY = btnParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        isMoving = false
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
                    MotionEvent.ACTION_UP -> {
                        if (!isMoving) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    // -----------------------------------------------------------
    // IMAGE TOUCH (Condition Based)
    // -----------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageTouch() {
        imageView.setOnTouchListener { _, event ->

            // 1. ZOOM
            if (zoomEnabled) scaleGestureDetector.onTouchEvent(event)

            // 2. ROTATE
            if (rotateEnabled && event.pointerCount == 2) {
                val deltaX = event.getX(1) - event.getX(0)
                val deltaY = event.getY(1) - event.getY(0)
                val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                    lastAngle = angle
                } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    rotationAngle += (angle - lastAngle)
                    imageView.rotation = rotationAngle
                    lastAngle = angle
                }
            }

            // 3. DRAG
            if (dragEnabled) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Hack: Store raw start pos in view tag or just rely on delta
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Simple relative drag logic
                        // Note: rawX is absolute screen coords
                        // Ideally we need initial touch logic like the button, but
                        // since this is inside a listener without state variables,
                        // we use a simplified delta approach or a helper class.
                        // FOR SIMPLICITY in this snippet, we'll assume standard drag:
                    }
                }
                // NOTE: To keep this file error-free and simple, I will implement
                // the full Drag Logic using a separate OnTouchListener instance below.
            }

            true
        }

        // Re-assign with full state listener
        imageView.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (zoomEnabled) scaleGestureDetector.onTouchEvent(event)

                // Rotate logic (same as above)
                if (rotateEnabled && event.pointerCount == 2) {
                    val deltaX = event.getX(1) - event.getX(0)
                    val deltaY = event.getY(1) - event.getY(0)
                    val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
                    if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) lastAngle = angle
                    else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        rotationAngle += (angle - lastAngle)
                        imageView.rotation = rotationAngle
                        lastAngle = angle
                    }
                }

                if (!dragEnabled) return true // Consume but don't drag

                when (event.action) {
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
                return false
            }
        })
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.2f, min(scaleFactor, 5.0f))
            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            return true
        }
    }

    // -----------------------------------------------------------
    // CONTROL PANEL (Dialog)
    // -----------------------------------------------------------
    private fun showControlPanel() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(0xEE222222.toInt()) // Dark background
        }

        // Helper to create toggle buttons
        fun addToggle(text: String, isEnabled: Boolean, onClick: () -> Unit) {
            val btn = Button(this)
            btn.text = if (isEnabled) "$text [ON]" else "$text [OFF]"
            btn.setOnClickListener { onClick() }
            layout.addView(btn)
        }

        addToggle("Drag", dragEnabled) {
            dragEnabled = !dragEnabled
            updateImageTouchability()
            stopDialog(layout)
        }

        addToggle("Zoom", zoomEnabled) {
            zoomEnabled = !zoomEnabled
            updateImageTouchability()
            stopDialog(layout)
        }

        addToggle("Rotate", rotateEnabled) {
            rotateEnabled = !rotateEnabled
            updateImageTouchability()
            stopDialog(layout)
        }

        val alphaBtn = Button(this).apply { text = "Transparency" }
        alphaBtn.setOnClickListener { showTransparencySlider(); stopDialog(layout) }
        layout.addView(alphaBtn)

        val closeBtn = Button(this).apply { text = "Close Overlay" }
        closeBtn.setOnClickListener {
            stopDialog(layout)
            stopSelf()
        }
        layout.addView(closeBtn)

        showSystemDialog(layout)
    }

    private fun showTransparencySlider() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(0xEE222222.toInt())
        }

        val title = TextView(this).apply {
            text = "Adjust Transparency"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            gravity = Gravity.CENTER
        }

        val slider = SeekBar(this).apply {
            max = 100
            progress = (imageView.alpha * 100).toInt()
            setPadding(0, 40, 0, 40)
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                imageView.alpha = p / 100f
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val btnDone = Button(this).apply {
            text = "Done"
            setOnClickListener { stopDialog(container) }
        }

        container.addView(title)
        container.addView(slider)
        container.addView(btnDone)

        showSystemDialog(container)
    }

    private fun showSystemDialog(view: View) {
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        // CRITICAL: Required for Dialogs in Services
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()

        view.tag = dialog // Store reference to dismiss later
    }

    private fun stopDialog(view: View) {
        (view.tag as? AlertDialog)?.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::imageLayout.isInitialized) windowManager.removeView(imageLayout)
            if (::controlBtn.isInitialized) windowManager.removeView(controlBtn)
        } catch (e: Exception) {
            // Views already removed
        }
    }
}
