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
import androidx.compose.foundation.lazy.itemsIndexed
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
                var tableName by remember { mutableStateOf(initialName) }
                var importing by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("") }
                var editingIndex by remember { mutableStateOf<Int?>(null) }
                val scope = rememberCoroutineScope()

                Scaffold(
                    topBar = { SmallTopAppBar(title = "课程预览") }
                ) { padding ->
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

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            itemsIndexed(courses) { index, c ->
                                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(c.name.ifBlank { "(未命名课程)" }, fontSize = 16.sp)
                                        Text(
                                            "周${c.day}  第${c.sections}节  周次:${c.weeks}",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        if (c.teacher.isNotBlank() || c.position.isNotBlank()) {
                                            Text(
                                                "${c.teacher} ${c.position}".trim(),
                                                fontSize = 12.sp,
                                                color = MiuixTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (c.isInvalid) {
                                            Text(c.invalidReason, color = Color(0xFFE53935), fontSize = 11.sp)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = "编辑",
                                                fontSize = 12.sp,
                                                color = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .background(
                                                        color = MiuixTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { editingIndex = index }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = "删除",
                                                fontSize = 12.sp,
                                                color = Color(0xFFE53935),
                                                modifier = Modifier
                                                    .background(
                                                        color = MiuixTheme.colorScheme.surfaceVariant,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { courses.removeAt(index) }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
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
                                if (courses.any { it.isInvalid }) {
                                    message = "失败: 请先修正标红课程"
                                    return@Button
                                }
                                importing = true
                                message = "正在导入..."
                                val finalCourses = courses.map { it.copyCourse() }
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            CourseRepository.importCourses(
                                                this@CoursePreviewScreen,
                                                HostCompat.getAppId(),
                                                name,
                                                finalCourses,
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
                        val c = source.copyCourse().apply {
                            this.name = name.trim()
                            this.teacher = teacher.trim()
                            this.position = position.trim()
                            this.day = day
                            this.sections = sections.trim()
                            this.weeks = weeks.trim()
                            sanitizeAndValidate()
                        }
                        if (c.isInvalid) {
                            error = c.invalidReason
                            return@TextButton
                        }
                        onSave(c)
                    })
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

