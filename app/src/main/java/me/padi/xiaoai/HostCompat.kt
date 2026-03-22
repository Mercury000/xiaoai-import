package me.padi.xiaoai

import android.content.Context

fun Context.writablePrefs() = getSharedPreferences(packageName + "_preferences", Context.MODE_PRIVATE)

object HostCompat {
    private const val PREF_KEY_SHIGUANG_REPO_URL = "debug_shiguang_repo_url"
    private const val PREF_KEY_SHIGUANG_REPO_BRANCH = "debug_shiguang_repo_branch"
    private const val DEFAULT_SHIGUANG_REPO_URL = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
    private const val DEFAULT_SHIGUANG_REPO_BRANCH = "main"

    @JvmField
    var hostLoader: ClassLoader? = null

    @Volatile
    var isImportFinished: Boolean = false

    @Volatile
    var importTargetTableId: Long? = null

    @Volatile
    var pendingCourseConfigJson: String? = null

    @Volatile
    var pendingTimeSlotSectionsJson: String? = null

    @Volatile
    var importSourceActiveSettingStr: String? = null

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

    fun getShiguangRepoUrl(context: Context): String {
        return context.writablePrefs()
            .getString(PREF_KEY_SHIGUANG_REPO_URL, DEFAULT_SHIGUANG_REPO_URL)
            ?.trim()
            ?.trimEnd('/')
            .takeUnless { it.isNullOrBlank() }
            ?: DEFAULT_SHIGUANG_REPO_URL
    }

    fun getShiguangRepoBranch(context: Context): String {
        return context.writablePrefs()
            .getString(PREF_KEY_SHIGUANG_REPO_BRANCH, DEFAULT_SHIGUANG_REPO_BRANCH)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: DEFAULT_SHIGUANG_REPO_BRANCH
    }

    fun buildShiguangRawUrl(context: Context, path: String): String {
        val normalizedPath = path.trim().trimStart('/')
        return "${getShiguangRepoUrl(context)}/raw/${getShiguangRepoBranch(context)}/$normalizedPath"
    }
}
