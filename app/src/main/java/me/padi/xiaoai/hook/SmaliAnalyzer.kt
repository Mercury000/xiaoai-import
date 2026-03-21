package me.padi.xiaoai.hook

import android.content.Context
import de.robv.android.xposed.XposedBridge
import org.luckypray.dexkit.DexKitBridge

object SmaliAnalyzer {
    private const val TAG = "SmaliAnalyzer"
    private const val PREFS_NAME = "hook_cache"
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

    fun findTokenClass(sourceDir: String): String? {
        return findClassByDexKit(sourceDir, listOf("token"))
    }

    fun findDeviceClass(sourceDir: String): String? {
        return findClassByDexKit(sourceDir, listOf("device"))
    }

    fun findWebViewHelperClass(sourceDir: String): String? {
        return findClassByDexKit(sourceDir, listOf("webview"))
    }

    private fun findClassByDexKit(sourceDir: String, targets: List<String>): String? {
        return try {
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
