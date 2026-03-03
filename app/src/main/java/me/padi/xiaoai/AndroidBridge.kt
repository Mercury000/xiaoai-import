// AndroidBridge.kt
package me.padi.xiaoai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import kotlinx.serialization.Serializable
import org.json.JSONArray

private const val TAG = "AndroidBridge"

// JS 端传来的时间段 JSON 模型
@Serializable
data class TimeSlotJsonModel(
    val number: Int, val startTime: String, val endTime: String
)

// 对话框数据类
data class AlertDialogData(
    val title: String, val content: String, val confirmText: String
)

data class PromptDialogData(
    val title: String, val tip: String, val defaultText: String, val validatorJsFunction: String?
)

data class SingleSelectionDialogData(
    val title: String, val items: List<String>, val defaultSelectedIndex: Int = -1
)

// 回调接口，用于与Activity/Fragment通信
interface BridgeCallback {
    fun onShowAlert(data: AlertDialogData, callback: (Boolean) -> Unit)
    fun onShowPrompt(
        data: PromptDialogData, callback: (String?) -> Unit, errorCallback: (String) -> Unit
    )

    fun onShowSingleSelection(data: SingleSelectionDialogData, callback: (Int?) -> Unit)
    fun onSaveImportedCourses(coursesJson: String, callback: (Boolean, String?) -> Unit)
    fun onSaveCourseConfig(configJson: String, callback: (Boolean, String?) -> Unit)
    fun onSavePresetTimeSlots(timeSlotsJson: String, callback: (Boolean, String?) -> Unit)
    fun onTaskCompleted()
}

/**
 * AndroidBridge：处理 WebView 与 Native 代码的通信。
 * 使用回调接口与 Activity/Fragment 通信。
 */
