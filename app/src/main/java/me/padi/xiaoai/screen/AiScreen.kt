package com.mercury.xiaoaiimport.screen

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
import com.mercury.xiaoaiimport.writablePrefs
import com.mercury.xiaoaiimport.HostCompat
import org.json.JSONObject
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
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Rename
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

class AiScreen : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scrollBehavior = MiuixScrollBehavior()

            // 从SharedPreferences读取保存的值
            var apiUrl by remember {
                mutableStateOf(context.writablePrefs().getString("api_url", "https://dashscope.aliyuncs.com/compatible-mode/v1") ?: "https://dashscope.aliyuncs.com/compatible-mode/v1")
            }
            var modelName by remember {
                mutableStateOf(context.writablePrefs().getString("model_name", "qwen3-coder-plus") ?: "qwen3-coder-plus")
            }
            var jwUrl by remember {
                mutableStateOf(context.writablePrefs().getString("jw_webview_url", "") ?: "")
            }
            var apiKey by remember {
                mutableStateOf(context.writablePrefs().getString("api_key", "") ?: "")
            }
            val isAllFieldsFilled = remember(apiUrl, modelName, apiKey, jwUrl) {
                apiUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank() && jwUrl.isNotBlank()
            }
            var passwordVisible by remember { mutableStateOf(false) }
            MiuixTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "AI 解析配置", scrollBehavior = scrollBehavior
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
                                    value = modelName, onValueChange = { newValue: String ->
                                        modelName = newValue
                                        if (newValue.isNotBlank()) {
                                            context.writablePrefs().edit().putString("model_name", newValue).apply()
                                        }
                                    }, label = "模型名称"
                                )

                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = apiKey,
                                    onValueChange = { newValue: String ->
                                        apiKey = newValue
                                        context.writablePrefs().edit().putString("api_key", newValue).apply()
                                    },
                                    label = "ApiKey"
                                )

                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = apiUrl, onValueChange = { newValue: String ->
                                        apiUrl = newValue
                                        context.writablePrefs().edit().putString("api_url", newValue).apply()
                                    }, label = "Api地址"
                                )

                                Spacer(Modifier.height(8.dp))

                                TextField(
                                    value = jwUrl,
                                    onValueChange = { newValue: String ->
                                        jwUrl = newValue
                                        context.writablePrefs().edit().putString("jw_webview_url", newValue).apply()
                                    },
                                    label = "教务系统链接"
                                )

                                Spacer(Modifier.height(8.dp))

                                Button(
                                    modifier = Modifier.fillMaxWidth(), onClick = {
                                        if (isAllFieldsFilled) {
                                            val intent = Intent(context, WebViewScreen::class.java).apply {
                                                putExtra("url", jwUrl)
                                                putExtra("title", "AI解析导入")
                                                putExtra("text", "请在登录后点击下方“开始解析导入”按钮")
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "请先填写完整的配置（教务链接、模型名称、ApiKey 等）",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }, colors = ButtonDefaults.buttonColorsPrimary()
                                ) {
                                    Text(
                                        "进入教务并开始解析", color = MiuixTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
