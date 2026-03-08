package me.padi.xiaoai.click

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.kongzue.dialogx.dialogs.BottomMenu
import com.kongzue.dialogx.dialogs.InputDialog
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import com.kongzue.dialogx.util.InputInfo
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.ApiClient.COLOR_PRESETS
import me.padi.xiaoai.Course
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.get
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.screen.AiScreen
import me.padi.xiaoai.screen.SchoolScreen
import me.padi.xiaoai.screen.WebViewScreen
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.widget.Toast
import top.sacz.xphelper.ext.toClass
import java.io.IOException
import kotlin.math.abs

fun importCourseFormJw(context: Context) {
    BottomMenu.show(
        "指定学校导入", "拾光适配仓库", "星链课表Json导入", "通用教务系统导入", "AI解析导入"
    ).setTitle("提示").setMessage("选择一个导入方式")
        .setOnMenuItemClickListener { dialog, text, index ->
            when (text) {
                "AI解析导入" -> {
                    val intent = Intent(context, AiScreen::class.java)
                    intent.putExtra(
                        "proxy_target_activity",
                        context.proxyActivity()
                    )
                    context.startActivity(intent)
                }

                "星链课表Json导入" -> {
                    InputDialog(
                        "提示", "请输入星链课表分享的Json文本进行导入", "导入", "取消", ""
                    ).setInputInfo(
                        InputInfo().setMultipleLines(true).setMAX_LENGTH(Int.MAX_VALUE)
                    ).setCancelable(false).setOkButton { baseDialog, v, inputStr ->

                        if (inputStr.isBlank()) {
                            TipDialog.show("请输入Json内容")
                            return@setOkButton true
                        }

                        WaitDialog.show("正在导入，请稍候...")

                        Thread {
                            try {
                                val act = context as Activity
                                val json = JSONObject(inputStr)
                                val tableName = json.optString("name").trim()
                                val coursesArray = json.optJSONArray("courses")

                                if (tableName.isEmpty()) {
                                    act.runOnUiThread {
                                        WaitDialog.dismiss()
                                        TipDialog.show("课表名称不能为空")
                                    }
                                    return@Thread
                                }

                                if (coursesArray == null || coursesArray.length() == 0) {
                                    act.runOnUiThread {
                                        WaitDialog.dismiss()
                                        TipDialog.show("课程列表不能为空")
                                    }
                                    return@Thread
                                }

                                val appId = HostCompat.getAppId()
                                val serviceToken = HostCompat.getAccessToken()
                                val deviceId = HostCompat.getDeviceId(context)

                                if (serviceToken == null || deviceId == null) {
                                    act.runOnUiThread {
                                        WaitDialog.dismiss()
                                        TipDialog.show("无法获取服务令牌或设备ID")
                                    }
                                    return@Thread
                                }

                                val courses = mutableListOf<Course>()
                                for (i in 0 until coursesArray.length()) {
                                    val courseJson = coursesArray.getJSONObject(i)
                                    val c = Course()
                                    c.name = courseJson.optString("name", "").trim()
                                    c.teacher = courseJson.optString("teacher", "").trim()
                                    c.position = courseJson.optString("location", "").trim()
                                    c.day = courseJson.optInt("weekday", 1)
                                    val start = courseJson.optInt("startSection", 1)
                                    val end = courseJson.optInt("endSection", 2)
                                    c.sections = (start..end).joinToString(",")
                                    val weeksArray = courseJson.optJSONArray("weeks")
                                    c.weeks = if (weeksArray != null) {
                                        buildString {
                                            for (j in 0 until weeksArray.length()) {
                                                append(weeksArray.getInt(j))
                                                if (j != weeksArray.length() - 1) append(",")
                                            }
                                        }
                                    } else ""
                                    val colorIndex = if (c.name.isNotEmpty()) {
                                        abs(c.name.hashCode() % COLOR_PRESETS.size)
                                    } else {
                                        i % COLOR_PRESETS.size
                                    }
                                    c.style = COLOR_PRESETS[colorIndex]
                                    courses.add(c)
                                }

                                val tables = ApiClient.fetchTables(appId, serviceToken, deviceId)
                                val fromCtId = tables.firstOrNull { it.current == 1 }?.id ?: 0L

                                val ctid = ApiClient.createTable(tableName, appId, serviceToken, deviceId)
                                ApiClient.switchTable(fromCtId, ctid, appId, serviceToken, deviceId)

                                ApiClient.uploadCoursesAll(courses, ctid, appId, serviceToken, deviceId)

                                act.runOnUiThread {
                                    WaitDialog.dismiss()
                                    TipDialog.show("导入成功")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                val act = context as Activity
                                act.runOnUiThread {
                                    WaitDialog.dismiss()
                                    TipDialog.show("失败: ${e::class.java.simpleName}", WaitDialog.TYPE.ERROR)
                                }
                            }
                        }.start()
                        false
                    }.show()
                }

                "通用教务系统导入", "拾光适配仓库", "指定学校导入" -> {
                    val activity = context as Activity
                    val waitDialog = WaitDialog.show("加载中")
                    val baseUrl = when (text) {
                        "通用教务系统导入" -> "https://gitee.com/padi/aishedule/raw/master/system.json"
                        "拾光适配仓库" -> "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/main/index/root_index.yaml"
                        else -> "https://gitee.com/padi/aishedule/raw/master/school.json"
                    }

                    OkHttpClientManager.get(
                        url = baseUrl,
                        onSuccess = { response ->
                            waitDialog.doDismiss()
                            try {
                                val responseBody = response.body.string()
                                if (text == "拾光适配仓库") {
                                    handleShiguangImport(context, responseBody)
                                } else {
                                    handleCommonImport(context, text.toString(), responseBody)
                                }
                            } catch (e: Exception) {
                                YLog.debug("解析失败: ${e.message}")
                            }
                        },
                        onError = { e ->
                            waitDialog.doDismiss()
                            TipDialog.show(e.message, WaitDialog.TYPE.ERROR)
                        })
                }
            }
            false
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
            // 处理首行
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
    var valuePart = line.substring(colonIndex + 1).trim()
    
    // 去掉行尾注释
    if (valuePart.contains("#")) {
        valuePart = valuePart.substring(0, valuePart.indexOf("#")).trim()
    }
    
    // 去掉引号
    val value = if (valuePart.startsWith("\"") && valuePart.endsWith("\"")) {
        valuePart.substring(1, valuePart.length - 1)
    } else if (valuePart.startsWith("'") && valuePart.endsWith("'")) {
        valuePart.substring(1, valuePart.length - 1)
    } else {
        valuePart
    }
    
    return key to value
}

private fun handleShiguangImport(context: Context, responseBody: String) {
    val activity = context as Activity
    
    // 解析 YAML: 提取 schools 列表
    val schools = parseYamlList(responseBody).filter { it.containsKey("id") && it.containsKey("name") }
    if (schools.isEmpty()) {
        TipDialog.show("未找到有效的学校列表")
        return
    }

    val names = schools.map { it["name"] ?: "Unknown" }.toTypedArray()
    val folders = schools.map { it["resource_folder"] ?: "" }

    activity.runOnUiThread {
        BottomMenu.show(names).setTitle("拾光适配仓库").setMessage("请选择分类/学校")
            .setSingleSelection()
            .setOnMenuItemClickListener { firstDialog, name, index ->
                val folder = folders[index]
                val adaptersUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/main/resources/$folder/adapters.yaml"
                
                WaitDialog.show("加载适配器列表...")
                OkHttpClientManager.get(adaptersUrl, onSuccess = { resp ->
                    WaitDialog.dismiss()
                    val yamlContent = resp.body.string()
                    val adapters = parseYamlList(yamlContent).filter { it.containsKey("adapter_id") && it.containsKey("adapter_name") }
                    
                    if (adapters.isEmpty()) {
                        TipDialog.show("未找到有效的适配脚本")
                        return@get
                    }

                    activity.runOnUiThread {
                        if (adapters.size == 1) {
                            val a = adapters[0]
                            launchOfficialAdapter(context, folder, a["adapter_name"] ?: "", a["asset_js_path"] ?: "", a["import_url"] ?: "", a["description"] ?: "")
                        } else {
                            val subNames = adapters.map { it["adapter_name"] ?: "Unknown" }.toTypedArray()
                            BottomMenu.show(subNames).setTitle(name).setMessage("请选择具体功能")
                                .setSingleSelection()
                                .setOnMenuItemClickListener { secondDialog, subName, subIndex ->
                                    val a = adapters[subIndex]
                                    launchOfficialAdapter(context, folder, a["adapter_name"] ?: "", a["asset_js_path"] ?: "", a["import_url"] ?: "", a["description"] ?: "")
                                    false
                                }
                        }
                    }
                }, onError = { e ->
                    WaitDialog.dismiss()
                    TipDialog.show("列表下载失败: ${e.message}")
                })
                false
            }
    }
}

private fun launchOfficialAdapter(context: Context, folder: String, name: String, jsPath: String, url: String, desc: String) {
    val scriptUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse/raw/main/resources/$folder/$jsPath"
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

private fun handleCommonImport(context: Context, type: String, responseBody: String) {
    val activity = context as Activity
    val jsonArray = JSONArray(responseBody)
    val names = mutableListOf<String>()
    val urls = mutableListOf<String>()
    val typesOrIds = mutableListOf<String>()

    for (i in 0 until jsonArray.length()) {
        val obj = jsonArray.getJSONObject(i)
        names.add(obj.optString("name", ""))
        urls.add(obj.optString("url", ""))
        typesOrIds.add(obj.optString(if (type == "通用教务系统导入") "type" else "id", ""))
    }

    activity.runOnUiThread {
        BottomMenu.show(names.toTypedArray()).setTitle("提示")
            .setMessage("请选择目标进行导入")
            .setSingleSelection()
            .setOnMenuItemClickListener { dialog, text, index ->
                val targetUrl = urls[index]
                val targetTypeOrId = typesOrIds[index]
                val jsUrl = if (type == "通用教务系统导入") {
                    "https://gitee.com/padi/aishedule/raw/master/system/$targetTypeOrId.js"
                } else {
                    "https://gitee.com/padi/aishedule/raw/master/import/$targetTypeOrId.js"
                }

                WaitDialog.show("加载中")
                OkHttpClientManager.get(jsUrl, onSuccess = { resp ->
                    WaitDialog.dismiss()
                    val jsStr = resp.body.string()
                    launchImportActivity(context, targetUrl, "导入课程表", "请登录后点击一键导入", jsStr)
                }, onError = { e ->
                    WaitDialog.dismiss()
                    TipDialog.show("加载失败: ${e.message}")
                })
                false
            }
    }
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

fun queryScoreFormSchool(context: Context) {
    val activity = context as Activity
    val waitDialog = WaitDialog.show("加载中")
    OkHttpClientManager.get(
        url = "https://gitee.com/padi/aishedule/raw/master/score.json",
        onSuccess = { response ->
            waitDialog.doDismiss()
            try {
                val responseBody = response.body.string()
                val jsonArray = JSONArray(responseBody)
                val names = mutableListOf<String>()
                val urls = mutableListOf<String>()
                val types = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    names.add(obj.optString("name", ""))
                    urls.add(obj.optString("url", ""))
                    types.add(obj.optString("type", ""))
                }

                activity.runOnUiThread {
                    BottomMenu.show(names.toTypedArray()).setTitle("提示")
                        .setMessage("选择学校进行导入")
                        .setSingleSelection().setOnMenuItemClickListener { dialog, text, index ->
                            val targetUrl = urls[index]
                            val targetType = types[index]
                            val jsUrl = "https://gitee.com/padi/aishedule/raw/master/score/$targetType.js"
                            WaitDialog.show("加载中")
                            OkHttpClientManager.get(jsUrl, onSuccess = { resp ->
                                WaitDialog.dismiss()
                                val jsStr = resp.body.string()
                                launchImportActivity(context, targetUrl, "导入成绩", "请登录后点击导入成绩", jsStr)
                            }, onError = { e ->
                                WaitDialog.dismiss()
                                TipDialog.show("加载失败: ${e.message}")
                            })
                            false
                        }
                }
            } catch (e: Exception) {
                YLog.debug("解析失败: ${e.message}")
            }
        },
        onError = { e ->
            waitDialog.doDismiss()
            TipDialog.show(e.message, WaitDialog.TYPE.ERROR)
        })
}

data class SchoolData(val name: String, val id: Long, val status: String, val url: String)
data class ScoreData(val name: String, val type: String, val status: String, val url: String)
data class SystemData(val name: String, val type: String, val status: String)

fun openContributorQQ(context: Context, uin: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=$uin")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "未找到QQ", Toast.LENGTH_SHORT).show()
    }
}
