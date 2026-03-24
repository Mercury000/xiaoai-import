package com.mercury.xiaoaiimport.click

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.kongzue.dialogx.dialogs.InputDialog
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import com.kongzue.dialogx.util.InputInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mercury.xiaoaiimport.ApiClient.COLOR_PRESETS
import com.mercury.xiaoaiimport.Course
import com.mercury.xiaoaiimport.openCoursePreviewScreen
import com.mercury.xiaoaiimport.ScheduleConfig
import com.mercury.xiaoaiimport.proxyActivity
import com.mercury.xiaoaiimport.screen.AiScreen
import com.mercury.xiaoaiimport.screen.JwSystemScreen
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

fun importCourseFormJw(context: Context) {
    val intent = Intent(context, JwSystemScreen::class.java)
    context.startActivity(intent)
}

fun openAiImportScreen(context: Context) {
    val intent = Intent(context, AiScreen::class.java)
    intent.putExtra("proxy_target_activity", context.proxyActivity())
    context.startActivity(intent)
}

fun openJsonImportDialog(context: Context) {
    val prompt = InputDialog.show("JSON导入", "请粘贴符合规范的 JSON 数据", "确定", "取消")
    prompt.setInputInfo(
        InputInfo()
            .setMAX_LENGTH(Int.MAX_VALUE)
            .setMultipleLines(true)
    )
    prompt.setOkButton { _, _, content ->
        val jsonContent = content.trim()
        if (jsonContent.isBlank()) return@setOkButton false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val json = try {
                    JSONObject(jsonContent)
                } catch (_: Exception) {
                    JSONObject().put("courses", JSONArray(jsonContent))
                }

                val courseArray = json.optJSONArray("courses") ?: JSONArray()
                val courses = mutableListOf<Course>()
                for (i in 0 until courseArray.length()) {
                    val obj = courseArray.getJSONObject(i)
                    val c = Course()
                    c.name = obj.optString("name").trim()
                    c.teacher = obj.optString("teacher", obj.optString("instructor", "")).trim()
                    c.position = obj.optString(
                        "position",
                        obj.optString("location", obj.optString("classroom", ""))
                    ).trim()
                    c.day = firstValidInt(
                        obj.optInt("day", -1),
                        obj.optInt("weekday", -1),
                        obj.optInt("weekDay", -1)
                    )
                    c.sections = parseSections(obj)
                    c.weeks = parseWeeks(obj)

                    c.sanitizeAndValidate()
                    val colorIndex = if (c.name.isNotEmpty()) abs(c.name.hashCode() % COLOR_PRESETS.size) else i % COLOR_PRESETS.size
                    c.style = COLOR_PRESETS[colorIndex]
                    courses.add(c)
                }

                val schedule = json.optJSONObject("schedule")?.let { sObj ->
                    ScheduleConfig().apply {
                        if (sObj.has("morningNum")) morningNum = sObj.getInt("morningNum")
                        if (sObj.has("afternoonNum")) afternoonNum = sObj.getInt("afternoonNum")
                        if (sObj.has("nightNum")) nightNum = sObj.getInt("nightNum")
                        if (sObj.has("sections")) sections = sObj.optString("sections")
                    }
                }
                context.openCoursePreviewScreen(
                    courses = courses,
                    schedule = schedule
                )
            } catch (e: Exception) {
                e.printStackTrace()
                WaitDialog.dismiss()
                TipDialog.show("失败: ${e.message}", WaitDialog.TYPE.ERROR)
            }
        }
        false
    }.show()
}

fun openContributorQQ(context: Context, uin: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=$uin")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "未找到QQ", Toast.LENGTH_SHORT).show()
    }
}

private fun firstValidInt(vararg values: Int): Int {
    return values.firstOrNull { it > 0 } ?: 0
}

private fun parseSections(obj: JSONObject): String {
    val sectionsText = obj.optString("sections", "").trim()
    if (sectionsText.isNotBlank()) return sectionsText

    val sectionsArray = obj.optJSONArray("sections")
    if (sectionsArray != null && sectionsArray.length() > 0) {
        val list = mutableListOf<String>()
        for (i in 0 until sectionsArray.length()) {
            val value = sectionsArray.opt(i)?.toString()?.trim().orEmpty()
            if (value.isNotBlank()) list.add(value)
        }
        if (list.isNotEmpty()) return list.joinToString(",")
    }

    val start = obj.optInt("startSection", -1)
    val end = obj.optInt("endSection", -1)
    if (start > 0 && end > 0) {
        val from = minOf(start, end)
        val to = maxOf(start, end)
        return (from..to).joinToString(",")
    }

    return ""
}

private fun parseWeeks(obj: JSONObject): String {
    val weeksAny = obj.opt("weeks")
    if (weeksAny is JSONArray) {
        val list = mutableListOf<String>()
        for (i in 0 until weeksAny.length()) {
            val value = weeksAny.opt(i)?.toString()?.trim().orEmpty()
            if (value.isNotBlank()) list.add(value)
        }
        if (list.isNotEmpty()) return list.joinToString(",")
    }

    val weeksText = obj.optString("weeks", "").trim()
    if (weeksText.isNotBlank()) return weeksText

    val alias = obj.optJSONArray("weekList")
    if (alias != null && alias.length() > 0) {
        val list = mutableListOf<String>()
        for (i in 0 until alias.length()) {
            val value = alias.opt(i)?.toString()?.trim().orEmpty()
            if (value.isNotBlank()) list.add(value)
        }
        if (list.isNotEmpty()) return list.joinToString(",")
    }

    return ""
}
