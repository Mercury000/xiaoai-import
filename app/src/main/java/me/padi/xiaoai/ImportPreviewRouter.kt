package com.mercury.xiaoaiimport

import android.content.Context
import android.content.Intent
import com.mercury.xiaoaiimport.screen.CoursePreviewScreen
import org.json.JSONArray
import org.json.JSONObject

private const val EXTRA_COURSES_JSON = "preview_courses_json"
private const val EXTRA_SCHEDULE_JSON = "preview_schedule_json"

fun Context.openCoursePreviewScreen(
    courses: List<Course>,
    schedule: ScheduleConfig? = null
) {
    val intent = Intent(this, CoursePreviewScreen::class.java).apply {
        putExtra(EXTRA_COURSES_JSON, coursesToJson(courses).toString())
        putExtra(EXTRA_SCHEDULE_JSON, scheduleToJson(schedule))
    }
    startActivity(intent)
}

fun Intent.readPreviewCourses(): List<Course> {
    val raw = getStringExtra(EXTRA_COURSES_JSON).orEmpty()
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    Course().apply {
                        name = obj.optString("name")
                        teacher = obj.optString("teacher")
                        position = obj.optString("position")
                        day = obj.optInt("day", 1)
                        sections = obj.optString("sections")
                        weeks = obj.optString("weeks")
                        style = obj.optString("style")
                        sanitizeAndValidate()
                    }
                )
            }
        }
    }.getOrDefault(emptyList())
}

fun Intent.readPreviewSchedule(): ScheduleConfig? {
    val raw = getStringExtra(EXTRA_SCHEDULE_JSON).orEmpty()
    if (raw.isBlank()) return null
    return runCatching {
        val sObj = JSONObject(raw)
        ScheduleConfig().apply {
            if (sObj.has("morningNum")) morningNum = sObj.getInt("morningNum")
            if (sObj.has("afternoonNum")) afternoonNum = sObj.getInt("afternoonNum")
            if (sObj.has("nightNum")) nightNum = sObj.getInt("nightNum")
            if (sObj.has("sections")) sections = sObj.optString("sections")
        }
    }.getOrNull()
}

private fun coursesToJson(courses: List<Course>): JSONArray {
    val arr = JSONArray()
    courses.forEach { c ->
        arr.put(
            JSONObject()
                .put("name", c.name)
                .put("teacher", c.teacher)
                .put("position", c.position)
                .put("day", c.day)
                .put("sections", c.sections)
                .put("weeks", c.weeks)
                .put("style", c.style)
        )
    }
    return arr
}

private fun scheduleToJson(schedule: ScheduleConfig?): String {
    if (schedule == null) return ""
    return JSONObject().apply {
        put("morningNum", schedule.morningNum)
        put("afternoonNum", schedule.afternoonNum)
        put("nightNum", schedule.nightNum)
        put("sections", schedule.sections)
    }.toString()
}
