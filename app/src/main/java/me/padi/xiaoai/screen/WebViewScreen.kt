package me.padi.xiaoai.screen

import android.annotation.SuppressLint
import android.content.Intent
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.ParseResult
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.writablePrefs
import me.padi.xiaoai.AlertDialogData
import me.padi.xiaoai.AndroidBridge
import me.padi.xiaoai.BridgeCallback
import me.padi.xiaoai.Course
import me.padi.xiaoai.PromptDialogData
import me.padi.xiaoai.SingleSelectionDialogData
import org.json.JSONArray
import org.json.JSONObject
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WebViewScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                WebViewScreenContent(intent)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebViewScreenContent(intent: Intent) {
    val navigator = rememberWebViewNavigator()
    var webViewLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 获取 Intent 参数
    val intentUrl = intent.getStringExtra("url") ?: ""
    val intentTitle = intent.getStringExtra("title") ?: "导入课程表"
    val intentScript = intent.getStringExtra("script") ?: ""
    val context = LocalContext.current
    
    var url by remember { mutableStateOf(intentUrl.ifBlank { HookEntry.prefs.getString("jw_webview_url", "") }) }
    var tableName by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }

    val coroutineScope = rememberCoroutineScope()
    val webViewState = rememberWebViewState(url)

    val bridgeCallback = remember {
        object : BridgeCallback {
            override fun onShowAlert(data: AlertDialogData, callback: (Boolean) -> Unit) {}
            override fun onShowPrompt(data: PromptDialogData, callback: (String?) -> Unit, errorCallback: (String) -> Unit) {}
            override fun onShowSingleSelection(data: SingleSelectionDialogData, callback: (Int?) -> Unit) {}
            override fun onSaveImportedCourses(coursesJson: String, callback: (Boolean, String?) -> Unit) {
                importState = ImportState.Parsing
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val coursesArray = if (coursesJson.trim().startsWith("{")) {
                            val root = JSONObject(coursesJson)
                            root.optJSONArray("courses") ?: JSONArray()
                        } else {
                            JSONArray(coursesJson)
                        }

                        val courses = mutableListOf<Course>()
                        for (i in 0 until coursesArray.length()) {
                            val courseJson = coursesArray.getJSONObject(i)
                            val c = Course()
                            c.name = courseJson.optString("name", "").trim()
                            c.teacher = courseJson.optString("teacher", "").trim()
                            c.position = courseJson.optString("location", courseJson.optString("position", "")).trim()
                            c.day = courseJson.optInt("weekday", courseJson.optInt("day", 1))
                            
                            val sectionsArr = courseJson.optJSONArray("sections")
                            c.sections = if (sectionsArr != null) {
                                // zf.js / 拾光格式: sections 是数字数组 [1,2,3]
                                buildString {
                                    for (j in 0 until sectionsArr.length()) {
                                        if (j > 0) append(",")
                                        append(sectionsArr.getInt(j))
                                    }
                                }
                            } else {
                                // AI 解析格式: sections 是逗号字符串 "1,2,3"
                                val start = courseJson.optInt("startSection", -1)
                                val end = courseJson.optInt("endSection", -1)
                                if (start != -1 && end != -1) {
                                    (start..end).joinToString(",")
                                } else {
                                    courseJson.optString("sections", "1,2").trim()
                                }
                            }
                            
                            val weeksArray = courseJson.optJSONArray("weeks")
                            c.weeks = if (weeksArray != null) {
                                buildString {
                                    for (j in 0 until weeksArray.length()) {
                                        append(weeksArray.getInt(j))
                                        if (j != weeksArray.length() - 1) append(",")
                                    }
                                }
                            } else courseJson.optString("weeks", "")

                            val colorIndex = if (c.name.isNotEmpty()) kotlin.math.abs(c.name.hashCode() % ApiClient.COLOR_PRESETS.size) else i % ApiClient.COLOR_PRESETS.size
                            c.style = ApiClient.COLOR_PRESETS[colorIndex]
                            courses.add(c)
                        }

                        val appId = HostCompat.getAppId()
                        val serviceToken = HostCompat.getAccessToken(context)
                        val deviceId = HostCompat.getDeviceId(context)
                        
                        if (serviceToken == null || deviceId == null) {
                             withContext(Dispatchers.Main) {
                                 importState = ImportState.Error("无法获取令牌")
                                 callback(false, "无法获取令牌")
                             }
                             return@launch
                        }

                        val ctid = ApiClient.createTable(tableName.ifBlank { "提取课表" }, appId, serviceToken, deviceId)

                        val fromCtId = ApiClient.fetchTables(appId, serviceToken, deviceId)
                            .firstOrNull { it.current == 1 }?.id ?: 0L
                        ApiClient.switchTable(fromCtId, ctid, appId, serviceToken, deviceId)

                        ApiClient.uploadCoursesAll(courses, ctid, appId, serviceToken, deviceId)
                        
                        withContext(Dispatchers.Main) {
                            importState = ImportState.Success("导入成功")
                            callback(true, null)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            importState = ImportState.Error(e.message ?: "解析失败")
                            callback(false, e.message)
                        }
                    }
                }
            }
            override fun onSaveCourseConfig(configJson: String, callback: (Boolean, String?) -> Unit) { callback(true, null) }
            override fun onSavePresetTimeSlots(timeSlotsJson: String, callback: (Boolean, String?) -> Unit) { callback(true, null) }
            override fun onTaskCompleted() {}
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = intentTitle)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .overScrollVertical()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = url,
                    onValueChange = { newValue: String ->
                        url = newValue
                        if (newValue.isNotBlank()) {
                            context.writablePrefs().edit().putString("jw_webview_url", newValue).apply()
                        }
                    },
                    label = "教务链接",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    webViewState.content = WebContent.Url(url)
                }) {
                    Icon(imageVector = MiuixIcons.Download, contentDescription = "前往")
                }
            }

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
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                webViewLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                webViewLoading = false
                                CookieManager.getInstance().flush()
                            }
                        }
                    },
                    onCreated = { webView ->
                        webViewRef = webView
                        webView.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                        }
                        val bridge = AndroidBridge(context, webView, bridgeCallback)
                        webView.addJavascriptInterface(bridge, "AndroidBridge")
                        webView.addJavascriptInterface(bridge, "app")
                    }
                )

                if (webViewLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 状态显示
            when (val state = importState) {
                is ImportState.Loading -> {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("正在解析...")
                        }
                    }
                }
                is ImportState.Parsing -> {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("正在导入...")
                        }
                    }
                }
                is ImportState.Success -> {
                    Text("✅ ${state.message}", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                }
                is ImportState.Error -> {
                    Text("❌ ${state.message}", color = MiuixTheme.colorScheme.error, fontSize = 12.sp)
                }
                ImportState.Idle -> {}
            }

            TextField(
                value = tableName,
                onValueChange = { tableName = it },
                label = "课表名称"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !webViewLoading && importState !is ImportState.Loading && importState !is ImportState.Parsing,
                onClick = {
                    if (tableName.isBlank()) {
                        importState = ImportState.Error("请输入课表名称")
                        return@Button
                    }
                    importState = ImportState.Loading
                    
                    if (intentScript.isNotBlank()) {
                        webViewRef?.evaluateJavascript(intentScript, null)
                    } else {
                        webViewRef?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                            coroutineScope.launch {
                                try {
                                    val appId = HostCompat.getAppId()
                                    val serviceToken = HostCompat.getAccessToken(context)
                                    val deviceId = HostCompat.getDeviceId(context)
                                    
                                    if (serviceToken == null || deviceId == null) {
                                        importState = ImportState.Error("无法获取令牌")
                                        return@launch
                                    }

                                    withContext(Dispatchers.IO) {
                                        val prefs = context.writablePrefs()
                                        val apiKey = prefs.getString("api_key", "") ?: ""
                                        val modelName = prefs.getString("model_name", "gpt-3.5-turbo") ?: "gpt-3.5-turbo"
                                        val apiUrl = prefs.getString("api_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"

                                        ApiClient.parseCoursesStreaming(
                                            html,
                                            apiKey,
                                            modelName,
                                            apiUrl,
                                            ApiClient.SYSTEM_PROMPT,
                                            object : ApiClient.ParseCallback {
                                                override fun onUpdate(reasoning: String, content: String) {}
                                                override fun onSuccess(result: ParseResult) {
                                                    coroutineScope.launch {
                                                        try {
                                                            importState = ImportState.Parsing
                                                            val ctid = withContext(Dispatchers.IO) {
                                                                ApiClient.createTable(tableName.trim(), appId, serviceToken, deviceId)
                                                            }
                                                            withContext(Dispatchers.IO) {
                                                                ApiClient.uploadCoursesAll(result.courses, ctid, appId, serviceToken, deviceId)
                                                            }
                                                            importState = ImportState.Success("AI解析并导入成功")
                                                        } catch (e: Exception) {
                                                            importState = ImportState.Error(e.message ?: "上传核心失败")
                                                        }
                                                    }
                                                }
                                                override fun onError(e: Exception) {
                                                    coroutineScope.launch { importState = ImportState.Error(e.message ?: "解析失败") }
                                                }
                                            }
                                        )
                                    }
                                } catch (e: Exception) {
                                    importState = ImportState.Error(e.message ?: "加载失败")
                                }
                            }
                        }
                    }
                }
            ) {
                Text(if (webViewLoading) "正在加载页面..." else "开始解析导入")
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}