package me.padi.xiaoai

import android.content.Context
import android.content.ContextWrapper
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

fun Context.proxyActivity(): String {
    var current: Context? = this
    repeat(8) {
        if (current == null) return@repeat
        val className = current.javaClass.name
        if (className.startsWith("com.xiaomi.voiceassistant") && className.endsWith("Activity")) {
            return className
        }
        current = (current as? ContextWrapper)?.baseContext
    }
    return "com.xiaomi.voiceassistant.web.container.AiWebActivity"
}

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
    val valuePart = line.substring(colonIndex + 1).trim()
    // 引号内可能含 #（如 initial: "#"），必须先找闭合引号，不能直接 stripComment
    val value: String = if (valuePart.startsWith("\"") || valuePart.startsWith("'")) {
        val q = valuePart[0]
        val sb = StringBuilder()
        var i = 1
        var escaped = false
        while (i < valuePart.length) {
            val ch = valuePart[i]
            if (escaped) {
                sb.append(
                    when (ch) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '\\' -> '\\'
                        '"' -> '"'
                        '\'' -> '\''
                        else -> ch
                    }
                )
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == q) {
                break
            } else {
                sb.append(ch)
            }
            i++
        }
        sb.toString()
    } else {
        val hashIdx = valuePart.indexOf('#')
        if (hashIdx >= 0) valuePart.substring(0, hashIdx).trim() else valuePart
    }
    return key to value
}
