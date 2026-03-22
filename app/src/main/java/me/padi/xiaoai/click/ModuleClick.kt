package me.padi.xiaoai.click

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.kongzue.dialogx.dialogs.BottomMenu
import com.kongzue.dialogx.dialogs.InputDialog
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.dialogs.WaitDialog
import com.kongzue.dialogx.util.InputInfo
import me.padi.xiaoai.ApiClient
import me.padi.xiaoai.ApiClient.COLOR_PRESETS
import me.padi.xiaoai.Course
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.OkHttpClientManager
import me.padi.xiaoai.get
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.CourseRepository
import me.padi.xiaoai.ScheduleConfig
import me.padi.xiaoai.parseYamlList
import me.padi.xiaoai.launchImportActivity
import me.padi.xiaoai.screen.AiScreen
import me.padi.xiaoai.screen.JwSystemScreen
import me.padi.xiaoai.screen.SchoolScreen
import me.padi.xiaoai.screen.WebViewScreen
import org.json.JSONArray
import org.json.JSONObject
import android.net.Uri
import android.widget.Toast
import top.sacz.xphelper.ext.toClass
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

fun importCourseFormJw(context: Context) {
    BottomMenu.show(
        "教务系统导入", "Json导入", "AI解析导入"
    ).setTitle("提示").setMessage("选择一个导入方式")
        .setOnMenuItemClickListener { dialog, text, index ->
            when (text) {
                "AI解析导入" -> {
                    val intent = Intent(context, AiScreen::class.java)
                    intent.putExtra(
                        "proxy_target_activity",
                        context.proxyActivity()
                    )
                    context.startActivity(intent)
                }

                "教务系统导入" -> {
                    val intent = Intent(context, JwSystemScreen::class.java)
                    context.startActivity(intent)
                }

                "Json导入" -> {
                    val prompt = InputDialog.show("Json导入", "请粘贴符合规范的 Json 数据", "确定", "取消")
                    prompt.setOkButton { _, _, content ->
                        if (content.isBlank()) return@setOkButton false
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val json = try {
                                    JSONObject(content)
                                } catch (e: Exception) {
                                    // 兼容纯数组格式
                                    JSONObject().put("courses", JSONArray(content))
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
                                    
                                    // 兼容旧版星链字段
                                    if (c.sections.isBlank()) {
                                        val start = obj.optInt("startSection", 1)
                                        val end = obj.optInt("endSection", 2)
                                        c.sections = (start..end).joinToString(",")
                                    }
                                    if (c.weeks.isBlank()) {
                                        val wArr = obj.optJSONArray("weeks")
                                        if (wArr != null) {
                                            val wList = mutableListOf<Int>()
                                            for (j in 0 until wArr.length()) wList.add(wArr.getInt(j))
                                            c.weeks = wList.joinToString(",")
                                        }
                                    }
                                    
                                    c.sanitizeAndValidate()
                                    val colorIndex = if (c.name.isNotEmpty()) {
                                        abs(c.name.hashCode() % COLOR_PRESETS.size)
                                    } else {
                                        i % COLOR_PRESETS.size
                                    }
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

                                WaitDialog.show("正在导入...")
                                val appId = HostCompat.getAppId()
                                val name = json.optString("name", "Json导入").ifBlank { "Json导入" }
                                CourseRepository.importCourses(context, appId, name, courses, schedule)
                                WaitDialog.dismiss()
                                TipDialog.show("导入成功")
                            } catch (e: Exception) {
                                e.printStackTrace()
                                WaitDialog.dismiss()
                                TipDialog.show("失败: ${e.message}", WaitDialog.TYPE.ERROR)
                            }
                        }
                        false
                    }.show()
                }

            }
            false
        }
}

data class SchoolData(val name: String, val id: Long, val status: String, val url: String)
data class SystemData(val name: String, val type: String, val status: String)

fun openContributorQQ(context: Context, uin: String) {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=$uin")
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "未找到QQ", Toast.LENGTH_SHORT).show()
    }
}
