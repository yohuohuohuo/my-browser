package com.yohuohuohuo.browser.ua

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UaManagerTest {

    private lateinit var context: Context
    private lateinit var uaManager: UaManager

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // 清理 SharedPreferences 确保测试隔离
        context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        uaManager = UaManager(context)
    }

    @Test
    fun `default mode is mobile`() {
        assertFalse(uaManager.isDesktop())
    }

    @Test
    fun `toggle switches to desktop and returns true`() {
        val result = uaManager.toggle()
        assertTrue(result)
        assertTrue(uaManager.isDesktop())
    }

    @Test
    fun `toggle twice returns to mobile`() {
        uaManager.toggle()
        assertTrue(uaManager.isDesktop())

        val result = uaManager.toggle()
        assertFalse(result)
        assertFalse(uaManager.isDesktop())
    }

    @Test
    fun `desktop ua contains Windows NT`() {
        uaManager.toggle()
        val ua = uaManager.getUa()
        assertTrue(ua.contains("Windows NT 10.0"))
        assertTrue(ua.contains("Chrome"))
    }

    @Test
    fun `mobile ua differs from desktop ua`() {
        val mobileUa = uaManager.getUa()
        uaManager.toggle()
        val desktopUa = uaManager.getUa()

        // 移动 UA 与桌面 UA 必须不同
        assertNotEquals(mobileUa, desktopUa)
        // 桌面 UA 包含 Windows NT 标识
        assertTrue(desktopUa.contains("Windows NT 10.0"))
        // 移动 UA 不应包含 Windows NT 标识
        assertFalse(mobileUa.contains("Windows NT 10.0"))
    }

    @Test
    fun `mode persists across instances`() {
        uaManager.toggle()
        assertTrue(uaManager.isDesktop())

        // 创建新实例，验证持久化
        val newManager = UaManager(context)
        assertTrue(newManager.isDesktop())
    }

    @Test
    fun `mobile mode persists across instances`() {
        assertFalse(uaManager.isDesktop())

        val newManager = UaManager(context)
        assertFalse(newManager.isDesktop())
    }
}
