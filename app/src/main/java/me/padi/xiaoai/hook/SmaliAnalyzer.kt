package me.padi.xiaoai.hook

import android.content.Context
import android.util.Log
import dalvik.system.DexFile
import me.padi.xiaoai.HostCompat
import top.sacz.xphelper.XpHelper
import java.io.File

/**
 * 动态查找 Hook 点解析器
 * 通过遍历宿主 APK 的类列表，识别具有特定特征的类名
 */
object SmaliAnalyzer {
    private const val TAG = "SmaliAnalyzer"
    private const val PREFS_NAME = "hook_cache"

    /**
     * 获取缓存的类名，如果版本不匹配或缓存不存在则重新查找
     */
    fun getOrResolveClass(context: Context, hostPackage: String, hostVersion: String, key: String, resolver: (ClassLoader) -> String?): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedVersion = prefs.getString("host_version", "")
        
        if (cachedVersion == hostVersion) {
            val cachedName = prefs.getString(key, null)
            if (!cachedName.isNullOrBlank()) {
                return cachedName
            }
        }

        // 重新查找
        Log.i(TAG, "Resolving class for key: $key (Host version: $hostVersion)")
        val loader = context.classLoader
        val resolvedName = resolver(loader)
        
        if (!resolvedName.isNullOrBlank()) {
            prefs.edit().apply {
                putString("host_version", hostVersion)
                putString(key, resolvedName)
                apply()
            }
            Log.i(TAG, "Resolved and cached: $key -> $resolvedName")
        }
        return resolvedName
    }

    /**
     * 查找 Token 类 (原 c30.b)
     * 特征: 包含字符串 "EngineAuthHelper" 和 "access_token:"
     */
    fun findTokenClass(loader: ClassLoader, sourceDir: String): String? {
        return findClassByStrings(loader, sourceDir, listOf("EngineAuthHelper", "access_token:"))
    }

    /**
     * 查找设备 ID 类 (原 q70.j)
     * 特征: 包含字符串 "DeviceUtils" 和方法 getDeviceId
     */
    fun findDeviceClass(loader: ClassLoader, sourceDir: String): String? {
        return findClassByStrings(loader, sourceDir, listOf("DeviceUtils", "getDeviceId"))
    }

    /**
     * 查找新版 WebView 助手 (原 j80.o)
     * 特征: 包含字符串 "V5Widget:TimeTableRender"
     */
    fun findWebViewHelperClass(loader: ClassLoader, sourceDir: String): String? {
        return findClassByStrings(loader, sourceDir, listOf("V5Widget:TimeTableRender"))
    }

    private fun findClassByStrings(loader: ClassLoader, sourceDir: String, targets: List<String>): String? {
        try {
            val dexFile = DexFile(sourceDir)
            val entries = dexFile.entries()
            while (entries.hasMoreElements()) {
                val className = entries.nextElement()
                
                // 优化 1：跳过非宿主业务逻辑的类
                if (className.startsWith("android.") || className.startsWith("com.google.") || 
                    className.startsWith("kotlin.") || className.startsWith("androidx.") ||
                    className.startsWith("com.xiaomi.mipush") || className.startsWith("com.xiaomi.metok")) continue
                
                // 优化 2：针对混淆名类进行筛选，通常是 a.b 或 c12.d 这种很短的形式
                val parts = className.split(".")
                val isProbablyObfuscated = parts.size <= 2 || (parts.size == 2 && parts[1].length <= 2)
                
                if (!isProbablyObfuscated && !className.contains("EducationHelper") && !className.contains("TimeTable")) continue

                try {
                    // 仅当类名结构匹配时才尝试加载，减少 linkage 错误风险
                    val clazz = loader.loadClass(className)
                    
                    if (targets.contains("access_token:")) {
                        // 寻找包含 getOauthV2AccessToken(boolean) 的类
                        val methods = clazz.declaredMethods
                        val hasMethod = methods.any { it.name == "getOauthV2AccessToken" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType }
                        if (hasMethod) return className
                    }
                    
                    if (targets.contains("getDeviceId")) {
                        // 寻找包含 getDeviceId(Context) 的类
                        val methods = clazz.declaredMethods
                        val hasStaticMethod = methods.any { it.name == "getDeviceId" && it.parameterTypes.size == 1 && it.parameterTypes[0].name.contains("Context") }
                        if (hasStaticMethod) return className
                    }

                    if (targets.contains("V5Widget:TimeTableRender")) {
                        // 该类通常包含特定字符串常量。反射拿字符串不便，通过类名中可能的关键字辅助定位
                        if (className.contains("TimeTableRender") || className.contains("TimeTableWidget")) return className
                        // 或者检查是否是 j80.o 的包结构 (假设它相对稳定)
                        if (className.startsWith("j8") && parts.size == 2 && parts[1] == "o") return className
                    }

                } catch (e: Throwable) {
                    // 使用 Throwable 捕获 NoClassDefFoundError 等 LinkageError
                    continue
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning dex", e)
        }
        return null
    }
}
