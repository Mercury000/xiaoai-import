package me.padi.xiaoai.hook

import android.content.Context
import android.os.Build
import de.robv.android.xposed.XposedBridge
import me.padi.xiaoai.BuildConfig
import org.luckypray.dexkit.DexKitBridge
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object SmaliAnalyzer {
    private const val TAG = "SmaliAnalyzer"
    private const val PREFS_NAME = "hook_cache"
    private val VALID_CACHE_KEYS = setOf("host_version", "token_class", "device_class")
    @Volatile
    private var dexKitLoaded = false
    private fun lsp(msg: String) = XposedBridge.log("[$TAG] $msg")

    private fun enforceCacheWhitelist(prefs: android.content.SharedPreferences) {
        val staleKeys = prefs.all.keys - VALID_CACHE_KEYS
        if (staleKeys.isNotEmpty()) {
            val editor = prefs.edit()
            staleKeys.forEach { editor.remove(it) }
            editor.apply()
            lsp("cache whitelist cleanup: removed=$staleKeys")
        }
    }

    fun getOrResolveClass(
        context: Context,
        hostVersion: String,
        key: String,
        resolver: (ClassLoader) -> String
    ): String {
        require(key in VALID_CACHE_KEYS) { "Unsupported cache key: $key" }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enforceCacheWhitelist(prefs)
        val cachedHostVersion = prefs.getString("host_version", null)
        if (cachedHostVersion == hostVersion) {
            val cachedName = prefs.getString(key, null)
            if (!cachedName.isNullOrBlank()) {
                lsp("cache hit: key=$key, hostVersion=$hostVersion -> $cachedName")
                return cachedName
            }
        }
        lsp("resolve class: key=$key")
        val resolvedName = resolver(context.classLoader)
        prefs.edit()
            .putString("host_version", hostVersion)
            .putString(key, resolvedName)
            .apply()
        lsp("resolved: key=$key -> $resolvedName")
        return resolvedName
    }

    fun findTokenClass(context: Context, sourceDir: String): String {
        return findClassByDexKit(context, sourceDir, listOf("token"))
    }

    fun findDeviceClass(context: Context, sourceDir: String): String {
        return findClassByDexKit(context, sourceDir, listOf("device"))
    }

    private fun ensureDexKitLoaded(context: Context) {
        if (dexKitLoaded) return
        synchronized(this) {
            if (dexKitLoaded) return
            val moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
            val apkPath = moduleContext.applicationInfo.sourceDir
            val extractedSo = File(context.codeCacheDir, "dexkit/libdexkit.so")
            val parent = extractedSo.parentFile
            if (parent == null || (!parent.exists() && !parent.mkdirs())) {
                throw IllegalStateException("Unable to create host code cache dir: ${parent?.absolutePath}")
            }

            ZipFile(apkPath).use { zip ->
                val soEntryName = Build.SUPPORTED_ABIS
                    .asSequence()
                    .map { abi -> "lib/$abi/libdexkit.so" }
                    .firstOrNull { entryName -> zip.getEntry(entryName) != null }
                    ?: throw UnsatisfiedLinkError("libdexkit.so not found in module apk for ABIs=${Build.SUPPORTED_ABIS.joinToString()}")

                zip.getInputStream(zip.getEntry(soEntryName)).use { input ->
                    FileOutputStream(extractedSo).use { output ->
                        input.copyTo(output)
                    }
                }
                lsp("DexKit so extracted: $soEntryName -> ${extractedSo.absolutePath}")
            }

            System.load(extractedSo.absolutePath)
            dexKitLoaded = true
            lsp("DexKit so loaded: ${extractedSo.absolutePath}")
        }
    }

    private fun findClassByDexKit(context: Context, sourceDir: String, targets: List<String>): String {
        ensureDexKitLoaded(context)
        lsp("DexKit open: targets=$targets")
        val bridge = DexKitBridge.create(sourceDir)
        try {
            return when {
                targets.contains("token") -> bridge.findClass {
                    matcher {
                        methods {
                            add {
                                name = "getOauthV2AccessToken"
                                paramTypes("boolean")
                            }
                        }
                        usingStrings("EngineAuthHelper", "access_token:")
                    }
                }.firstOrNull()?.name ?: error("DexKit token class not found")

                targets.contains("device") -> bridge.findClass {
                    matcher {
                        methods {
                            add {
                                name = "getDeviceId"
                                paramTypes("android.content.Context")
                            }
                        }
                        usingStrings("DeviceUtils")
                    }
                }.firstOrNull()?.name ?: error("DexKit device class not found")

                else -> error("Unsupported targets: $targets")
            }
        } finally {
            bridge.close()
            lsp("DexKit closed: targets=$targets")
        }
    }
}
