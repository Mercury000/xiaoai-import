package me.padi.xiaoai.hook

import android.app.Application
import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import me.padi.xiaoai.HostCompat
import top.sacz.xphelper.XpHelper

object VoiceAssistHook : YukiBaseHooker() {

    override fun onHook() {
        Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val context = instance<Context>()
                val loader = context.classLoader
                HostCompat.hostLoader = loader

                val hostPackage = context.packageName
                val hostVersion = try {
                    context.packageManager.getPackageInfo(hostPackage, 0).versionName
                } catch (e: Exception) {
                    "unknown"
                } ?: "unknown"
                val sourceDir = context.applicationInfo.sourceDir

                SmaliAnalyzer.getOrResolveClass(context, hostPackage, hostVersion, "token_class") {
                    SmaliAnalyzer.findTokenClass(it, sourceDir)
                }

                SmaliAnalyzer.getOrResolveClass(context, hostPackage, hostVersion, "device_class") {
                    SmaliAnalyzer.findDeviceClass(it, sourceDir)
                }

                val webViewClassName = SmaliAnalyzer.getOrResolveClass(context, hostPackage, hostVersion, "webview_helper_class") {
                    SmaliAnalyzer.findWebViewHelperClass(it, sourceDir)
                }

                "com.xiaomi.voiceassistant.web.container.AiWebActivity"
                    .toClass(loader).resolve()
                    .firstMethod {
                        name = "onCreate"
                        parameterCount = 1
                    }.hook {
                        after {
                            val ctx = instance<Context>()
                            XpHelper.injectResourcesToContext(ctx)
                        }
                    }

                "com.xiaomi.voiceassistant.web.container.AiWebActivity"
                    .toClass(loader).resolve()
                    .firstMethod {
                        name = "onResume"
                        parameterCount = 0
                    }.hook {
                        after {
                            if (HostCompat.isImportFinished) {
                                HostCompat.isImportFinished = false
                                val activity = instance<android.app.Activity>()
                                activity.recreate()
                            }
                        }
                    }

                if (!webViewClassName.isNullOrBlank()) {
                    try {
                        webViewClassName.toClass(loader).resolve()
                            .firstMethod {
                                name = "onCreate"
                                parameterCount = 1
                            }.hook {
                                after {
                                    val ctx = instance<Context>()
                                    XpHelper.injectResourcesToContext(ctx)
                                }
                            }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}