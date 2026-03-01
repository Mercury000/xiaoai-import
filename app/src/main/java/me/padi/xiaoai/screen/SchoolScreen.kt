package me.padi.xiaoai.screen

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.ApiClient.ParseCallback
import me.padi.xiaoai.ParseResult
import me.padi.xiaoai.R
import me.padi.xiaoai.hook.MainHook.prefs
import org.json.JSONArray
import org.json.JSONObject
import top.sacz.xphelper.activity.BaseActivity
import top.sacz.xphelper.ext.toClass
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.LocalWindowDialogState
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

class SchoolScreen : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val jsonString = readRawFile(R.raw.school) ?: ""

        val schoolsArray = JSONArray(jsonString)
        val schoolList = mutableListOf<SchoolData>()

        for (i in 0 until schoolsArray.length()) {
            val school = schoolsArray.getJSONObject(i)
            val name = school.getString("name")
            val type = school.getString("type")
            val url = school.getString("url")
            val importType = school.getString("importType")
            val sortKey = school.getString("sortKey")

            schoolList.add(SchoolData(name, type, url, importType, sortKey))
        }

        setContent {
            MiuixTheme {
                SchoolListScreenContent(schoolList = schoolList)
            }
        }
    }
}

data class SchoolData(
    val name: String, val type: String, val url: String, val importType: String, val sortKey: String
)

