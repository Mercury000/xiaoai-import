package me.padi.xiaoai.screen

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
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.get
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RawRes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import me.padi.xiaoai.click.openContributorQQ
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.writablePrefs
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.ParseResult
import me.padi.xiaoai.CourseRepository
import me.padi.xiaoai.R
import me.padi.xiaoai.ShiguangAdapterEntry
import me.padi.xiaoai.parseShiguangSchoolIndexPb
import org.json.JSONArray
import org.json.JSONObject
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
        private const val CACHE_KEY_TS   = "school_index_pb_ts"
        private const val CACHE_KEY_SOURCE = "school_index_pb_source"
        /** 缓存有效期 6 小时；强制刷新时忽略 */
        private const val CACHE_TTL_MS   = 6 * 60 * 60 * 1000L
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
     * 加载学校列表。
     * - 始终先以本地 school.json 作兜底。
     * - forceRefresh=false：优先读本地缓存（6h 内有效），缓存过期或不存在则拉远程。
     * - forceRefresh=true ：直接拉远程，跳过缓存检查（用户点击"刷新"按钮）。
     */
    private fun loadSchoolList(forceRefresh: Boolean = false) {
        // 1. 本地 JSON 兜底
        val localJson = readRawFile(R.raw.school) ?: ""
        parseAndPopulateList(localJson)

        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val sourceKey = HostCompat.getShiguangRepoUrl(this)

        // 2. 非强制刷新时尝试从缓存恢复
        if (!forceRefresh) {
            val cached = if (prefs.getString(CACHE_KEY_SOURCE, null) == sourceKey) {
                prefs.getString(CACHE_KEY_PB, null)
            } else null
            val ts     = prefs.getLong(CACHE_KEY_TS, 0L)
            if (!cached.isNullOrBlank()) {
                parseAndPopulatePb(android.util.Base64.decode(cached, android.util.Base64.DEFAULT))
                // 缓存仍新鲜 → 不发网络请求，直接返回
                if (System.currentTimeMillis() - ts < CACHE_TTL_MS) {
                    return
                }
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
                        .putLong(CACHE_KEY_TS, System.currentTimeMillis())
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

    private fun parseAndPopulateList(content: String) {
        try {
            val newList = mutableListOf<SchoolData>()
            if (content.contains("schools:") || content.contains("- id:")) {
                // 官方 YAML 规范
                val schools = parseYamlList(content).filter { it.containsKey("id") && it.containsKey("name") }
                for (school in schools) {
                    val schoolName = school["name"] ?: "Unknown"
                    val schoolId = school["id"] ?: ""
                    // 含"通用"字样或 GLOBAL_TOOLS 置顶到 # 分组
                    val schoolSortKey = if (schoolName.contains("通用") || schoolId == "GLOBAL_TOOLS") "#"
                                       else school["initial"] ?: "#"
                    newList.add(SchoolData(
                        name = schoolName,
                        type = "拾光适配",
                        url = school["resource_folder"] ?: "",
                        importType = "shiguang_official",
                        sortKey = schoolSortKey,
                        isPinned = (schoolSortKey == "#")
                    ))
                }
            } else {
                // 传统 JSON 规范
                val schoolsArray = if (content.startsWith("{")) {
                    JSONObject(content).optJSONArray("3") ?: return
                } else {
                    JSONArray(content)
                }
                for (i in 0 until schoolsArray.length()) {
                    val school = schoolsArray.getJSONObject(i)
                    if (school.has("2")) { // 数字键格式
                        newList.add(SchoolData(
                            school.optString("2", "Unknown"),
                            "拾光适配",
                            school.optString("1", ""),
                            "shiguang",
                            school.optString("3", "#")
                        ))
                    } else { // 标准键格式
                        newList.add(SchoolData(
                            school.optString("name", "Unknown"),
                            school.optString("type", ""),
                            school.optString("url", ""),
                            school.optString("importType", "wakeup"),
                            school.optString("sortKey", "#")
                        ))
                    }
                }
            }

            if (newList.isNotEmpty()) {
                schoolList.clear()
                // 先按 sortKey 再按 name 排序，保证 "#" 置顶分组内条目也有序
                schoolList.addAll(newList.sortedWith(compareBy({ it.sortKey }, { it.name })))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun readRawFile(@RawRes resId: Int): String? {
        return try {
            resources.openRawResource(resId).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }
}

private fun parseYamlList(content: String): List<Map<String, String>> {
    val items = mutableListOf<Map<String, String>>()
    var currentItem = mutableMapOf<String, String>()
    val lines = content.lines()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        if (trimmed.startsWith("- ")) {
            if (currentItem.isNotEmpty()) {
                items.add(currentItem)
                currentItem = mutableMapOf()
            }
            val pair = extractYamlPair(trimmed.substring(2))
            if (pair != null) currentItem[pair.first] = pair.second
        } else if (trimmed.contains(":")) {
            val pair = extractYamlPair(trimmed)
            if (pair != null) currentItem[pair.first] = pair.second
        }
    }
    if (currentItem.isNotEmpty()) items.add(currentItem)
    return items
}

private fun extractYamlPair(line: String): Pair<String, String>? {
    val colonIndex = line.indexOf(":")
    if (colonIndex == -1) return null
    val key = line.substring(0, colonIndex).trim()
    val valuePart = line.substring(colonIndex + 1).trim()
    // 引号内可能含 #（如 initial: "#"），必须先找闭合引号，不能直接 stripComment
    val value: String = if (valuePart.startsWith("\"") || valuePart.startsWith("'")) {
        val q = valuePart[0]
        val sb = StringBuilder()
        var i = 1
        var escaped = false
        while (i < valuePart.length) {
            val ch = valuePart[i]
            if (escaped) {
                sb.append(
                    when (ch) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '\\' -> '\\'
                        '"' -> '"'
                        '\'' -> '\''
                        else -> ch
                    }
                )
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == q) {
                break
            } else {
                sb.append(ch)
            }
            i++
        }
        sb.toString()
    } else {
        // 不带引号：截掉 # 注释
        val hashIdx = valuePart.indexOf('#')
        if (hashIdx >= 0) valuePart.substring(0, hashIdx).trim() else valuePart
    }
    return key to value
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

private fun launchImportActivity(context: Context, url: String, title: String, text: String, script: String) {
    val intent = Intent(context, WebViewScreen::class.java).apply {
        putExtra("url", url)
        putExtra("title", title)
        putExtra("script", "(async function () {${script}})();")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
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
    var tableName by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    var webViewLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<NativeWebView?>(null) }

    val context = LocalContext.current
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

                    TextField(value = tableName, onValueChange = { tableName = it }, label = "课表名称")

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !webViewLoading && importState !is ImportState.Loading && importState !is ImportState.Parsing,
                        onClick = {
                            if (tableName.isBlank()) {
                                Toast.makeText(context, "请输入名称", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            importState = ImportState.Loading()
                            webViewRef?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                // evaluateJavascript 返回的是 JSON 编码字符串（带外层引号和转义），需解码
                                val rawHtml = try { org.json.JSONArray("[$html]").getString(0) } catch (_: Exception) { html }
                                coroutineScope.launch {
                                    try {
                                        val appId = HostCompat.getAppId()
                                        val serviceToken = HostCompat.getAccessToken(context, HostCompat.hostLoader ?: context.classLoader)
                                        val deviceId = HostCompat.getDeviceId(context, HostCompat.hostLoader ?: context.classLoader)
                                        if (serviceToken.isNullOrBlank() || deviceId.isNullOrBlank()) {
                                            importState = ImportState.Error("获取令牌失败")
                                            return@launch
                                        }

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
                                                                 importState = ImportState.Parsing()
                                                                 CourseRepository.importCourses(
                                                                     context,
                                                                     appId,
                                                                     tableName.trim(),
                                                                     result.courses,
                                                                     result.schedule
                                                                 )
                                                                 importState = ImportState.Success("导入成功")
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
