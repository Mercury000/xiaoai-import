package me.padi.xiaoai.hook

import android.app.Application
import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import me.padi.xiaoai.WebAppInterface
import top.sacz.xphelper.XpHelper

/**
 * 小爱同学新版宿主 (com.miui.voiceassist) 的 Hook 逻辑
 *
 * Hook 点：
 *   1. com.xiaomi.voiceassistant.web.container.AiWebActivity#onCreate
 *        → 注入模块资源
 *   2. Lj80/o; → WebView 宿主类（新版）
 *        → 注入 WebAppInterface JS 桥接
 *
 * WebView JS 注入（tools.js + 教育按钮拦截）由 WebViewHook 负责
 * Token / DeviceId 通过 HostCompat 统一获取（c30.b / q70.j）
 */
object VoiceAssistHook : YukiBaseHooker() {

    override fun onHook() {
        Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val context = instance<Context>()
                val loader = context.classLoader
                val hostPackage = context.packageName
                val hostVersion = try {
                    context.packageManager.getPackageInfo(hostPackage, 0).versionName
                } catch (e: Exception) { "unknown" } ?: "unknown"
                val sourceDir = context.applicationInfo.sourceDir

                // 动态解析 Token 类名
                val tokenClassName = SmaliAnalyzer.getOrResolveClass(context, hostPackage, hostVersion, "token_class") {
                    SmaliAnalyzer.findTokenClass(it, sourceDir)
                } ?: "c30.b"

                // 动态解析设备 ID 类名
                val deviceClassName = SmaliAnalyzer.getOrResolveClass(context, hostPackage, hostVersion, "device_class") {
                    SmaliAnalyzer.findDeviceClass(it, sourceDir)
                } ?: "q70.j"

                // 动态解析新版 WebView 类名
                val webViewClassName = SmaliAnalyzer.getOrResolveClass(context, hostPackage, hostVersion, "webview_helper_class") {
                    SmaliAnalyzer.findWebViewHelperClass(it, sourceDir)
                } ?: "j80.o"

                // Hook AiWebActivity.onCreate → 注入模块资源
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

                // Hook 新版 WebView 宿主类 → 注入 JS 桥接
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
                } catch (e: Exception) {
                }
            }
        }
    }
}
