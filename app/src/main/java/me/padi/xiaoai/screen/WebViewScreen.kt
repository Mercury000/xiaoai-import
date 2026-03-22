package me.padi.xiaoai.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
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
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.TimeZone

/** 每次页面加载后注入的桥接胶水脚本，保证页面跳转后 AndroidBridgePromise 始终可用 */
private val BRIDGE_GLUE_JS = """
(function(){
  try {
    console.log('Bridge Glue: Starting injection...');
    if(window._bridgeInjected){
       console.log('Bridge Glue: Already exists, skipping.');
       return;
    }
    window._bridgeInjected = true;
    var reg={};
    window._resolveAndroidPromise=function(id,r){
       console.log('Bridge Glue: Resolving ' + id);
       var p=reg[id];if(p){delete reg[id];p[0](r);}
    };
    window._rejectAndroidPromise=function(id,e){
       console.error('Bridge Glue: Rejecting ' + id, e);
       var p=reg[id];if(p){delete reg[id];p[1](new Error(e));}
    };
    function mkp(fn){return new Promise(function(res,rej){var id='_bp'+Date.now()+Math.random().toString(36).slice(2);reg[id]=[res,rej];fn(id);});}
    function sarg(a){ return typeof a === 'string' ? a : JSON.stringify(a); }
    
    // 针对旧版适配器设计的同步桥接挂载（阻塞式）
    window.app = window.app || {};
    window.app.showAlert = function(t,c,b){ 
        console.log('Sync Bridge: showAlert called'); 
        if (arguments.length === 1) return AndroidBridge.showAlert(sarg(t));
        if (arguments.length === 2) return AndroidBridge.showAlert(sarg(t), sarg(c));
        return AndroidBridge.showAlert(sarg(t), sarg(c), sarg(b || '确定')); 
    };
    window.app.showPrompt = function(t,p,d,v){ 
        console.log('Sync Bridge: showPrompt called'); 
        if (arguments.length === 1) return AndroidBridge.showPrompt(sarg(t), "");
        if (arguments.length === 2) return AndroidBridge.showPrompt(sarg(t), sarg(p));
        if (arguments.length === 3) return AndroidBridge.showPrompt(sarg(t), sarg(p), sarg(d));
        return AndroidBridge.showPrompt(sarg(t), sarg(p), sarg(d || ''), sarg(v || '')); 
    };
    window.app.showSingleSelection = function(t,i,d){ 
        console.log('Sync Bridge: showSingleSelection called'); 
        if (arguments.length === 1) return AndroidBridge.showSingleSelection(sarg(t), "[]");
        if (arguments.length === 2) return AndroidBridge.showSingleSelection(sarg(t), sarg(i));
        return AndroidBridge.showSingleSelection(sarg(t), sarg(i), d != null ? d : -1); 
    };
    window.app.saveImportedCourses = function(j){ return AndroidBridge.saveImportedCourses(sarg(j)); };
    window.app.saveCourseConfig = function(j){ return AndroidBridge.saveCourseConfig(sarg(j)); };
    window.app.savePresetTimeSlots = function(j){ return AndroidBridge.savePresetTimeSlots(sarg(j)); };
    window.app.postData = function(m){ return AndroidBridge.postData(sarg(m)); };
    window.app.reportError = function(e){ return AndroidBridge.reportError(sarg(e)); };
    window.app.notifyTaskCompleted = function(){ AndroidBridge.notifyTaskCompletion(); };
    window.app.notifyTaskCompletion = function(){ AndroidBridge.notifyTaskCompletion(); };
    window.app.postHtml = function(h){ AndroidBridge.postHtml(sarg(h)); };
    window.app.closeWebView = function(){ AndroidBridge.onTaskCompleted(); };
    window.app.close = window.app.closeWebView;
    
    window.AndroidBridge = window.app; // 保证 window.AndroidBridge 也有相同别名
    
    // 同时也保留异步注入，供新版或 AI 模式使用
    window.AndroidBridgePromise = {
      showAlert:function(t,c,b){ return mkp(function(id){AndroidBridge.showAlertAsync(sarg(t),sarg(c),sarg(b),id);});},
      showPrompt:function(t,p,d,v){ return mkp(function(id){AndroidBridge.showPromptAsync(sarg(t),sarg(p),sarg(d||''),sarg(v||''),id);});},
      showSingleSelection:function(t,i,d){ return mkp(function(id){AndroidBridge.showSingleSelectionAsync(sarg(t),sarg(i),d!=null?d:-1,id);});},
      saveImportedCourses:function(j){ return mkp(function(id){AndroidBridge.saveImportedCourses(sarg(j),id);});},
      saveCourseConfig:function(j){ return mkp(function(id){AndroidBridge.saveCourseConfig(sarg(j),id);});},
      savePresetTimeSlots:function(j){ return mkp(function(id){AndroidBridge.savePresetTimeSlots(sarg(j),id);});},
      notifyTaskCompleted:function(){ AndroidBridge.notifyTaskCompletion(); },
      notifyTaskCompletion:function(){ AndroidBridge.notifyTaskCompletion(); }
    };

    console.log('Bridge Glue: Injected successfully (Ultimate Hybrid Mode).');
  } catch(e) { console.error('Bridge Glue: Error during injection', e); }
})();
""".trimIndent()

