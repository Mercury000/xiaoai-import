package me.padi.xiaoai.screen

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.padi.xiaoai.R
import me.padi.xiaoai.click.importCourseFormJw
import me.padi.xiaoai.click.openContributorQQ
import me.padi.xiaoai.click.queryScoreFormSchool
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import org.json.JSONObject
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.LocalWindowBottomSheetState
import top.yukonga.miuix.kmp.extra.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class ModuleScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                var showBottomSheet = remember { mutableStateOf(false) }

                var url by remember { mutableStateOf<String>(HookEntry.prefs.getString("debug_jw_url", "")) }
                var javaScriptStr by remember { mutableStateOf("") }
                val context = LocalContext.current
                Scaffold { paddingValues ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    ) {
                        item {
                            WindowBottomSheet(
                                show = showBottomSheet,
                                title = "JavaScript注入",
                                onDismissRequest = { showBottomSheet.value = false }) {
                                val dismiss = LocalWindowBottomSheetState.current
                                TextField(
                                    value = url,
                                    onValueChange = { newValue: String -> url = newValue },
                                    label = "教务系统链接"
                                )
                                Spacer(Modifier.height(10.dp))
                                TextField(
                                    modifier = Modifier.heightIn(max = 250.dp),
                                    value = javaScriptStr,
                                    onValueChange = { javaScriptStr = it },
                                    label = "JavaScript"
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        modifier = Modifier.weight(1f),
                                        text = "关闭",
                                        onClick = { dismiss?.invoke() })
                                    Spacer(Modifier.width(16.dp))
                                    Button(
                                        modifier = Modifier.weight(1f), onClick = {
                                            HookEntry.prefs.edit().putString("debug_jw_url", url).apply()
                                            dismiss?.invoke()
                                            (context as Activity).finish()
                                            val intent = Intent(context, WebViewScreen::class.java).apply {
                                                putExtra("url", url)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        }, colors = ButtonDefaults.buttonColorsPrimary()
                                    ) {
                                        Text(
                                            "进入", color = MiuixTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Spacer(Modifier.height(20.dp))
                            }
                            Image(
                                modifier = Modifier.fillMaxWidth(),
                                painter = painterResource(id = R.drawable.xiaoai),
                                contentDescription = null
                            )
                            Spacer(Modifier.height(10.dp))
                            SmallTitle(
                                text = "教务系统", insideMargin = PaddingValues(16.dp, 4.dp)
                            )
                            Card {
                                BasicComponent(
                                    title = "导入课表",
                                    summary = "从教务系统中导入课表",
                                    startAction = {
                                        Icon(
                                            modifier = Modifier.padding(end = 16.dp),
                                            painter = painterResource(R.drawable.database_upload_24px),
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onBackground
                                        )
                                    },
                                    onClick = {
                                        importCourseFormJw(context)
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            Card {
                                BasicComponent(
                                    title = "成绩查询",
                                    summary = "从指定学校查询成绩",
                                    startAction = {
                                        Icon(
                                            modifier = Modifier.padding(end = 16.dp),
                                            painter = painterResource(R.drawable.social_leaderboard_24px),
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onBackground
                                        )
                                    },
                                    onClick = {
                                        queryScoreFormSchool(context)
                                    })
                            }

                            Spacer(Modifier.height(10.dp))
                            SmallTitle(
                                text = "调试", insideMargin = PaddingValues(16.dp, 4.dp)
                            )
                            Card {
                                BasicComponent(
                                    title = "JavaScript注入",
                                    summary = "调用小爱的界面点击导入后自动注入JavaScript",
                                    startAction = {
                                        Icon(
                                            modifier = Modifier.padding(end = 16.dp),
                                            painter = painterResource(R.drawable.frame_bug_24px),
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onBackground
                                        )
                                    },
                                    onClick = {
                                        showBottomSheet.value = true
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            SmallTitle(
                                text = "首席开发者", insideMargin = PaddingValues(16.dp, 4.dp)
                            )
                            Card {
                                BasicComponent(
                                    title = "帕帝天秀",
                                    summary = "小爱课程表的忠实粉丝",
                                    startAction = {
                                        AsyncImage(
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .size(50.dp)
                                                .clip(
                                                    CircleShape
                                                ),
                                            model = "https://q.qlogo.cn/headimg_dl?dst_uin=3373587110&spec=640&img_type=jpg",
                                            contentDescription = null,

                                            )
                                    },
                                    onClick = {
                                        openContributorQQ(context, "3373587110")
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            SmallTitle(
                                text = "开发者", insideMargin = PaddingValues(16.dp, 4.dp)
                            )
                            Card {
                                BasicComponent(
                                    title = "Mercury",
                                    summary = "AI导入课表部分全部代码实现",
                                    startAction = {
                                        AsyncImage(
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .size(50.dp)
                                                .clip(
                                                    CircleShape
                                                ),
                                            model = "https://q.qlogo.cn/headimg_dl?dst_uin=3038899204&spec=640&img_type=jpg",
                                            contentDescription = null,

                                            )
                                    },
                                    onClick = {
                                        openContributorQQ(context, "3038899204")
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            Card {
                                BasicComponent(
                                    title = "颜致恒plus",
                                    summary = "教务导入思路提供者",
                                    startAction = {
                                        AsyncImage(
                                            modifier = Modifier
                                                .padding(end = 16.dp)
                                                .size(50.dp)
                                                .clip(
                                                    CircleShape
                                                ),
                                            model = "https://q.qlogo.cn/headimg_dl?dst_uin=2488971290&spec=640&img_type=jpg",
                                            contentDescription = null,

                                            )
                                    },
                                    onClick = {
                                        openContributorQQ(context, "2488971290")
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            SmallTitle(
                                text = "贡献者", insideMargin = PaddingValues(16.dp, 4.dp)
                            )

                            Card {
                                BasicComponent(title = "川意", startAction = {
                                    AsyncImage(
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(50.dp)
                                            .clip(
                                                CircleShape
                                            ),
                                        model = "https://q.qlogo.cn/headimg_dl?dst_uin=3299699002&spec=640&img_type=jpg",
                                        contentDescription = null,

                                        )
                                }, onClick = {
                                    openContributorQQ(context, "3299699002")
                                })
                            }
                            Spacer(Modifier.height(10.dp))
                            Card {
                                BasicComponent(title = "Aven Cole", startAction = {
                                    AsyncImage(
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(50.dp)
                                            .clip(
                                                CircleShape
                                            ),
                                        model = "https://q.qlogo.cn/headimg_dl?dst_uin=1587005702&spec=640&img_type=jpg",
                                        contentDescription = null,

                                        )
                                }, onClick = {
                                    openContributorQQ(context, "1587005702")
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}