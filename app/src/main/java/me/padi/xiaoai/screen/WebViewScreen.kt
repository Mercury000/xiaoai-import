package me.padi.xiaoai.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
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
import me.padi.xiaoai.CourseRepository
import me.padi.xiaoai.ScheduleConfig
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

private data class AlertPendingState(val data: AlertDialogData, val callback: (Boolean) -> Unit)
private data class PromptPendingState(val data: PromptDialogData, val callback: (String?) -> Unit, val errorCallback: (String) -> Unit)
private data class SelectionPendingState(val data: SingleSelectionDialogData, val callback: (Int?) -> Unit)

class WebViewScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val intentText = intent.getStringExtra("text") ?: ""
    val context = LocalContext.current
    
    var url by remember { mutableStateOf(intentUrl.ifBlank { HookEntry.prefs.getString("jw_webview_url", "") }) }
    var tableName by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }

    val coroutineScope = rememberCoroutineScope()
    val initialUrl = remember { url }
    val webViewState = rememberWebViewState(initialUrl)

    // 对话框状态（用显式 MutableState ref 以便在 remember 闭包中捕获）
    val alertStateRef: MutableState<AlertPendingState?> = remember { mutableStateOf(null) }
    val promptStateRef: MutableState<PromptPendingState?> = remember { mutableStateOf(null) }
    val selectionStateRef: MutableState<SelectionPendingState?> = remember { mutableStateOf(null) }
    val promptInputRef: MutableState<String> = remember { mutableStateOf("") }

    val bridgeCallback = remember {
        object : BridgeCallback {
            override fun onShowAlert(data: AlertDialogData, callback: (Boolean) -> Unit) {
                alertStateRef.value = AlertPendingState(data, callback)
            }
            override fun onShowPrompt(data: PromptDialogData, callback: (String?) -> Unit, errorCallback: (String) -> Unit) {
                promptInputRef.value = data.defaultText
                promptStateRef.value = PromptPendingState(data, callback, errorCallback)
            }
            override fun onShowSingleSelection(data: SingleSelectionDialogData, callback: (Int?) -> Unit) {
                selectionStateRef.value = SelectionPendingState(data, callback)
            }
            override fun onSaveImportedCourses(coursesJson: String, callback: (Boolean, String?) -> Unit) {
                importState = ImportState.Parsing
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val root = try { JSONObject(coursesJson) } catch (e: Exception) { null }
                        val coursesArray = if (root != null) {
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
                            
                            val isCustomTime = courseJson.optBoolean("isCustomTime", false)
                            val sectionsArr = courseJson.optJSONArray("sections")
                            c.sections = if (isCustomTime) {
                                "1"  // 自定义时间课程，小爱接口不支持，用最小节次占位
                            } else if (sectionsArr != null) {
                                // zf.js / 拾光格式: sections 是数字数组 [1,2,3]
                                buildString {
                                    for (j in 0 until sectionsArr.length()) {
                                        if (j > 0) append(",")
                                        append(sectionsArr.getInt(j))
                                    }
                                }
                            } else {
                                // AI 解析格式 / 拾光仓库格式: startSection + endSection
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

                            c.sanitizeAndValidate()
                            val colorIndex = if (c.name.isNotEmpty()) kotlin.math.abs(c.name.hashCode() % ApiClient.COLOR_PRESETS.size) else i % ApiClient.COLOR_PRESETS.size
                            c.style = ApiClient.COLOR_PRESETS[colorIndex]
                            courses.add(c)
                        }

                        val schedule = root?.optJSONObject("schedule")?.let { sObj ->
                            ScheduleConfig().apply {
                                if (sObj.has("morningNum")) morningNum = sObj.getInt("morningNum")
                                if (sObj.has("afternoonNum")) afternoonNum = sObj.getInt("afternoonNum")
                                if (sObj.has("nightNum")) nightNum = sObj.getInt("nightNum")
                                if (sObj.has("sections")) sections = sObj.optString("sections")
                            }
                        }

                        val appId = HostCompat.getAppId()
                        CourseRepository.importCourses(context, appId, tableName.ifBlank { "提取课表" }, courses, schedule)
                        
                        withContext(Dispatchers.Main) {
                            importState = ImportState.Success("导入成功")
                            HostCompat.isImportFinished = true
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
            override fun onSaveCourseConfig(configJson: String, callback: (Boolean, String?) -> Unit) {
                coroutineScope.launch {
                    try {
                        val appId = HostCompat.getAppId()
                        val ctid = CourseRepository.getActiveTableId(context, appId) ?: throw Exception("无活跃课表")
                        CourseRepository.updateTableSettings(context, appId, ctid, "当前课表", configJson, null)
                        callback(true, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(false, e.message)
                    }
                }
            }
            override fun onSavePresetTimeSlots(timeSlotsJson: String, callback: (Boolean, String?) -> Unit) {
                coroutineScope.launch {
                    try {
                        val appId = HostCompat.getAppId()
                        val ctid = CourseRepository.getActiveTableId(context, appId) ?: throw Exception("无活跃课表")
                        val schedule = ScheduleConfig().apply { sections = timeSlotsJson }
                        CourseRepository.updateTableSettings(context, appId, ctid, "当前课表", null, schedule)
                        callback(true, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(false, e.message)
                    }
                }
            }
            override fun onTaskCompleted() {
                coroutineScope.launch {
                    importState = ImportState.Success("导入完成")
                    HostCompat.isImportFinished = true
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(title = intentTitle)
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .overScrollVertical()
                .navigationBarsPadding()
                .imePadding()
        ) {
            item {
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
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
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
                            // 阻止外层 LazyColumn 拦截触摸事件，让 WebView 能正常滚动
                            webView.setOnTouchListener { v, event ->
                                when (event.action) {
                                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                                false
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
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))

                // 状态显示
                if (intentText.isNotBlank()) {
                    Text(intentText, color = MiuixTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                }
            }

            item {
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
            }

            item {
                TextField(
                    value = tableName,
                    onValueChange = { tableName = it },
                    label = "课表名称"
                )
            }

            item {
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
                            // 先注入 window.AndroidBridgePromise JS 胶水，再执行适配器脚本
                            val glueJs = """
    (function(){
      if(window.AndroidBridgePromise)return;
      var reg={};
      window._resolveAndroidPromise=function(id,r){var p=reg[id];if(p){delete reg[id];p[0](r);}};
      window._rejectAndroidPromise=function(id,e){var p=reg[id];if(p){delete reg[id];p[1](new Error(e));}};
      function mkp(fn){return new Promise(function(res,rej){var id='_bp'+Date.now()+Math.random().toString(36).slice(2);reg[id]=[res,rej];fn(id);});}
      window.AndroidBridgePromise={
        showAlert:function(t,c,b){return mkp(function(id){AndroidBridge.showAlert(t,c,b,id);});},
        showPrompt:function(t,p,d,v){return mkp(function(id){AndroidBridge.showPrompt(t,p,d||'',v||'',id);});},
        showSingleSelection:function(t,i,d){return mkp(function(id){AndroidBridge.showSingleSelection(t,i,d!=null?d:-1,id);});},
        saveImportedCourses:function(j){return mkp(function(id){AndroidBridge.saveImportedCourses(j,id);});},
        saveCourseConfig:function(j){return mkp(function(id){AndroidBridge.saveCourseConfig(j,id);});},
        savePresetTimeSlots:function(j){return mkp(function(id){AndroidBridge.savePresetTimeSlots(j,id);});}
      };
    })();""".trimIndent()
                            webViewRef?.evaluateJavascript(glueJs) {
                                webViewRef?.evaluateJavascript(intentScript, null)
                            }
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
                                                        CourseRepository.importCourses(
                                                            context,
                                                            appId,
                                                            tableName.trim(),
                                                            result.courses
                                                        )
                                                        importState = ImportState.Success("AI解析并导入成功")
                                                    } catch (e: Exception) {
                                                        importState = ImportState.Error(e.message ?: "导入报错")
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
            }
            
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // ---- 对话框 UI（由拾光仓库适配器脚本通过 AndroidBridgePromise 触发）----

    // Alert 弹窗
    val alert = alertStateRef.value
    if (alert != null) {
        Dialog(onDismissRequest = {}) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(alert.data.title, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(alert.data.content, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Button(onClick = {
                            alertStateRef.value = null
                            alert.callback(true)
                        }) {
                            Text(alert.data.confirmText.ifBlank { "确定" })
                        }
                    }
                }
            }
        }
    }

    // Prompt 输入弹窗
    val prompt = promptStateRef.value
    if (prompt != null) {
        Dialog(onDismissRequest = {}) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(prompt.data.title, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (prompt.data.tip.isNotBlank()) {
                        Text(prompt.data.tip, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    TextField(
                        value = promptInputRef.value,
                        onValueChange = { promptInputRef.value = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                promptStateRef.value = null
                                prompt.callback(null)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("取消") }
                        Spacer(modifier = Modifier.size(8.dp))
                        Button(
                            onClick = {
                                val input = promptInputRef.value
                                promptStateRef.value = null
                                prompt.callback(input)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("确定") }
                    }
                }
            }
        }
    }

    // 单选列表弹窗
    val selection = selectionStateRef.value
    if (selection != null) {
        Dialog(onDismissRequest = {
            selectionStateRef.value = null
            selection.callback(null)
        }) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)) {
                    Text(selection.data.title, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(selection.data.items) { index, item ->
                            Text(
                                text = item,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectionStateRef.value = null
                                        selection.callback(index)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            selectionStateRef.value = null
                            selection.callback(null)
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) { Text("取消") }
                }
            }
        }
    }
}