package me.padi.xiaoai.screen

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.hook.MainHook.prefs
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class WebViewScreen : BaseActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navigator = rememberWebViewNavigator()
            var webViewLoading by remember { mutableStateOf(false) }
            var webViewRef by remember { mutableStateOf<WebView?>(null) }
            var url by remember { mutableStateOf(prefs.native().getString("jw_webview_url", "")) }

            val webViewState = rememberWebViewState("")
            MiuixTheme {
                val context = LocalContext.current
                Scaffold(topBar = {
                    SmallTopAppBar(
                        title = "通用教务导入"
                    )

                }, floatingActionButton = {
                    FloatingActionButton(
                        onClick = {

                        }) {
                        Icon(
                            imageVector = MiuixIcons.Download,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }, floatingActionButtonPosition = FabPosition.End) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .padding(horizontal = 16.dp)
                            .padding(paddingValues),
                    ) {
                        TextField(
                            value = url,
                            onValueChange = { url = it },
                            label = "教务系统链接",
                            trailingIcon = {
                                IconButton(onClick = {
                                    webViewState.content = WebContent.Url(url)
                                    if (url.isNotBlank()) {
                                        prefs.native().edit {
                                            putString("jw_webview_url", url)
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = MiuixIcons.Download,
                                        contentDescription = null,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                }
                            })

                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            WebView(
                                state = webViewState,
                                modifier = Modifier.matchParentSize(),
                                navigator = navigator,
                                captureBackPresses = false,
                                client = remember {
                                    object : AccompanistWebViewClient() {
                                        override fun onPageStarted(
                                            view: WebView,
                                            url: String?,
                                            favicon: android.graphics.Bitmap?
                                        ) {
                                            super.onPageStarted(view, url, favicon)
                                            webViewLoading = true
                                        }

                                        override fun onPageFinished(view: WebView, url: String?) {
                                            super.onPageFinished(view, url)
                                            webViewLoading = false
                                            CookieManager.getInstance().flush() // 强制同步 Cookie
                                        }

                                        override fun shouldInterceptRequest(
                                            view: WebView?, request: WebResourceRequest?
                                        ): WebResourceResponse? {
                                            request?.requestHeaders?.let { headers ->
                                                if (headers.containsKey("X-Requested-With")) {
                                                    val newHeaders = headers.toMutableMap()
                                                    newHeaders.remove("X-Requested-With")
                                                    newHeaders["sec-ch-ua"] = ApiClient.SEC_CH_UA
                                                    newHeaders["sec-ch-ua-mobile"] =
                                                        ApiClient.SEC_CH_UA_MOBILE
                                                    newHeaders["sec-ch-ua-platform"] =
                                                        ApiClient.SEC_CH_UA_PLATFORM
                                                }
                                            }
                                            return super.shouldInterceptRequest(view, request)
                                        }
                                    }
                                },
                                onCreated = { webView ->
                                    webViewRef = webView
                                    webView.settings.apply {
                                        javaScriptEnabled = true
                                        userAgentString = ApiClient.PUBLIC_UA
                                    }
                                    CookieManager.getInstance().apply {
                                        setAcceptCookie(true)
                                        setAcceptThirdPartyCookies(webView, true)
                                    }

                                    // 其他设置
                                    webView.settings.apply {
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        javaScriptCanOpenWindowsAutomatically = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                    }
                                },
                                onDispose = {
                                    webViewRef = null
                                })

                            // WebView加载进度条 - 悬浮在顶部
                            if (webViewLoading) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter)
                                )
                            }
                        }
                    }
                }

            }
        }


    }
}