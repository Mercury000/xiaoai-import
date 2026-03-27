package com.mercury.xiaoaiimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
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
        val timerBuild = buildTimerConfig(schedule, courses)
        val payload = JSONObject().apply {
            put("isV2", true)
            put("t", System.currentTimeMillis().toString())
            put("parserRes", JSONObject().put("courseInfos", buildCourseInfos(timerBuild.courses)))
            put("timerRes", timerBuild.timerConfig)
            put("schoolName", "")
            put("feedbackId", "preset_" + System.currentTimeMillis())
            put("id", "proj_" + System.currentTimeMillis())
        }

        return JSONObject()
            .put("importData", payload.toString())
            .toString()
    }

    private data class TimerBuildResult(
        val timerConfig: JSONObject,
        val courses: List<Course>
    )

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

    private fun buildTimerConfig(schedule: ScheduleConfig?, courses: List<Course>): TimerBuildResult {
        val pendingConfig = parseJsonObject(HostCompat.pendingCourseConfigJson)
        val pendingCandidates = parseTimeSlotCandidates(HostCompat.pendingTimeSlotSectionsJson)
        val scheduleCandidates = parseJsonArray(schedule?.sections)
            ?.takeIf { it.length() > 0 }
            ?.let { listOf(TimeSlotCandidate("schedule", emptyList(), normalizeSections(it))) }
            .orEmpty()
        val candidates = if (pendingCandidates.isNotEmpty()) pendingCandidates else scheduleCandidates
        val baseSections = candidates.firstOrNull()?.sections ?: JSONArray()
        val selectedCandidate = chooseBestTimeSlotCandidate(candidates, courses)
        val resolved = resolveCustomTimeCourses(courses, selectedCandidate?.sections ?: baseSections)
        val finalSections = resolved.sections

        val inferredCounts = inferSessionCounts(finalSections)
        val forenoon = schedule?.morningNum?.takeIf { it > 0 }
            ?: pendingConfig?.optInt("forenoon")?.takeIf { it > 0 }
            ?: pendingConfig?.optInt("morningNum")?.takeIf { it > 0 }
            ?: inferredCounts.first
        val afternoon = schedule?.afternoonNum?.takeIf { it > 0 }
            ?: pendingConfig?.optInt("afternoon")?.takeIf { it > 0 }
            ?: pendingConfig?.optInt("afternoonNum")?.takeIf { it > 0 }
            ?: inferredCounts.second
        val night = schedule?.nightNum?.takeIf { it > 0 }
            ?: pendingConfig?.optInt("night")?.takeIf { it > 0 }
            ?: pendingConfig?.optInt("nightNum")?.takeIf { it > 0 }
            ?: inferredCounts.third
        val totalWeek = pendingConfig?.optInt("totalWeek")
            ?.takeIf { it > 0 }
            ?: 20
        val startSemester = normalizeStartSemester(
            pendingConfig?.opt("startSemester")?.toString()
                ?: pendingConfig?.opt("semesterStartDate")?.toString()
                ?: pendingConfig?.opt("startDate")?.toString()
                ?: pendingConfig?.opt("termStartDate")?.toString()
        )

        val timerConfig = JSONObject().apply {
            put("totalWeek", totalWeek)
            put("startSemester", startSemester)
            put("startWithSunday", pendingConfig?.optBoolean("startWithSunday") ?: false)
            put("showWeekend", pendingConfig?.optBoolean("showWeekend") ?: false)
            put("forenoon", forenoon)
            put("afternoon", afternoon)
            put("night", night)
            put("sections", finalSections)
        }
        return TimerBuildResult(timerConfig = timerConfig, courses = resolved.courses)
    }

    private data class ResolvedCourseSections(
        val sections: JSONArray,
        val courses: List<Course>
    )

    private fun resolveCustomTimeCourses(courses: List<Course>, baseSections: JSONArray): ResolvedCourseSections {
        val sections = JSONArray(baseSections.toString())

        val resolvedCourses = courses.map { course ->
            course.copyResolvedCourse().also { resolvedCourse ->
                if (!course.isCustomTime || course.hasExplicitSectionRange) return@also

                val start = course.customStartTime.trim()
                val end = course.customEndTime.trim()
                if (start.isEmpty() || end.isEmpty()) return@also

                val mappedSections = findBestSectionRange(sections, start, end)
                if (mappedSections.isNotEmpty()) {
                    resolvedCourse.sections = mappedSections
                }
            }
        }
        return ResolvedCourseSections(sections = sections, courses = resolvedCourses)
    }

    private data class TimeSlotCandidate(
        val name: String,
        val buildings: List<String>,
        val sections: JSONArray
    )

    private data class SectionRow(
        val section: Int,
        val startTime: String,
        val endTime: String
    )

    private fun parseTimeSlotCandidates(raw: String?): List<TimeSlotCandidate> {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return emptyList()
        return runCatching {
            if (value.startsWith("[")) {
                val sections = normalizeSections(JSONArray(value))
                if (sections.length() == 0) emptyList()
                else listOf(TimeSlotCandidate("default", emptyList(), sections))
            } else {
                val root = JSONObject(value)
                val schedules = root.optJSONArray("schedules")
                if (schedules != null && schedules.length() > 0) {
                    buildList {
                        for (i in 0 until schedules.length()) {
                            val item = schedules.optJSONObject(i) ?: continue
                            val sections = normalizeSections(item.optJSONArray("sections") ?: JSONArray())
                            if (sections.length() == 0) continue
                            add(
                                TimeSlotCandidate(
                                    name = item.optString("scheduleType", "schedule_$i"),
                                    buildings = jsonArrayToStrings(item.optJSONArray("applicableBuildings")),
                                    sections = sections
                                )
                            )
                        }
                    }
                } else {
                    emptyList()
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun chooseBestTimeSlotCandidate(
        candidates: List<TimeSlotCandidate>,
        courses: List<Course>
    ): TimeSlotCandidate? {
        if (candidates.isEmpty()) return null
        val customCourses = courses.filter {
            it.isCustomTime && !it.hasExplicitSectionRange &&
                it.customStartTime.isNotBlank() && it.customEndTime.isNotBlank()
        }
        if (customCourses.isEmpty()) return candidates.first()

        return candidates.maxByOrNull { candidate ->
            customCourses.sumOf { course ->
                val mapping = findBestSectionRange(candidate.sections, course.customStartTime, course.customEndTime)
                val matchScore = if (mapping.isNotEmpty()) 1000 else 0
                val buildingBoost = if (candidate.buildings.any { b -> course.position.contains(b, ignoreCase = true) }) 100 else 0
                val distancePenalty = bestRangeDistance(candidate.sections, course.customStartTime, course.customEndTime)
                matchScore + buildingBoost - distancePenalty
            }
        }
    }

    private fun findBestSectionRange(sections: JSONArray, start: String, end: String): String {
        val rows = parseSectionRows(sections)
        if (rows.isEmpty()) return ""

        rows.firstOrNull { it.startTime == start && it.endTime == end }?.let {
            return it.section.toString()
        }

        var bestScore = Int.MAX_VALUE
        var bestRange: IntRange? = null
        for (i in rows.indices) {
            for (j in i until rows.size) {
                if (j > i && rows[j].section != rows[j - 1].section + 1) break
                val score = timeDistance(rows[i].startTime, start) + timeDistance(rows[j].endTime, end)
                if (score < bestScore) {
                    bestScore = score
                    bestRange = rows[i].section..rows[j].section
                }
                if (rows[i].startTime == start && rows[j].endTime == end) {
                    return (rows[i].section..rows[j].section).joinToString(",")
                }
            }
        }
        return bestRange?.joinToString(",").orEmpty()
    }

    private fun bestRangeDistance(sections: JSONArray, start: String, end: String): Int {
        val rows = parseSectionRows(sections)
        if (rows.isEmpty()) return Int.MAX_VALUE / 4
        var best = Int.MAX_VALUE / 4
        for (i in rows.indices) {
            for (j in i until rows.size) {
                if (j > i && rows[j].section != rows[j - 1].section + 1) break
                best = minOf(best, timeDistance(rows[i].startTime, start) + timeDistance(rows[j].endTime, end))
            }
        }
        return best
    }

    private fun parseSectionRows(sections: JSONArray): List<SectionRow> {
        return buildList {
            for (i in 0 until sections.length()) {
                val obj = sections.optJSONObject(i) ?: continue
                val section = obj.optInt("section", -1)
                val start = obj.optString("startTime").trim()
                val end = obj.optString("endTime").trim()
                if (section > 0 && start.isNotEmpty() && end.isNotEmpty()) {
                    add(SectionRow(section, start, end))
                }
            }
        }.sortedBy { it.section }
    }

    private fun timeDistance(left: String, right: String): Int {
        val leftMinutes = parseClockMinutes(left)
        val rightMinutes = parseClockMinutes(right)
        if (leftMinutes < 0 || rightMinutes < 0) return Int.MAX_VALUE / 8
        return abs(leftMinutes - rightMinutes)
    }

    private fun jsonArrayToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    }

    private fun inferSessionCounts(sections: JSONArray): Triple<Int, Int, Int> {
        val morningSections = linkedSetOf<Int>()
        val afternoonSections = linkedSetOf<Int>()
        val nightSections = linkedSetOf<Int>()

        for (i in 0 until sections.length()) {
            val obj = sections.optJSONObject(i) ?: continue
            val section = obj.optInt("section", -1)
            if (section <= 0) continue
            val startMinutes = parseClockMinutes(obj.optString("startTime"))
            when {
                startMinutes in 0 until 12 * 60 -> morningSections += section
                startMinutes in 12 * 60 until 18 * 60 -> afternoonSections += section
                startMinutes >= 18 * 60 -> nightSections += section
                else -> morningSections += section
            }
        }

        return Triple(
            morningSections.size,
            afternoonSections.size,
            nightSections.size
        )
    }

    private fun parseClockMinutes(raw: String?): Int {
        val value = raw.orEmpty().trim()
        val parts = value.split(":")
        if (parts.size < 2) return -1
        val hour = parts[0].toIntOrNull() ?: return -1
        val minute = parts[1].toIntOrNull() ?: return -1
        return hour * 60 + minute
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

    private fun Course.copyResolvedCourse(): Course {
        return Course().also { copy ->
            copy.name = name
            copy.teacher = teacher
            copy.position = position
            copy.day = day
            copy.sections = sections
            copy.weeks = weeks
            copy.isCustomTime = isCustomTime
            copy.customStartTime = customStartTime
            copy.customEndTime = customEndTime
            copy.hasExplicitSectionRange = hasExplicitSectionRange
        }
    }

    private fun normalizeStartSemester(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return currentDayStartMillisUtc8()
        value.toLongOrNull()?.let { numeric ->
            return if (numeric in 1L..99_999_999_999L) {
                (numeric * 1000L).toString()
            } else {
                numeric.toString()
            }
        }
        return value
    }

    private fun currentDayStartMillisUtc8(): String {
        return LocalDate.now(ZoneOffset.ofHours(8))
            .atStartOfDay()
            .toInstant(ZoneOffset.ofHours(8))
            .toEpochMilli()
            .toString()
    }
}
