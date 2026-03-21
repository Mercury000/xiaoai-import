package me.padi.xiaoai.hook

import android.content.Context
import android.util.Log
import org.luckypray.dexkit.DexKitBridge

object SmaliAnalyzer {
    private const val TAG = "SmaliAnalyzer"
    private const val PREFS_NAME = "hook_cache"

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
            if (!cachedName.isNullOrBlank()) return cachedName
        }

        Log.i(TAG, "Resolving class for key: $key (Host version: $hostVersion)")
        val resolvedName = resolver(context.classLoader)

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
            val bridge = DexKitBridge.create(sourceDir)
            try {
                when {
                    targets.contains("token") -> {
                        bridge.findClass {
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
                    }

                    targets.contains("device") -> {
                        bridge.findClass {
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
                    }

                    targets.contains("webview") -> {
                        bridge.findClass {
                            matcher {
                                usingStrings("V5Widget:TimeTableRender")
                            }
                        }.firstOrNull()?.name
                    }

                    else -> null
                }
            } finally {
                bridge.close()
            }
        } catch (e: Throwable) {
            Log.e(TAG, "DexKit lookup failed", e)
            null
        }
    }
}
