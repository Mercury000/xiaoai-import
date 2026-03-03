package me.padi.xiaoai.click

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
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
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.get
import me.padi.xiaoai.hook.MainHook.prefs
import me.padi.xiaoai.screen.AiScreen
import org.json.JSONArray
import org.json.JSONObject
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
                        "com.xiaomi.aischedule.activity.DeleteAccountActivity"
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
                                // ========= 1. 解析JSON =========
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

                                val appId = "2882303761518539170"

                                // ========= 2. 获取Token =========
                                val serviceToken = try {
                                    "a.h.g.h".toClass().resolve().firstMethod {
                                        name = "getInstance"
                                        parameterCount = 0
                                    }.invoke()?.asResolver()?.firstMethod {
                                        name = "getAccessToken"
                                    }?.invoke<String>()
                                } catch (e: Exception) {
                                    null
                                }

                                val deviceId = try {
                                    "a.h.a.j.m".toClass().resolve().firstMethod {
                                        name = "getDeviceId"
                                    }.invoke<String>()
                                } catch (e: Exception) {
                                    null
                                }

                                if (serviceToken == null || deviceId == null) {
                                    act.runOnUiThread {
                                        WaitDialog.dismiss()
                                        TipDialog.show("无法获取服务令牌或设备ID")
                                    }
                                    return@Thread
                                }

                                // ========= 3. 解析课程 =========
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

                                val ctid = ApiClient.createTable(
                                    tableName, appId, serviceToken, deviceId
                                )


                                val tables = ApiClient.fetchTables(appId, serviceToken, deviceId)

                                val currentTable = tables.firstOrNull { it.current == 1 }

                                if (currentTable != null) {
                                    val fromCid = currentTable.id
                                    // 切换到当前课表（如果还没切换的话）
                                    ApiClient.switchTable(
                                        fromCid, ctid, appId, serviceToken, deviceId
                                    )

                                } else {
                                    TipDialog.show("未找到当前课表")
                                }

                                // ========= 5. 上传课程（网络） =========
                                ApiClient.uploadCoursesAll(
                                    courses, ctid, appId, serviceToken, deviceId
                                )

                                act.runOnUiThread {
                                    WaitDialog.dismiss()
                                    TipDialog.show("导入成功")
                                }

                            } catch (e: Exception) {

                                e.printStackTrace()
                                val act = context as Activity

                                act.runOnUiThread {
                                    WaitDialog.dismiss()
                                    TipDialog.show(
                                        "失败: ${e::class.java.simpleName}", WaitDialog.TYPE.ERROR
                                    )
                                }
                            }

                        }.start()

                        false
                    }.show()
                }

                "通用教务系统导入" -> {
                    val activity = context as Activity
                    val waitDialog = WaitDialog.show("加载中");
                    OkHttpClientManager.get(
                        url = "https://gitee.com/padi/aishedule/raw/master/system.json",
                        onSuccess = { response ->
                            waitDialog.doDismiss()
                            try {
                                val responseBody = response.body.string()
                                val jsonArray = JSONArray(responseBody)
                                val systemList = mutableListOf<SystemData>()
                                for (i in 0 until jsonArray.length()) {
                                    val jsonObject = jsonArray.getJSONObject(
                                        i
                                    )
                                    val type = jsonObject.optString(
                                        "type", ""
                                    )
                                    val name = jsonObject.optString(
                                        "name", ""
                                    )
                                    val status = jsonObject.optString(
                                        "status", ""
                                    )
                                    systemList.add(
                                        SystemData(
                                            name = name, type = type, status = status
                                        )
                                    )
                                }
                                InputDialog(
                                    "提示",
                                    "请输入教务系统链接",
                                    "确定",
                                    "取消",
                                    prefs.native().getString("jw_webview_url", "")
                                ).setCancelable(false).setOkButton { baseDialog, v, inputStr ->
                                    prefs.native().edit {
                                        putString("jw_webview_url", inputStr)
                                    }
                                    activity.runOnUiThread {
                                        val systems = systemList.map { it.name }.toTypedArray()
                                        BottomMenu.show(systems).setTitle("提示")
                                            .setMessage("选择你的教务系统进行导入")
                                            .setSingleSelection()
                                            .setOnMenuItemClickListener { dialog, text, index ->
                                                val system = systemList[index]
                                                val waitDialog = WaitDialog.show(
                                                    "加载中"
                                                );
                                                OkHttpClientManager.get(
                                                    url = "https://gitee.com/padi/aishedule/raw/master/system/${system.type}.js",
                                                    onSuccess = { response ->
                                                        waitDialog.doDismiss()
                                                        val jsStr = response.body.string()
                                                        val intent = Intent().apply {
                                                            component = ComponentName(
                                                                "com.xiaomi.aischedule",
                                                                "com.xiaomi.aischedule.activity.ScheduleEducationalImportActivity"
                                                            )
                                                            val params = JSONObject().apply {
                                                                put(
                                                                    "url", inputStr
                                                                )
                                                                put(
                                                                    "title", "导入课程表"
                                                                )
                                                                put(
                                                                    "titleColor", "#0D84FF"
                                                                )
                                                                put(
                                                                    "text",
                                                                    "请先在浏览器登录教务系统，定位到个人课程表页面后，点击一键导入"
                                                                )
                                                                put(
                                                                    "textColor", "#0D84FF"
                                                                )
                                                                put(
                                                                    "buttonText", "一键导入"
                                                                )
                                                                put(
                                                                    "buttonTextColor", "#0D84FF"
                                                                )
                                                                put(
                                                                    "buttonColor", "#d1e8ff"
                                                                )
                                                                put(
                                                                    "backgroundColor", "#e7f3ff"
                                                                )
                                                                put(
                                                                    "script", jsStr
                                                                )
                                                            }

                                                            putExtra(
                                                                "EXTRA_PARAMS", params.toString()
                                                            )
                                                            addFlags(
                                                                Intent.FLAG_ACTIVITY_NEW_TASK
                                                            )

                                                        }
                                                        context.startActivity(
                                                            intent
                                                        )
                                                    },
                                                    onError = { e ->
                                                        waitDialog.doDismiss()
                                                        TipDialog.show(
                                                            e.message, WaitDialog.TYPE.ERROR
                                                        );
                                                    })
                                                false
                                            }
                                    }



                                    false
                                }.show()


                            } catch (e: IOException) {
                                YLog.debug("读取响应失败: ${e.message}")
                            }
                        },
                        onError = { e ->
                            waitDialog.doDismiss()
                            TipDialog.show(
                                e.message, WaitDialog.TYPE.ERROR
                            );
                            YLog.debug("GET 失败: ${e.message}")
                        })
                }

                "拾光适配仓库" -> {
                    val activity = context as Activity
                    val waitDialog = WaitDialog.show("加载中");

                    OkHttpClientManager.get(
                        url = "https://gitee.com/padi/shiguang/raw/main/school.json",
                        onSuccess = { response ->
                            waitDialog.doDismiss()
                            try {
                                val responseBody = response.body.string()
                                val rootJson = JSONObject(responseBody)

                                // 学校数据在 "3" 这个数组里！
                                val schoolArray = rootJson.getJSONArray("3")

                                // 存储学校信息的列表
                                val schoolIdList = mutableListOf<String>()      // 学校ID，如 "CQU"
                                val schoolNameList = mutableListOf<String>()    // 学校名称，如 "重庆大学"
                                val adapterInfoList = mutableListOf<JSONObject>() // 适配器信息

                                // 遍历数组中的每个学校/工具
                                for (i in 0 until schoolArray.length()) {
                                    val item = schoolArray.getJSONObject(i)
                                    val itemId =
                                        item.optString("1", "")        // 如 "GLOBAL_TOOLS", "CQU"
                                    val itemName =
                                        item.optString("2", "")      // 如 "通用工具与服务", "重庆大学"

                                    // 检查是否有适配器（优先检查 5-1，然后是 5）
                                    if (item.has("5-1")) {
                                        val adapterObj = item.get("5-1")

                                        when (adapterObj) {
                                            is JSONObject -> {
                                                // 单个适配器
                                                schoolIdList.add(itemId)
                                                schoolNameList.add(itemName)
                                                adapterInfoList.add(adapterObj)
                                            }

                                            is JSONArray -> {
                                                // 多个适配器，只取第一个（简化处理）
                                                if (adapterObj.length() > 0) {
                                                    schoolIdList.add(itemId)
                                                    schoolNameList.add(itemName)
                                                    adapterInfoList.add(adapterObj.getJSONObject(0))
                                                }
                                            }
                                        }
                                    } else if (item.has("5")) {
                                        val adapterObj = item.get("5")

                                        if (adapterObj is JSONObject) {
                                            // 单个适配器
                                            schoolIdList.add(itemId)
                                            schoolNameList.add(itemName)
                                            adapterInfoList.add(adapterObj)
                                        }
                                    }
                                }

                                activity.runOnUiThread {
                                    if (schoolNameList.isEmpty()) {
                                        TipDialog.show("没有找到学校数据", WaitDialog.TYPE.ERROR)
                                        return@runOnUiThread
                                    }

                                    val schools = schoolNameList.toTypedArray()

                                    BottomMenu.show(schools).setTitle("提示")
                                        .setMessage("选择你的学校进行导入，如果没有目标学校请申请适配")
                                        .setSingleSelection()
                                        .setOnMenuItemClickListener { dialog, text, index ->
                                            val schoolId = schoolIdList[index]
                                            val adapterObj = adapterInfoList[index]

                                            // 获取适配器信息
                                            val adapterName =
                                                adapterObj.optString("2", "课程表导入")
                                            val fileName = adapterObj.optString("4", "")
                                            var schoolUrl = adapterObj.optString("5", "")
                                            val description = adapterObj.optString(
                                                "6",
                                                "请先在浏览器登录教务系统，定位到个人课程表页面后，点击一键导入"
                                            )

                                            // 构建脚本URL
                                            val scriptUrl =
                                                "https://gitee.com/padi/shiguang/raw/main/resources/$schoolId/$fileName"
                                            //https://gitee.com/padi/shiguang/raw/main/resources/AHSZU/ahszu_01.js


                                            YLog.debug("下载脚本: $scriptUrl")

                                            val waitDialog2 = WaitDialog.show("加载中")

                                            OkHttpClientManager.get(
                                                url = scriptUrl,
                                                onSuccess = { response2 ->
                                                    waitDialog2.doDismiss()
                                                    try {
                                                        val jsStr = response2.body.string()
                                                        if (text.contains("-通用教务")) {
                                                            InputDialog(
                                                                "提示",
                                                                "请输入教务系统链接",
                                                                "确定",
                                                                "取消",
                                                                prefs.native()
                                                                    .getString("jw_webview_url", "")
                                                            ).setCancelable(false)
                                                                .setOkButton { baseDialog, v, inputStr ->
                                                                    prefs.native().edit {
                                                                        putString(
                                                                            "jw_webview_url",
                                                                            inputStr
                                                                        )
                                                                    }
                                                                    schoolUrl = inputStr
                                                                    val intent = Intent().apply {
                                                                        component = ComponentName(
                                                                            "com.xiaomi.aischedule",
                                                                            "com.xiaomi.aischedule.activity.ScheduleEducationalImportActivity"
                                                                        )
                                                                        val params =
                                                                            JSONObject().apply {
                                                                                put(
                                                                                    "url", schoolUrl
                                                                                )
                                                                                put(
                                                                                    "title",
                                                                                    adapterName
                                                                                )
                                                                                put(
                                                                                    "titleColor",
                                                                                    "#0D84FF"
                                                                                )
                                                                                put(
                                                                                    "text",
                                                                                    description
                                                                                )
                                                                                put(
                                                                                    "textColor",
                                                                                    "#0D84FF"
                                                                                )
                                                                                put(
                                                                                    "buttonText",
                                                                                    "一键导入"
                                                                                )
                                                                                put(
                                                                                    "buttonTextColor",
                                                                                    "#0D84FF"
                                                                                )
                                                                                put(
                                                                                    "buttonColor",
                                                                                    "#d1e8ff"
                                                                                )
                                                                                put(
                                                                                    "backgroundColor",
                                                                                    "#e7f3ff"
                                                                                )
                                                                                put(
                                                                                    "script",
                                                                                    "(async function () {${jsStr}})();"
                                                                                )
                                                                            }
                                                                        putExtra(
                                                                            "EXTRA_PARAMS",
                                                                            params.toString()
                                                                        )
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                    context.startActivity(intent)
                                                                    false
                                                                }.show()
                                                        } else {

                                                            val intent = Intent().apply {
                                                                component = ComponentName(
                                                                    "com.xiaomi.aischedule",
                                                                    "com.xiaomi.aischedule.activity.ScheduleEducationalImportActivity"
                                                                )
                                                                val params = JSONObject().apply {
                                                                    put("url", schoolUrl)
                                                                    put("title", adapterName)
                                                                    put("titleColor", "#0D84FF")
                                                                    put("text", description)
                                                                    put("textColor", "#0D84FF")
                                                                    put("buttonText", "一键导入")
                                                                    put(
                                                                        "buttonTextColor", "#0D84FF"
                                                                    )
                                                                    put("buttonColor", "#d1e8ff")
                                                                    put(
                                                                        "backgroundColor", "#e7f3ff"
                                                                    )
                                                                    put(
                                                                        "script",
                                                                        "(async function () {${jsStr}})();"
                                                                    )
                                                                }
                                                                putExtra(
                                                                    "EXTRA_PARAMS",
                                                                    params.toString()
                                                                )
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                    } catch (e: Exception) {
                                                        TipDialog.show(
                                                            "脚本加载失败: ${e.message}",
                                                            WaitDialog.TYPE.ERROR
                                                        )
                                                    }

                                                },
                                                onError = { e ->
                                                    waitDialog2.doDismiss()
                                                    TipDialog.show(
                                                        "下载失败: ${e.message}",
                                                        WaitDialog.TYPE.ERROR
                                                    )
                                                })

                                            false
                                        }
                                }

                            } catch (e: Exception) {
                                YLog.debug("解析失败: ${e.message}")
                                TipDialog.show("数据解析失败", WaitDialog.TYPE.ERROR)
                            }
                        },
                        onError = { e ->
                            waitDialog.doDismiss()
                            TipDialog.show(e.message ?: "网络请求失败", WaitDialog.TYPE.ERROR)
                            YLog.debug("GET 失败: ${e.message}")
                        })
                }

                "指定学校导入" -> {
                    val activity = context as Activity
                    val waitDialog = WaitDialog.show("加载中");
                    OkHttpClientManager.get(
                        url = "https://gitee.com/padi/aishedule/raw/master/school.json",
                        onSuccess = { response ->
                            waitDialog.doDismiss()
                            try {
                                val responseBody = response.body.string()
                                val jsonArray = JSONArray(responseBody)
                                val schoolList = mutableListOf<SchoolData>()
                                for (i in 0 until jsonArray.length()) {
                                    val jsonObject = jsonArray.getJSONObject(
                                        i
                                    )
                                    val id = jsonObject.optLong(
                                        "id", 0
                                    )
                                    val name = jsonObject.optString(
                                        "name", ""
                                    )
                                    val status = jsonObject.optString(
                                        "status", ""
                                    )
                                    val url = jsonObject.optString(
                                        "url", ""
                                    )
                                    schoolList.add(
                                        SchoolData(
                                            name = name, id = id, status = status, url = url
                                        )
                                    )
                                }

                                activity.runOnUiThread {
                                    val schools = schoolList.map { it.name }.toTypedArray()
                                    BottomMenu.show(schools).setTitle("提示")
                                        .setMessage("选择你的学校进行导入，如果没有目标学校请申请适配")
                                        .setSingleSelection()
                                        .setOnMenuItemClickListener { dialog, text, index ->
                                            val school = schoolList[index]
                                            val waitDialog = WaitDialog.show(
                                                "加载中"
                                            );
                                            OkHttpClientManager.get(
                                                url = "https://gitee.com/padi/aishedule/raw/master/import/${school.id}.js",
                                                onSuccess = { response ->
                                                    waitDialog.doDismiss()
                                                    val jsStr = response.body.string()
                                                    val intent = Intent().apply {
                                                        component = ComponentName(
                                                            "com.xiaomi.aischedule",
                                                            "com.xiaomi.aischedule.activity.ScheduleEducationalImportActivity"
                                                        )
                                                        val params = JSONObject().apply {
                                                            put(
                                                                "url", school.url
                                                            )
                                                            put(
                                                                "title", "导入课程表"
                                                            )
                                                            put(
                                                                "titleColor", "#0D84FF"
                                                            )
                                                            put(
                                                                "text",
                                                                "请先在浏览器登录教务系统，定位到个人课程表页面后，点击一键导入"
                                                            )
                                                            put(
                                                                "textColor", "#0D84FF"
                                                            )
                                                            put(
                                                                "buttonText", "一键导入"
                                                            )
                                                            put(
                                                                "buttonTextColor", "#0D84FF"
                                                            )
                                                            put(
                                                                "buttonColor", "#d1e8ff"
                                                            )
                                                            put(
                                                                "backgroundColor", "#e7f3ff"
                                                            )
                                                            put(
                                                                "script", jsStr
                                                            )
                                                        }

                                                        putExtra(
                                                            "EXTRA_PARAMS", params.toString()
                                                        )
                                                        addFlags(
                                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                                        )

                                                    }
                                                    context.startActivity(
                                                        intent
                                                    )
                                                },
                                                onError = { e ->
                                                    waitDialog.doDismiss()
                                                    TipDialog.show(
                                                        e.message, WaitDialog.TYPE.ERROR
                                                    );
                                                })
                                            false
                                        }
                                }


                            } catch (e: IOException) {
                                YLog.debug("读取响应失败: ${e.message}")
                            }
                        },
                        onError = { e ->
                            waitDialog.doDismiss()
                            TipDialog.show(
                                e.message, WaitDialog.TYPE.ERROR
                            );
                            YLog.debug("GET 失败: ${e.message}")
                        })

                }
            }
            false
        }
}


