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

    /**
     * 由 VoiceAssistHook.Application.attach 在宿主进程启动时保存的宿主 ClassLoader。
     * 在模块 Activity（SchoolScreen / WebViewScreen）中 context.classLoader 是模块 loader，
     * 无法加载宿主混淆类，必须使用这个字段。
     */
    @JvmField
    var hostLoader: ClassLoader? = null

    private fun getCachedClassName(context: Context?, key: String, default: String): String {
        return context?.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)?.getString(key, default) ?: default
    }

    fun isLogin(): Boolean = true // TODO: 实现真实的登录检查

    fun getAppId(): String = "326813440150602752"

    private fun tokenMask(token: String?): String {
        if (token.isNullOrBlank()) return "<blank>"
        return if (token.length > 12) "${token.take(10)}...${token.takeLast(4)}" else token
    }

    /**
     * 获取访问令牌
     * @param context 优先使用 Context.writablePrefs() 中的本地缓存
     * @param loader 宿主的 ClassLoader；传 null 时自动使用 [hostLoader]
     */
    fun getAccessToken(context: Context? = null, loader: ClassLoader? = null, forceRefresh: Boolean = false): String? {
        val tag = "XiaoAiKeBiao"
        val effectiveLoader = loader ?: hostLoader
        android.util.Log.d(tag, "getAccessToken called: forceRefresh=$forceRefresh, loaderArg=${loader != null}, hostLoader=${hostLoader != null}, effectiveLoader=${effectiveLoader != null}")

        if (effectiveLoader != null) {
            try {
                val className = getCachedClassName(context, "token_class", "c30.b")
                android.util.Log.d(tag, "getAccessToken: trying className=$className")

                val loginMgrClass = try {
                    effectiveLoader.loadClass(className).also {
                        android.util.Log.d(tag, "getAccessToken: class loaded OK -> $className")
                    }
                } catch (e: ClassNotFoundException) {
                    android.util.Log.e(tag, "getAccessToken: ClassNotFoundException for '$className', falling back to prefs", e)
                    null
                }

                if (loginMgrClass == null) {
                    // 类名可能已混淆变化，清除缓存让下次重新扫描
                    context?.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)?.edit()?.remove("token_class")?.apply()
                    android.util.Log.w(tag, "getAccessToken: cleared cached token_class name")
                } else {
                    // 打印该类所有方法，方便确认反射目标
                    val methodNames = loginMgrClass.declaredMethods.joinToString { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                    android.util.Log.d(tag, "getAccessToken: methods in $className -> $methodNames")

                    var instance: Any? = null
                    try {
                        val fieldA = loginMgrClass.getDeclaredField("a")
                        fieldA.isAccessible = true
                        instance = fieldA.get(null)
                        android.util.Log.d(tag, "getAccessToken: got instance via field 'a': ${instance != null}")
                    } catch (e: Exception) {
                        android.util.Log.w(tag, "getAccessToken: no field 'a' or null: ${e.message}")
                    }

                    if (instance == null) {
                        try {
                            instance = loginMgrClass.newInstance()
                            android.util.Log.d(tag, "getAccessToken: created instance via newInstance()")
                        } catch (e: Exception) {
                            if (context != null) {
                                try {
                                    val constructor = loginMgrClass.getDeclaredConstructor(Context::class.java)
                                    constructor.isAccessible = true
                                    instance = constructor.newInstance(context)
                                    android.util.Log.d(tag, "getAccessToken: created instance via constructor(Context)")
                                } catch (e2: Exception) {
                                    android.util.Log.e(tag, "getAccessToken: all instance creation attempts failed", e2)
                                }
                            }
                        }
                    }

                    if (instance != null) {
                        var token: String? = null

                        // 优先使用 getAuthorization(boolean)：宿主自己构建的完整 Authorization header
                        // 它内部已正确处理 device binding，我们直接使用，不再手动拼装 buildAuth
                        try {
                            val method = loginMgrClass.getDeclaredMethod("getAuthorization", Boolean::class.javaPrimitiveType)
                            method.isAccessible = true
                            android.util.Log.d(tag, "getAccessToken: invoking getAuthorization(Boolean=$forceRefresh)")
                            token = method.invoke(instance, forceRefresh)?.toString()?.trim()
                            android.util.Log.d(tag, "getAccessToken: getAuthorization returned=${tokenMask(token)}")
                        } catch (e: NoSuchMethodException) {
                            android.util.Log.w(tag, "getAccessToken: getAuthorization(Boolean) not found, trying getOauthV2AccessToken")
                        } catch (e: Exception) {
                            android.util.Log.e(tag, "getAccessToken: getAuthorization invoke exception", e)
                        }

                        // 备用：getOauthV2AccessToken(boolean)
                        if (token.isNullOrBlank()) {
                            try {
                                val method = loginMgrClass.getDeclaredMethod("getOauthV2AccessToken", Boolean::class.javaPrimitiveType)
                                method.isAccessible = true
                                android.util.Log.d(tag, "getAccessToken: invoking getOauthV2AccessToken(Boolean=$forceRefresh)")
                                token = method.invoke(instance, forceRefresh)?.toString()?.trim()
                                android.util.Log.d(tag, "getAccessToken: method(bool) returned token=${tokenMask(token)}")
                            } catch (e: NoSuchMethodException) {
                                android.util.Log.w(tag, "getAccessToken: getOauthV2AccessToken(Boolean) not found, trying no-arg variant")
                                try {
                                    val method = loginMgrClass.getDeclaredMethod("getOauthV2AccessToken")
                                    method.isAccessible = true
                                    token = method.invoke(instance)?.toString()?.trim()
                                    android.util.Log.d(tag, "getAccessToken: method() returned token=${tokenMask(token)}, NOTE: forceRefresh=$forceRefresh was IGNORED (no-arg)")
                                } catch (e2: Exception) {
                                    android.util.Log.e(tag, "getAccessToken: both method variants failed", e2)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(tag, "getAccessToken: invoke exception", e)
                            }
                        }

                        if (!token.isNullOrBlank()) {
                            return token
                        }
                        android.util.Log.w(tag, "getAccessToken: reflection succeeded but returned blank token, falling back to prefs")
                    } else {
                        android.util.Log.e(tag, "getAccessToken: instance is null, cannot invoke getOauthV2AccessToken")
                        // 尝试备用字段 f
                        try {
                            val fieldF = loginMgrClass.getDeclaredField("f")
                            fieldF.isAccessible = true
                            val fieldFToken = fieldF.get(null) as? String
                            android.util.Log.d(tag, "getAccessToken: fallback field 'f' returned token=${tokenMask(fieldFToken)}")
                            if (!fieldFToken.isNullOrBlank()) return fieldFToken
                        } catch (e2: Exception) {
                            android.util.Log.w(tag, "getAccessToken: fallback field 'f' also failed: ${e2.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(tag, "getAccessToken: unexpected exception in reflection block", e)
            }
        } else {
            android.util.Log.w(tag, "getAccessToken: effectiveLoader is null, using SharedPrefs fallback")
        }

        val prefToken = if (context != null) context.writablePrefs().getString("service_token", "") else HookEntry.prefs.getString("service_token", "")
        android.util.Log.d(tag, "getAccessToken: returning SharedPrefs token=${tokenMask(prefToken)}")
        return prefToken
    }

    /**
     * 获取设备 ID
     * @param loader 宿主的 ClassLoader；传 null 时自动使用 [hostLoader]
     */
    fun getDeviceId(context: Context, loader: ClassLoader? = null): String? {
        val tag = "XiaoAiKeBiao"
        val effectiveLoader = loader ?: hostLoader
        android.util.Log.d(tag, "getDeviceId: loaderArg=${loader != null}, hostLoader=${hostLoader != null}, effectiveLoader=${effectiveLoader != null}")
        if (effectiveLoader != null) {
            try {
                val className = getCachedClassName(context, "device_class", "q70.j")
                val deviceMgrClass = effectiveLoader.loadClass(className)
                val method = deviceMgrClass.getDeclaredMethod("getDeviceId", Context::class.java)
                method.isAccessible = true
                val result = method.invoke(null, context)
                val deviceId = result?.toString()
                android.util.Log.d(tag, "getDeviceId: reflection returned deviceId=${if (!deviceId.isNullOrBlank()) deviceId.take(8)+"..." else "<blank>"}")
                if (!deviceId.isNullOrBlank()) return deviceId
            } catch (e: Exception) {
                android.util.Log.e(tag, "getDeviceId: reflection failed", e)
            }
        }
        val prefId = context.writablePrefs().getString("device_id", "") ?: HookEntry.prefs.getString("device_id", "")
        android.util.Log.d(tag, "getDeviceId: returning SharedPrefs deviceId=${if (!prefId.isNullOrBlank()) prefId.take(8)+"..." else "<blank>"}")
        return prefId
    }

    fun saveTokens(context: Context, token: String?, deviceId: String?) {
        val editor = context.writablePrefs().edit()
        if (!token.isNullOrBlank()) editor.putString("service_token", token)
        if (!deviceId.isNullOrBlank()) editor.putString("device_id", deviceId)
        editor.apply()
    }
}
