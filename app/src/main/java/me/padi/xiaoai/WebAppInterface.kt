package me.padi.xiaoai

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.padi.xiaoai.screen.ModuleScreen
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
            val intent = Intent(context, ModuleScreen::class.java)
            intent.putExtra(
                "proxy_target_activity", "com.xiaomi.aischedule.activity.DeleteAccountActivity"
            )
            context.startActivity(intent)

        } else {
            Toast.makeText(
                context, "请先登录小米账号", Toast.LENGTH_SHORT
            ).show()
        }
    }

    @JavascriptInterface
    fun processData(input: String): String {
        // 处理从 JavaScript 传来的数据并返回结果
        return "处理结果: $input"
    }

    @JavascriptInterface
    fun callAndroidFunction(param1: String, param2: Int) {

    }
}