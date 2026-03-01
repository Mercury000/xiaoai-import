package me.padi.xiaoai

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.log.YLog
import com.kongzue.dialogx.dialogs.BottomMenu
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import me.padi.xiaoai.screen.ModuleScreen
import me.padi.xiaoai.screen.SchoolScreen
import org.json.JSONArray
import org.json.JSONObject
import top.sacz.xphelper.ext.toClass
import java.io.IOException


class WebAppInterface(private val context: Context) {
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun navSchoolScreen() {
        if ("a.h.g.h".toClass().resolve().firstMethod {
                name = "getInstance"
                parameterCount = 0
            }.invoke()?.asResolver()?.firstMethod {
                name = "isLogin"
            }?.invoke<Boolean>() == true) {


            BottomMenu.show("指定学校导入", "AI解析导入", "教务系统导入").setTitle("提示")
                .setMessage("选择一个导入方式").setOnMenuItemClickListener { dialog, text, index ->
                    when (text) {
                        "AI解析导入" -> {
                            val intent = Intent(context, ModuleScreen::class.java)
                            intent.putExtra(
                                "proxy_target_activity",
                                "com.xiaomi.aischedule.activity.DeleteAccountActivity"
                            )
                            context.startActivity(intent)
                        }

                        "教务系统导入" -> {
                            val intent = Intent(context, SchoolScreen::class.java)
                            intent.putExtra("type", "jiaowu")
                            intent.putExtra(
                                "proxy_target_activity",
                                "com.xiaomi.aischedule.activity.DeleteAccountActivity"
                            )
                            context.startActivity(intent)
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
                                            val jsonObject = jsonArray.getJSONObject(i)
                                            val id = jsonObject.optLong("id", 0)
                                            val name = jsonObject.optString("name", "")
                                            val status = jsonObject.optString("status", "")
                                            val uri = jsonObject.optString("url", "")
                                            schoolList.add(
                                                SchoolData(
                                                    name = name, id = id, status = status, url = uri
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
                                                    val waitDialog = WaitDialog.show("加载中");
                                                    OkHttpClientManager.get(
                                                        url = "https://gitee.com/padi/aishedule/raw/master/import/${school.id}.js",
                                                        onSuccess = { response ->
                                                            waitDialog.doDismiss()
                                                            val jsStr = response.body.string()
                                                            YLog.debug(jsStr)
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
                                                                    put(
                                                                        "buttonTextColor", "#0D84FF"
                                                                    )
                                                                    put("buttonColor", "#d1e8ff")
                                                                    put(
                                                                        "backgroundColor", "#e7f3ff"
                                                                    )
                                                                    put("script", jsStr)
                                                                }

                                                                putExtra(
                                                                    "EXTRA_PARAMS",
                                                                    params.toString()
                                                                )
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                                                            }
                                                            context.startActivity(intent)
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
                                    TipDialog.show(e.message, WaitDialog.TYPE.ERROR);
                                    YLog.debug("GET 失败: ${e.message}")
                                })

                        }
                    }
                    false
                }.setCancelButton("取消") { baseDialog, v ->
                    false
                }


        } else {
            Toast.makeText(
                context, "请先登录小米账号", Toast.LENGTH_SHORT
            ).show()
        }
    }

    @JavascriptInterface
    fun processData(input: String): String {
        return "处理结果: $input"
    }

    @JavascriptInterface
    fun callAndroidFunction(param1: String, param2: Int) {

    }
}

data class SchoolData(
    val name: String,
    val id: Long,
    val status: String,
    val url: String,
)