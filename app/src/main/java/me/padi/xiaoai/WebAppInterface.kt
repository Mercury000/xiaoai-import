package me.padi.xiaoai

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.kongzue.dialogx.dialogs.BottomMenu
import me.padi.xiaoai.screen.ModuleScreen
import me.padi.xiaoai.screen.SchoolScreen
import top.sacz.xphelper.ext.toClass


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

                        }
                    }
                    true
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