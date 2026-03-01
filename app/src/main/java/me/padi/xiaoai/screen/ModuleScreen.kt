package me.padi.xiaoai.screen

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import me.padi.xiaoai.hook.MainHook.prefs
import top.sacz.xphelper.activity.BaseActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class ModuleScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scrollBehavior = MiuixScrollBehavior()

            // 从SharedPreferences读取保存的值
            var apiUrl by remember {
                mutableStateOf(prefs.native().getString("api_url", ""))
            }
            var modelName by remember {
                mutableStateOf(prefs.native().getString("model_name", ""))
            }
            var apiKey by remember {
                mutableStateOf(prefs.native().getString("api_key", ""))
            }

            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            val isAllFieldsFilled = remember(apiUrl, modelName, apiKey) {
                apiUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank()
            }
            var passwordVisible by remember { mutableStateOf(false) }
            MiuixTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "小爱课程表"
                        )
                    }) { paddingValues ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = paddingValues.calculateTopPadding())
                    ) {
                        item {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Spacer(Modifier.height(8.dp))
                                TextField(
                                    value = apiUrl, onValueChange = { newValue ->
                                        apiUrl = newValue
                                        // 不为空时自动保存
                                        if (newValue.isNotBlank()) {
                                            prefs.native().edit {
                                                putString("api_url", newValue)
                                            }
                                        }
                                    }, label = "Api地址"
                                )

                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = modelName, onValueChange = { newValue ->
                                        modelName = newValue
                                        if (newValue.isNotBlank()) {
                                            prefs.native().edit {
                                                putString("model_name", newValue)
                                            }
                                        }
                                    }, label = "模型名称"
                                )

                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = apiKey,
                                    onValueChange = { newValue ->
                                        apiKey = newValue
                                        if (newValue.isNotBlank()) {
                                            prefs.native().edit {
                                                putString("api_key", newValue)
                                            }
                                        }
                                    },
                                    label = "ApiKey",
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier.padding(end = 12.dp)
                                        ) {
                                            Icon(
                                                imageVector = MiuixIcons.Rename,
                                                tint = if (passwordVisible) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSecondaryContainer,
                                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                                            )
                                        }
                                    })
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    modifier = Modifier.fillMaxWidth(), onClick = {
                                        if (isAllFieldsFilled) {
                                            val intent = Intent(context, SchoolScreen::class.java)
                                            intent.putExtra(
                                                "proxy_target_activity",
                                                "com.xiaomi.aischedule.activity.DeleteAccountActivity"
                                            )
                                            context.startActivity(intent)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "请先填写完整的API信息（Api地址、模型名称、ApiKey）",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }, colors = ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    Text(
                                        "进入导入课表页面", color = MiuixTheme.colorScheme.onPrimary
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                SuperArrow(
                                    title = "作者", summary = "帕帝天秀", onClick = {
                                        uriHandler.openUri("https://qm.qq.com/q/xbS3z4KP16")
                                    })
                                Spacer(Modifier.height(8.dp))


                                SuperArrow(
                                    title = "特别鸣谢", summary = "Mercury", onClick = {
                                        uriHandler.openUri("https://www.coolapk.com/u/3336736")
                                    })

                                Spacer(Modifier.height(8.dp))


                                SuperArrow(
                                    title = "特别鸣谢", summary = "颜致恒plus", onClick = {
                                        uriHandler.openUri("https://space.bilibili.com/516163236")
                                    })

                                Spacer(Modifier.height(8.dp))


                                SuperArrow(
                                    title = "交流群", summary = "适配你的学校", onClick = {
                                        uriHandler.openUri("https://qm.qq.com/q/93j8jE1vWw")
                                    })


                            }
                        }
                    }
                }
            }
        }
    }
}