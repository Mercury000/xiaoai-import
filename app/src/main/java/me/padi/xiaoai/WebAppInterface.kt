package me.padi.xiaoai

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.TextView
import android.widget.Toast
import me.padi.xiaoai.screen.JwSystemScreen
import me.padi.xiaoai.screen.ModuleScreen

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
        AlertDialog.Builder(context)
            .setTitle("提示")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .setNeutralButton("复制") { _, _ ->
                copyToClipboard(message)
                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
            .show()
            .findViewById<TextView>(android.R.id.message)
            ?.setTextIsSelectable(true)
    }

    @JavascriptInterface
    fun navSchoolScreen() {
        if (!HostCompat.isLogin(context)) {
            Toast.makeText(context, "请先登录小米账号", Toast.LENGTH_SHORT).show()
            return
        }
        val loader = context.classLoader
        val token = HostCompat.getAccessToken(context, loader)
        val deviceId = HostCompat.getDeviceId(context, loader)
        val intent = Intent(context, JwSystemScreen::class.java).apply {
            putExtra("service_token", token)
            putExtra("device_id", deviceId)
            putExtra("proxy_target_activity", context.proxyActivity())
        }
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun navModuleScreen() {
        if (!HostCompat.isLogin(context)) {
            Toast.makeText(context, "请先登录小米账号", Toast.LENGTH_SHORT).show()
            return
        }
        val loader = context.classLoader
        val token = HostCompat.getAccessToken(context, loader)
        val deviceId = HostCompat.getDeviceId(context, loader)
        val intent = Intent(context, ModuleScreen::class.java).apply {
            putExtra("service_token", token)
            putExtra("device_id", deviceId)
            putExtra("proxy_target_activity", context.proxyActivity())
        }
        context.startActivity(intent)
    }
}
