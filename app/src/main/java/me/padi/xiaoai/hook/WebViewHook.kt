package me.padi.xiaoai.hook

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import me.padi.xiaoai.R
import me.padi.xiaoai.WebAppInterface
import me.padi.xiaoai.readRawFile
import top.sacz.xphelper.XpHelper

object WebViewHook : YukiBaseHooker() {
    private var cachedToolsJs: String? = null

    private fun injectJs(view: Any?, url: String) {
        val webView = view as? WebView ?: return
        if (!url.contains("ai-schedule")) return
        try {
            if (cachedToolsJs == null) {
                cachedToolsJs = webView.context.readRawFile(R.raw.tools) ?: ""
            }
            cachedToolsJs?.let { if (it.isNotBlank()) webView.evaluateJavascript(it, null) }
            webView.evaluateJavascript(ENTRY_POINT_SCRIPT, null)
        } catch (e: Throwable) {
        }
    }

    override fun onHook() {
        android.app.Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val loader = (args[0] as? Context)?.classLoader ?: return@after

                try {
                    val commonWebViewClass = loader.loadClass("com.xiaomi.voiceassistant.commonweb.CommonWebView")
                    commonWebViewClass.resolve().firstConstructor { parameterCount = 1 }.hook {
                        after {
                            val webView = instance<WebView>()
                            XpHelper.injectResourcesToContext(webView.context)
                            webView.addJavascriptInterface(WebAppInterface(webView.context), "Android")
                        }
                    }
                } catch (e: Throwable) {}

                try {
                    val webViewClientClass = loader.loadClass("android.webkit.WebViewClient")
                    
                    webViewClientClass.resolve().firstMethod {
                        name = "onPageFinished"
                        parameters(WebView::class.java, String::class.java)
                    }.hook {
                        after {
                            val view = args[0]
                            val url = args[1] as? String ?: ""
                            injectJs(view, url)
                        }
                    }

                    webViewClientClass.resolve().firstMethod {
                        name = "doUpdateVisitedHistory"
                        parameters(WebView::class.java, String::class.java, java.lang.Boolean.TYPE)
                    }.hook {
                        after {
                            val view = args[0]
                            val url = args[1] as? String ?: ""
                            injectJs(view, url)
                        }
                    }
                } catch (e: Throwable) {}
            }
        }
    }

    private const val ENTRY_POINT_SCRIPT = """
(function() {
    if (typeof Android === 'undefined') return;
    const OLD_TEXT = '教务导入系统暂停维护中';
    const NEW_TEXT = '从教务系统中导入课表';
    const replaceMaintenanceText = () => {
        if (!document.body) return;
        const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
        let node = walker.nextNode();
        while (node) {
            if (node.nodeValue && node.nodeValue.indexOf(OLD_TEXT) !== -1) {
                node.nodeValue = node.nodeValue.split(OLD_TEXT).join(NEW_TEXT);
            }
            node = walker.nextNode();
        }
    };
    const inject = () => {
        if (location.href.indexOf('/setting') === -1) return;
        replaceMaintenanceText();
        const btn = document.getElementById('ai-class-shedule-fe-setting-button-jiaoyu');
        if (btn && !btn._padiAttached) {
            btn._padiAttached = true;
            btn.addEventListener('click', (e) => {
                e.preventDefault(); e.stopPropagation();
                Android.navSchoolScreen();
            }, true);
        }
    };
    if (window._padiObserver) window._padiObserver.disconnect();
    window._padiObserver = new MutationObserver(inject);
    window._padiObserver.observe(document.documentElement, { childList: true, subtree: true });
    inject();
    window.addEventListener('hashchange', inject);
})();
"""
}