private data class AlertPendingState(val data: AlertDialogData, val latch: CountDownLatch, val result: BooleanArray)
private data class PromptPendingState(val data: PromptDialogData, val latch: CountDownLatch, val result: Array<String?>)
private data class SelectionPendingState(val data: SingleSelectionDialogData, val latch: CountDownLatch, val result: IntArray)

private fun normalizeShiguangCourseConfig(configJson: String): String {
    return try {
        val raw = JSONObject(configJson)
        val src = raw.optJSONObject("courseConfig") ?: raw
        val mapped = JSONObject(src.toString())

        // 直接透传可能已经是小爱字段的数据
        if (src.has("startSemester")) mapped.put("startSemester", src.get("startSemester"))
        if (src.has("totalWeek")) mapped.put("totalWeek", src.get("totalWeek"))
        if (src.has("weekStart")) mapped.put("weekStart", src.get("weekStart"))

        // 拾光规范字段 -> 小爱字段
        if (src.has("semesterStartDate")) mapped.put("startSemester", src.get("semesterStartDate"))
        if (src.has("semesterTotalWeeks")) mapped.put("totalWeek", src.get("semesterTotalWeeks"))
        if (src.has("firstDayOfWeek")) mapped.put("weekStart", src.get("firstDayOfWeek"))
        if (src.has("startDate")) mapped.put("startSemester", src.get("startDate"))
        if (src.has("termStartDate")) mapped.put("startSemester", src.get("termStartDate"))
        if (src.has("currentWeek")) mapped.put("presentWeek", src.get("currentWeek"))

        val startDateStr = sequenceOf(
            src.optString("semesterStartDate", ""),
            src.optString("startDate", ""),
            src.optString("termStartDate", ""),
            mapped.optString("startSemester", "")
        ).firstOrNull { it.isNotBlank() }
        val totalWeeks = when {
            src.has("semesterTotalWeeks") -> src.optInt("semesterTotalWeeks", 0)
            mapped.has("totalWeek") -> mapped.optInt("totalWeek", 0)
            else -> 0
        }

        startDateStr?.let { rawStart ->
            normalizeSemesterStart(rawStart)?.let { normalizedStart ->
                mapped.put("startSemester", normalizedStart)
                mapped.put("presentWeek", calculatePresentWeek(rawStart, totalWeeks))
            }
        }

        mapped.toString()
    } catch (_: Exception) {
        configJson
    }
}

private fun normalizeSemesterStart(raw: String): String? {
    val value = raw.trim()
    if (value.isEmpty()) return null

    value.toLongOrNull()?.let { numeric ->
        return if (numeric in 1L..99_999_999_999L) (numeric * 1000L).toString() else numeric.toString()
    }

    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        isLenient = false
        timeZone = TimeZone.getDefault()
    }
    val date = parser.parse(value) ?: return null
    val calendar = Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis.toString()
}

