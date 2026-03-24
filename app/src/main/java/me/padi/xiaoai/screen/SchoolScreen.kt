package com.mercury.xiaoaiimport.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView as NativeWebView
import com.kongzue.dialogx.dialogs.BottomMenu
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import com.mercury.xiaoaiimport.OkHttpClientManager
import com.mercury.xiaoaiimport.get
import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import com.mercury.xiaoaiimport.launchImportActivity
import com.mercury.xiaoaiimport.click.openContributorQQ
import com.mercury.xiaoaiimport.hook.HookEntry
import com.mercury.xiaoaiimport.writablePrefs
import com.mercury.xiaoaiimport.ApiClient
import com.mercury.xiaoaiimport.HostCompat
import com.mercury.xiaoaiimport.ParseResult
import com.mercury.xiaoaiimport.openCoursePreviewScreen
import com.mercury.xiaoaiimport.R
import com.mercury.xiaoaiimport.ShiguangAdapterEntry
import com.mercury.xiaoaiimport.parseShiguangSchoolIndexPb
import top.sacz.xphelper.activity.BaseActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SchoolData(
    val name: String,
    val type: String,
    val url: String,
    val importType: String,
    val sortKey: String,
    val isPinned: Boolean = false,
    val adapters: List<ShiguangAdapterEntry> = emptyList()
)

enum class SchoolImportType {
    JW,
    COMMON
}

class SchoolScreen : BaseActivity() {
    private val schoolList = mutableStateListOf<SchoolData>()
    private var isRefreshing by mutableStateOf(false)

    companion object {
        private const val PREF_NAME = "shiguang_cache"
        private const val CACHE_KEY_PB = "school_index_pb_base64"
        private const val CACHE_KEY_SOURCE = "school_index_pb_source"
        /** 缓存有效期 6 小时；强制刷新时忽略 */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        loadSchoolList(forceRefresh = false)

        setContent {
            MiuixTheme {
                SchoolListScreenContent(schoolList, isRefreshing) { loadSchoolList(forceRefresh = true) }
            }
        }
    }

