package com.mercury.xiaoaiimport

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface
import android.widget.TextView
import android.widget.Toast
import com.mercury.xiaoaiimport.screen.JwSystemScreen
import com.mercury.xiaoaiimport.screen.ModuleScreen

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
        val intent = Intent(context, JwSystemScreen::class.java)
        context.startActivity(intent)
    }

    @JavascriptInterface
    fun navModuleScreen() {
        val intent = Intent(context, ModuleScreen::class.java)
        context.startActivity(intent)
    }
}
