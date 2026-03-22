package me.padi.xiaoai.screen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.icu.text.Transliterator
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.ShiguangAdapterEntry
import me.padi.xiaoai.get
import me.padi.xiaoai.launchImportActivity
import me.padi.xiaoai.parseShiguangSchoolIndexPb
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

enum class JwType {
    COMMON, SCHOOL, SHIGUANG
}

data class JwItem(
    val name: String,
    val type: JwType,
    val url: String = "",
    val extra: String = "", // id for SCHOOL/COMMON, resource_folder for SHIGUANG
    val isCommon: Boolean = false,
    val sortKey: String = "#",
    val adapters: List<ShiguangAdapterEntry> = emptyList()
)

class JwSystemScreen : BaseActivity() {
    private val allItems = mutableStateListOf<JwItem>()
    private var isLoading by mutableStateOf(false)
    // 适配器选择面板状态
    private var showAdaptersSheet by mutableStateOf(false)
    private val sheetAdapters = mutableStateListOf<ShiguangAdapterEntry>()
    private var sheetFolder by mutableStateOf("")
    private var sheetCategory by mutableStateOf("")

    companion object {
        private const val PREF_NAME    = "jw_cache"
        private const val KEY_COMMON   = "cache_common"
        private const val KEY_SCHOOLS  = "cache_schools"
        private const val KEY_SHIGUANG = "cache_shiguang_pb_base64"
        private const val KEY_SHIGUANG_SOURCE = "cache_shiguang_source"
        private const val URL_COMMON   = "https://gitee.com/padi/aishedule/raw/master/system.json"
        private const val URL_SCHOOLS  = "https://gitee.com/padi/aishedule/raw/master/school.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fetchData(forceRefresh = false)
        setContent {
            MiuixTheme {
                // 外层 Box 让遮罩能覆盖整屏（含 TopAppBar）
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = {
                            SmallTopAppBar(title = "教务系统导入")
                        }
                    ) { paddingValues ->
                        JwSystemContent(paddingValues)
                    }
                    // 遮罩层：覆盖整屏含标题栏
                    if (showAdaptersSheet) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable { showAdaptersSheet = false }
                        )
                    }
                    // 底部适配器面板
                    AnimatedVisibility(
                        visible = showAdaptersSheet,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MiuixTheme.colorScheme.surface,
                                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                                )
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(top = 8.dp, bottom = 4.dp)
                                    .size(width = 40.dp, height = 4.dp)
                                    .background(
                                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            Text(
                                text = sheetCategory,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .padding(bottom = 8.dp)
                            ) {
                                items(sheetAdapters) { adapter ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                showAdaptersSheet = false
                                                launchShiguangAdapter(this@JwSystemScreen, sheetFolder, adapter)
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                            Text(adapter.adapterName.ifBlank { "Unknown" }, fontSize = 16.sp)
                                            val maintainer = adapter.maintainer
                                            if (!maintainer.isNullOrBlank()) {
                                                Text(
                                                    text = "贡献者：$maintainer",
                                                    fontSize = 12.sp,
                                                    color = MiuixTheme.colorScheme.primary
                                                )
                                            }
                                            val desc = adapter.description
                                            if (!desc.isNullOrBlank()) {
                                                Text(
                                                    text = desc.replace("\\n", "\n"),
                                                    fontSize = 11.sp,
                                                    color = MiuixTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    internal fun fetchData(forceRefresh: Boolean = false) {
        isLoading = true
        val prefs: SharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val commonDeferred   = async { fetchCommon(prefs, forceRefresh) }
                val schoolsDeferred  = async { fetchSchools(prefs, forceRefresh) }
                val shiguangDeferred = async { fetchShiguang(prefs, forceRefresh) }

                val items = mutableListOf<JwItem>()
                items.addAll(commonDeferred.await())
                items.addAll(schoolsDeferred.await())
                items.addAll(shiguangDeferred.await())

                // 按 sortKey 排序（# 最前），同组内按名称排
                val sorted = items.sortedWith(
                    compareBy(
                        { if (it.sortKey == "#") "\u0000" else it.sortKey.lowercase(Locale.ROOT) },
                        { it.name }
                    )
                )

                withContext(Dispatchers.Main) {
                    allItems.clear()
                    allItems.addAll(sorted)
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    TipDialog.show("加载失败: ${e.message}")
                }
            }
        }
    }

    /** 从缓存或远程获取 common 系统列表 */
    private suspend fun fetchCommon(prefs: SharedPreferences, forceRefresh: Boolean): List<JwItem> {
        return try {
            val raw = if (!forceRefresh) prefs.getString(KEY_COMMON, null) else null
            val json = if (!raw.isNullOrBlank()) raw else {
                val fetched = OkHttpClientManager.getSync(URL_COMMON)
                prefs.edit().putString(KEY_COMMON, fetched).apply()
                fetched
            }
            val arr = JSONArray(json)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                JwItem(
                    name  = obj.optString("name", "Unknown"),
                    type  = JwType.COMMON,
                    url   = obj.optString("url", ""),
                    extra = obj.optString("type", ""),
                    isCommon = true,
                    sortKey = "#"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 从缓存或远程获取学校列表 */
    private suspend fun fetchSchools(prefs: SharedPreferences, forceRefresh: Boolean): List<JwItem> {
        return try {
            val raw = if (!forceRefresh) prefs.getString(KEY_SCHOOLS, null) else null
            val json = if (!raw.isNullOrBlank()) raw else {
                val fetched = OkHttpClientManager.getSync(URL_SCHOOLS)
                prefs.edit().putString(KEY_SCHOOLS, fetched).apply()
                fetched
            }
            val arr = JSONArray(json)
            val trans = try {
                Transliterator.getInstance("Han-Latin; NFD; [:Nonspacing Mark:] Remove; NFC")
            } catch (e: Exception) { null }
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name", "Unknown")
                val sortKey = run {
                    val firstCjk = name.firstOrNull { it.code in 0x4E00..0x9FFF }
                    if (firstCjk != null && trans != null) {
                        val latin = trans.transliterate(firstCjk.toString())
                        latin.firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "#"
                    } else "#"
                }
                JwItem(
                    name  = name,
                    type  = JwType.SCHOOL,
                    url   = obj.optString("url", ""),
                    extra = obj.optString("id", ""),
                    isCommon = false,
                    sortKey = sortKey
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 从缓存或远程获取拾光仓库列表 */
    private suspend fun fetchShiguang(prefs: SharedPreferences, forceRefresh: Boolean): List<JwItem> {
        return try {
            val sourceKey = HostCompat.getShiguangRepoUrl(this) + "|" + HostCompat.getShiguangRepoBranch(this)
            val raw = if (!forceRefresh && prefs.getString(KEY_SHIGUANG_SOURCE, null) == sourceKey) {
                prefs.getString(KEY_SHIGUANG, null)
            } else null
            val bytes = if (!raw.isNullOrBlank()) {
                android.util.Base64.decode(raw, android.util.Base64.DEFAULT)
            } else {
                val fetched = OkHttpClientManager.getBytesSync(HostCompat.buildShiguangRawUrl(this, "school_index.pb"))
                prefs.edit()
                    .putString(KEY_SHIGUANG, android.util.Base64.encodeToString(fetched, android.util.Base64.NO_WRAP))
                    .putString(KEY_SHIGUANG_SOURCE, sourceKey)
                    .apply()
                fetched
            }
            parseShiguangSchoolIndexPb(bytes).map { school ->
                val isCommon = school.name.contains("通用") || school.id == "GLOBAL_TOOLS"
                JwItem(
                    name  = school.name,
                    type  = JwType.SHIGUANG,
                    extra = school.resourceFolder,
                    isCommon = isCommon,
                    sortKey = if (isCommon) "#" else school.initial.ifBlank { "#" },
                    adapters = school.adapters
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Composable
    private fun JwSystemContent(paddingValues: PaddingValues) {
        val context = LocalContext.current
        var searchQuery by remember { mutableStateOf("") }
        // 按 sortKey 分组，# 组排最前
        val groupedItems by remember(searchQuery) {
            derivedStateOf {
                val filtered = if (searchQuery.isBlank()) allItems
                    else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
                filtered.groupBy { it.sortKey }
                    .toSortedMap(compareBy { if (it == "#") "\u0000" else it })
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索学校或系统...",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { (context as JwSystemScreen).fetchData(forceRefresh = true) },
                    enabled = !isLoading
                ) {
                    Text(if (isLoading) "加载中" else "刷新")
                }
            }

            if (isLoading && allItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groupedItems.forEach { (key, groupItems) ->
                        item { SmallTitle(text = if (key == "#") "通用教务" else key) }
                        items(groupItems) { jwItem ->
                            JwItemRow(jwItem)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun JwItemRow(item: JwItem) {
        val context = LocalContext.current
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable {
                    handleItemClick(context, item)
                }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.name, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isCommon) {
                            Text(
                                text = "通用支持",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        val typeLabel = when(item.type) {
                            JwType.COMMON -> "自有仓库"
                            JwType.SCHOOL -> "自有仓库"
                            JwType.SHIGUANG -> "拾光仓库"
                        }
                        Text(text = typeLabel, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurface)
                    }
                }
                // Removed SuperArrow due to compilation error
            }
        }
    }

    private fun handleItemClick(context: Context, item: JwItem) {
        when (item.type) {
            JwType.COMMON, JwType.SCHOOL -> {
                val jsUrl = if (item.type == JwType.COMMON) {
                    "https://gitee.com/padi/aishedule/raw/master/system/${item.extra}.js"
                } else {
                    "https://gitee.com/padi/aishedule/raw/master/import/${item.extra}.js"
                }

                WaitDialog.show("加载适配脚本...")
                OkHttpClientManager.get(jsUrl, onSuccess = { resp ->
                    WaitDialog.dismiss()
                    val jsStr = resp.body.string()
                    launchImportActivity(context, item.url, item.name, "请登录后点击一键导入", jsStr)
                }, onError = { e ->
                    WaitDialog.dismiss()
                    TipDialog.show("加载失败: ${e.message}")
                })
            }
            JwType.SHIGUANG -> {
                if (item.adapters.isEmpty()) {
                    TipDialog.show("未找到有效的适配脚本")
                } else {
                    sheetAdapters.clear()
                    sheetAdapters.addAll(item.adapters)
                    sheetFolder = item.extra
                    sheetCategory = item.name
                    showAdaptersSheet = true
                }
            }
        }
    }

    private fun launchShiguangAdapter(context: Context, folder: String, adapter: ShiguangAdapterEntry) {
        val scriptUrl = HostCompat.buildShiguangRawUrl(context, "resources/$folder/${adapter.assetJsPath}")
        WaitDialog.show("脚本下载中...")
        OkHttpClientManager.get(scriptUrl, onSuccess = { resp ->
            WaitDialog.dismiss()
            val jsStr = resp.body.string()
            launchImportActivity(context, adapter.importUrl, adapter.adapterName, adapter.description.replace("\\n", "\n"), jsStr)
        }, onError = { e ->
            WaitDialog.dismiss()
            TipDialog.show("脚本下载失败: ${e.message}")
        })
    }
}
