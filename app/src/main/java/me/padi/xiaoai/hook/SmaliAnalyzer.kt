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
    @Volatile
    private var dexKitLoaded = false
    private fun lsp(msg: String) = XposedBridge.log("[$TAG] $msg")

    fun getOrResolveClass(
        context: Context,
        key: String,
        resolver: (ClassLoader) -> String
    ): String {
        val prefs = context.getSharedPreferences("hook_cache", Context.MODE_PRIVATE)
        lsp("resolve class: key=$key")
        val resolvedName = resolver(context.classLoader)
        prefs.edit().putString(key, resolvedName).apply()
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
