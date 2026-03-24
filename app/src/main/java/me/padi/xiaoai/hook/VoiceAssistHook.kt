package com.mercury.xiaoaiimport.hook

import android.app.Application
import android.content.Context
import android.os.Build
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import de.robv.android.xposed.XposedBridge
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
                val processName = currentProcessName(context)
                lsp("process=$processName")
                lsp("host=${context.packageName}")

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

            }
        }
    }
}
