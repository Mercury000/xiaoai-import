package me.padi.xiaoai

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.TextView
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import me.padi.xiaoai.screen.ModuleScreen
import me.padi.xiaoai.screen.ScoreScreen
import top.sacz.xphelper.ext.toClass


class WebAppInterface(private val context: Context) {

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("copied_text", text))
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun showDialog(message: String) {
        AlertDialog.Builder(context).setTitle("提示").setMessage(message)
            .setPositiveButton("确定", null).setNeutralButton("复制") { _, _ ->
                copyToClipboard(message)
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }.show().findViewById<TextView>(android.R.id.message)?.setTextIsSelectable(true)

    }

    @JavascriptInterface
    fun navScoreScreen(json: String) {
        val intent = Intent(context, ScoreScreen::class.java)
        intent.putExtra("json", json)
        intent.putExtra(
            "proxy_target_activity", context.proxyActivity()
        )
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun navSchoolScreen() {
        if (HostCompat.isLogin()) {
        val intent = Intent(context, ModuleScreen::class.java)
        intent.putExtra(
            "proxy_target_activity", context.proxyActivity()
        )
        context.startActivity(intent)
        } else {
            Toast.makeText(
                context, "请先登录小米账号", Toast.LENGTH_SHORT
            ).show()
        }
    }
}

fun Context.proxyActivity(): String = "com.xiaomi.voiceassistant.web.container.AiWebActivity"
