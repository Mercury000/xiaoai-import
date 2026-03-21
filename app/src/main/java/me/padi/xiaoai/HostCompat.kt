package me.padi.xiaoai

import android.content.Context

fun Context.writablePrefs() = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)

object HostCompat {
    @JvmField
    var hostLoader: ClassLoader? = null

    @Volatile
    var isImportFinished: Boolean = false

    private fun getCachedClassName(context: Context?, key: String): String? {
        return context?.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)?.getString(key, null)
    }

    fun isLogin(context: Context? = null): Boolean {
        return !getAccessToken(context).isNullOrBlank()
    }

    fun getAppId(): String = "326813440150602752"

    fun getAccessToken(context: Context? = null, loader: ClassLoader? = null, forceRefresh: Boolean = false): String? {
        val effectiveLoader = loader ?: hostLoader ?: return null
        val className = getCachedClassName(context, "token_class") ?: return null

        return try {
            val loginMgrClass = effectiveLoader.loadClass(className)
            var instance: Any? = null
            try {
                val fieldA = loginMgrClass.getDeclaredField("a")
                fieldA.isAccessible = true
                instance = fieldA.get(null)
            } catch (_: Exception) {
            }

            if (instance == null) return null

            var token: String? = null
            try {
                val method = loginMgrClass.getDeclaredMethod("getAuthorization", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                token = method.invoke(instance, forceRefresh)?.toString()?.trim()
            } catch (_: NoSuchMethodException) {
            }

            if (token.isNullOrBlank()) {
                try {
                    val method = loginMgrClass.getDeclaredMethod("getOauthV2AccessToken", Boolean::class.javaPrimitiveType)
                    method.isAccessible = true
                    token = method.invoke(instance, forceRefresh)?.toString()?.trim()
                } catch (_: Exception) {
                }
            }

            token?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun getDeviceId(context: Context, loader: ClassLoader? = null): String? {
        val effectiveLoader = loader ?: hostLoader ?: return null
        val className = getCachedClassName(context, "device_class") ?: return null

        return try {
            val deviceMgrClass = effectiveLoader.loadClass(className)
            val method = deviceMgrClass.getDeclaredMethod("getDeviceId", Context::class.java)
            method.isAccessible = true
            method.invoke(null, context)?.toString()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun saveTokens(context: Context, token: String?, deviceId: String?) {
        val editor = context.writablePrefs().edit()
        if (!token.isNullOrBlank()) editor.putString("service_token", token)
        if (!deviceId.isNullOrBlank()) editor.putString("device_id", deviceId)
        editor.apply()
    }
}
