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
                val loader = instance<Context>().classLoader

                // Hook AiWebActivity.onCreate → 注入模块资源
                "com.xiaomi.voiceassistant.web.container.AiWebActivity"
                    .toClass(loader).resolve()
                    .firstMethod {
                        name = "onCreate"
                        parameterCount = 1
                    }.hook {
                        after {
                            val context = instance<Context>()
                            XpHelper.injectResourcesToContext(context)
                        }
                    }

                // Hook 新版 WebView 宿主类 Lj80/o; → 注入 JS 桥接
                // 类名为混淆名，需与新版 APK 对应
                try {
                    "j80.o".toClass(loader).resolve()
                        .firstMethod {
                            name = "onCreate"
                            parameterCount = 1
                        }.hook {
                            after {
                                val context = instance<Context>()
                                XpHelper.injectResourcesToContext(context)
                                // WebAppInterface 由 WebViewHook 在 createWebView after 里注入
                            }
                        }
                } catch (e: Exception) {
                    // 类名混淆可能变化，由 WebViewHook 的 DefaultWebCreator hook 兜底
                }
            }
        }
    }
}
