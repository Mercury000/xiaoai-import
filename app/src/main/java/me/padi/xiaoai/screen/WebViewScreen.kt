package com.mercury.xiaoaiimport.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
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
import com.mercury.xiaoaiimport.ApiClient
import com.mercury.xiaoaiimport.HostCompat
import com.mercury.xiaoaiimport.ParseResult
import com.mercury.xiaoaiimport.hook.HookEntry
import com.mercury.xiaoaiimport.writablePrefs
import com.mercury.xiaoaiimport.AlertDialogData
import com.mercury.xiaoaiimport.AndroidBridge
import com.mercury.xiaoaiimport.BridgeCallback
import com.mercury.xiaoaiimport.Course
import com.mercury.xiaoaiimport.PromptDialogData
import com.mercury.xiaoaiimport.SingleSelectionDialogData
import com.mercury.xiaoaiimport.openCoursePreviewScreen
import com.mercury.xiaoaiimport.ScheduleConfig
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
import com.kongzue.dialogx.dialogs.InputDialog
import com.kongzue.dialogx.dialogs.MessageDialog
import com.kongzue.dialogx.dialogs.MessageMenu
import com.kongzue.dialogx.interfaces.OnDialogButtonClickListener
import com.kongzue.dialogx.interfaces.OnInputDialogButtonClickListener
import com.kongzue.dialogx.interfaces.OnMenuButtonClickListener
import com.kongzue.dialogx.interfaces.OnMenuItemClickListener
import com.tencent.smtt.export.external.interfaces.ConsoleMessage
import com.tencent.smtt.export.external.interfaces.JsResult
import com.tencent.smtt.export.external.interfaces.SslError
import com.tencent.smtt.export.external.interfaces.SslErrorHandler
import com.tencent.smtt.sdk.CookieManager
import com.tencent.smtt.sdk.WebChromeClient
import com.tencent.smtt.sdk.WebSettings
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient

