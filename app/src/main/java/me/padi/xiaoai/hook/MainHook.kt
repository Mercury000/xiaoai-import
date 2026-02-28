package me.padi.xiaoai.hook

import android.app.Application
import android.content.Context
import android.webkit.WebView
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import me.padi.xiaoai.WebAppInterface
import top.sacz.xphelper.XpHelper

object MainHook : YukiBaseHooker() {
    override fun onHook() {
        Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val application = instance<Application>()
                val appContext = instance<Context>()
                val loader = appContext.classLoader

                "com.xiaomi.aischedule.activity.MainActivity".toClass(loader).resolve()
                    .firstMethod {
                        name = "onCreate"
                        parameterCount = 1
                    }.hook {
                        after {
                            val context = instance<Context>()
                            XpHelper.injectResourcesToContext(context)
                        }
                    }

                "com.just.agentweb.DefaultWebCreator".toClass(loader).resolve().firstMethod {
                    name = "createWebView"
                    parameterCount = 0
                }.hook {
                    after {
                        val webView = result as WebView
                        val context = webView.context
                        XpHelper.injectResourcesToContext(context)
                        webView.addJavascriptInterface(
                            WebAppInterface(context), "Android"
                        )
                    }
                }

                "android.webkit.WebViewClient".toClass(loader).resolve().firstMethod {
                    name = "onPageFinished"
                    parameters(WebView::class.java, String::class.java)
                }.hook {
                    after {
                        val webView = args().first().cast<WebView>()
                        webView?.evaluateJavascript(
                            """
    (function() {
        if (window._eduButtonListenerAttached) return;
        
        const checkButton = function() {
            const eduButton = document.getElementById('ai-class-shedule-fe-setting-button-jiaoyu');
            if (eduButton && !window._eduButtonListenerAttached) {
                eduButton.addEventListener('click', function(event) {
                    event.preventDefault();
                    event.stopPropagation();
                    Android.navSchoolScreen();
                }, true);
                
                window._eduButtonListenerAttached = true;
                return true;
            }
            return false;
        };
        
        if (!checkButton()) {
            const observer = new MutationObserver(function(mutations) {
                checkButton();
                if (window._eduButtonListenerAttached) {
                    observer.disconnect();
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        }
    })();
    """.trimIndent(), null
                        )
                    }
                }

            }
        }

    }
}