fun Context.readRawFile(@RawRes resourceId: Int): String? {
    return try {
        resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 导入状态类
sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object Parsing : ImportState()
    data class Success(val message: String) : ImportState()
    data class Error(val message: String) : ImportState()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SchoolListScreenContent(schoolList: List<SchoolData>) {
    var showBottomSheet = remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    var currentSchoolName by remember { mutableStateOf("") }
    var tableName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val showErrorDialog = remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    val showResultDialog = remember { mutableStateOf(false) }
    val setErrorState: (String) -> Unit = { message ->
        importState = ImportState.Error(message)
        errorMessage = message
        showErrorDialog.value = true
    }

    val filteredSchoolList = remember(searchText, schoolList) {
        if (searchText.isBlank()) {
            schoolList
        } else {
            schoolList.filter { school ->
                school.name.contains(searchText, ignoreCase = true) || school.type.contains(
                    searchText, ignoreCase = true
                )
            }
        }
    }

    // 对过滤后的列表进行分组
    val groupedSchools = remember(filteredSchoolList) {
        filteredSchoolList.groupBy { it.sortKey }.toSortedMap()
    }
    var url by remember { mutableStateOf("") }
    val navigator = rememberWebViewNavigator()
    var webViewLoading by remember { mutableStateOf(false) }

    val webViewState = rememberWebViewState(url)
    val coroutineScope = rememberCoroutineScope()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "选择学校"
            )
        }) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            WindowDialog(
                title = "导入失败",
                summary = errorMessage,
                show = showErrorDialog,
                onDismissRequest = { showErrorDialog.value = false }) {
                val dismiss = LocalWindowDialogState.current
                TextButton(
                    text = "我知道了", onClick = {
                        dismiss.invoke()
                    }, modifier = Modifier.fillMaxWidth()
                )
            }

            WindowDialog(
                title = "导入结果",
                summary = resultMessage,
                show = showResultDialog,
                onDismissRequest = { showResultDialog.value = false }) {
                val dismiss = LocalWindowDialogState.current
                TextButton(
                    text = "我知道了", onClick = {
                        dismiss.invoke()
                    }, modifier = Modifier.fillMaxWidth()
                )
            }

            WindowBottomSheet(
                show = showBottomSheet, title = "导入课表", onDismissRequest = {
                    showBottomSheet.value = false
                    importState = ImportState.Idle // 关闭时重置状态
                    webViewLoading = false
                }) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 20.dp)
                ) {
                    // WebView区域 - 固定高度
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

                    // 状态显示区域 - 在WebView下方
                    when (val state = importState) {
                        is ImportState.Loading -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Text("正在解析课表...")
                                }
                            }
                        }

                        is ImportState.Parsing -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.size(12.dp))
                                    Text("正在上传课表...")
                                }
                            }
                        }

                        is ImportState.Success -> {}

                        is ImportState.Error -> {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "❌ ", color = MiuixTheme.colorScheme.error
                                        )
                                        Text(
                                            "导入失败", color = MiuixTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        state.message, fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        ImportState.Idle -> {}
                    }

                    TextField(
                        value = tableName, onValueChange = { tableName = it }, label = "课表名称"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 导入按钮
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        enabled = importState !is ImportState.Loading && importState !is ImportState.Parsing && !webViewLoading,
                        onClick = {
                            if (tableName.isBlank()) {
                                setErrorState("请先输入课表名称")
                                return@Button
                            }
                            importState = ImportState.Loading

                            val appId = "2882303761518539170"
                            val serviceToken = try {
                                "a.h.g.h".toClass().resolve().firstMethod {
                                    name = "getInstance"
                                    parameterCount = 0
                                }.invoke()?.asResolver()?.firstMethod {
                                    name = "getAccessToken"
                                }?.invoke<String>()
                            } catch (e: Exception) {
                                YLog.error("Failed to get service token: ${e.message}")
                                null
                            }

                            val deviceId = try {
                                "a.h.a.j.m".toClass().resolve().firstMethod {
                                    name = "getDeviceId"
                                }.invoke<String>()
                            } catch (e: Exception) {
                                YLog.error("Failed to get device id: ${e.message}")
                                null
                            }

                            if (serviceToken == null || deviceId == null) {
                                setErrorState("无法获取服务令牌或设备ID")
                                return@Button
                            }
                            webViewRef?.evaluateJavascript(
                                "document.documentElement.outerHTML"
                            ) { html ->
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ApiClient.parseCoursesStreaming(
                                                html,
                                                prefs.native().getString("api_key", ""),
                                                prefs.native().getString("model_name", ""),
                                                prefs.native().getString("api_url", ""),
                                                ApiClient.SYSTEM_PROMPT,
                                                object : ParseCallback {
                                                    override fun onUpdate(
                                                        reasoning: String, content: String
                                                    ) {
                                                        // 可以在这里更新解析进度
                                                    }

                                                    override fun onSuccess(result: ParseResult) {
                                                        coroutineScope.launch {
                                                            try {
                                                                importState = ImportState.Parsing

                                                                val ctid =
                                                                    withContext(Dispatchers.IO) {
                                                                        ApiClient.createTable(
                                                                            tableName.trim(),
                                                                            appId,
                                                                            serviceToken,
                                                                            deviceId
                                                                        )
                                                                    }

                                                                YLog.debug("Create table result: $ctid")

                                                                var successCount = 0
                                                                var failCount = 0
                                                                var errorMessages =
                                                                    mutableListOf<String>()

                                                                result.courses.forEachIndexed { index, course ->
                                                                    try {
                                                                        val uploadResult =
                                                                            withContext(Dispatchers.IO) {
                                                                                ApiClient.uploadCourse(
                                                                                    course,
                                                                                    ctid,
                                                                                    appId,
                                                                                    serviceToken,
                                                                                    deviceId
                                                                                )
                                                                            }
                                                                        YLog.debug("Upload result for course ${index + 1}: $uploadResult")
                                                                        successCount++
                                                                    } catch (e: Exception) {
                                                                        YLog.error("Upload failed for course ${index + 1}: ${e.message}")
                                                                        failCount++
                                                                        errorMessages.add("课程${index + 1}: ${e.message}")
                                                                    }
                                                                }

                                                                val message = buildString {
                                                                    append("成功导入 $successCount 门课程")
                                                                    if (failCount > 0) {
                                                                        append("，$failCount 门失败")
                                                                        if (errorMessages.isNotEmpty()) {
                                                                            append(
                                                                                "\n${
                                                                                    errorMessages.take(
                                                                                        3
                                                                                    ).joinToString(
                                                                                        "\n"
                                                                                    )
                                                                                }"
                                                                            )
                                                                            if (errorMessages.size > 3) {
                                                                                append("\n...等${errorMessages.size}条错误")
                                                                            }
                                                                        }
                                                                    }
                                                                }

                                                                if (successCount > 0) {
                                                                    importState =
                                                                        ImportState.Success(message)
                                                                    resultMessage = message
                                                                    showResultDialog.value = true
                                                                } else {
                                                                    setErrorState(
                                                                        "全部导入失败：${
                                                                            errorMessages.firstOrNull() ?: "未知错误"
                                                                        }"
                                                                    )
                                                                }

                                                            } catch (e: Exception) {
                                                                YLog.error("Upload failed: ${e.message}")
                                                                setErrorState("上传失败: ${e.message}")
                                                            }
                                                        }
                                                    }

                                                    override fun onError(e: Exception) {
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            YLog.error(e)
                                                            setErrorState("解析失败: ${e.message}")
                                                        }
                                                    }
                                                })
                                        }
                                    } catch (e: Exception) {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            YLog.error(e)
                                            setErrorState("处理失败: ${e.message}")
                                        }
                                    }
                                }

                            }
                        }) {
                        Text(
                            if (webViewLoading) "页面加载中..."
                            else "开始导入",
                            color = if (importState is ImportState.Loading || importState is ImportState.Parsing || webViewLoading) MiuixTheme.colorScheme.onPrimary.copy(
                                alpha = 0.5f
                            )
                            else MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 搜索框
            TextField(
                value = searchText, onValueChange = { searchText = it }, label = "检索学校"
            )

            // 学校列表
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                groupedSchools.forEach { (sortKey, schools) ->
                    item {
                        SmallTitle(
                            text = sortKey
                        )
                    }

                    items(schools) { school ->
                        SuperArrow(
                            title = school.name, onClick = {
                                currentSchoolName = school.name
                                url = school.url
                                val importType = (context as Activity).intent.getStringExtra("type")
                                YLog.debug(importType)
                                if (importType == "jiaowu") {
                                    val intent = Intent().apply {
                                        component = ComponentName(
                                            "com.xiaomi.aischedule",
                                            "com.xiaomi.aischedule.activity.ScheduleEducationalImportActivity"
                                        )
                                        val params = JSONObject().apply {
                                            put("url", school.url)
                                            put("title", "导入课程表")
                                            put("titleColor", "#0D84FF")
                                            put(
                                                "text",
                                                "请先在浏览器登录教务系统，定位到个人课程表页面后，点击一键导入"
                                            )
                                            put("textColor", "#0D84FF")
                                            put("buttonText", "一键导入")
                                            put("buttonTextColor", "#0D84FF")
                                            put("buttonColor", "#d1e8ff")
                                            put("backgroundColor", "#e7f3ff")
                                            put("script", "(async function() {alert('没适配');})()")
                                        }

                                        putExtra("EXTRA_PARAMS", params.toString())
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                    }
                                    context.startActivity(intent)

                                } else {
                                    showBottomSheet.value = true
                                    importState = ImportState.Idle
                                    webViewLoading = true
                                }
                            })
                    }
                }
            }
        }
    }
}
