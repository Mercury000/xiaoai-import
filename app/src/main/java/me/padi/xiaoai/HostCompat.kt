package me.padi.xiaoai

import android.content.Context
import top.sacz.xphelper.XpHelper
import me.padi.xiaoai.hook.HookEntry

fun Context.readRawFile(resId: Int): String? {
    return try {
        resources.openRawResource(resId).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }
}

fun Context.writablePrefs() = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)

/**
 * 宿主兼容性工具类 - 仅保留超级小爱适配 (com.miui.voiceassist)
 */
object HostCompat {
    const val HOST_PACKAGE = "com.miui.voiceassist"

    private fun getCachedClassName(context: Context?, key: String, default: String): String {
        return context?.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)?.getString(key, default) ?: default
    }

    fun isLogin(): Boolean = true // TODO: 实现真实的登录检查

    fun getAppId(): String = "326813440150602752"

    /**
     * 获取访问令牌
     * @param context 优先使用 Context.writablePrefs() 中的本地缓存
     * @param loader 宿主的 ClassLoader (如果在宿主进程中调用) 或 null
     */
    fun getAccessToken(context: Context? = null, loader: ClassLoader? = null): String? {
        if (loader != null) {
            try {
                // 动态获取类名
                val className = getCachedClassName(context, "token_class", "c30.b")
                val loginMgrClass = loader.loadClass(className)
                var instance: Any? = null
                try {
                    instance = loginMgrClass.newInstance()
                } catch (e: Exception) {
                    if (context != null) {
                        try {
                            val constructor = loginMgrClass.getDeclaredConstructor(Context::class.java)
                            constructor.isAccessible = true
                            instance = constructor.newInstance(context)
                        } catch (e2: Exception) {}
                    }
                }
                
                if (instance != null) {
                    val method = loginMgrClass.getDeclaredMethod("getOauthV2AccessToken", Boolean::class.javaPrimitiveType)
                    method.isAccessible = true
                    val result = method.invoke(instance, false)
                    val token = result?.toString()?.trim()
                    if (!token.isNullOrBlank()) {
                        return token
                    }
                }
            } catch (e: Exception) {
                // 如果方法不存在或反射失败，尝试直接读取字段 f (如果它是 token)
                try {
                    val className = getCachedClassName(context, "token_class", "c30.b")
                    val loginMgrClass = loader.loadClass(className)
                    val fieldA = loginMgrClass.getDeclaredField("a")
                    fieldA.isAccessible = true
                    val instance = fieldA.get(null)
                    val fieldF = loginMgrClass.getDeclaredField("f")
                    fieldF.isAccessible = true
                    return fieldF.get(instance) as? String
                } catch (e2: Exception) {}
            }
        }
        if (context != null) return context.writablePrefs().getString("service_token", "")
        return HookEntry.prefs.getString("service_token", "")
    }

    /**
     * 获取设备 ID
     */
    fun getDeviceId(context: Context, loader: ClassLoader? = null): String? {
        if (loader != null) {
            try {
                // 动态获取类名
                val className = getCachedClassName(context, "device_class", "q70.j")
                val deviceMgrClass = loader.loadClass(className)
                val method = deviceMgrClass.getDeclaredMethod("getDeviceId", Context::class.java)
                method.isAccessible = true
                val result = method.invoke(null, context)
                return result?.toString()
            } catch (e: Exception) {
            }
        }
        return context.writablePrefs().getString("device_id", "") ?: HookEntry.prefs.getString("device_id", "")
    }

    fun saveTokens(context: Context, token: String?, deviceId: String?) {
        val editor = context.writablePrefs().edit()
        if (!token.isNullOrBlank()) editor.putString("service_token", token)
        if (!deviceId.isNullOrBlank()) editor.putString("device_id", deviceId)
        editor.apply()
    }
}