fun queryScoreFormSchool(context: Context) {
    val activity = context as Activity
    val waitDialog = WaitDialog.show("加载中");
    OkHttpClientManager.get(
        url = "https://gitee.com/padi/aishedule/raw/master/score.json",
        onSuccess = { response ->
            waitDialog.doDismiss()
            try {
                val responseBody = response.body.string()
                val jsonArray = JSONArray(responseBody)
                val schoolList = mutableListOf<ScoreData>()
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(
                        i
                    )
                    val type = jsonObject.optString(
                        "type", ""
                    )
                    val name = jsonObject.optString(
                        "name", ""
                    )
                    val status = jsonObject.optString(
                        "status", ""
                    )
                    val url = jsonObject.optString(
                        "url", ""
                    )
                    schoolList.add(
                        ScoreData(
                            name = name, type = type, status = status, url = url
                        )
                    )
                }

                activity.runOnUiThread {
                    val schools = schoolList.map { it.name }.toTypedArray()
                    BottomMenu.show(schools).setTitle("提示")
                        .setMessage("选择你的学校进行导入，如果没有目标学校请申请适配")
                        .setSingleSelection().setOnMenuItemClickListener { dialog, text, index ->
                            val school = schoolList[index]
                            val waitDialog = WaitDialog.show(
                                "加载中"
                            );
                            OkHttpClientManager.get(
                                url = "https://gitee.com/padi/aishedule/raw/master/score/${school.type}.js",
                                onSuccess = { response ->
                                    waitDialog.doDismiss()
                                    val jsStr = response.body.string()
                                    val intent = Intent().apply {
                                        component = ComponentName(
                                            "com.xiaomi.aischedule",
                                            "com.xiaomi.aischedule.activity.ScheduleEducationalImportActivity"
                                        )
                                        val params = JSONObject().apply {
                                            put(
                                                "url", school.url
                                            )
                                            put(
                                                "title", "导入成绩"
                                            )
                                            put(
                                                "titleColor", "#0D84FF"
                                            )
                                            put(
                                                "text",
                                                "请先在浏览器登录教务系统，定位到个人成绩页面后，点击导入成绩"
                                            )
                                            put(
                                                "textColor", "#0D84FF"
                                            )
                                            put(
                                                "buttonText", "导入成绩"
                                            )
                                            put(
                                                "buttonTextColor", "#0D84FF"
                                            )
                                            put(
                                                "buttonColor", "#d1e8ff"
                                            )
                                            put(
                                                "backgroundColor", "#e7f3ff"
                                            )
                                            put(
                                                "script", jsStr
                                            )
                                        }

                                        putExtra(
                                            "EXTRA_PARAMS", params.toString()
                                        )
                                        addFlags(
                                            Intent.FLAG_ACTIVITY_NEW_TASK
                                        )

                                    }
                                    context.startActivity(
                                        intent
                                    )
                                },
                                onError = { e ->
                                    waitDialog.doDismiss()
                                    TipDialog.show(
                                        e.message, WaitDialog.TYPE.ERROR
                                    );
                                })
                            false
                        }
                }


            } catch (e: IOException) {
                YLog.debug("读取响应失败: ${e.message}")
            }
        },
        onError = { e ->
            waitDialog.doDismiss()
            TipDialog.show(
                e.message, WaitDialog.TYPE.ERROR
            );
            YLog.debug("GET 失败: ${e.message}")
        })
}

