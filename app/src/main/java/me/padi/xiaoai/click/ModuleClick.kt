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
                                    c.sections = "$start,$end"
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

                                val ctid = ApiClient.createTable(tableName, appId, serviceToken, deviceId)
                                val tables = ApiClient.fetchTables(appId, serviceToken, deviceId)
                                val currentTable = tables.firstOrNull { it.current == 1 }

                                if (currentTable != null) {
                                    ApiClient.switchTable(currentTable.id, ctid, appId, serviceToken, deviceId)
                                } else {
                                    act.runOnUiThread { TipDialog.show("未找到当前课表") }
                                }

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
                        "拾光适配仓库" -> "https://gitee.com/padi/shiguang/raw/main/school.json"
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

private fun handleShiguangImport(context: Context, responseBody: String) {
    val activity = context as Activity
    val rootJson = JSONObject(responseBody)
    val schoolArray = rootJson.getJSONArray("3")
    val schoolIdList = mutableListOf<String>()
    val schoolNameList = mutableListOf<String>()
    val adapterInfoList = mutableListOf<JSONObject>()

    for (i in 0 until schoolArray.length()) {
        val item = schoolArray.getJSONObject(i)
        val itemId = item.optString("1", "")
        val itemName = item.optString("2", "")
        if (item.has("5-1")) {
            val adapterObj = item.get("5-1")
            if (adapterObj is JSONObject) {
                schoolIdList.add(itemId)
                schoolNameList.add(itemName)
                adapterInfoList.add(adapterObj)
            } else if (adapterObj is JSONArray && adapterObj.length() > 0) {
                schoolIdList.add(itemId)
                schoolNameList.add(itemName)
                adapterInfoList.add(adapterObj.getJSONObject(0))
            }
        } else if (item.has("5")) {
            val adapterObj = item.get("5")
            if (adapterObj is JSONObject) {
                schoolIdList.add(itemId)
                schoolNameList.add(itemName)
                adapterInfoList.add(adapterObj)
            }
        }
    }

    activity.runOnUiThread {
        BottomMenu.show(schoolNameList.toTypedArray()).setTitle("提示")
            .setMessage("选择你的学校进行导入")
            .setSingleSelection()
            .setOnMenuItemClickListener { dialog, text, index ->
                val schoolId = schoolIdList[index]
                val adapterObj = adapterInfoList[index]
                val adapterName = adapterObj.optString("2", "课程表导入")
                val fileName = adapterObj.optString("4", "")
                var schoolUrl = adapterObj.optString("5", "")
                val description = adapterObj.optString("6", "请先登录教务系统后再点击导入")
                val scriptUrl = "https://gitee.com/padi/shiguang/raw/main/resources/$schoolId/$fileName"

                WaitDialog.show("加载脚本...")
                OkHttpClientManager.get(scriptUrl, onSuccess = { resp ->
                    WaitDialog.dismiss()
                    val jsStr = resp.body.string()
                    launchImportActivity(context, schoolUrl, adapterName, description, jsStr)
                }, onError = { e ->
                    WaitDialog.dismiss()
                    TipDialog.show("下载失败: ${e.message}")
                })
                false
            }
    }
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