    /**
     * ???????
     * `forceRefresh=false` ???????????????????????
     * `forceRefresh=true` ?????????????????
     */
    private fun loadSchoolList(forceRefresh: Boolean = false) {
        // 1. 本地 JSON 兜底
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val sourceKey = HostCompat.getShiguangRepoUrl(this)

        // 2. 非强制刷新时尝试从缓存恢复
        if (!forceRefresh) {
            val cached = if (prefs.getString(CACHE_KEY_SOURCE, null) == sourceKey) {
                prefs.getString(CACHE_KEY_PB, null)
            } else null
            if (!cached.isNullOrBlank()) {
                parseAndPopulatePb(android.util.Base64.decode(cached, android.util.Base64.DEFAULT))
                return
            }
        }

        // 3. 拉取远程，更新缓存并刷新 UI
        isRefreshing = true
        OkHttpClientManager.get(HostCompat.buildShiguangIndexRawUrl(this), onSuccess = { response ->
            try {
                val remoteBytes = response.body.bytes()
                if (remoteBytes.isNotEmpty()) {
                    // 持久化到缓存
                    prefs.edit()
                        .putString(CACHE_KEY_PB, android.util.Base64.encodeToString(remoteBytes, android.util.Base64.NO_WRAP))
                        .putString(CACHE_KEY_SOURCE, sourceKey)
                        .apply()
                    runOnUiThread { parseAndPopulatePb(remoteBytes) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                runOnUiThread { isRefreshing = false }
            }
        }, onError = { _ ->
            runOnUiThread { isRefreshing = false }
        })
    }

    private fun parseAndPopulatePb(bytes: ByteArray) {
        try {
            val newList = parseShiguangSchoolIndexPb(bytes).map { school ->
                val sortKey = if (school.name.contains("通用") || school.id == "GLOBAL_TOOLS") "#" else school.initial.ifBlank { "#" }
                SchoolData(
                    name = school.name,
                    type = "拾光适配",
                    url = school.resourceFolder,
                    importType = "shiguang_official",
                    sortKey = sortKey,
                    isPinned = sortKey == "#",
                    adapters = school.adapters
                )
            }
            schoolList.clear()
            schoolList.addAll(newList.sortedWith(compareBy<SchoolData> { it.sortKey }.thenBy { it.name }))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}


private fun startOfficialJsImport(context: Context, folder: String, name: String, jsPath: String, url: String, desc: String) {
    val scriptUrl = HostCompat.buildShiguangScriptRawUrl(context, "resources/$folder/$jsPath")
    WaitDialog.show("加载脚本...")
    OkHttpClientManager.get(scriptUrl, onSuccess = { resp ->
        WaitDialog.dismiss()
        val jsStr = resp.body.string()
        launchImportActivity(context, url, name, desc.replace("\\n", "\n"), jsStr)
    }, onError = { e ->
        WaitDialog.dismiss()
        TipDialog.show("脚本下载失败: ${e.message}")
    })
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SchoolListScreenContent(
    schoolList: List<SchoolData>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    // 必须用 derivedStateOf 而非 remember(key=schoolList)：
    // SnapshotStateList.equals(self) 永远返回 true，导致 remember 在 clear()+addAll() 后不重算
    val filteredSchoolList by remember(searchText) {
        derivedStateOf {
            if (searchText.isBlank()) schoolList
            else schoolList.filter { it.name.contains(searchText, ignoreCase = true) }
        }
    }

    val groupedSchools by remember {
        derivedStateOf {
            filteredSchoolList.groupBy { it.sortKey }.toSortedMap()
        }
    }

    var selectedSchool by remember { mutableStateOf<SchoolData?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    var webViewLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<NativeWebView?>(null) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val navigator = rememberWebViewNavigator()
    val webViewState = rememberWebViewState(url)

    Scaffold(
        topBar = { 
            SmallTopAppBar(
                title = "选择学校",
                actions = {
                    TextButton(
                        text = if (isRefreshing) "加载中..." else "刷新列表",
                        onClick = onRefresh,
                        enabled = !isRefreshing
                    )
                }
            ) 
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            if (isRefreshing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "搜素学校",
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                groupedSchools.forEach { (key, schools) ->
                    item { SmallTitle(text = if (key == "#") "★ 通用" else key) }
                    items(schools) { school ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                                if (school.importType == "shiguang_official") {
                                    val folder = school.url // 我们之前把 resource_folder 存到了 url
                                    val adapters = school.adapters
                                    if (adapters.isEmpty()) {
                                        TipDialog.show("暂无适配脚本")
                                        return@clickable
                                    }

                                    (context as SchoolScreen).runOnUiThread {
                                        val subNames: Array<String> = adapters.map {
                                            if (it.maintainer.isNotBlank()) "${it.adapterName}  (贡献者：${it.maintainer})" else it.adapterName
                                        }.toTypedArray()
                                        BottomMenu.show(subNames).setTitle(school.name).setMessage(if (adapters.size == 1) "点击开始导入" else "选择导入方式")
                                            .setOnMenuItemClickListener { _, _, subIndex ->
                                                val a = adapters[subIndex]
                                                val desc = buildString {
                                                    append(a.description)
                                                    if (a.maintainer.isNotBlank()) append("\\n\\n贡献者：${a.maintainer}")
                                                }
                                                startOfficialJsImport(context, folder, a.adapterName, a.assetJsPath, a.importUrl, desc)
                                                false
                                            }
                                    }
                                } else {
                                    selectedSchool = school
                                    url = school.url
                                    webViewState.content = WebContent.Url(school.url)
                                    showBottomSheet = true
                                }
                            }
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(school.name, modifier = Modifier.weight(1f))
                                    if (school.isPinned) {
                                        Text(
                                            text = "通用",
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier
                                                .background(MiuixTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(school.type, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

        if (showBottomSheet) {
            WindowBottomSheet(
                title = selectedSchool?.name ?: "导入",
                show = remember { mutableStateOf(showBottomSheet) },
                onDismissRequest = { showBottomSheet = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .navigationBarsPadding()
                        // 不对整列做 imePadding，避免 WebView 被键盘压缩
                ) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        WebView(
                            state = webViewState,
                            modifier = Modifier.matchParentSize(),
                            navigator = navigator,
                            client = remember {
                                object : AccompanistWebViewClient() {
                                    override fun onPageStarted(view: NativeWebView, url: String?, favicon: Bitmap?) {
                                        webViewLoading = true
                                    }
                                    override fun onPageFinished(view: NativeWebView, url: String?) {
                                        webViewLoading = false
                                        CookieManager.getInstance().flush()
                                    }
                                }
                            },
                            onCreated = { webView ->
                                webViewRef = webView
                                webView.settings.javaScriptEnabled = true
                                webView.settings.domStorageEnabled = true
                            }
                        )
                        if (webViewLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                        }
                    }

                    // 底部控件区：仅此区域随键盘上移，WebView 保持固定不动
                    Column(modifier = Modifier.fillMaxWidth().imePadding()) {

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !webViewLoading && importState !is ImportState.Loading && importState !is ImportState.Parsing,
                        onClick = {
                            importState = ImportState.Loading()
                            webViewRef?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                // evaluateJavascript 返回的是 JSON 编码字符串（带外层引号和转义），需解码
                                val rawHtml = try { org.json.JSONArray("[$html]").getString(0) } catch (_: Exception) { html }
                                coroutineScope.launch {
                                    try {
                                        val prefs = context.writablePrefs()
                                        val apiKey = prefs.getString("api_key", "") ?: ""
                                        val modelName = prefs.getString("model_name", "qwen3-coder-plus") ?: "qwen3-coder-plus"
                                        val apiUrl = prefs.getString("api_url", "https://dashscope.aliyuncs.com/compatible-mode/v1") ?: "https://dashscope.aliyuncs.com/compatible-mode/v1"

                                        withContext(Dispatchers.IO) {
                                            ApiClient.parseCoursesStreaming(
                                                rawHtml,
                                                apiKey,
                                                modelName,
                                                apiUrl,
                                                ApiClient.SYSTEM_PROMPT,
                                                object : ApiClient.ParseCallback {
                                                    override fun onUpdate(reasoning: String, content: String) {}
                                                    override fun onSuccess(result: ParseResult) {
                                                        coroutineScope.launch {
                                                            try {
                                                                 context.openCoursePreviewScreen(
                                                                     courses = result.courses,
                                                                     schedule = result.schedule
                                                                 )
                                                                 importState = ImportState.Success("已进入预览，请确认导入")
                                                             } catch (e: Exception) {
                                                                importState = ImportState.Error(e.message ?: "导入报错")
                                                             }
                                                        }
                                                    }
                                                    override fun onError(e: Exception) {
                                                        coroutineScope.launch {
                                                            importState = ImportState.Error(e.message ?: "解析报错")
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    } catch (e: Exception) {
                                        importState = ImportState.Error(e.message ?: "未知错误")
                                    }
                                }
                            }
                        }
                    ) {
                        Text("提取并导入")
                    }
                    
                    when (importState) {
                        is ImportState.Loading -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("AI 正在解析课表，请稍候...", fontSize = 12.sp)
                        }
                        is ImportState.Parsing -> {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("正在创建并导入课程数据...", fontSize = 12.sp)
                        }
                        is ImportState.Error -> Text((importState as ImportState.Error).message, color = MiuixTheme.colorScheme.error, fontSize = 12.sp)
                        is ImportState.Success -> Text("✅ 导入成功", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                        else -> {}
                    }
                    } // end 底部控件 Column (imePadding)
                }
            }
        }
    }
}
