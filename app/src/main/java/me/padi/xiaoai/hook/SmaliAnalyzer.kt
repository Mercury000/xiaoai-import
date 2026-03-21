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
    @Volatile
    private var dexKitLoaded = false
    private fun lsp(msg: String) = XposedBridge.log("[$TAG] $msg")

    fun getOrResolveClass(
        context: Context,
        hostVersion: String,
        key: String,
        resolver: (ClassLoader) -> String?
    ): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedVersion = prefs.getString("host_version", "")

        if (cachedVersion == hostVersion) {
            val cachedName = prefs.getString(key, null)
            if (!cachedName.isNullOrBlank()) {
                lsp("cache hit: key=$key -> $cachedName (version=$hostVersion)")
                return cachedName
            }
        }

        lsp("cache miss: key=$key (version=$hostVersion), start DexKit resolve")
        val resolvedName = resolver(context.classLoader)

        if (!resolvedName.isNullOrBlank()) {
            prefs.edit().apply {
                putString("host_version", hostVersion)
                putString(key, resolvedName)
                apply()
            }
            lsp("resolved and cached: key=$key -> $resolvedName")
        } else {
            lsp("resolve failed: key=$key (version=$hostVersion)")
        }
        return resolvedName
    }

    fun findTokenClass(context: Context, sourceDir: String): String? {
        return findClassByDexKit(context, sourceDir, listOf("token"))
    }

    fun findDeviceClass(context: Context, sourceDir: String): String? {
        return findClassByDexKit(context, sourceDir, listOf("device"))
    }

    fun findWebViewHelperClass(context: Context, sourceDir: String): String? {
        return findClassByDexKit(context, sourceDir, listOf("webview"))
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

    private fun findClassByDexKit(context: Context, sourceDir: String, targets: List<String>): String? {
        return try {
            ensureDexKitLoaded(context)
            lsp("DexKit open: targets=$targets")
            val bridge = DexKitBridge.create(sourceDir)
            try {
                when {
                    targets.contains("token") -> {
                        val result = bridge.findClass {
                            matcher {
                                methods {
                                    add {
                                        name = "getOauthV2AccessToken"
                                        paramTypes("boolean")
                                    }
                                }
                                usingStrings("EngineAuthHelper", "access_token:")
                            }
                        }.firstOrNull()?.name
                        lsp("DexKit token result: ${result ?: "null"}")
                        result
                    }

                    targets.contains("device") -> {
                        val result = bridge.findClass {
                            matcher {
                                methods {
                                    add {
                                        name = "getDeviceId"
                                        paramTypes("android.content.Context")
                                    }
                                }
                                usingStrings("DeviceUtils")
                            }
                        }.firstOrNull()?.name
                        lsp("DexKit device result: ${result ?: "null"}")
                        result
                    }

                    targets.contains("webview") -> {
                        val result = bridge.findClass {
                            matcher {
                                usingStrings("V5Widget:TimeTableRender")
                            }
                        }.firstOrNull()?.name
                        lsp("DexKit webview result: ${result ?: "null"}")
                        result
                    }

                    else -> null
                }
            } finally {
                bridge.close()
                lsp("DexKit closed: targets=$targets")
            }
        } catch (e: Throwable) {
            lsp("DexKit lookup failed: ${e.message}")
            XposedBridge.log(e)
            null
        }
    }
}
