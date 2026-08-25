package com.yohuohuohuo.browser.gesture

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoOrientationTest {

    @Test
    fun `wide video requests landscape`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            VideoOrientation.fromDimensions(1920f, 1080f)
        )
    }

    @Test
    fun `tall video requests portrait`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            VideoOrientation.fromDimensions(1080f, 1920f)
        )
    }

    @Test
    fun `invalid or square dimensions do not force an orientation`() {
        assertNull(VideoOrientation.fromDimensions(0f, 1080f))
        assertNull(VideoOrientation.fromDimensions(1080f, 0f))
        assertNull(VideoOrientation.fromDimensions(1000f, 1000f))
    }
}
