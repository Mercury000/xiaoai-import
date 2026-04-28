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
import com.mercury.xiaoaiimport.Course
import com.mercury.xiaoaiimport.HostCompat
import com.mercury.xiaoaiimport.OkHttpClientManager
import com.mercury.xiaoaiimport.openCoursePreviewScreen
import com.mercury.xiaoaiimport.ScheduleConfig
import com.mercury.xiaoaiimport.screen.AiScreen
import com.mercury.xiaoaiimport.screen.JwSystemScreen
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset

fun importCourseFormJw(context: Context) {
    val intent = Intent(context, JwSystemScreen::class.java)
    context.startActivity(intent)
}

fun openAiImportScreen(context: Context) {
    val intent = Intent(context, AiScreen::class.java)
    context.startActivity(intent)
}

fun openWakeUpImportDialog(context: Context) {
    val prompt = InputDialog.show("WakeUp导入", "请粘贴 WakeUp V2 分享口令或整段分享文本", "确定", "取消")
    prompt.setInputInfo(
        InputInfo()
            .setMAX_LENGTH(Int.MAX_VALUE)
            .setMultipleLines(true)
    )
    prompt.setOkButton { _, _, content ->
        val input = content.trim()
        if (input.isBlank()) {
            TipDialog.show("口令不能为空", WaitDialog.TYPE.ERROR)
            return@setOkButton true
        }
        if (!Regex("[a-fA-F0-9]{32}").containsMatchIn(input)) {
            TipDialog.show("未检测到 32 位 WakeUp V2 分享口令", WaitDialog.TYPE.ERROR)
            return@setOkButton true
        }

        WaitDialog.show("正在解析 WakeUp 口令...")
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val parsed = withContext(Dispatchers.IO) { parseWakeUpShare(input) }
                HostCompat.pendingCourseConfigJson = parsed.courseConfig.toString()
                HostCompat.pendingTimeSlotSectionsJson = parsed.sections.toString()
                WaitDialog.dismiss()
                context.openCoursePreviewScreen(
                    courses = parsed.courses,
                    schedule = null
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

private data class WakeUpParseResult(
    val courses: List<Course>,
    val sections: JSONArray,
    val courseConfig: JSONObject
)

private suspend fun parseWakeUpShare(input: String): WakeUpParseResult {
    val response = OkHttpClientManager.postSync(
        "https://api.303889.xyz/parse-v2",
        JSONObject().put("input", extractWakeUpKey(input)).toString()
    )
    val root = JSONObject(response)
    if (!root.optBoolean("success", false)) {
        throw IllegalStateException(root.optString("error", "API 解析失败"))
    }

    val parts = root.optJSONArray("shareDataParts") ?: throw IllegalStateException("API 未返回 shareDataParts")
    if (parts.length() != 5) throw IllegalStateException("API 返回数据格式不完整")

    val baseConfig = parts.getJSONObject(0)
    val timeSlotsRaw = parts.getJSONArray(1)
    val uiConfig = parts.getJSONObject(2)
    val coursesRaw = parts.getJSONArray(3)
    val courseDetailRaw = parts.getJSONArray(4)

    val validNodes = readWakeUpValidNodes(uiConfig)
    val sections = JSONArray()
    for (i in 0 until timeSlotsRaw.length()) {
        val slot = timeSlotsRaw.optJSONObject(i) ?: continue
        val node = slot.optInt("node", -1)
        val start = slot.optString("startTime").trim()
        val end = slot.optString("endTime").trim()
        if (node <= 0 || start == "00:00" || end == "00:00") continue
        if (node !in validNodes) continue
        sections.put(
            JSONObject()
                .put("number", node)
                .put("startTime", start)
                .put("endTime", end)
        )
    }

    val courseMap = mutableMapOf<String, JSONObject>()
    for (i in 0 until coursesRaw.length()) {
        val course = coursesRaw.optJSONObject(i) ?: continue
        courseMap[course.opt("id")?.toString().orEmpty()] = course
    }

    val courses = mutableListOf<Course>()
    for (i in 0 until courseDetailRaw.length()) {
        val detail = courseDetailRaw.optJSONObject(i) ?: continue
        val courseInfo = courseMap[detail.opt("id")?.toString().orEmpty()] ?: continue
        val weeks = buildWakeUpWeeks(
            detail.optInt("startWeek", -1),
            detail.optInt("endWeek", -1),
            detail.optInt("type", 0)
        )
        val startNode = detail.optInt("startNode", -1)
        val step = detail.optInt("step", 1).coerceAtLeast(1)
        if (weeks.isEmpty() || startNode <= 0) continue

        courses.add(
            Course().apply {
                name = courseInfo.optString("courseName").trim()
                teacher = detail.optString("teacher", "").trim()
                position = detail.optString("room", "").trim()
                day = detail.optInt("day", 0)
                this.sections = (startNode until startNode + step).joinToString(",")
                this.weeks = weeks.joinToString(",")
                sanitizeAndValidate()
            }
        )
    }

    if (courses.isEmpty()) throw IllegalStateException("未解析到课程数据")

    val courseConfig = JSONObject().apply {
        normalizeWakeUpDate(uiConfig.optString("startDate", ""))?.let { put("startSemester", it) }
        put("totalWeek", uiConfig.optInt("maxWeek", 20).takeIf { it > 0 } ?: 20)
        if (baseConfig.has("courseLen")) put("defaultClassDuration", baseConfig.optInt("courseLen"))
        if (baseConfig.has("theBreakLen")) put("defaultBreakDuration", baseConfig.optInt("theBreakLen"))
    }

    return WakeUpParseResult(courses, sections, courseConfig)
}

private fun extractWakeUpKey(input: String): String {
    Regex("「([a-fA-F0-9]{32})」").find(input)?.let { return it.groupValues[1] }
    return Regex("[a-fA-F0-9]{32}").find(input)?.value
        ?: throw IllegalArgumentException("未检测到有效的 WakeUp V2 分享口令")
}

private fun readWakeUpValidNodes(uiConfig: JSONObject): Set<Int> {
    return when (val nodes = uiConfig.opt("nodes")) {
        is JSONArray -> buildSet {
            for (i in 0 until nodes.length()) nodes.optInt(i, -1).takeIf { it > 0 }?.let(::add)
        }
        is Number -> (1..nodes.toInt()).toSet()
        else -> emptySet()
    }
}

private fun buildWakeUpWeeks(startWeek: Int, endWeek: Int, type: Int): List<Int> {
    if (startWeek <= 0 || endWeek <= 0) return emptyList()
    val from = minOf(startWeek, endWeek)
    val to = maxOf(startWeek, endWeek)
    return (from..to).filter { week ->
        type == 0 || (type == 1 && week % 2 != 0) || (type == 2 && week % 2 == 0)
    }
}

private fun normalizeWakeUpDate(raw: String): String? {
    val value = raw.trim().replace('/', '-')
    if (value.isEmpty()) return null
    return runCatching {
        LocalDate.parse(value)
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli()
            .toString()
    }.getOrNull()
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
