package me.padi.xiaoai

import android.content.Context
import top.sacz.xphelper.XpHelper
import com.highcapable.yukihookapi.hook.factory.prefs

fun Context.readRawFile(resId: Int): String? {
    return try {
        resources.openRawResource(resId).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }
}

/**
 * 宿主兼容性工具类 - 仅保留超级小爱适配 (com.miui.voiceassist)
 */
object HostCompat {
    const val HOST_PACKAGE = "com.miui.voiceassist"

    fun isLogin(): Boolean = true // 简化逻辑，假设已登录

    fun getAppId(): String = "326813440150602752"

    fun getAccessToken(): String? {
        return try {
            val loader = XpHelper.context.classLoader
            val loginMgrClass = loader.loadClass("c30.b")
            val fieldA = loginMgrClass.getDeclaredField("a")
            fieldA.isAccessible = true
            val instance = fieldA.get(null)
            val fieldF = loginMgrClass.getDeclaredField("f")
            fieldF.isAccessible = true
            fieldF.get(instance) as? String
        } catch (e: Exception) {
            null
        }
    }

    fun getDeviceId(context: Context): String? {
        return try {
            val loader = context.classLoader
            val deviceMgrClass = loader.loadClass("q70.j")
            val methodB = deviceMgrClass.getDeclaredMethod("b")
            methodB.isAccessible = true
            val instance = methodB.invoke(null)
            val methodA = deviceMgrClass.getDeclaredMethod("a")
            methodA.isAccessible = true
            methodA.invoke(instance) as? String
        } catch (e: Exception) {
            XpHelper.context.prefs().getString("device_id", "")
        }
    }
}
