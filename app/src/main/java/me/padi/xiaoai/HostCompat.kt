package me.padi.xiaoai

import android.content.Context
import top.sacz.xphelper.XpHelper
import me.padi.xiaoai.hook.HookEntry


fun Context.writablePrefs() = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)

/**
 * 宿主兼容性工具类 - 仅保留超级小爱适配 (com.miui.voiceassist)
 */
object HostCompat {
    const val HOST_PACKAGE = "com.miui.voiceassist"

    /**
     * 由 VoiceAssistHook.Application.attach 在宿主进程启动时保存的宿主 ClassLoader。
     * 在模块 Activity（SchoolScreen / WebViewScreen）中 context.classLoader 是模块 loader，
     * 无法加载宿主混淆类，必须使用这个字段。
     */
    @JvmField
    var hostLoader: ClassLoader? = null

    /**
     * 导入完成标志位，用于在回到 AiWebActivity 时强制刷新一次
     */
    @Volatile
    var isImportFinished: Boolean = false

    private fun getCachedClassName(context: Context?, key: String, default: String): String {
        return context?.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)?.getString(key, default) ?: default
    }

    fun isLogin(): Boolean = true // TODO: 实现真实的登录检查

    fun getAppId(): String = "326813440150602752"

    /**
     * 获取访问令牌
     * @param context 优先使用 Context.writablePrefs() 中的本地缓存
     * @param loader 宿主的 ClassLoader；传 null 时自动使用 [hostLoader]
     */
    fun getAccessToken(context: Context? = null, loader: ClassLoader? = null, forceRefresh: Boolean = false): String? {
        val effectiveLoader = loader ?: hostLoader

        if (effectiveLoader != null) {
            try {
                val className = getCachedClassName(context, "token_class", "c30.b")
                val loginMgrClass = try {
                    effectiveLoader.loadClass(className)
                } catch (e: ClassNotFoundException) {
                    // 类名可能已混淆变化，清除缓存让下次重新扫描
                    context?.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)?.edit()?.remove("token_class")?.apply()
                    null
                } ?: return context?.writablePrefs()?.getString("service_token", "") ?: HookEntry.prefs.getString("service_token", "")

                var instance: Any? = null
                try {
                    val fieldA = loginMgrClass.getDeclaredField("a")
                    fieldA.isAccessible = true
                    instance = fieldA.get(null)
                } catch (_: Exception) {}

                if (instance != null) {
                    var token: String? = null

                    // 优先使用 getAuthorization(boolean)：宿主自己构建的完整 Authorization header
                    // 它内部已正确处理 device binding，我们直接使用，不再手动拼装 buildAuth
                    try {
                        val method = loginMgrClass.getDeclaredMethod("getAuthorization", Boolean::class.javaPrimitiveType)
                        method.isAccessible = true
                        token = method.invoke(instance, forceRefresh)?.toString()?.trim()
                    } catch (_: NoSuchMethodException) {}

                    // 备用：getOauthV2AccessToken(boolean)
                    if (token.isNullOrBlank()) {
                        try {
                            val method = loginMgrClass.getDeclaredMethod("getOauthV2AccessToken", Boolean::class.javaPrimitiveType)
                            method.isAccessible = true
                            token = method.invoke(instance, forceRefresh)?.toString()?.trim()
                        } catch (_: Exception) {}
                    }

                    if (!token.isNullOrBlank()) return token
                }
            } catch (_: Exception) {}
        }

        return if (context != null) context.writablePrefs().getString("service_token", "") else HookEntry.prefs.getString("service_token", "")
    }

    /**
     * 获取设备 ID
     * @param loader 宿主的 ClassLoader；传 null 时自动使用 [hostLoader]
     */
    fun getDeviceId(context: Context, loader: ClassLoader? = null): String? {
        val effectiveLoader = loader ?: hostLoader
        if (effectiveLoader != null) {
            try {
                val className = getCachedClassName(context, "device_class", "q70.j")
                val deviceMgrClass = effectiveLoader.loadClass(className)
                val method = deviceMgrClass.getDeclaredMethod("getDeviceId", Context::class.java)
                method.isAccessible = true
                val deviceId = method.invoke(null, context)?.toString()
                if (!deviceId.isNullOrBlank()) return deviceId
            } catch (_: Exception) {}
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
