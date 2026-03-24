package com.mercury.xiaoaiimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

object PresetDataLauncher {
    private const val XIAOAI_WEB_URL = "https://i.ai.mi.com/h5/precache/ai-schedule/"

    fun launch(
        context: Context,
        courses: List<Course>,
        schedule: ScheduleConfig? = null
    ) {
        val presetData = buildPresetData(courses, schedule)
        val deepLink = "voiceassist://aiweb/?" +
            "source=widget" +
            "&flag=268468224" +
            "&url=${Uri.encode(XIAOAI_WEB_URL)}" +
            "&presetData=${Uri.encode(presetData)}"

        val intent = Intent(Intent.ACTION_VIEW, deepLink.toUri())
        context.startActivity(intent)
    }

    fun buildPresetData(
        courses: List<Course>,
        schedule: ScheduleConfig? = null
    ): String {
        val timerConfig = buildTimerConfig(schedule)
        val payload = JSONObject().apply {
            put("isV2", true)
            put("t", System.currentTimeMillis().toString())
            put("parserRes", JSONObject().put("courseInfos", buildCourseInfos(courses)))
            put("timerRes", timerConfig)
            put("schoolName", "")
            put("feedbackId", "preset_" + System.currentTimeMillis())
            put("id", "proj_" + System.currentTimeMillis())
        }

        return JSONObject()
            .put("importData", payload.toString())
            .toString()
    }

    private fun buildCourseInfos(courses: List<Course>): JSONArray {
        return JSONArray().apply {
            courses.forEach { course ->
                put(
                    JSONObject().apply {
                        put("name", course.name.trim())
                        put("teacher", course.teacher.trim())
                        put("position", course.position.trim())
                        put("day", course.day)
                        put("weeks", parseNumberArray(course.weeks))
                        put("sections", parseNumberArray(course.sections))
                    }
                )
            }
        }
    }

    private fun buildTimerConfig(schedule: ScheduleConfig?): JSONObject {
        val pendingConfig = parseJsonObject(HostCompat.pendingCourseConfigJson)
        val pendingSections = parseJsonArray(HostCompat.pendingTimeSlotSectionsJson)
        val scheduleSections = parseJsonArray(schedule?.sections)
        val finalSections = when {
            pendingSections != null && pendingSections.length() > 0 -> normalizeSections(pendingSections)
            scheduleSections != null && scheduleSections.length() > 0 -> normalizeSections(scheduleSections)
            else -> JSONArray()
        }

        val forenoon = schedule?.morningNum
            ?: pendingConfig?.optInt("forenoon")
            ?: 0
        val afternoon = schedule?.afternoonNum
            ?: pendingConfig?.optInt("afternoon")
            ?: 0
        val night = schedule?.nightNum
            ?: pendingConfig?.optInt("night")
            ?: 0
        val totalWeek = pendingConfig?.optInt("totalWeek")
            ?.takeIf { it > 0 }
            ?: 20
        val startSemester = normalizeStartSemester(
            pendingConfig?.opt("startSemester")?.toString()
                ?: pendingConfig?.opt("semesterStartDate")?.toString()
                ?: pendingConfig?.opt("startDate")?.toString()
                ?: pendingConfig?.opt("termStartDate")?.toString()
        )

        return JSONObject().apply {
            put("totalWeek", totalWeek)
            put("startSemester", startSemester)
            put("startWithSunday", pendingConfig?.optBoolean("startWithSunday") ?: false)
            put("showWeekend", pendingConfig?.optBoolean("showWeekend") ?: false)
            put("forenoon", forenoon)
            put("afternoon", afternoon)
            put("night", night)
            put("sections", finalSections)
        }
    }

    private fun normalizeSections(source: JSONArray): JSONArray {
        val sections = JSONArray()
        for (i in 0 until source.length()) {
            val obj = source.optJSONObject(i) ?: continue
            val section = when {
                obj.has("section") -> obj.optInt("section", -1)
                obj.has("number") -> obj.optInt("number", -1)
                obj.has("i") -> obj.optInt("i", -1)
                else -> -1
            }
            val startTime = when {
                obj.has("startTime") -> obj.optString("startTime")
                obj.has("start") -> obj.optString("start")
                obj.has("s") -> obj.optString("s")
                else -> ""
            }.trim()
            val endTime = when {
                obj.has("endTime") -> obj.optString("endTime")
                obj.has("end") -> obj.optString("end")
                obj.has("e") -> obj.optString("e")
                else -> ""
            }.trim()
            if (section > 0 && startTime.isNotEmpty() && endTime.isNotEmpty()) {
                sections.put(
                    JSONObject().apply {
                        put("section", section)
                        put("startTime", startTime)
                        put("endTime", endTime)
                    }
                )
            }
        }
        return sections
    }

    private fun parseNumberArray(raw: String?): JSONArray {
        val array = JSONArray()
        raw.orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
            .sorted()
            .forEach { array.put(it) }
        return array
    }

    private fun parseJsonObject(raw: String?): JSONObject? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching { JSONObject(value) }.getOrNull()
    }

    private fun parseJsonArray(raw: String?): JSONArray? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return runCatching { JSONArray(value) }.getOrNull()
    }

    private fun normalizeStartSemester(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        value.toLongOrNull()?.let { numeric ->
            return if (numeric in 1L..99_999_999_999L) {
                (numeric * 1000L).toString()
            } else {
                numeric.toString()
            }
        }
        return value
    }
}
