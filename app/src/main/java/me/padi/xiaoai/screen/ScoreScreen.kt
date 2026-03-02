package me.padi.xiaoai.screen

import android.app.Activity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

class ScoreScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val scoreItems = mutableListOf<ScoreItem>()
        val scoreJson = intent.getStringExtra("json") ?: ""
        val jsonArray = JSONArray(scoreJson)

        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            scoreItems.add(
                ScoreItem(
                    courseName = item.optString("courseName"),
                    totalScore = item.optString("totalScore"),
                    courseType = item.optString("courseType"),
                    examType = item.optString("examType"),
                    semester = item.optString("semester"),
                    credit = item.optString("credit"),
                    teacherName = item.optString("teacherName"),
                    department = item.optString("department")
                )
            )
        }

        setContent {
            val scrollBehavior = MiuixScrollBehavior()

            var tabs by remember { mutableStateOf(listOf("全部")) }
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            var filteredScores by remember { mutableStateOf<List<ScoreItem>>(emptyList()) }

            val context = LocalContext.current

            LaunchedEffect(selectedTabIndex) {
                val scores = scoreItems
                val semesters = scores.map { it.semester }
                val newTabs = listOf("全部") + semesters.distinct()
                if (newTabs != tabs) {
                    tabs = newTabs
                }
                val selectedSemester = if (selectedTabIndex == 0) {
                    null
                } else {
                    tabs[selectedTabIndex]
                }

                filteredScores = if (selectedSemester == null) {
                    scores
                } else {
                    scores.filter { it.semester == selectedSemester }
                }
            }

            MiuixTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            scrollBehavior = scrollBehavior,
                            title = "考试成绩",
                            navigationIcon = {
                                IconButton(
                                    modifier = Modifier.padding(start = 16.dp), onClick = {
                                        (context as Activity).finish()
                                    }) {
                                    Icon(MiuixIcons.Back, contentDescription = "返回")
                                }
                            },
                            actions = {})
                    }) { paddingValues ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .padding(16.dp),
                        contentPadding = PaddingValues(
                            top = paddingValues.calculateTopPadding()
                        )
                    ) {
                        item {
                            SuperDropdown(
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                                title = "按学期筛选",
                                items = tabs,
                                selectedIndex = selectedTabIndex,
                                onSelectedIndexChange = { selectedTabIndex = it })
                        }
                        items(filteredScores.size) { index ->
                            ScoreCard(filteredScores[index])
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScoreCard(score: ScoreItem) {
    Spacer(Modifier.height(8.dp))
    SmallTitle(
        text = score.teacherName, insideMargin = PaddingValues(8.dp, 0.dp)
    )
    Card(
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        BasicComponent(
            title = score.courseName, summary = score.courseType, endActions = {
                Text(
                    text = score.totalScore, color = MiuixTheme.colorScheme.primary
                )
            })
    }
}

data class ScoreItem(
    val courseName: String = "",
    val totalScore: String = "",
    val courseType: String = "",
    val examType: String = "",
    val semester: String = "",
    val credit: String = "",
    val teacherName: String = "",
    val department: String = ""
)