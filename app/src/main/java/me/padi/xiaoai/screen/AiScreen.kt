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
import me.padi.xiaoai.hook.HookEntry
import me.padi.xiaoai.proxyActivity
import me.padi.xiaoai.writablePrefs
import me.padi.xiaoai.HostCompat
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
                mutableStateOf(context.writablePrefs().getString("api_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1")
            }
            var modelName by remember {
                mutableStateOf(context.writablePrefs().getString("model_name", "gpt-3.5-turbo") ?: "gpt-3.5-turbo")
            }
            var apiKey by remember {
                mutableStateOf(context.writablePrefs().getString("api_key", "") ?: "")
            }
            val isAllFieldsFilled = remember(apiUrl, modelName, apiKey) {
                apiUrl.isNotBlank() && modelName.isNotBlank() && apiKey.isNotBlank()
            }
            var passwordVisible by remember { mutableStateOf(false) }
            MiuixTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "小爱课程表", scrollBehavior = scrollBehavior
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
                                    value = apiUrl, onValueChange = { newValue: String ->
                                        apiUrl = newValue
                                        context.writablePrefs().edit().putString("api_url", newValue).apply()
                                    }, label = "Api地址"
                                )

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
                                Button(
                                    modifier = Modifier.fillMaxWidth(), onClick = {
                                        if (isAllFieldsFilled) {
                                            val intent = Intent(context, SchoolScreen::class.java)
                                            intent.putExtra(
                                                "proxy_target_activity",
                                                context.proxyActivity()
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
                            }
                        }
                    }
                }
            }
        }
    }
}