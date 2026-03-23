package me.padi.xiaoai.hook

import android.content.Context
import android.webkit.WebView
import androidx.annotation.RawRes
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import me.padi.xiaoai.R
import me.padi.xiaoai.WebAppInterface
import top.sacz.xphelper.XpHelper

object WebViewHook : YukiBaseHooker() {
    private var cachedToolsJs: String? = null

    private fun readRawText(context: Context, @RawRes resId: Int): String? {
        return try {
            context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun injectJs(view: Any?, url: String) {
        val webView = view as? WebView ?: return
        if (!url.contains("ai-schedule")) return
        try {
            if (cachedToolsJs == null) {
                cachedToolsJs = readRawText(webView.context, R.raw.tools) ?: ""
            }
            cachedToolsJs?.let { if (it.isNotBlank()) webView.evaluateJavascript(it, null) }
            webView.evaluateJavascript(ENTRY_POINT_SCRIPT, null)
        } catch (_: Throwable) {
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
                } catch (_: Throwable) {
                }

                try {
                    val webViewClientClass = loader.loadClass("android.webkit.WebViewClient")
                    webViewClientClass.resolve().firstMethod {
                        name = "onPageFinished"
                        parameters(WebView::class.java, String::class.java)
                    }.hook { after { injectJs(args[0], args[1] as? String ?: "") } }

                    webViewClientClass.resolve().firstMethod {
                        name = "doUpdateVisitedHistory"
                        parameters(WebView::class.java, String::class.java, java.lang.Boolean.TYPE)
                    }.hook { after { injectJs(args[0], args[1] as? String ?: "") } }
                } catch (_: Throwable) {
                }
            }
        }
    }

    private const val ENTRY_POINT_SCRIPT = """
(function () {
  if (typeof Android === 'undefined') return;

  const OLD_IMPORT_DESC = '\u6559\u52a1\u5bfc\u5165\u7cfb\u7edf\u6682\u505c\u7ef4\u62a4\u4e2d';
  const NEW_IMPORT_DESC = '\u4ece\u6559\u52a1\u7cfb\u7edf\u4e2d\u5bfc\u5165\u8bfe\u8868';
  const OLD_DEGREE_LABEL = '\u9009\u62e9\u5b66\u5386';
  const OLD_DEGREE_DESC = '\u672c\u79d1/\u4e13\u79d1';
  const NEW_DEGREE_LABEL = '\u5173\u4e8e\u6a21\u5757';
  const NEW_DEGREE_DESC = '\u5c0f\u7231\u8bfe\u7a0b\u8868\u590d\u6d3b\u8ba1\u5212';

  const replaceTextOnly = () => {
    const root = document.body || document.documentElement;
    if (!root) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode();
    while (node) {
      if (node.nodeValue) {
        let v = node.nodeValue;
        if (v.indexOf(OLD_IMPORT_DESC) !== -1) v = v.split(OLD_IMPORT_DESC).join(NEW_IMPORT_DESC);
        if (v.indexOf(OLD_DEGREE_LABEL) !== -1) v = v.split(OLD_DEGREE_LABEL).join(NEW_DEGREE_LABEL);
        if (v.indexOf(OLD_DEGREE_DESC) !== -1) v = v.split(OLD_DEGREE_DESC).join(NEW_DEGREE_DESC);
        if (v !== node.nodeValue) node.nodeValue = v;
      }
      node = walker.nextNode();
    }
  };

  const bindImportClick = () => {
    const btn = document.querySelector('#ai-class-shedule-fe-setting-button-jiaoyu');
    if (!btn || btn._padiAttached) return;
    btn._padiAttached = true;
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      Android.navSchoolScreen();
    }, true);
  };

  const bindDegreeClick = () => {
    const rows = Array.from(document.querySelectorAll('div.wrap___1nVhq.withDesc___1t1YL'));
    const degreeRow = rows.find((row) => {
      const title = ((row.querySelector('.labelWrap___240P9 .label___224jm') || {}).textContent || '').trim();
      return title === OLD_DEGREE_LABEL || title === NEW_DEGREE_LABEL;
    });
    if (!degreeRow) return;
    degreeRow.setAttribute('aria-label', NEW_DEGREE_LABEL + ' ' + NEW_DEGREE_DESC + ' ');
    if (degreeRow._padiAttached) return;
    degreeRow._padiAttached = true;
    degreeRow.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      Android.navModuleScreen();
    }, true);
  };

  const run = () => {
    if (location.href.indexOf('/setting') === -1) return false;
    replaceTextOnly();
    bindImportClick();
    bindDegreeClick();
    return true;
  };

  let n = 0;
  const timer = setInterval(function () {
    n += 1;
    run();
    if (n >= 20) clearInterval(timer);
  }, 250);
  run();
})();
"""
}
