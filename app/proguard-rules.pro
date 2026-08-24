# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep JavascriptInterface methods used by WebView JS bridge
-keepclassmembers class com.yohuohuohuo.browser.gesture.VideoJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep ViewBinding generated classes
-keep class com.yohuohuohuo.browser.databinding.** { *; }
