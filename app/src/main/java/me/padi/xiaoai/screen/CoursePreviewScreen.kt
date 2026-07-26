package com.mercury.xiaoaiimport.screen

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kongzue.dialogx.dialogs.MessageDialog
import com.kongzue.dialogx.dialogs.TipDialog
import com.kongzue.dialogx.interfaces.OnBindView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mercury.xiaoaiimport.Course
import com.mercury.xiaoaiimport.PresetDataLauncher
import com.mercury.xiaoaiimport.R
import com.mercury.xiaoaiimport.readPreviewCourses
import com.mercury.xiaoaiimport.readPreviewSchedule
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

class CoursePreviewScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
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

                var importing by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                val scope = rememberCoroutineScope()

                fun startImport(finalCourses: List<Course>) {
                    importing = true
                    message = "正在唤起小爱..."
                    scope.launch {
                        try {
                            withContext(Dispatchers.Main) {
                                PresetDataLauncher.launch(
                                    context = this@CoursePreviewScreen,
                                    courses = finalCourses.map { it.copyCourse() },
                                    schedule = initialSchedule
                                )
                            }
                            message = "已唤起小爱，请在小爱内继续导入"
                        } catch (e: Exception) {
                            message = "失败: ${e.message}"
                        } finally {
                            importing = false
                        }
                    }
                }

                Scaffold(topBar = { SmallTopAppBar(title = "课程预览") }) { padding ->
                    val dialogContext = this@CoursePreviewScreen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = 16.dp)
                    ) {
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
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(source.name.ifBlank { "(未命名课程)" }, fontSize = 16.sp)
                                        Text(
                                            "${dayLabel(normalized.day)}  第${normalized.sections}节",
                                            fontSize = 12.sp,
                                            color = if (highlight) Color(0xFFE53935) else MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "周次:${normalized.weeks}",
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
                                                    .clickable {
                                                        showEditCourseDialog(dialogContext, source) { edited ->
                                                            courses[index] = edited
                                                        }
                                                    }
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
                                if (courses.isEmpty()) {
                                    message = "失败: 课程列表为空"
                                    return@Button
                                }
                                if (invalidCount > 0) {
                                    message = "失败: 请先修正标红课程"
                                    return@Button
                                }
                                if (conflictCourseCount > 0) {
                                    showConflictImportConfirmDialog(dialogContext, conflictCourseCount) {
                                        startImport(courses.map { it.copyCourse() })
                                    }
                                    return@Button
                                }
                                startImport(normalizedCourses)
                            }
                        ) {
                            if (importing) CircularProgressIndicator()
                            else Text("确认导入", color = MiuixTheme.colorScheme.onPrimary)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

private fun showEditCourseDialog(context: Activity, source: Course, onSave: (Course) -> Unit) {
    lateinit var nameEt: EditText
    lateinit var teacherEt: EditText
    lateinit var positionEt: EditText
    lateinit var dayEt: EditText
    lateinit var sectionsEt: EditText
    lateinit var weeksEt: EditText

    MessageDialog.build()
        .setTitle("编辑课程")
        .setMessage("")
        .setCustomView(object : OnBindView<MessageDialog>(R.layout.dialog_edit_course) {
            override fun onBind(dialog: MessageDialog, v: View) {
                nameEt = v.findViewById(R.id.etCourseName)
                teacherEt = v.findViewById(R.id.etTeacher)
                positionEt = v.findViewById(R.id.etPosition)
                dayEt = v.findViewById(R.id.etDay)
                sectionsEt = v.findViewById(R.id.etSections)
                weeksEt = v.findViewById(R.id.etWeeks)

                nameEt.setText(source.name)
                teacherEt.setText(source.teacher)
                positionEt.setText(source.position)
                dayEt.setText(source.day.toString())
                sectionsEt.setText(source.sections)
                weeksEt.setText(source.weeks)
            }
        })
        .setCancelButton("取消")
        .setOkButton("保存", object : com.kongzue.dialogx.interfaces.OnDialogButtonClickListener<MessageDialog> {
            override fun onClick(dialog: MessageDialog, v: View): Boolean {
                val day = dayEt.text?.toString()?.trim()?.toIntOrNull()
                if (day == null) {
                    TipDialog.show("星期必须是数字")
                    return true
                }

                val edited = source.copyCourse().apply {
                    name = nameEt.text?.toString()?.trim().orEmpty()
                    teacher = teacherEt.text?.toString()?.trim().orEmpty()
                    position = positionEt.text?.toString()?.trim().orEmpty()
                    this.day = day
                    sections = sectionsEt.text?.toString()?.trim().orEmpty()
                    weeks = weeksEt.text?.toString()?.trim().orEmpty()
                    sanitizeAndValidate()
                }
                if (edited.isInvalid) {
                    TipDialog.show(edited.invalidReason)
                    return true
                }
                onSave(edited)
                return false
            }
        })
        .show(context)
}

private fun showConflictImportConfirmDialog(
    context: Activity,
    conflictCourseCount: Int,
    onConfirm: () -> Unit
) {
    MessageDialog.build()
        .setTitle("冲突提醒")
        .setMessage("检测到 $conflictCourseCount 门课程存在时间冲突，如果导入可能导致整个课表导入失败，建议先编辑修正。仍要继续导入吗？")
        .setCancelButton("取消")
        .setOkButton("继续导入", object : com.kongzue.dialogx.interfaces.OnDialogButtonClickListener<MessageDialog> {
            override fun onClick(dialog: MessageDialog, v: View): Boolean {
                onConfirm()
                return false
            }
        })
        .show(context)
}

private fun Course.copyCourse(): Course {
    return Course().also { c ->
        c.name = name
        c.teacher = teacher
        c.position = position
        c.day = day
        c.sections = sections
        c.weeks = weeks
        c.isCustomTime = isCustomTime
        c.customStartTime = customStartTime
        c.customEndTime = customEndTime
        c.hasExplicitSectionRange = hasExplicitSectionRange
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
