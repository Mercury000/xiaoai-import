package me.padi.xiaoai.screen

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
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
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.WebContent
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import me.padi.xiaoai.click.queryScoreFormSchool
import me.padi.xiaoai.click.openContributorQQ
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.ParseResult
import me.padi.xiaoai.R
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
    val sortKey: String
)

enum class SchoolImportType {
    JW,
    COMMON
}

class SchoolScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val jsonString = readRawFile(R.raw.school) ?: ""
        val schoolsArray = JSONArray(jsonString)
        val schoolList = mutableListOf<SchoolData>()

        for (i in 0 until schoolsArray.length()) {
            val school = schoolsArray.getJSONObject(i)
            schoolList.add(SchoolData(
                school.getString("name"),
                school.getString("type"),
                school.getString("url"),
                school.getString("importType"),
                school.getString("sortKey")
            ))
        }

        setContent {
            MiuixTheme {
                SchoolListScreenContent(schoolList)
            }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SchoolListScreenContent(schoolList: List<SchoolData>) {
    var searchText by remember { mutableStateOf("") }
    val filteredSchoolList = remember(searchText, schoolList) {
        if (searchText.isBlank()) schoolList
        else schoolList.filter { it.name.contains(searchText, ignoreCase = true) }
    }

    val groupedSchools = remember(filteredSchoolList) {
        filteredSchoolList.groupBy { it.sortKey }.toSortedMap()
    }

    var selectedSchool by remember { mutableStateOf<SchoolData?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var tableName by remember { mutableStateOf("") }
    var importState by remember { mutableStateOf<ImportState>(ImportState.Idle) }
    var webViewLoading by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navigator = rememberWebViewNavigator()
    val webViewState = rememberWebViewState(url)

    Scaffold(
        topBar = { SmallTopAppBar(title = "选择学校") }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp)) {
            TextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = "搜素学校",
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                groupedSchools.forEach { (key, schools) ->
                    item { SmallTitle(text = key) }
                    items(schools) { school ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                selectedSchool = school
                                url = school.url
                                webViewState.content = WebContent.Url(school.url)
                                showBottomSheet = true
                            }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(school.name)
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
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        WebView(
                            state = webViewState,
                            modifier = Modifier.matchParentSize(),
                            navigator = navigator,
                            client = remember {
                                object : AccompanistWebViewClient() {
                                    override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                        webViewLoading = true
                                    }
                                    override fun onPageFinished(view: WebView, url: String?) {
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
                            importState = ImportState.Loading
                            webViewRef?.evaluateJavascript("document.documentElement.outerHTML") { html ->
                                coroutineScope.launch {
                                    try {
                                        val appId = HostCompat.getAppId()
                                        val serviceToken = HostCompat.getAccessToken()
                                        val deviceId = HostCompat.getDeviceId(context)
                                        if (serviceToken == null || deviceId == null) {
                                            importState = ImportState.Error("获取令牌失败")
                                            return@launch
                                        }

                                        withContext(Dispatchers.IO) {
                                            ApiClient.parseCoursesStreaming(
                                                html,
                                                HookEntry.prefs.getString("api_key", ""),
                                                HookEntry.prefs.getString("model_name", ""),
                                                HookEntry.prefs.getString("api_url", ""),
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
                                                                importState = ImportState.Success("导入成功")
                                                            } catch (e: Exception) {
                                                                importState = ImportState.Error(e.message ?: "导入报错")
                                                            }
                                                        }
                                                    }
                                                    override fun onError(e: Exception) {
                                                        importState = ImportState.Error(e.message ?: "解析报错")
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
                    
                    if (importState is ImportState.Error) {
                        Text((importState as ImportState.Error).message, color = MiuixTheme.colorScheme.error, fontSize = 12.sp)
                    } else if (importState is ImportState.Success) {
                        Text("✅ 导入成功", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
