package me.padi.xiaoai.hook

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import me.padi.xiaoai.R
import me.padi.xiaoai.WebAppInterface
import me.padi.xiaoai.screen.readRawFile
import top.sacz.xphelper.XpHelper

/**
 * 针对超级小爱 (com.miui.voiceassist) 的 WebView 适配与 JS 注入
 */
object WebViewHook : YukiBaseHooker() {
    private var cachedToolsJs: String? = null

    override fun onHook() {
        android.app.Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val loader = instanceClass?.classLoader ?: return@after

                // 1. 针对 CommonWebView 进行初始化拦截 (注入 JS 桥接)
                "com.xiaomi.voiceassistant.commonweb.CommonWebView".toClass(loader).resolve()
                    .firstConstructor { parameterCount = 1 }
                    .hook {
                        after {
                            val webView = instance<WebView>()
                            XpHelper.injectResourcesToContext(webView.context)
                            webView.addJavascriptInterface(WebAppInterface(webView.context), "Android")
                        }
                    }

                // 2. 注入入口脚本与工具类
                "android.webkit.WebViewClient".toClass(loader).resolve().firstMethod {
                    name = "onPageFinished"
                    parameters(WebView::class.java, String::class.java)
                }.hook {
                    after {
                        val url = args[1] as? String ?: ""
                        if (!url.contains("ai-schedule")) return@after

                        val view = args[0] ?: return@after
                        try {
                            val context = (view as? WebView)?.context ?: view.javaClass.getMethod("getContext").invoke(view) as android.content.Context
                            if (cachedToolsJs == null) cachedToolsJs = context.readRawFile(R.raw.tools) ?: ""
                            
                            val evalMethod = view.javaClass.getMethod("evaluateJavascript", String::class.java, ValueCallback::class.java)
                            cachedToolsJs?.let { if (it.isNotBlank()) evalMethod.invoke(view, it, null) }
                            evalMethod.invoke(view, ENTRY_POINT_SCRIPT, null)
                        } catch (e: Exception) {}
                    }
                }
            }
        }
    }

    private const val ENTRY_POINT_SCRIPT = """
(function() {
    if (typeof Android === 'undefined' || window._eduButtonListenerAttached) return;
    const check = function() {
        const btn = document.getElementById('ai-class-shedule-fe-setting-button-jiaoyu');
        if (btn && !window._eduButtonListenerAttached) {
            btn.addEventListener('click', function(e) {
                e.preventDefault(); e.stopPropagation();
                if (Android.navSchoolScreen) Android.navSchoolScreen();
            }, true);
            window._eduButtonListenerAttached = true;
            return true;
        }
        return false;
    };
    if (!check()) {
        const observer = new MutationObserver(() => { if (check()) observer.disconnect(); });
        observer.observe(document.documentElement, { childList: true, subtree: true });
    }
})();
"""
}
