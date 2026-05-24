package com.susnatacodes.digitaldrawingassist

import android.view.View
import android.view.animation.DecelerateInterpolator

object UiEffects {

    fun applyPressAnimation(vararg views: View) {
        views.forEach { it.isClickable = true }
    }

    fun playStaggeredEntrance(views: List<View>, distance: Float = 26f, delayStep: Long = 55L) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = distance
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * delayStep)
                .setDuration(430L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }
}
