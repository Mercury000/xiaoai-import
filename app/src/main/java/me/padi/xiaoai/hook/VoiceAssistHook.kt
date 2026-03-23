package me.padi.xiaoai.hook

import android.app.Application
import android.content.Context
import android.os.Build
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
import me.padi.xiaoai.HostCompat
import top.sacz.xphelper.XpHelper

object VoiceAssistHook : YukiBaseHooker() {
    private const val TAG = "VoiceAssistHook"
    private fun lsp(msg: String) = XposedBridge.log("[$TAG] $msg")
    private fun currentProcessName(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            context.packageName
        }
    }

    override fun onHook() {
        Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val context = instance<Context>()
                val loader = context.classLoader
                HostCompat.hostLoader = loader
                lsp("Application.attach hooked, hostLoader saved")
                val processName = currentProcessName(context)
                val isMainProcess = processName == context.packageName
                lsp("process=$processName, main=$isMainProcess")

                val hostVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "unknown"
                } ?: "unknown"
                val sourceDir = context.applicationInfo.sourceDir
                lsp("host=${context.packageName}, version=$hostVersion")

                SmaliAnalyzer.getOrResolveClass(context, hostVersion, "token_class") {
                    SmaliAnalyzer.findTokenClass(context, sourceDir)
                }

                SmaliAnalyzer.getOrResolveClass(context, hostVersion, "device_class") {
                    SmaliAnalyzer.findDeviceClass(context, sourceDir)
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
            }
        }
    }
}
