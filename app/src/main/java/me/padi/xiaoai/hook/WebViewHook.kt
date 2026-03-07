package me.padi.xiaoai.hook

import android.webkit.WebView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import me.padi.xiaoai.R
import me.padi.xiaoai.WebAppInterface
import me.padi.xiaoai.screen.readRawFile
import top.sacz.xphelper.XpHelper

private const val TAG = "XiaoAiWebHook"

object WebViewHook : YukiBaseHooker() {

    override fun onHook() {
        android.app.Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val loader = instanceClass?.classLoader ?: run {
                    YLog.debug("$TAG Application.attach: classLoader is null!")
                    return@after
                }
                YLog.debug("$TAG Application.attach fired")

                // ── Hook CommonWebView 构造函数 → 子类字段初始化完毕后注入桥接 ──
                // 不能用 WebView.<init>：CommonWebView 重写了 addJavascriptInterface，
                // 其内部字段 (n90.b0) 在 WebView 父类构造阶段尚未赋值，调用会 NPE。
                // 必须等 CommonWebView 自己的 <init> after，字段才全部就绪。
                try {
                    "com.xiaomi.voiceassistant.commonweb.CommonWebView"
                        .toClass(loader).resolve()
                        .firstConstructor { parameterCount = 1 }
                        .hook {
                            after {
                                val webView = instance<WebView>()
                                val context = webView.context
                                YLog.debug("$TAG CommonWebView.<init> fired → addJavascriptInterface")
                                XpHelper.injectResourcesToContext(context)
                                webView.addJavascriptInterface(WebAppInterface(context), "Android")
                                YLog.debug("$TAG CommonWebView addJavascriptInterface done")
                            }
                        }
                    YLog.debug("$TAG CommonWebView constructor hook registered OK")
                } catch (e: Exception) {
                    YLog.debug("$TAG CommonWebView constructor hook failed: ${e.message}")
                }

                // ── Hook WebViewClient.onPageFinished → 注入 JS ──
                "android.webkit.WebViewClient".toClass(loader).resolve().firstMethod {
                    name = "onPageFinished"
                    parameters(WebView::class.java, String::class.java)
                }.hook {
                    after {
                        YLog.debug("$TAG onPageFinished triggered. Method: ${method.name}, Args size: ${args.size}")
                        
                        // Dump all arguments for diagnosis
                        args.forEachIndexed { index, arg ->
                            YLog.debug("$TAG Arg$index: type=${arg?.javaClass?.name}, value=${arg?.toString()?.take(50)}")
                        }

                        val firstArg = if (args.size > 0) args[0] else null
                        val secondArg = if (args.size > 1) args[1] else null
                        
                        if (firstArg == null) {
                            YLog.debug("$TAG onPageFinished: firstArg is null")
                            return@after
                        }
                        
                        val url = secondArg as? String ?: ""
                        YLog.debug("$TAG onPageFinished target url=$url")

                        // Helper to call evaluateJavascript via reflection or direct
                        fun injectJs(script: String, callback: ((String?) -> Unit)? = null) {
                            try {
                                if (firstArg is WebView) {
                                    firstArg.evaluateJavascript(script, callback)
                                } else {
                                    // Fallback to reflection if cast fails
                                    YLog.debug("$TAG injectJs: using reflection for ${firstArg.javaClass.name}")
                                    val method = firstArg.javaClass.getMethod("evaluateJavascript", String::class.java, android.webkit.ValueCallback::class.java)
                                    method.invoke(firstArg, script, callback?.let { 
                                        android.webkit.ValueCallback<String> { res -> it(res) }
                                    })
                                }
                            } catch (e: Exception) {
                                YLog.debug("$TAG injectJs failed: ${e.message}")
                            }
                        }

                        // Get context via reflection if needed
                        val context = try {
                            if (firstArg is WebView) firstArg.context else {
                                firstArg.javaClass.getMethod("getContext").invoke(firstArg) as android.content.Context
                            }
                        } catch (e: Exception) {
                            YLog.debug("$TAG getContext failed: ${e.message}")
                            null
                        }

                        if (context == null) {
                            YLog.debug("$TAG onPageFinished: context is null")
                            return@after
                        }

                        // 注入 tools.js
                        val tools = context.readRawFile(R.raw.tools) ?: ""
                        if (tools.isBlank()) YLog.debug("$TAG tools.js is EMPTY!")
                        injectJs(tools)

                        // 挂载教育按钮监听
                        injectJs("""
(function() {
    try {
        if (typeof Android === 'undefined') {
            console.log('[XiaoAiWebHook] Android bridge NOT found on url: ' + location.href);
            return 'NO_BRIDGE';
        }
        console.log('[XiaoAiWebHook] Android bridge OK');

        if (window._eduButtonListenerAttached) return 'ALREADY_ATTACHED';

        const checkButton = function() {
            const eduButton = document.getElementById('ai-class-shedule-fe-setting-button-jiaoyu');
            console.log('[XiaoAiWebHook] checkButton: ' + (eduButton ? 'FOUND' : 'not found'));
            if (eduButton && !window._eduButtonListenerAttached) {
                eduButton.addEventListener('click', function(event) {
                    event.preventDefault();
                    event.stopPropagation();
                    console.log('[XiaoAiWebHook] edu button clicked!');
                    Android.navSchoolScreen();
                }, true);
                window._eduButtonListenerAttached = true;
                return true;
            }
            return false;
        };

        if (!checkButton()) {
            const observer = new MutationObserver(function() {
                if (checkButton()) observer.disconnect();
            });
            observer.observe(document.documentElement, { childList: true, subtree: true });
            return 'OBSERVER_STARTED';
        }
        return 'ATTACHED';
    } catch(e) {
        return 'ERROR:' + e;
    }
})();
""".trimIndent()) { result ->
                            YLog.debug("$TAG eduButton JS result: $result")
                        }
                    }
                }
YLog.debug("$TAG onPageFinished hook registered OK")
            }
        }
    }
}

