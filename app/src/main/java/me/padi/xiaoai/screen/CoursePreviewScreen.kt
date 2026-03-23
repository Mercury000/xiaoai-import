package me.padi.xiaoai.screen

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.padi.xiaoai.Course
import me.padi.xiaoai.CourseRepository
import me.padi.xiaoai.HostCompat
import me.padi.xiaoai.readPreviewCourses
import me.padi.xiaoai.readPreviewSchedule
import me.padi.xiaoai.readPreviewTableName
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CoursePreviewScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                val initialName = intent.readPreviewTableName().ifBlank { "提取课表" }
                val initialCourses = intent.readPreviewCourses()
                val initialSchedule = intent.readPreviewSchedule()

                val courses = remember { mutableStateListOf<Course>().apply { addAll(initialCourses) } }
                val normalizedCourses = courses.map { it.copyCourse() }
                val conflictMap = buildConflictMap(normalizedCourses)
                val conflictCourseCount = conflictMap.size
                val invalidCount = normalizedCourses.count { it.isInvalid }
                val autoCorrectCount = normalizedCourses.count { it.isAutoCorrected }
                val displayIndexes = courses.indices.sortedWith(
                    compareBy<Int>(
                        { normalizedCourses[it].day },
                        { parseStartNumber(normalizedCourses[it].sections) },
                        { parseStartNumber(normalizedCourses[it].weeks) }
                    )
                )

                var tableName by remember { mutableStateOf(initialName) }
                var importing by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var editingIndex by remember { mutableStateOf<Int?>(null) }
                var showConflictConfirm by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                fun startImport(finalCourses: List<Course>) {
                    importing = true
                    message = "正在导入..."
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                CourseRepository.importCourses(
                                    this@CoursePreviewScreen,
                                    HostCompat.getAppId(),
                                    tableName.trim(),
                                    finalCourses.map { it.copyCourse() },
                                    initialSchedule
                                )
                            }
                            message = "导入成功"
                            finish()
                        } catch (e: Exception) {
                            message = "失败: ${e.message}"
                        } finally {
                            importing = false
                        }
                    }
                }

                Scaffold(topBar = { SmallTopAppBar(title = "课程预览") }) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        TextField(
                            value = tableName,
                            onValueChange = { tableName = it },
                            label = "课表名称"
                        )
                        Spacer(Modifier.height(8.dp))

                        if (invalidCount > 0) {
                            Text(
                                "检测到 $invalidCount 门课程格式不合法，已标红，请先修正。",
                                color = Color(0xFFE53935),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (conflictCourseCount > 0) {
                            Text(
                                "检测到 $conflictCourseCount 门课程存在时间冲突，请检查，冲突课程无法导入。",
                                color = Color(0xFFE53935),
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        if (autoCorrectCount > 0) {
                            Text(
                                "已自动纠正规范化 $autoCorrectCount 门课程的节次/周次，请确认结果。",
                                color = MiuixTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(displayIndexes) { index ->
                                val source = courses[index]
                                val normalized = normalizedCourses[index]
                                val conflictNames = conflictMap[index]
                                val highlight = normalized.isInvalid || conflictNames != null

                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(
                                        modifier = Modifier
                                            .padding(12.dp)
                                    ) {
                                        Text(source.name.ifBlank { "(未命名课程)" }, fontSize = 16.sp)
                                        Text(
                                            "${dayLabel(normalized.day)}  第${normalized.sections}节  周次:${normalized.weeks}",
                                            fontSize = 12.sp,
                                            color = if (highlight) Color(0xFFE53935) else MiuixTheme.colorScheme.onSurface
                                        )
                                        if (source.teacher.isNotBlank() || source.position.isNotBlank()) {
                                            Text(
                                                "${source.teacher} ${source.position}".trim(),
                                                fontSize = 12.sp,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (normalized.isInvalid) {
                                            Text(
                                                "错误: ${normalized.invalidReason}",
                                                color = Color(0xFFE53935),
                                                fontSize = 11.sp
                                            )
                                        } else if (normalized.isAutoCorrected && normalized.autoCorrectedReason.isNotBlank()) {
                                            Text(
                                                "已纠正: ${normalized.autoCorrectedReason}",
                                                color = Color(0xFFD97B00),
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (conflictNames != null) {
                                            Text(
                                                "冲突课程: $conflictNames",
                                                color = Color(0xFFE53935),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = "编辑",
                                                fontSize = 12.sp,
                                                color = Color(0xFF1565C0),
                                                modifier = Modifier
                                                    .background(
                                                        color = Color(0xFFE3F2FD),
                                                        shape = RoundedCornerShape(999.dp)
                                                    )
                                                    .clickable { editingIndex = index }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "删除",
                                                fontSize = 12.sp,
                                                color = Color(0xFFC62828),
                                                modifier = Modifier
                                                    .background(
                                                        color = Color(0xFFFFEBEE),
                                                        shape = RoundedCornerShape(999.dp)
                                                    )
                                                    .clickable { courses.removeAt(index) }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (message.isNotBlank()) {
                            Text(
                                message,
                                color = if (message.startsWith("失败")) Color(0xFFE53935) else MiuixTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(6.dp))
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !importing,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                            onClick = {
                                val name = tableName.trim()
                                if (name.isBlank()) {
                                    message = "失败: 请输入课表名称"
                                    return@Button
                                }
                                if (courses.isEmpty()) {
                                    message = "失败: 课程列表为空"
                                    return@Button
                                }
                                if (invalidCount > 0) {
                                    message = "失败: 请先修正标红课程"
                                    return@Button
                                }
                                if (conflictCourseCount > 0) {
                                    showConflictConfirm = true
                                    return@Button
                                }
                                startImport(normalizedCourses)
                            }
                        ) {
                            if (importing) {
                                CircularProgressIndicator()
                            } else {
                                Text("确认导入", color = MiuixTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                val idx = editingIndex
                if (idx != null && idx in courses.indices) {
                    EditCourseDialog(
                        source = courses[idx],
                        onDismiss = { editingIndex = null },
                        onSave = { edited ->
                            courses[idx] = edited
                            editingIndex = null
                        }
                    )
                }

                if (showConflictConfirm) {
                    ConflictImportConfirmDialog(
                        conflictCourseCount = conflictCourseCount,
                        onDismiss = { showConflictConfirm = false },
                        onConfirm = {
                            showConflictConfirm = false
                            startImport(normalizedCourses)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCourseDialog(
    source: Course,
    onDismiss: () -> Unit,
    onSave: (Course) -> Unit
) {
    var name by remember { mutableStateOf(source.name) }
    var teacher by remember { mutableStateOf(source.teacher) }
    var position by remember { mutableStateOf(source.position) }
    var dayText by remember { mutableStateOf(source.day.toString()) }
    var sections by remember { mutableStateOf(source.sections) }
    var weeks by remember { mutableStateOf(source.weeks) }
    var error by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("编辑课程", fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                TextField(value = name, onValueChange = { name = it }, label = "课程名")
                Spacer(Modifier.height(6.dp))
                TextField(value = teacher, onValueChange = { teacher = it }, label = "教师")
                Spacer(Modifier.height(6.dp))
                TextField(value = position, onValueChange = { position = it }, label = "地点")
                Spacer(Modifier.height(6.dp))
                TextField(value = dayText, onValueChange = { dayText = it }, label = "星期(1-7)")
                Spacer(Modifier.height(6.dp))
                TextField(value = sections, onValueChange = { sections = it }, label = "节次")
                Spacer(Modifier.height(6.dp))
                TextField(value = weeks, onValueChange = { weeks = it }, label = "周次")
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = Color(0xFFE53935), fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.weight(1f))
                    TextButton(text = "保存", onClick = {
                        val day = dayText.trim().toIntOrNull()
                        if (day == null) {
                            error = "星期必须是数字"
                            return@TextButton
                        }
                        val edited = source.copyCourse().apply {
                            this.name = name.trim()
                            this.teacher = teacher.trim()
                            this.position = position.trim()
                            this.day = day
                            this.sections = sections.trim()
                            this.weeks = weeks.trim()
                            sanitizeAndValidate()
                        }
                        if (edited.isInvalid) {
                            error = edited.invalidReason
                            return@TextButton
                        }
                        onSave(edited)
                    })
                }
            }
        }
    }
}

@Composable
private fun ConflictImportConfirmDialog(
    conflictCourseCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("冲突提醒", fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "检测到 $conflictCourseCount 门课程存在时间冲突，建议先编辑修正。仍要继续导入吗？",
                    color = Color(0xFFE53935),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(text = "取消", onClick = onDismiss)
                    Spacer(Modifier.weight(1f))
                    TextButton(text = "继续导入", onClick = onConfirm)
                }
            }
        }
    }
}

private fun Course.copyCourse(): Course {
    return Course().also { c ->
        c.name = name
        c.teacher = teacher
        c.position = position
        c.day = day
        c.sections = sections
        c.weeks = weeks
        c.style = style
        c.sanitizeAndValidate()
    }
}

private fun buildConflictMap(courses: List<Course>): Map<Int, String> {
    val indexToNames = mutableMapOf<Int, MutableSet<String>>()
    for (i in courses.indices) {
        val left = courses[i]
        if (left.isInvalid) continue
        for (j in i + 1 until courses.size) {
            val right = courses[j]
            if (right.isInvalid) continue
            if (left.day != right.day) continue
            if (!hasIntersection(parseNumberSet(left.sections), parseNumberSet(right.sections))) continue
            if (!hasIntersection(parseNumberSet(left.weeks), parseNumberSet(right.weeks))) continue
            indexToNames.getOrPut(i) { linkedSetOf() }.add(right.name.ifBlank { "未命名课程" })
            indexToNames.getOrPut(j) { linkedSetOf() }.add(left.name.ifBlank { "未命名课程" })
        }
    }
    return indexToNames.mapValues { entry -> entry.value.joinToString("、") }
}

private fun parseNumberSet(value: String): Set<Int> {
    return value.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .toSet()
}

private fun hasIntersection(left: Set<Int>, right: Set<Int>): Boolean {
    if (left.isEmpty() || right.isEmpty()) return false
    val smaller = if (left.size <= right.size) left else right
    val larger = if (left.size <= right.size) right else left
    return smaller.any { it in larger }
}

private fun parseStartNumber(value: String): Int {
    return Regex("\\d+")
        .find(value)
        ?.value
        ?.toIntOrNull()
        ?: Int.MAX_VALUE
}

private fun dayLabel(day: Int): String {
    return when (day) {
        1 -> "星期一"
        2 -> "星期二"
        3 -> "星期三"
        4 -> "星期四"
        5 -> "星期五"
        6 -> "星期六"
        7 -> "星期日"
        else -> "星期$day"
    }
}