class AndroidBridge(
    private val context: Context, private val webView: WebView, private val callback: BridgeCallback
) {
    private val handler = Handler(Looper.getMainLooper())
    private var importTableId: String? = null
    private var currentToast: Toast? = null

    // 外部设置导入课表 ID
    fun setImportTableId(tableId: String) {
        this.importTableId = tableId
    }

    /** JS 调用：显示短暂的 Toast 消息。 */
    @JavascriptInterface
    fun showToast(message: String) {
        handler.post {
            currentToast?.cancel()
            val newToast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
            newToast.show()
            currentToast = newToast
        }
    }

    /** JS 调用：显示 Alert 弹窗。 */
    @JavascriptInterface
    fun showAlert(titleText: String, contentText: String, confirmText: String, promiseId: String) {
        handler.post {
            val data = AlertDialogData(titleText, contentText, confirmText)

            callback.onShowAlert(data) { confirmed ->
                if (confirmed) {
                    resolveJsPromise(promiseId, "true")
                } else {
                    resolveJsPromise(promiseId, "false")
                }
            }
        }
    }

    /**
     * JS 调用：显示 Prompt 弹窗，并支持异步 JS 验证。
     */
    @JavascriptInterface
    fun showPrompt(
        titleText: String,
        tipText: String,
        defaultText: String,
        validatorJsFunction: String,
        promiseId: String
    ) {
        handler.post {
            val data = PromptDialogData(titleText, tipText, defaultText, validatorJsFunction)
            // 成功回调：用户确认输入
            val onConfirm: (String?) -> Unit = onConfirm@{ input ->
                if (input == null) {
                    // 用户取消
                    resolveJsPromise(promiseId, "null")
                    return@onConfirm
                }

                // 无验证函数时直接成功
                if (data.validatorJsFunction.isNullOrEmpty()) {
                    val escapedInput = input.replace("'", "\\'")
                    resolveJsPromise(promiseId, "'$escapedInput'")
                    return@onConfirm
                }

                // 执行 JS 验证
                validatePromptInput(input, data.validatorJsFunction, promiseId)
            }

            // 错误回调：验证失败时的错误消息
            val onError: (String) -> Unit = { errorMsg ->
                // 这里可以将错误消息返回给JS，或者通过其他方式处理
                Log.d(TAG, "验证错误: $errorMsg")
            }

            callback.onShowPrompt(data, onConfirm, onError)
        }
    }

    /**
     * 执行JS验证函数
     */
    private fun validatePromptInput(input: String, validatorFunction: String?, promiseId: String) {
        handler.post {
            // 构造 JS 验证代码
            val jsScript = "javascript: $validatorFunction('${input.replace("'", "\\'")}')"

            // 执行 JS 验证
            webView.evaluateJavascript(jsScript, ValueCallback { result ->
                val validationResult = result?.trim('\"')

                if (validationResult.isNullOrEmpty() || validationResult.equals(
                        "false", ignoreCase = true
                    )
                ) {
                    // 验证成功：解决 Promise
                    val escapedInput = input.replace("'", "\\'")
                    resolveJsPromise(promiseId, "'$escapedInput'")
                } else {
                    // 验证失败时拒绝 Promise，避免 JS 端 await 卡住。
                    handler.post {
                        Toast.makeText(context, validationResult, Toast.LENGTH_SHORT).show()
                    }
                    rejectJsPromise(promiseId, validationResult)
                }
            })
        }
    }

    /** JS 调用：显示单选列表弹窗。 */
    @JavascriptInterface
    fun showSingleSelection(
        titleText: String, itemsJsonString: String, defaultSelectedIndex: Int, promiseId: String
    ) {
        handler.post {
            try {
                // 解析JSON数组
                val items = mutableListOf<String>()
                val jsonArray = JSONArray(itemsJsonString)
                for (i in 0 until jsonArray.length()) {
                    items.add(jsonArray.getString(i))
                }
                val data = SingleSelectionDialogData(titleText, items, defaultSelectedIndex)
                callback.onShowSingleSelection(data) { selectedIndex ->
                    if (selectedIndex != null) {
                        resolveJsPromise(promiseId, selectedIndex.toString())
                    } else {
                        resolveJsPromise(promiseId, "null")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "解析单选列表 itemsJsonString 失败: ${e.message}", e)
                Toast.makeText(context, "单选列表数据错误，无法显示。", Toast.LENGTH_LONG).show()
                rejectJsPromise(promiseId, "选项列表 JSON 无效: ${e.message}")
            }
        }
    }

    /** JS 调用：将课程数据传回 Android 端进行保存。 */
    @JavascriptInterface
    fun saveImportedCourses(coursesJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到课程数据，大小: ${coursesJsonString.length / 1024} KB")
        callback.onSaveImportedCourses(coursesJsonString) { success, errorMsg ->
            handler.post {
                if (success) {
                    resolveJsPromise(promiseId, "true")
                } else {
                    Toast.makeText(context, errorMsg ?: "课程导入失败", Toast.LENGTH_LONG).show()
                    rejectJsPromise(promiseId, errorMsg ?: "课程导入失败")
                }
            }
        }
    }

    /**
     * 将课表配置数据传回 Android 端进行保存。
     */
    @JavascriptInterface
    fun saveCourseConfig(configJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到课表配置数据，大小: ${configJsonString.length} 字节")
        callback.onSaveCourseConfig(configJsonString) { success, errorMsg ->
            handler.post {
                if (success) {
                    resolveJsPromise(promiseId, "true")
                } else {
                    Toast.makeText(context, errorMsg ?: "课表配置导入失败", Toast.LENGTH_LONG)
                        .show()
                    rejectJsPromise(promiseId, errorMsg ?: "课表配置导入失败")
                }
            }
        }
    }

    /** JS 调用：将预设时间段数据传回 Android 端进行保存。 */
    @JavascriptInterface
    fun savePresetTimeSlots(timeSlotsJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到预设时间段数据，大小: ${timeSlotsJsonString.length / 1024} KB")

        callback.onSavePresetTimeSlots(timeSlotsJsonString) { success, errorMsg ->
            handler.post {
                if (success) {
                    resolveJsPromise(promiseId, "true")
                } else {
                    Toast.makeText(context, errorMsg ?: "预设时间段导入失败", Toast.LENGTH_LONG)
                        .show()
                    rejectJsPromise(promiseId, errorMsg ?: "预设时间段导入失败")
                }
            }
        }
    }

    /** JS 调用：通知 Native 端 JS 任务已逻辑完成。 */
    @JavascriptInterface
    fun notifyTaskCompletion() {
        handler.post {
            importTableId = null
            callback.onTaskCompleted()
        }
    }

    /** 在 JS 环境中解决 Promise。 */
    private fun resolveJsPromise(promiseId: String, result: String) {
        handler.post {
            webView.evaluateJavascript(
                "window._resolveAndroidPromise('$promiseId', $result);", null
            )
        }
    }

    /** 在 JS 环境中拒绝 Promise。 */
    private fun rejectJsPromise(promiseId: String, error: String) {
        handler.post {
            val escapedError = error.replace("'", "\\'")
            webView.evaluateJavascript(
                "window._rejectAndroidPromise('$promiseId', '$escapedError');", null
            )
        }
    }
}
