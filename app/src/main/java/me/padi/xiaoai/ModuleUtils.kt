package me.padi.xiaoai

import android.content.Context
import android.content.Intent
import androidx.annotation.RawRes
import me.padi.xiaoai.screen.WebViewScreen

/**
 * 模块通用工具函数
 */

fun Context.readRawFile(@RawRes resId: Int): String? {
    return try {
        resources.openRawResource(resId).bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }
}

fun Context.proxyActivity(): String = "com.xiaomi.voiceassistant.web.container.AiWebActivity"

fun launchImportActivity(context: Context, url: String, title: String, text: String = "", script: String) {
    val intent = Intent(context, WebViewScreen::class.java).apply {
        putExtra("url", url)
        putExtra("title", title)
        putExtra("text", text)
        putExtra("script", "(async function () {${script}})();")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/**
 * 解析简单 YAML 列表
 */
fun parseYamlList(content: String): List<Map<String, String>> {
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
    
    if (valuePart.contains("#")) {
        valuePart = valuePart.substring(0, valuePart.indexOf("#")).trim()
    }
    
    val value = if (valuePart.startsWith("\"") && valuePart.endsWith("\"")) {
        valuePart.substring(1, valuePart.length - 1)
    } else if (valuePart.startsWith("'") && valuePart.endsWith("'")) {
        valuePart.substring(1, valuePart.length - 1)
    } else {
        valuePart
    }
    
    return key to value
}
