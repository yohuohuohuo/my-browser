package com.yohuohuohuo.browser

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Content draws behind the transparent status bar without deprecated flags.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment is BrowserFragment && fragment.onBackPressed()) return
                finish()
            }
        })

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, BrowserFragment())
                .commit()
        }
    }

    fun setFullscreen(fullscreen: Boolean, requestedOrientation: Int? = null) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreen) {
            requestedOrientation?.let {
                if (it == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    it == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                ) {
                    setRequestedOrientation(it)
                }
            }
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = true
        }
    }
}