fun openContributorQQ(context: Context, uin: String) {
    val url = "mqq://card/show_pslcard?src_type=internal&source=sharecard&version=1&uin=$uin"
    val intent = Intent(
        Intent.ACTION_VIEW, url.toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun Long.toColorHex(includeAlpha: Boolean = false): String {
    return if (includeAlpha) {
        String.format("#%08X", this)  // 包含透明度
    } else {
        String.format("#%06X", this and 0xFFFFFF)  // 只包含 RGB
    }
}

private fun parseWeeks(weeksArray: JSONArray?): List<Int> {
    val weeks = mutableListOf<Int>()
    if (weeksArray != null) {
        for (i in 0 until weeksArray.length()) {
            weeks.add(weeksArray.getInt(i))
        }
    }
    return weeks
}


data class SchoolData(
    val name: String,
    val id: Long,
    val status: String,
    val url: String,
)

data class ScoreData(
    val name: String,
    val type: String,
    val status: String,
    val url: String,
)

data class SystemData(
    val name: String,
    val type: String,
    val status: String,
)

data class SchoolCategory(
    val id: String,              // 分类ID，如 "CQU"
    val name: String,            // 分类名称，如 "重庆大学"
    val adapters: List<SchoolAdapter> // 该分类下的适配器列表
)

data class SchoolAdapter(
    val scriptId: String,        // 脚本ID，如 "CQU_01"
    val name: String,            // 适配器名称
    val fileName: String,        // 文件名，如 "cqu.js"
    val url: String,             // 教务系统URL
    val description: String,     // 描述说明
    val author: String           // 作者
)
