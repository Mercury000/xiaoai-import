package me.padi.xiaoai.screen

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongzue.dialogx.dialogs.BottomMenu
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.get
import me.padi.xiaoai.parseYamlList
import me.padi.xiaoai.launchImportActivity
import me.padi.xiaoai.click.importCourseFormJw
import me.padi.xiaoai.click.openContributorQQ
import me.padi.xiaoai.click.queryScoreFormSchool
import me.padi.xiaoai.hook.HookEntry
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
    val isCommon: Boolean = false
)

class JwSystemScreen : BaseActivity() {
    private val allItems = mutableStateListOf<JwItem>()
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        fetchData()
        setContent {
            MiuixTheme {
                Scaffold(
                    topBar = {
                        SmallTopAppBar(
                            title = "教务系统导入"
                        )
                    }
                ) { paddingValues ->
                    JwSystemContent(paddingValues)
                }
            }
        }
    }

    private fun fetchData() {
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val systemDeferred = async { fetchCommon() }
                val schoolDeferred = async { fetchSchools() }
                val shiguangDeferred = async { fetchShiguang() }

                val items = mutableListOf<JwItem>()
                items.addAll(systemDeferred.await())
                items.addAll(schoolDeferred.await())
                items.addAll(shiguangDeferred.await())

                // Sorting: Common items (isCommon=true) first, then others alphabetically by name
                val sorted = items.sortedWith(
                    compareByDescending<JwItem> { it.isCommon }
                        .thenBy { it.name.lowercase(Locale.ROOT) }
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

    private suspend fun fetchCommon(): List<JwItem> {
        return try {
            val resp = OkHttpClientManager.getSync("https://gitee.com/padi/aishedule/raw/master/system.json")
            val arr = JSONArray(resp)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                JwItem(
                    name = obj.optString("name", "Unknown"),
                    type = JwType.COMMON,
                    url = obj.optString("url", ""),
                    extra = obj.optString("type", ""),
                    isCommon = true
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSchools(): List<JwItem> {
        return try {
            val resp = OkHttpClientManager.getSync("https://gitee.com/padi/aishedule/raw/master/school.json")
            val arr = JSONArray(resp)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                JwItem(
                    name = obj.optString("name", "Unknown"),
                    type = JwType.SCHOOL,
                    url = obj.optString("url", ""),
                    extra = obj.optString("id", ""),
                    isCommon = false
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchShiguang(): List<JwItem> {
        return try {
            val resp = OkHttpClientManager.getSync("https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/main/index/root_index.yaml")
            val list = parseYamlList(resp)
            list.mapNotNull { map ->
                val name = map["name"] ?: return@mapNotNull null
                val folder = map["resource_folder"] ?: return@mapNotNull null
                val isPinned = name == "通用工具" || name == "教务"
                JwItem(
                    name = name,
                    type = JwType.SHIGUANG,
                    extra = folder,
                    isCommon = isPinned
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Composable
    private fun JwSystemContent(paddingValues: PaddingValues) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredItems = remember(searchQuery, allItems.size) {
            if (searchQuery.isBlank()) allItems
            else allItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = "搜索学校或系统...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            if (isLoading && allItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredItems) { item ->
                        JwItemRow(item)
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
                            JwType.COMMON -> "系统型"
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
                    val jsStr = resp.body?.string() ?: ""
                    launchImportActivity(context, item.url, item.name, "请登录后点击一键导入", jsStr)
                }, onError = { e ->
                    WaitDialog.dismiss()
                    TipDialog.show("加载失败: ${e.message}")
                })
            }
            JwType.SHIGUANG -> {
                fetchShiguangAdapters(context, item.extra, item.name)
            }
        }
    }

    private fun fetchShiguangAdapters(context: Context, folder: String, categoryName: String) {
        val adaptersUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/main/resources/$folder/adapters.yaml"
        WaitDialog.show("加载适配器列表...")
        OkHttpClientManager.get(adaptersUrl, onSuccess = { resp ->
            WaitDialog.dismiss()
            val yamlContent = resp.body?.string() ?: ""
            val adapters = parseYamlList(yamlContent).filter { it.containsKey("adapter_id") && it.containsKey("adapter_name") }
            
            if (adapters.isEmpty()) {
                TipDialog.show("未找到有效的适配脚本")
                return@get
            }

            (context as Activity).runOnUiThread {
                val subNames = adapters.map { 
                    val name = it["adapter_name"] ?: "Unknown"
                    val contributor = it["contributor"]
                    if (!contributor.isNullOrBlank()) "$name ($contributor)" else name
                }.toTypedArray()
                BottomMenu.show(subNames).setTitle(categoryName).setMessage("请选择具体学校/功能")
                    .setSingleSelection()
                    .setOnMenuItemClickListener { _, _, subIndex ->
                        val a = adapters[subIndex]
                        launchShiguangAdapter(context, folder, a)
                        false
                    }
            }
        }, onError = { e ->
            WaitDialog.dismiss()
            TipDialog.show("列表下载失败: ${e.message}")
        })
    }

    private fun launchShiguangAdapter(context: Context, folder: String, adapterMap: Map<String, String>) {
        val name = adapterMap["adapter_name"] ?: ""
        val jsPath = adapterMap["asset_js_path"] ?: ""
        val url = adapterMap["import_url"] ?: ""
        val desc = adapterMap["description"] ?: ""
        
        val scriptUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/main/resources/$folder/$jsPath"
        WaitDialog.show("脚本下载中...")
        OkHttpClientManager.get(scriptUrl, onSuccess = { resp ->
            WaitDialog.dismiss()
            val jsStr = resp.body?.string() ?: ""
            launchImportActivity(context, url, name, desc.replace("\\n", "\n"), jsStr)
        }, onError = { e ->
            WaitDialog.dismiss()
            TipDialog.show("脚本下载失败: ${e.message}")
        })
    }
}