private val BRIDGE_GLUE_JS = """
(function(){
  try {
    console.log('Bridge Glue: Starting injection...');
    if(window._bridgeInjected){
       console.log('Bridge Glue: Already exists, skipping.');
       return;
    }

    // 新名称优先；旧名称和 app 仅作为过渡期回退。
    var nativeBridge = window.shiguangBridge || window.AndroidBridge || window.app;
    if (!nativeBridge) {
       throw new Error('Native bridge is unavailable');
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
    function sarg(a){
       if (typeof a === 'string') return a;
       if (typeof a === 'function') return a.toString();
       var encoded = JSON.stringify(a);
       return typeof encoded === 'undefined' ? (a == null ? '' : String(a)) : encoded;
    }

    // 对外统一暴露新的 shiguangBridge；AndroidBridge/app 保留为同一 API 的兼容别名。
    var bridgeApi = {};
    bridgeApi.showToast = function(m){ return nativeBridge.showToast(sarg(m)); };
    bridgeApi.showAlert = function(t,c,b){
        console.log('Sync Bridge: showAlert called');
        if (arguments.length === 1) return nativeBridge.showAlert(sarg(t));
        if (arguments.length === 2) return nativeBridge.showAlert(sarg(t), sarg(c));
        return nativeBridge.showAlert(sarg(t), sarg(c), sarg(b == null ? '确定' : b));
    };
    bridgeApi.showPrompt = function(t,p,d,v){
        console.log('Sync Bridge: showPrompt called');
        if (arguments.length === 1) return nativeBridge.showPrompt(sarg(t), '');
        if (arguments.length === 2) return nativeBridge.showPrompt(sarg(t), sarg(p));
        if (arguments.length === 3) return nativeBridge.showPrompt(sarg(t), sarg(p), sarg(d));
        return nativeBridge.showPrompt(sarg(t), sarg(p), sarg(d == null ? '' : d), sarg(v == null ? '' : v));
    };
    bridgeApi.showSingleSelection = function(t,i,d){
        console.log('Sync Bridge: showSingleSelection called');
        if (arguments.length === 1) return nativeBridge.showSingleSelection(sarg(t), '[]');
        if (arguments.length === 2) return nativeBridge.showSingleSelection(sarg(t), sarg(i));
        return nativeBridge.showSingleSelection(sarg(t), sarg(i), d != null ? d : -1);
    };
    bridgeApi.showAlertAsync = function(t,c,b,id){ return nativeBridge.showAlertAsync(sarg(t),sarg(c),sarg(b),sarg(id)); };
    bridgeApi.showPromptAsync = function(t,p,d,v,id){ return nativeBridge.showPromptAsync(sarg(t),sarg(p),sarg(d),sarg(v),sarg(id)); };
    bridgeApi.showSingleSelectionAsync = function(t,i,d,id){ return nativeBridge.showSingleSelectionAsync(sarg(t),sarg(i),d!=null?d:-1,sarg(id)); };
    bridgeApi.saveImportedCourses = function(j,id){
        if (arguments.length > 1) return nativeBridge.saveImportedCourses(sarg(j),sarg(id));
        return nativeBridge.saveImportedCourses(sarg(j));
    };
    bridgeApi.saveCourseConfig = function(j,id){
        if (arguments.length > 1) return nativeBridge.saveCourseConfig(sarg(j),sarg(id));
        return nativeBridge.saveCourseConfig(sarg(j));
    };
    bridgeApi.savePresetTimeSlots = function(j,id){
        if (arguments.length > 1) return nativeBridge.savePresetTimeSlots(sarg(j),sarg(id));
        return nativeBridge.savePresetTimeSlots(sarg(j));
    };
    bridgeApi.postData = function(m){ return nativeBridge.postData(sarg(m)); };
    bridgeApi.reportError = function(e){ return nativeBridge.reportError(sarg(e)); };
    bridgeApi.notifyTaskCompleted = function(){ return nativeBridge.notifyTaskCompletion(); };
    bridgeApi.notifyTaskCompletion = function(){ return nativeBridge.notifyTaskCompletion(); };
    bridgeApi.postHtml = function(h){ return nativeBridge.postHtml(sarg(h)); };
    bridgeApi.closeWebView = function(){ return nativeBridge.closeWebView(); };
    bridgeApi.close = bridgeApi.closeWebView;

    window.shiguangBridge = bridgeApi;
    window.app = bridgeApi;
    window.AndroidBridge = bridgeApi;

    var promiseBridge = {
      showAlert:function(t,c,b){ return mkp(function(id){nativeBridge.showAlertAsync(sarg(t),sarg(c == null ? '' : c),sarg(b == null ? '确定' : b),id);});},
      showPrompt:function(t,p,d,v){ return mkp(function(id){nativeBridge.showPromptAsync(sarg(t),sarg(p),sarg(d == null ? '' : d),sarg(v == null ? '' : v),id);});},
      showSingleSelection:function(t,i,d){ return mkp(function(id){nativeBridge.showSingleSelectionAsync(sarg(t),sarg(i),d!=null?d:-1,id);});},
      saveImportedCourses:function(j){ return mkp(function(id){nativeBridge.saveImportedCourses(sarg(j),id);});},
      saveCourseConfig:function(j){ return mkp(function(id){nativeBridge.saveCourseConfig(sarg(j),id);});},
      savePresetTimeSlots:function(j){ return mkp(function(id){nativeBridge.savePresetTimeSlots(sarg(j),id);});},
      notifyTaskCompleted:function(){ return nativeBridge.notifyTaskCompletion(); },
      notifyTaskCompletion:function(){ return nativeBridge.notifyTaskCompletion(); }
    };
    window.shiguangBridgePromise = promiseBridge;
    window.AndroidBridgePromise = promiseBridge;

    console.log('Bridge Glue: Injected successfully (shiguangBridge primary, AndroidBridge compatible).');
  } catch(e) { console.error('Bridge Glue: Error during injection', e); }
})();
""".trimIndent()

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private val DESKTOP_SPOOF_JS = """
(function() {
  try {
    var override = function(target, key, value) {
      try {
        Object.defineProperty(target, key, { get: function(){ return value; }, configurable: true });
      } catch (_) {}
    };
    override(navigator, 'platform', 'Win32');
    // Keep touch capability so pinch-zoom still works in desktop mode.
    override(navigator, 'maxTouchPoints', Math.max(navigator.maxTouchPoints || 0, 5));
    var metas = document.querySelectorAll('meta[name="viewport"]');
    var viewportContent = 'width=1280, initial-scale=1.0, minimum-scale=0.25, maximum-scale=5.0, user-scalable=yes';
    metas.forEach(function(m){ m.setAttribute('content', viewportContent); });
    if (!metas.length) {
      var m = document.createElement('meta');
      m.name = 'viewport';
      m.content = viewportContent;
      document.head && document.head.appendChild(m);
    }
  } catch (e) {
    console.warn('desktop spoof failed', e);
  }
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
        if (src.has("startSemester")) mapped.put("startSemester", src.get("startSemester"))
        if (src.has("totalWeek")) mapped.put("totalWeek", src.get("totalWeek"))
        if (src.has("weekStart")) mapped.put("weekStart", src.get("weekStart"))
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
        fun normalizeSectionArray(src: JSONArray): JSONArray {
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
                            .put("section", number)
                            .put("startTime", start)
                            .put("endTime", end)
                    )
                }
            }
            return mapped
        }

        val raw = timeSlotsJson.trim()
        if (raw.startsWith("[")) {
            val mapped = normalizeSectionArray(JSONArray(raw))
            if (mapped.length() > 0) mapped.toString() else timeSlotsJson
        } else {
            val src = JSONObject(raw)
            val schedules = src.optJSONArray("schedules")
            if (schedules != null && schedules.length() > 0) {
                val mappedSchedules = JSONArray()
                for (i in 0 until schedules.length()) {
                    val schedule = schedules.optJSONObject(i) ?: continue
                    val sections = normalizeSectionArray(schedule.optJSONArray("sections") ?: JSONArray())
                    if (sections.length() == 0) continue
                    mappedSchedules.put(
                        JSONObject().apply {
                            put("scheduleType", schedule.optString("scheduleType"))
                            put("applicableBuildings", schedule.optJSONArray("applicableBuildings") ?: JSONArray())
                            put("note", schedule.optString("note"))
                            put("sections", sections)
                        }
                    )
                }
                if (mappedSchedules.length() > 0) {
                    JSONObject().put("schedules", mappedSchedules).toString()
                } else {
                    timeSlotsJson
                }
            } else {
                timeSlotsJson
            }
        }
    } catch (_: Exception) {
        timeSlotsJson
    }
}

private fun joinSectionsFromRange(start: Int, end: Int): String {
    if (start <= 0 || end <= 0) return ""
    val from = minOf(start, end)
    val to = maxOf(start, end)
    return (from..to).joinToString(",")
}

private fun assignPreviewSection(
    courseJson: JSONObject,
    isCustomTime: Boolean,
    fallbackSection: Int
): Pair<String, Boolean> {
    val sectionsArr = courseJson.optJSONArray("sections")
    if (sectionsArr != null && sectionsArr.length() > 0) {
        return buildString {
            for (j in 0 until sectionsArr.length()) {
                if (j > 0) append(",")
                append(sectionsArr.getInt(j))
            }
        } to true
    }

    val start = courseJson.optInt("startSection", -1)
    val end = courseJson.optInt("endSection", -1)
    if (start > 0 && end > 0) {
        return joinSectionsFromRange(start, end) to true
    }

    val sectionsText = courseJson.optString("sections", "").trim()
    if (sectionsText.isNotBlank()) {
        return sectionsText to !isCustomTime
    }

    return fallbackSection.toString() to false
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
    val intentUrl = intent.getStringExtra("url") ?: ""
    val intentTitle = intent.getStringExtra("title") ?: "导入课表"
    val intentScript = intent.getStringExtra("script") ?: ""
    val intentText = intent.getStringExtra("text") ?: ""
    val context = LocalContext.current

    var webViewLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var url by remember { mutableStateOf(intentUrl.ifBlank { HookEntry.prefs.getString("jw_webview_url", "") }) }
    var desktopMode by remember { mutableStateOf(HookEntry.prefs.getBoolean("jw_webview_desktop_mode", false)) }
    var lastLoadedUrl by remember { mutableStateOf(url) }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    val currentImportState by rememberUpdatedState(importState)
    var aiParsingInProgress by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    
    var canGoBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }
    val alertStateRef: MutableState<AlertPendingState?> = remember { mutableStateOf(null) }
    val promptStateRef: MutableState<PromptPendingState?> = remember { mutableStateOf(null) }
    val selectionStateRef: MutableState<SelectionPendingState?> = remember { mutableStateOf(null) }
    val promptInputRef: MutableState<String> = remember { mutableStateOf("") }

    /** Extract HTML and parse via AI mode. */
    fun processHtmlForAi(html: String) {
        aiParsingInProgress = true
        Log.d("WebViewScreen", "processHtmlForAi started, html length: ${html.length}")
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val prefs = context.writablePrefs()
                    val apiKey = prefs.getString("api_key", "") ?: ""
                    val modelName = prefs.getString("model_name", "qwen3-coder-plus") ?: "qwen3-coder-plus"
                    val apiUrl = prefs.getString("api_url", "https://dashscope.aliyuncs.com/compatible-mode/v1") ?: "https://dashscope.aliyuncs.com/compatible-mode/v1"

                    ApiClient.parseCoursesStreaming(
                        html, apiKey, modelName, apiUrl, ApiClient.SYSTEM_PROMPT,
                        object : ApiClient.ParseCallback {
                            override fun onUpdate(reasoning: String, content: String) {
                                coroutineScope.launch {
                                    importState = ImportState.Parsing("AI 正在解析中，请稍候...")
                                }
                            }
                            override fun onSuccess(result: ParseResult) {
                                coroutineScope.launch {
                                    try {
                                        context.openCoursePreviewScreen(
                                            courses = result.courses,
                                            schedule = result.schedule
                                        )
                                        importState = ImportState.Success("已进入预览，请确认导入")
                                    } catch (e: Exception) {
                                        importState = ImportState.Error(e.message ?: "导入失败")
                                    } finally {
                                        aiParsingInProgress = false
                                    }
                                }
                            }
                            override fun onError(e: Exception) {
                                coroutineScope.launch {
                                    aiParsingInProgress = false
                                    importState = ImportState.Error(e.message ?: "解析失败")
                                }
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                aiParsingInProgress = false
                importState = ImportState.Error(e.message ?: "操作异常")
            }
        }
    }

    val bridgeCallback = remember {
        object : BridgeCallback {
            override fun onShowAlert(data: AlertDialogData, latch: CountDownLatch, result: BooleanArray) {
                MessageDialog.build()
                    .setTitle(data.title)
                    .setMessage(data.content)
                    .setCancelable(false)
                    .setCancelButton("")
                    .setOkButton(data.confirmText.ifBlank { "确定" }, object : OnDialogButtonClickListener<MessageDialog> {
                        override fun onClick(dialog: MessageDialog, v: android.view.View): Boolean {
                            result[0] = true
                            latch.countDown()
                            return false
                        }
                    })
                    .show()
            }
            override fun onShowPrompt(data: PromptDialogData, latch: CountDownLatch, result: Array<String?>) {
                InputDialog.show(
                    data.title,
                    data.tip,
                    "确定",
                    "取消",
                    data.defaultText
                ).setCancelable(false)
                    .setOkButton(object : OnInputDialogButtonClickListener<InputDialog> {
                        override fun onClick(baseDialog: InputDialog, v: android.view.View, inputStr: String): Boolean {
                            result[0] = inputStr
                            latch.countDown()
                            return false
                        }
                    })
                    .setCancelButton(object : OnInputDialogButtonClickListener<InputDialog> {
                        override fun onClick(baseDialog: InputDialog, v: android.view.View, inputStr: String): Boolean {
                            result[0] = null
                            latch.countDown()
                            return false
                        }
                    })
                    .show()
            }
            override fun onShowSingleSelection(data: SingleSelectionDialogData, latch: CountDownLatch, result: IntArray) {
                val options = data.items.map { it as CharSequence }
                MessageMenu.show(data.title, options, object : OnMenuItemClickListener<MessageMenu> {
                    override fun onClick(dialog: MessageMenu, text: CharSequence, index: Int): Boolean {
                        result[0] = index
                        latch.countDown()
                        return false
                    }
                }).setCancelable(false)
                    .setSelection(data.defaultSelectedIndex)
                    .setCancelButton("取消", object : OnMenuButtonClickListener<MessageMenu> {
                        override fun onClick(dialog: MessageMenu, v: android.view.View): Boolean {
                            result[0] = -1
                            latch.countDown()
                            return false
                        }
                    })
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
                        var previewCustomSection = 1000
                        for (i in 0 until coursesArray.length()) {
                            val courseJson = coursesArray.getJSONObject(i)
                            val c = Course()
                            c.name = courseJson.optString("name", "").trim()
                            c.teacher = courseJson.optString("teacher", "").trim()
                            c.position = courseJson.optString("location", courseJson.optString("position", "")).trim()
                            c.day = courseJson.optInt("weekday", courseJson.optInt("day", 1))
                            
                            val isCustomTime = courseJson.optBoolean("isCustomTime", false)
                            val (previewSections, explicitSectionRange) = assignPreviewSection(
                                courseJson = courseJson,
                                isCustomTime = isCustomTime,
                                fallbackSection = previewCustomSection
                            )
                            c.sections = previewSections
                            c.isCustomTime = isCustomTime
                            c.customStartTime = courseJson.optString("customStartTime", "").trim()
                            c.customEndTime = courseJson.optString("customEndTime", "").trim()
                            c.hasExplicitSectionRange = explicitSectionRange
                            if (isCustomTime && !explicitSectionRange) {
                                previewCustomSection += 1
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

                        context.openCoursePreviewScreen(
                            courses = courses,
                            schedule = schedule
                        )
                        
                        withContext(Dispatchers.Main) {
                            importState = ImportState.Success("已进入预览，请确认导入")
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
                        val normalizedConfig = normalizeShiguangCourseConfig(configJson)
                        HostCompat.pendingCourseConfigJson = normalizedConfig
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
                        val normalizedTimeSlots = normalizeShiguangTimeSlots(timeSlotsJson)
                        HostCompat.pendingTimeSlotSectionsJson = normalizedTimeSlots
                        callback(true, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(false, e.message)
                    }
                }
            }
            override fun onTaskCompleted() {
                coroutineScope.launch {
                    if (!aiParsingInProgress && (currentImportState is ImportState.Loading || currentImportState is ImportState.Parsing)) {
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
                    TextButton(
                        text = if (desktopMode) "手机版" else "桌面版",
                        onClick = {
                            desktopMode = !desktopMode
                            context.writablePrefs().edit()
                                .putBoolean("jw_webview_desktop_mode", desktopMode)
                                .apply()
                            webViewRef?.settings?.userAgentString =
                                if (desktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(context)
                            webViewRef?.reload()
                        }
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .navigationBarsPadding()
        ) {
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
                    singleLine = true,
                    maxLines = 1,
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
                        WebView(ctx).apply {                            this.webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, urlValue: String, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, urlValue, favicon)
                                    webViewLoading = true
                                    canGoBack = view.canGoBack()
                                    lastLoadedUrl = urlValue
                                    url = urlValue
                                    Log.d("WebViewScreen", "onPageStarted: $urlValue")
                                    val isTransientUrl = urlValue == "about:blank"
                                    if (!aiParsingInProgress && currentImportState is ImportState.Loading && !isTransientUrl) {
                                        importState = ImportState.Idle
                                    }
                                }

                                override fun onPageFinished(view: WebView, urlValue: String) {
                                    super.onPageFinished(view, urlValue)
                                    webViewLoading = false
                                    canGoBack = view.canGoBack()
                                    url = urlValue
                                    Log.d("WebViewScreen", "onPageFinished: $urlValue")
                                    CookieManager.getInstance().flush()
                                    if (desktopMode) {
                                        view.evaluateJavascript(DESKTOP_SPOOF_JS, null)
                                    }
                                    view.evaluateJavascript(BRIDGE_GLUE_JS, null)
                                }

                                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                    super.onReceivedError(view, errorCode, description, failingUrl)
                                    Log.e("WebViewScreen", "WebView Error: code=$errorCode, description=$description, url=$failingUrl")
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.proceed()
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    val safeUrl = url ?: return false
                                    if (safeUrl.startsWith("http://") || safeUrl.startsWith("https://")) {
                                        return false
                                    }
                                    return try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(safeUrl))
                                        view?.context?.startActivity(intent)
                                        true
                                    } catch (e: Exception) {
                                        Log.e("WebViewScreen", "Failed to launch intent for URL: $safeUrl", e)
                                        true
                                    }
                                }
                            }

                            this.webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.let {
                                        Log.d("WebViewConsole", "[${it.messageLevel()}] ${it.message()}")
                                    }
                                    return true
                                }
                                override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                    val latch = CountDownLatch(1)
                                    val res = BooleanArray(1) { false }
                                    bridgeCallback.onShowAlert(AlertDialogData("来自网页的消息", message, "确定"), latch, res)
                                    Thread { latch.await(); view.post { result.confirm() } }.start()
                                    return true
                                }
                                override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                                    val latch = CountDownLatch(1)
                                    val res = BooleanArray(1) { false }
                                    bridgeCallback.onShowAlert(AlertDialogData("请确认", message, "确定"), latch, res)
                                    Thread { latch.await(); view.post { if (res[0]) result.confirm() else result.cancel() } }.start()
                                    return true
                                }
                                
                                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
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
                                userAgentString = if (desktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(context)
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                mixedContentMode = 0
                                textZoom = 100
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
                            // 拾光新脚本优先使用 shiguangBridge；旧适配继续兼容 AndroidBridge/app。
                            addJavascriptInterface(bridge, "shiguangBridge")
                            addJavascriptInterface(bridge, "AndroidBridge")
                            addJavascriptInterface(bridge, "app")

                            webViewRef = this
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = {
                    }
                )


                if (webViewLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
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
                            Text("✓ ${state.message}", color = MiuixTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        is ImportState.Error -> {
                            Text("✕ ${state.message}", color = MiuixTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        ImportState.Idle -> {}
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !webViewLoading && importState !is ImportState.Loading && importState !is ImportState.Parsing,
                        onClick = {
                            val currentWebView = webViewRef ?: run {
                                importState = ImportState.Error("WebView 未准备好，请稍后重试")
                                return@Button
                            }
                            HostCompat.pendingCourseConfigJson = null
                            HostCompat.pendingTimeSlotSectionsJson = null
                            importState = ImportState.Loading("脚本启动中...")
                            Log.d("WebViewScreen", "寮€濮嬬偣鍑昏В鏋? webViewRef is ${if(webViewRef == null) "NULL" else "NOT NULL"}")
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(10000)
                                if (importState is ImportState.Loading) {
                                    Log.w("WebViewScreen", "Redundant timeout guard triggered (10s), reset to Idle")
                                    importState = ImportState.Idle
                                }
                            }
                            if (intentScript.isNotBlank()) {
                                Log.d("WebViewScreen", "Inject and execute script, length: ${intentScript.length}")
                                currentWebView.evaluateJavascript(BRIDGE_GLUE_JS) {
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
                                                AndroidBridge.notifyTaskCompletion();
                                                return "Error";
                                            }
                                        })();
                                    """.trimIndent()
                                    currentWebView.evaluateJavascript(wrappedScript) { res ->
                                        Log.d("WebViewScreen", "Script evaluateJavascript finished, result: $res")
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(10000)
                                            if (importState is ImportState.Loading) {
                                                Log.w("WebViewScreen", "Script timeout (10s), still Loading, force reset to Idle")
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
                                            if (html.length > 2000000) { // > 2MB 鍒欓檷绾?
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
                                currentWebView.evaluateJavascript(extractionScript) { res ->
                                    Log.d("WebViewScreen", "AI extraction script injected, result: $res")
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectionStateRef.value = null
                                            selection.result[0] = index
                                            selection.latch.countDown()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item, modifier = Modifier.weight(1f))
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
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("取消") }
                        }
                    }
                }
            }
        }
    }
}



