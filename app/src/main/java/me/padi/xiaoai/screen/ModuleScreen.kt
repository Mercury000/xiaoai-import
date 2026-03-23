package me.padi.xiaoai.screen

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import me.padi.xiaoai.R
import me.padi.xiaoai.click.openContributorQQ
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.writablePrefs
import me.padi.xiaoai.HostCompat
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
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        
        // 捕获并保存 Token (从 WebAppInterface.navSchoolScreen 传来的 Intent)
        val token = intent.getStringExtra("service_token")
        val deviceId = intent.getStringExtra("device_id")
        if (!token.isNullOrBlank() || !deviceId.isNullOrBlank()) {
            HostCompat.saveTokens(this, token, deviceId)
        }

        enableEdgeToEdge()
        setContent {
            MiuixTheme {
                var showBottomSheet = remember { mutableStateOf(false) }
                var showShiguangSourceSheet = remember { mutableStateOf(false) }

                var url by remember { mutableStateOf<String>(HookEntry.prefs.getString("debug_jw_url", "")) }
                var javaScriptStr by remember { mutableStateOf<String>(HookEntry.prefs.getString("debug_jw_script", "")) }
                var shiguangRepoUrl by remember {
                    mutableStateOf<String>(HookEntry.prefs.getString("debug_shiguang_repo_url", "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse") ?: "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse")
                }
                var shiguangRepoBranch by remember {
                    mutableStateOf<String>(HookEntry.prefs.getString("debug_shiguang_repo_branch", "main") ?: "main")
                }
                val context = LocalContext.current

                BackHandler(enabled = showBottomSheet.value || showShiguangSourceSheet.value) {
                    when {
                        showBottomSheet.value -> showBottomSheet.value = false
                        showShiguangSourceSheet.value -> showShiguangSourceSheet.value = false
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold { paddingValues ->
                        LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding()),
                    ) {
                        item {
                            /* JS Sheet moved */
                            /* Source Sheet moved */
                            Image(
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                painter = painterResource(id = R.drawable.xiaoai),
                                contentDescription = null
                            )
                            Spacer(Modifier.height(10.dp))
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
                            Card {
                                BasicComponent(
                                    title = "自定义拾光仓库源",
                                    summary = "可配置仓库 URL 和脚本分支",
                                    startAction = {
                                        Icon(
                                            modifier = Modifier.padding(end = 16.dp),
                                            painter = painterResource(R.drawable.social_leaderboard_24px),
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onBackground
                                        )
                                    },
                                    onClick = {
                                        showShiguangSourceSheet.value = true
                                    })
                            }
                            Spacer(Modifier.height(10.dp))
                            SmallTitle(
                                text = "开发者", insideMargin = PaddingValues(16.dp, 4.dp)
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
                            Card {
                                BasicComponent(
                                    title = "Mercury",
                                    summary = "AI导入课表部分全部代码实现；超级小爱适配",
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

                if (showBottomSheet.value) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)).clickable{ showBottomSheet.value=false })
                }
                AnimatedVisibility(
                    visible = showBottomSheet.value,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(topStart=16.dp, topEnd=16.dp))
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp, bottom = 4.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .background(
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Text("JavaScript注入", fontSize=18.sp, modifier=Modifier.padding(vertical=16.dp))
                        TextField(value = url, onValueChange = { url = it }, label = "教务系统链接")
                        Spacer(Modifier.height(10.dp))
                        TextField(modifier = Modifier.heightIn(max=250.dp), value = javaScriptStr, onValueChange = { javaScriptStr = it }, label = "JavaScript")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(modifier = Modifier.weight(1f), text = "关闭", onClick = { showBottomSheet.value = false })
                            Spacer(Modifier.width(16.dp))
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    context.writablePrefs().edit().putString("debug_jw_url", url).putString("debug_jw_script", javaScriptStr).apply()
                                    showBottomSheet.value = false
                                    (context as Activity).finish()
                                    val intent = Intent(context, WebViewScreen::class.java).apply {
                                        putExtra("url", url); putExtra("script", javaScriptStr); putExtra("title", "JavaScript注入")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) { Text("进入", color = MiuixTheme.colorScheme.onPrimary) }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }

                if (showShiguangSourceSheet.value) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)).clickable{ showShiguangSourceSheet.value=false })
                }
                AnimatedVisibility(
                    visible = showShiguangSourceSheet.value,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MiuixTheme.colorScheme.surface, RoundedCornerShape(topStart=16.dp, topEnd=16.dp))
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 12.dp, bottom = 4.dp)
                                .size(width = 40.dp, height = 4.dp)
                                .background(
                                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Text("自定义拾光仓库源", fontSize=18.sp, modifier=Modifier.padding(vertical=16.dp))
                        TextField(value = shiguangRepoUrl, onValueChange = { shiguangRepoUrl = it }, label = "仓库 URL")
                        Spacer(Modifier.height(10.dp))
                        TextField(value = shiguangRepoBranch, onValueChange = { shiguangRepoBranch = it }, label = "脚本分支")
                        Spacer(Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(modifier = Modifier.weight(1f), text = "重置", onClick = { shiguangRepoUrl = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"; shiguangRepoBranch = "main" })
                            Spacer(Modifier.width(16.dp))
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    context.writablePrefs().edit().putString("debug_shiguang_repo_url", shiguangRepoUrl).putString("debug_shiguang_repo_branch", shiguangRepoBranch).apply()
                                    showShiguangSourceSheet.value = false
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) { Text("保存", color = MiuixTheme.colorScheme.onPrimary) }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
            }
        }
    }
}
