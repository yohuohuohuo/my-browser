package com.yohuohuohuo.browser.gesture

import android.content.pm.ActivityInfo

object VideoOrientation {
    fun fromDimensions(width: Float, height: Float): Int? {
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            return null
        }
        return when {
            width > height -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            height > width -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> null
        }
    }
}
