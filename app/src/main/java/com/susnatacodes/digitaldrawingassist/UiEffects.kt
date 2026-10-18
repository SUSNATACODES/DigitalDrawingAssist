package com.susnatacodes.digitaldrawingassist

import android.R
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object UiEffects {

    fun applyPressAnimation(vararg views: View) {
        views.forEach { view ->
            view.isClickable = true
            view.isFocusable = true

            val stateListAnimator = StateListAnimator()

            // Pressed state (scale down, slightly transparent)
            val pressedScaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 0.94f)
            val pressedScaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.94f)
            val pressedAlpha = ObjectAnimator.ofFloat(view, View.ALPHA, 0.85f)

            val pressedSet = AnimatorSet().apply {
                playTogether(pressedScaleX, pressedScaleY, pressedAlpha)
                duration = 80
                interpolator = DecelerateInterpolator()
            }

            // Default state (scale back up with snappy overshoot)
            val defaultScaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f)
            val defaultScaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
            val defaultAlpha = ObjectAnimator.ofFloat(view, View.ALPHA, 1f)

            val defaultSet = AnimatorSet().apply {
                playTogether(defaultScaleX, defaultScaleY, defaultAlpha)
                duration = 160
                interpolator = OvershootInterpolator(1.4f)
            }

            stateListAnimator.addState(intArrayOf(R.attr.state_pressed), pressedSet)
            stateListAnimator.addState(intArrayOf(), defaultSet)

            view.stateListAnimator = stateListAnimator
        }
    }

    fun playStaggeredEntrance(views: List<View>, distance: Float = 32f, delayStep: Long = 50L) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = distance
            view.scaleX = 0.92f
            view.scaleY = 0.92f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * delayStep)
                .setDuration(450L)
                .setInterpolator(OvershootInterpolator(1.2f))
                .start()
        }
    }

    fun playShakeAnimation(view: View) {
        val animator = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, 22f, -22f, 16f, -16f, 8f, -8f, 0f)
        animator.duration = 420L
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    fun playPulseAnimation(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.035f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800L
        }
        val scaleY = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.035f, 1f).apply {
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            duration = 1800L
        }
        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }
}
