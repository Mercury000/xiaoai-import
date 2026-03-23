package me.padi.xiaoai.click

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.kongzue.dialogx.dialogs.InputDialog
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.padi.xiaoai.ApiClient.COLOR_PRESETS
import me.padi.xiaoai.Course
import me.padi.xiaoai.openCoursePreviewScreen
import me.padi.xiaoai.ScheduleConfig
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.screen.AiScreen
import me.padi.xiaoai.screen.JwSystemScreen
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
                    c.name = obj.optString("name")
                    c.teacher = obj.optString("teacher")
                    c.position = obj.optString("position")
                    c.day = obj.optInt("day")
                    c.sections = obj.optString("sections")
                    c.weeks = obj.optString("weeks")

                    if (c.sections.isBlank()) {
                        val start = obj.optInt("startSection", 1)
                        val end = obj.optInt("endSection", 2)
                        c.sections = (start..end).joinToString(",")
                    }
                    if (c.weeks.isBlank()) {
                        val weekArray = obj.optJSONArray("weeks")
                        if (weekArray != null) {
                            val weekList = mutableListOf<Int>()
                            for (j in 0 until weekArray.length()) weekList.add(weekArray.getInt(j))
                            c.weeks = weekList.joinToString(",")
                        }
                    }

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
                val parsedName = json.optString("name").trim()
                context.openCoursePreviewScreen(
                    tableName = if (parsedName.isBlank()) "提取课表" else parsedName,
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