private fun calculatePresentWeek(rawStart: String, totalWeeks: Int): Int {
    val startCal = Calendar.getInstance()
    rawStart.trim().toLongOrNull()?.let { numeric ->
        val millis = if (numeric in 1L..99_999_999_999L) numeric * 1000L else numeric
        startCal.timeInMillis = millis
    } ?: run {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            isLenient = false
            timeZone = TimeZone.getDefault()
        }
        val startDate = parser.parse(rawStart.trim()) ?: return 1
        startCal.time = startDate
    }
    startCal.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffDays = ((todayCal.timeInMillis - startCal.timeInMillis) / (24L * 60L * 60L * 1000L)).toInt()
    val week = max(1, diffDays / 7 + 1)
    return if (totalWeeks > 0) week.coerceAtMost(totalWeeks) else week
}

private fun normalizeShiguangTimeSlots(timeSlotsJson: String): String {
    return try {
        val src = JSONArray(timeSlotsJson)
        val mapped = JSONArray()
        for (i in 0 until src.length()) {
            val node = src.optJSONObject(i) ?: continue
            val number = when {
                node.has("i") -> node.optInt("i", -1)
                node.has("number") -> node.optInt("number", -1)
                node.has("section") -> node.optInt("section", -1)
                else -> -1
            }
            val start = when {
                node.has("s") -> node.optString("s")
                node.has("startTime") -> node.optString("startTime")
                node.has("start") -> node.optString("start")
                else -> ""
            }
            val end = when {
                node.has("e") -> node.optString("e")
                node.has("endTime") -> node.optString("endTime")
                node.has("end") -> node.optString("end")
                else -> ""
            }
            if (number > 0 && start.isNotBlank() && end.isNotBlank()) {
                mapped.put(
                    JSONObject()
                        .put("i", number)
                        .put("s", start)
                        .put("e", end)
                )
            }
        }
        if (mapped.length() > 0) mapped.toString() else timeSlotsJson
    } catch (_: Exception) {
        timeSlotsJson
    }
}

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
    // 获取 Intent 参数
    val intentUrl = intent.getStringExtra("url") ?: ""
    val intentTitle = intent.getStringExtra("title") ?: "导入课程表"
    val intentScript = intent.getStringExtra("script") ?: ""
    val intentText = intent.getStringExtra("text") ?: ""
    val context = LocalContext.current

    var webViewLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var url by remember { mutableStateOf(intentUrl.ifBlank { HookEntry.prefs.getString("jw_webview_url", "") }) }
    var lastLoadedUrl by remember { mutableStateOf(url) }
    var tableName by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    val currentImportState by rememberUpdatedState(importState)

    val coroutineScope = rememberCoroutineScope()
    
    var canGoBack by remember { mutableStateOf(false) }
    
    // BackHandler 处理
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    // 对话框状态（用显式 MutableState ref 以便在 remember 闭包中捕获）
    val alertStateRef: MutableState<AlertPendingState?> = remember { mutableStateOf(null) }
    val promptStateRef: MutableState<PromptPendingState?> = remember { mutableStateOf(null) }
    val selectionStateRef: MutableState<SelectionPendingState?> = remember { mutableStateOf(null) }
    val promptInputRef: MutableState<String> = remember { mutableStateOf("") }

    /** 抽取出来的 AI 源码解析逻辑，避免与 Bridge 交互逻辑混杂 */
    fun processHtmlForAi(html: String) {
        Log.d("WebViewScreen", "开始 processHtmlForAi, html 长度: ${html.length}")
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
                        html, apiKey, modelName, apiUrl, ApiClient.SYSTEM_PROMPT,
                        object : ApiClient.ParseCallback {
                            override fun onUpdate(reasoning: String, content: String) {}
                            override fun onSuccess(result: ParseResult) {
                                coroutineScope.launch {
                                    try {
                                        importState = ImportState.Parsing()
                                        CourseRepository.importCourses(context, appId, tableName.ifBlank { "提取课表" }.trim(), result.courses)
                                        importState = ImportState.Success("AI解析并导入成功")
                                    } catch (e: Exception) {
                                        importState = ImportState.Error(e.message ?: "导入失败")
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
                importState = ImportState.Error(e.message ?: "操作异常")
            }
        }
    }

    val bridgeCallback = remember {
        object : BridgeCallback {
            override fun onShowAlert(data: AlertDialogData, latch: CountDownLatch, result: BooleanArray) {
                alertStateRef.value = AlertPendingState(data, latch, result)
            }
            override fun onShowPrompt(data: PromptDialogData, latch: CountDownLatch, result: Array<String?>) {
                promptInputRef.value = data.defaultText
                promptStateRef.value = PromptPendingState(data, latch, result)
            }
            override fun onShowSingleSelection(data: SingleSelectionDialogData, latch: CountDownLatch, result: IntArray) {
                selectionStateRef.value = SelectionPendingState(data, latch, result)
            }
            override fun onSaveImportedCourses(coursesJson: String, callback: (Boolean, String?) -> Unit) {
                importState = ImportState.Parsing("正在解析课程数据...")
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val root = try { JSONObject(coursesJson) } catch (e: Exception) { null }
                        val coursesArray = if (root != null) {
                            if (root.has("courses")) root.getJSONArray("courses")
                            else if (root.has("parserRes")) {
                                val pr = root.get("parserRes")
                                if (pr is JSONArray) pr else JSONObject(pr.toString()).getJSONArray("courses")
                            }
                            else JSONArray().put(root)
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
                                "1"
                            } else if (sectionsArr != null) {
                                buildString {
                                    for (j in 0 until sectionsArr.length()) {
                                        if (j > 0) append(",")
                                        append(sectionsArr.getInt(j))
                                    }
                                }
                            } else {
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
                            importState = ImportState.Success("完成！已导入至 $tableName")
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
                        val normalizedConfig = normalizeShiguangCourseConfig(configJson)
                        HostCompat.pendingCourseConfigJson = normalizedConfig
                        val ctid = HostCompat.importTargetTableId
                        if (ctid != null) {
                            CourseRepository.updateTableSettings(context, appId, ctid, "当前课表", normalizedConfig, null)
                        }
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
                        val normalizedTimeSlots = normalizeShiguangTimeSlots(timeSlotsJson)
                        HostCompat.pendingTimeSlotSectionsJson = normalizedTimeSlots
                        val ctid = HostCompat.importTargetTableId
                        if (ctid != null) {
                            val schedule = ScheduleConfig().apply { sections = normalizedTimeSlots }
                            CourseRepository.updateTableSettings(context, appId, ctid, "当前课表", null, schedule)
                        }
                        callback(true, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(false, e.message)
                    }
                }
            }
            override fun onTaskCompleted() {
                coroutineScope.launch {
                    if (currentImportState is ImportState.Loading || currentImportState is ImportState.Parsing) {
                        importState = ImportState.Idle
                    }
                }
            }
            override fun onReceiveHtml(html: String) {
                importState = ImportState.Loading("接收到源码，正在处理...")
                processHtmlForAi(html)
            }
            override fun onError(message: String) {
                importState = ImportState.Error(message)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = intentTitle,
                actions = {
                    IconButton(onClick = {
                        webViewRef?.reload()
                        importState = ImportState.Idle
                    }) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
                .imePadding()
        ) {
            // URL Area
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                    webViewRef?.loadUrl(url)
                }) {
                    Icon(imageVector = MiuixIcons.Download, contentDescription = "前往")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            this.webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    webViewLoading = true
                                    canGoBack = view.canGoBack()
                                    lastLoadedUrl = url
                                    Log.d("WebViewScreen", "onPageStarted: $url")
                                    val isTransientUrl = url == "about:blank"
                                    if (currentImportState is ImportState.Loading && !isTransientUrl) {
                                        importState = ImportState.Idle
                                    }
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    super.onPageFinished(view, url)
                                    webViewLoading = false
                                    canGoBack = view.canGoBack()
                                    Log.d("WebViewScreen", "onPageFinished: $url")
                                    CookieManager.getInstance().flush()
                                    view.evaluateJavascript(BRIDGE_GLUE_JS, null)
                                }

                                override fun onReceivedError(view: WebView, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    Log.e("WebViewScreen", "WebView Error: ${error?.description}")
                                }

                                override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                    Log.e("WebViewScreen", "Render process gone! Reason: ${detail.rendererPriorityAtExit()}")
                                    importState = ImportState.Error("渲染进程崩溃，正在恢复...")
                                    view.reload()
                                    return true
                                }
                                
                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                    handler.proceed()
                                }

                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url.toString()
                                    if (url.startsWith("http://") || url.startsWith("https://")) {
                                        return false
                                    }
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                        view.context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        Log.e("WebViewScreen", "Failed to launch intent for URL: $url", e)
                                        return true // Return true to consume the click even if it fails
                                    }
                                }
                            }

                            this.webChromeClient = object : android.webkit.WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                    consoleMessage?.let {
                                        Log.d("WebViewConsole", "[${it.messageLevel()}] ${it.message()}")
                                    }
                                    return true
                                }
                                override fun onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                                    val latch = CountDownLatch(1)
                                    val res = BooleanArray(1) { false }
                                    bridgeCallback.onShowAlert(AlertDialogData("来自网页的消息", message, "确定"), latch, res)
                                    Thread { latch.await(); view.post { result.confirm() } }.start()
                                    return true
                                }
                                override fun onJsConfirm(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean {
                                    val latch = CountDownLatch(1)
                                    val res = BooleanArray(1) { false }
                                    bridgeCallback.onShowAlert(AlertDialogData("请确认", message, "确定"), latch, res)
                                    Thread { latch.await(); view.post { if (res[0]) result.confirm() else result.cancel() } }.start()
                                    return true
                                }
                                
                                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                                    // 许多闪退是因为在同一 WebView 中处理多窗口时状态冲突
                                    // 给 transport 设置一个已经存在的 WebView 并直接返回 true 可能导致某些设备闪退
                                    // 如果只是想在当前窗口打开，推荐返回 false 让父类或默认逻辑处理，或者手动 loadUrl
                                    val url = view.hitTestResult.extra
                                    if (url != null) {
                                        view.loadUrl(url)
                                        return false
                                    }
                                    return false
                                }
                            }

                            settings.apply {
                                javaScriptEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 XiaoAi/1.0"
                                textZoom = 100

                                // 增强加载与兼容性设置
                                allowFileAccess = true
                                allowContentAccess = true
                                domStorageEnabled = true
                                setGeolocationEnabled(true)
                                javaScriptCanOpenWindowsAutomatically = true
                                mediaPlaybackRequiresUserGesture = false
                                setSupportMultipleWindows(false)
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }

                            val webView = this
                            CookieManager.getInstance().apply {
                                setAcceptCookie(true)
                                setAcceptThirdPartyCookies(webView, true)
                            }

                            val bridge = AndroidBridge(context, this, bridgeCallback)
                            addJavascriptInterface(bridge, "AndroidBridge")
                            addJavascriptInterface(bridge, "app")
                            
                            webViewRef = this
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = {
                        // 如果有特殊更新逻辑可以放在这里，目前 loadUrl 已在 factory 处理
                    }
                )


                if (webViewLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }

            // Bottom Control Panel
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .heightIn(max = 240.dp)
            ) {
                item {
                    if (intentText.isNotBlank()) {
                        Text(intentText, color = MiuixTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }

                item {
                    when (val state = importState) {
                        is ImportState.Loading -> {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(state.message, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(text = "重置", onClick = { importState = ImportState.Idle })
                                    }
                                }
                            }
                        }
                        is ImportState.Parsing -> {
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text(state.message, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(text = "取消", onClick = { importState = ImportState.Idle })
                                    }
                                }
                            }
                        }
                        is ImportState.Success -> {
                            Text("✅ ${state.message}", color = MiuixTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        is ImportState.Error -> {
                            Text("❌ ${state.message}", color = MiuixTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
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
                            importState = ImportState.Loading("脚本启动中...")
                            Log.d("WebViewScreen", "开始点击解析, webViewRef is ${if(webViewRef == null) "NULL" else "NOT NULL"}")
                            
                            // 启动冗余超时保护：如果脚本执行挂起（例如 V8 死循环），10秒后强制复位
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(10000)
                                if (importState is ImportState.Loading) {
                                    Log.w("WebViewScreen", "冗余超时保护触发（10s），强制重置到 Idle")
                                    importState = ImportState.Idle
                                }
                            }
                            if (intentScript.isNotBlank()) {
                                Log.d("WebViewScreen", "注入并运行脚本, 长度: ${intentScript.length}")
                                webViewRef?.evaluateJavascript(BRIDGE_GLUE_JS) {
                                    // 为脚本添加 try-catch 包装以防挂起
                                    val wrappedScript = """
                                        (function(){
                                            console.log('WrappedScript: Started');
                                            try {
                                                $intentScript
                                                console.log('WrappedScript: Execution reached end');
                                                return "OK";
                                            } catch(e) {
                                                console.error('WrappedScript: Execution Error:', e);
                                                if (window.app && window.app.reportError) window.app.reportError(e.message || e.toString());
                                                AndroidBridge.notifyTaskCompleted();
                                                return "Error";
                                            }
                                        })();
                                    """.trimIndent()
                                    webViewRef?.evaluateJavascript(wrappedScript) { res ->
                                        Log.d("WebViewScreen", "脚本 evaluateJavascript 完成, 返回值: $res")
                                        // 启动 10 秒超时检测：如果脚本执行完了但 10 秒后还没进入 Parsing 或 Success 状态，重置为 Idle
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(10000)
                                            if (importState is ImportState.Loading) {
                                                Log.w("WebViewScreen", "脚本执行超时（10s），状态仍为 Loading，强制重置到 Idle")
                                                importState = ImportState.Idle
                                            }
                                        }
                                    }
                                }
                            } else {
                                Log.d("WebViewScreen", "AI 模式：通过桥接获取 HTML 源码（带安全校验）")
                                val extractionScript = """
                                    (function(){
                                        try {
                                            var html = (document.documentElement ? document.documentElement.outerHTML : (document.body ? document.body.innerHTML : "")) || "";
                                            var frameChunks = [];
                                            var frameTexts = [];
                                            var visited = new WeakSet();
                                            function compactHtml(raw) {
                                                if (!raw) return '';
                                                return raw
                                                    .replace(/<script[\s\S]*?<\/script>/gi, '')
                                                    .replace(/<style[\s\S]*?<\/style>/gi, '')
                                                    .replace(/<noscript[\s\S]*?<\/noscript>/gi, '')
                                                    .replace(/\s{2,}/g, ' ');
                                            }
                                            function collectFrames(win, path, depth) {
                                                if (!win || depth > 3) return;
                                                var doc = null;
                                                try { doc = win.document; } catch (e) { return; }
                                                if (!doc || !doc.querySelectorAll) return;
                                                var frames = doc.querySelectorAll('iframe,frame');
                                                for (var i = 0; i < frames.length; i++) {
                                                    var el = frames[i];
                                                    var tag = (el.tagName || 'iframe').toLowerCase();
                                                    var src = el.getAttribute('src') || '';
                                                    var id = el.id || '';
                                                    var name = el.getAttribute('name') || '';
                                                    var mark = 'frame=' + path + '.' + i + ' tag=' + tag + ' src=' + src + ' id=' + id + ' name=' + name;
                                                    try {
                                                        var childWin = el.contentWindow;
                                                        if (!childWin) {
                                                            frameChunks.push('<!-- XIAOAI_IFRAME_EMPTY ' + mark + ' -->');
                                                            continue;
                                                        }
                                                        if (visited.has(childWin)) {
                                                            frameChunks.push('<!-- XIAOAI_IFRAME_SKIP_VISITED ' + mark + ' -->');
                                                            continue;
                                                        }
                                                        visited.add(childWin);
                                                        var childDoc = childWin.document;
                                                        var childHtml = (childDoc && childDoc.documentElement)
                                                            ? childDoc.documentElement.outerHTML
                                                            : ((childDoc && childDoc.body) ? childDoc.body.innerHTML : '');
                                                        frameChunks.push('<!-- XIAOAI_IFRAME_BEGIN ' + mark + ' -->\\n' + compactHtml(childHtml || '') + '\\n<!-- XIAOAI_IFRAME_END ' + mark + ' -->');
                                                        var childText = (childDoc && childDoc.body && childDoc.body.innerText) ? childDoc.body.innerText : '';
                                                        if (childText) frameTexts.push('[IFRAME ' + mark + ']\\n' + childText);
                                                        collectFrames(childWin, path + '.' + i, depth + 1);
                                                    } catch (e) {
                                                        frameChunks.push('<!-- XIAOAI_IFRAME_CROSS_ORIGIN ' + mark + ' -->');
                                                    }
                                                }
                                            }
                                            html = compactHtml(html);
                                            collectFrames(window, 'root', 0);
                                            if (frameChunks.length > 0) {
                                                html += '\\n<!-- XIAOAI_IFRAME_CONTENT_BEGIN -->\\n' + frameChunks.join('\\n') + '\\n<!-- XIAOAI_IFRAME_CONTENT_END -->';
                                            }
                                            if (html.length > 2000000) { // > 2MB 则降级
                                                var mainText = (document.body && document.body.innerText) ? document.body.innerText : '';
                                                var textPayload = '[XIAOAI_TEXT_FALLBACK]\\n[MAIN_TEXT]\\n' + mainText;
                                                if (frameTexts.length > 0) {
                                                    textPayload += '\\n[IFRAME_TEXTS]\\n' + frameTexts.join('\\n\\n');
                                                }
                                                if (textPayload.length > 2000000) {
                                                    console.warn('HTML/text payload too large, truncating text fallback');
                                                    textPayload = textPayload.slice(0, 2000000);
                                                } else {
                                                    console.warn('HTML too large, switched to text fallback');
                                                }
                                                html = textPayload;
                                            }
                                            AndroidBridge.postHtml(html);
                                            return "Extraction Process Started";
                                        } catch(e) {
                                            console.error('Extraction Failed:', e);
                                            return "Extraction Failed: " + e.message;
                                        }
                                    })()
                                """.trimIndent()
                                webViewRef?.evaluateJavascript(extractionScript) { res ->
                                    Log.d("WebViewScreen", "AI 提取脚本注入结果: $res")
                                }
                            }
                        }
                    ) {
                        Text(if (webViewLoading) "页面加载中..." else "开始解析导入")
                    }
                }
            }
        }
    }

    // ---- 对话框 UI（由拾光仓库适配器脚本通过 AndroidBridgePromise 触发）----

    // Alert 弹窗
    val alert = alertStateRef.value
    if (alert != null) {
        Dialog(onDismissRequest = {}) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(alert.data.title, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(alert.data.content, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.weight(1f))
                            Button(onClick = {
                                alertStateRef.value = null
                                alert.result[0] = true
                                alert.latch.countDown()
                            }) {
                                Text(alert.data.confirmText.ifBlank { "确定" })
                            }
                        }
                    }
                }
            }
        }
    }

    val prompt = promptStateRef.value
    if (prompt != null) {
        Dialog(onDismissRequest = {}) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                ) {
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
                                    prompt.result[0] = null
                                    prompt.latch.countDown()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("取消") }
                            Spacer(modifier = Modifier.size(8.dp))
                            Button(
                                onClick = {
                                    val input = promptInputRef.value
                                    promptStateRef.value = null
                                    prompt.result[0] = input
                                    prompt.latch.countDown()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("确定") }
                        }
                    }
                }
            }
        }
    }

    val selection = selectionStateRef.value
    if (selection != null) {
        val initialIndex = if (selection.data.defaultSelectedIndex in selection.data.items.indices) {
            selection.data.defaultSelectedIndex
        } else {
            -1
        }
        var selectedIndex by remember(selection) { mutableStateOf(initialIndex) }
        Dialog(onDismissRequest = {
            selectionStateRef.value = null
            selection.result[0] = -1
            selection.latch.countDown()
        }) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)) {
                        Text(selection.data.title, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            itemsIndexed(selection.data.items) { index, item ->
                                val isSelected = index == selectedIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        )
                                        .clickable { selectedIndex = index }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item, modifier = Modifier.weight(1f))
                                    if (isSelected) {
                                        Text(
                                            text = "已选",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Button(
                                onClick = {
                                    selectionStateRef.value = null
                                    selection.result[0] = -1
                                    selection.latch.countDown()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("取消") }
                            Spacer(modifier = Modifier.size(8.dp))
                            Button(
                                onClick = {
                                    selectionStateRef.value = null
                                    selection.result[0] = selectedIndex
                                    selection.latch.countDown()
                                },
                                enabled = selectedIndex >= 0,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) { Text("确定", color = MiuixTheme.colorScheme.onPrimary) }
                        }
                    }
                }
            }
        }
    }
}
