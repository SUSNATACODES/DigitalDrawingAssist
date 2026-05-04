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
    private lateinit var imageLayout: View
    private lateinit var imageView: ImageView
    private lateinit var controlBtn: ImageView

    private lateinit var imageParams: WindowManager.LayoutParams
    private lateinit var btnParams: WindowManager.LayoutParams

    private var dragEnabled = false
    private var zoomEnabled = false
    private var rotateEnabled = false
    private var resizeEnabled = false

    private var scaleFactor = 1f
    private var rotationAngle = 0f
    private var lastAngle = 0f

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // IMAGE VIEW
        imageLayout = LayoutInflater.from(this).inflate(R.layout.popup_layout, null)
        imageView = imageLayout.findViewById(R.id.imageView)

        imageParams = WindowManager.LayoutParams(
            600,
            600,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        imageParams.gravity = Gravity.TOP or Gravity.START
        imageParams.x = 100
        imageParams.y = 300

        windowManager.addView(imageLayout, imageParams)

        // FLOAT BUTTON
        controlBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_manage)
            setBackgroundColor(0xCC000000.toInt())
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

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        setupControlButton()
        setupImageTouch()
        updateTouchMode()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("imageUri")?.let {
            imageView.setImageURI(Uri.parse(it))
        }
        return START_STICKY
    }

    // ---------------- TOUCH MODE ----------------
    private fun updateTouchMode() {
        val interactive = dragEnabled || zoomEnabled || rotateEnabled || resizeEnabled

        imageParams.flags = if (interactive) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        windowManager.updateViewLayout(imageLayout, imageParams)
    }

    // ---------------- CONTROL BUTTON ----------------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlButton() {

        controlBtn.setOnClickListener { showControlPanel() }

        controlBtn.setOnTouchListener(object : View.OnTouchListener {

            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moving = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {
                        startX = btnParams.x
                        startY = btnParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moving = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()

                        if (abs(dx) > 10 || abs(dy) > 10) moving = true

                        btnParams.x = startX + dx
                        btnParams.y = startY + dy
                        windowManager.updateViewLayout(controlBtn, btnParams)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        if (!moving) v.performClick()
                        return true
                    }
                }
                return false
            }
        })
    }

    // ---------------- IMAGE TOUCH ----------------
    @SuppressLint("ClickableViewAccessibility")
    private fun setupImageTouch() {

        imageView.setOnTouchListener(object : View.OnTouchListener {

            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {

                if (zoomEnabled) scaleGestureDetector.onTouchEvent(event)

                if (rotateEnabled && event.pointerCount == 2) {
                    val dx = event.getX(1) - event.getX(0)
                    val dy = event.getY(1) - event.getY(0)

                    val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

                    if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                        lastAngle = angle
                    } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        rotationAngle += (angle - lastAngle)
                        imageView.rotation = rotationAngle
                        lastAngle = angle
                    }
                }

                // RESIZE
                if (resizeEnabled) {
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        imageParams.width = (imageParams.width + event.x / 10).toInt()
                        imageParams.height = (imageParams.height + event.y / 10).toInt()
                        windowManager.updateViewLayout(imageLayout, imageParams)
                        return true
                    }
                }

                // DRAG
                if (!dragEnabled) return true

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {
                        startX = imageParams.x
                        startY = imageParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        imageParams.x = startX + (event.rawX - touchX).toInt()
                        imageParams.y = startY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(imageLayout, imageParams)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        v.performClick()
                        return true
                    }
                }

                return false
            }
        })
    }

    // ---------------- ZOOM ----------------
    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = max(0.3f, min(scaleFactor, 4f))
            imageView.scaleX = scaleFactor
            imageView.scaleY = scaleFactor
            return true
        }
    }

    // ---------------- CONTROL PANEL ----------------
    private fun showControlPanel() {

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(0xDD222222.toInt())
        }

        fun toggle(name: String, state: Boolean, action: () -> Unit) {
            val btn = Button(this)
            btn.text = if (state) "$name ON" else "$name OFF"
            btn.setOnClickListener {
                action()
                updateTouchMode()
            }
            layout.addView(btn)
        }

        toggle("DRAG", dragEnabled) { dragEnabled = !dragEnabled }
        toggle("ZOOM", zoomEnabled) { zoomEnabled = !zoomEnabled }
        toggle("ROTATE", rotateEnabled) { rotateEnabled = !rotateEnabled }
        toggle("RESIZE", resizeEnabled) { resizeEnabled = !resizeEnabled }

        val transBtn = Button(this).apply {
            text = "TRANSPARENCY"
            setOnClickListener { showTransparency() }
        }

        val minimize = Button(this).apply {
            text = "MINIMIZE"
            setOnClickListener { imageLayout.visibility = View.GONE }
        }

        val show = Button(this).apply {
            text = "SHOW"
            setOnClickListener { imageLayout.visibility = View.VISIBLE }
        }

        val close = Button(this).apply {
            text = "CLOSE"
            setOnClickListener { stopSelf() }
        }

        layout.addView(transBtn)
        layout.addView(minimize)
        layout.addView(show)
        layout.addView(close)

        val dialog = AlertDialog.Builder(this).setView(layout).create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun showTransparency() {
        val seek = SeekBar(this)
        seek.max = 100
        seek.progress = (imageView.alpha * 100).toInt()

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, v: Int, b: Boolean) {
                imageView.alpha = v / 100f
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(this).setView(seek).create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            windowManager.removeView(imageLayout)
            windowManager.removeView(controlBtn)
        } catch (_: Exception) {}
    }
}