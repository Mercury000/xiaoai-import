package me.padi.xiaoai.click

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.highcapable.yukihookapi.hook.log.YLog
import com.kongzue.dialogx.dialogs.BottomMenu
import com.kongzue.dialogx.dialogs.InputDialog
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.get
import me.padi.xiaoai.hook.MainHook.prefs
import me.padi.xiaoai.screen.AiScreen
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException


fun importCourseFormJw(context: Context) {
    BottomMenu.show("指定学校导入", "通用教务系统导入", "AI解析导入").setTitle("提示")
        .setMessage("选择一个导入方式").setOnMenuItemClickListener { dialog, text, index ->
            when (text) {
                "AI解析导入" -> {
                    val intent = Intent(context, AiScreen::class.java)
                    intent.putExtra(
                        "proxy_target_activity",
                        "com.xiaomi.aischedule.activity.DeleteAccountActivity"
                    )
                    context.startActivity(intent)
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
        }.setCancelButton("取消") { baseDialog, v ->
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