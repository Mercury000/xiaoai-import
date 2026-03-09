// AndroidBridge.kt
package me.padi.xiaoai

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import kotlinx.serialization.Serializable
import org.json.JSONArray
import java.util.concurrent.CountDownLatch

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
    // 同步对话框：在主线程展示UI，通过CountDownLatch阻塞JS线程等待用户响应
    fun onShowAlert(data: AlertDialogData, latch: CountDownLatch, result: BooleanArray)
    fun onShowPrompt(data: PromptDialogData, latch: CountDownLatch, result: Array<String?>)
    fun onShowSingleSelection(data: SingleSelectionDialogData, latch: CountDownLatch, result: IntArray)
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

    /** JS 调用：显示 Alert 弹窗（同步，阻塞JS线程直到用户响应）。 */
    @JavascriptInterface
    fun showAlert(titleText: String, contentText: String, confirmText: String): Boolean {
        Log.d(TAG, "JS 触发 showAlert: $titleText")
        val latch = CountDownLatch(1)
        val result = BooleanArray(1) { false }
        val data = AlertDialogData(titleText, contentText, confirmText)
        handler.post { callback.onShowAlert(data, latch, result) }
        try { latch.await() } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        Log.d(TAG, "showAlert 返回: ${result[0]}")
        return result[0]
    }

    /**
     * JS 调用：显示 Prompt 弹窗，并支持异步 JS 验证。
     */
    @JavascriptInterface
    fun showPrompt(
        titleText: String,
        tipText: String,
        defaultText: String,
        validatorJsFunction: String
    ): String? {
        Log.d(TAG, "JS 触发 showPrompt: $titleText")
        val latch = CountDownLatch(1)
        val result = arrayOfNulls<String>(1)
        val validValidator = if (validatorJsFunction == "null" || validatorJsFunction.isEmpty()) null else validatorJsFunction
        val data = PromptDialogData(titleText, tipText, defaultText, validValidator)
        handler.post { callback.onShowPrompt(data, latch, result) }
        try { latch.await() } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
        Log.d(TAG, "showPrompt 返回: ${result[0]}")
        return result[0]
    }

    /**
     * 异步 Alert 弹窗：不阻塞 JS 线程
     */
    @JavascriptInterface
    fun showAlertAsync(titleText: String, contentText: String, confirmText: String, promiseId: String) {
        val latch = CountDownLatch(1)
        val result = BooleanArray(1) { false }
        val data = AlertDialogData(titleText, contentText, confirmText)
        handler.post { 
            callback.onShowAlert(data, latch, result)
            Thread(Runnable {
                try {
                    latch.await()
                    resolveJsPromise(promiseId, result[0].toString())
                } catch (e: Exception) {
                    rejectJsPromise(promiseId, e.message)
                }
            }).start()
        }
    }

    /**
     * 异步 Prompt 弹窗
     */
    @JavascriptInterface
    fun showPromptAsync(titleText: String, tipText: String, defaultText: String, validatorJsFunction: String, promiseId: String) {
        val latch = CountDownLatch(1)
        val result = arrayOfNulls<String>(1)
        val validValidator = if (validatorJsFunction == "null" || validatorJsFunction.isEmpty()) null else validatorJsFunction
        val data = PromptDialogData(titleText, tipText, defaultText, validValidator)
        handler.post { 
            callback.onShowPrompt(data, latch, result)
            Thread(Runnable {
                try {
                    latch.await()
                    resolveJsPromise(promiseId, result[0], wrapInQuotes = true)
                } catch (e: Exception) {
                    rejectJsPromise(promiseId, e.message)
                }
            }).start()
        }
    }

    /** JS 调用：显示单选列表弹窗（同步，阻塞JS线程直到用户响应）。 */
    @JavascriptInterface
    fun showSingleSelection(
        titleText: String, itemsJsonString: String, defaultSelectedIndex: Int
    ): Int {
        Log.d(TAG, "JS 触发 showSingleSelection: $titleText")
        return try {
            val items = mutableListOf<String>()
            val jsonArray = JSONArray(itemsJsonString)
            for (i in 0 until jsonArray.length()) items.add(jsonArray.getString(i))
            val latch = CountDownLatch(1)
            val result = IntArray(1) { -1 }
            val data = SingleSelectionDialogData(titleText, items, defaultSelectedIndex)
            handler.post { callback.onShowSingleSelection(data, latch, result) }
            try { latch.await() } catch (e: InterruptedException) { Thread.currentThread().interrupt() }
            Log.d(TAG, "showSingleSelection 返回: ${result[0]}")
            result[0]
        } catch (e: Exception) {
            Log.e(TAG, "解析单选列表 itemsJsonString 失败: ${e.message}", e)
            handler.post { Toast.makeText(context, "单选列表数据错误，无法显示。", Toast.LENGTH_LONG).show() }
            -1
        }
    }

    /**
     * 异步单选列表弹窗
     */
    @JavascriptInterface
    fun showSingleSelectionAsync(titleText: String, itemsJson: String, defaultIndex: Int, promiseId: String) {
        val latch = CountDownLatch(1)
        val result = IntArray(1) { -1 }
        val items = mutableListOf<String>()
        try {
            val arr = org.json.JSONArray(itemsJson)
            for (i in 0 until arr.length()) items.add(arr.getString(i))
        } catch (e: Exception) {}
        val data = SingleSelectionDialogData(titleText, items, defaultIndex)
        handler.post { 
            callback.onShowSingleSelection(data, latch, result)
            Thread(Runnable {
                try {
                    latch.await()
                    resolveJsPromise(promiseId, result[0].toString())
                } catch (e: Exception) {
                    rejectJsPromise(promiseId, e.message)
                }
            }).start()
        }
    }

    /**
     * JS 调用：输出日志到 Logcat，方便调试
     */
    @JavascriptInterface
    fun showLog(message: String) {
        Log.d(TAG, "JS_LOG: $message")
    }

    /** JS 调用：将课程数据传回 Android 端进行保存。 */
    @JavascriptInterface
    fun saveImportedCourses(coursesJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到课程数据，大小: ${coursesJsonString.length / 1024} KB")
        handler.post {
            callback.onSaveImportedCourses(coursesJsonString) { success, errorMsg ->
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
        handler.post {
            callback.onSaveCourseConfig(configJsonString) { success, errorMsg ->
                if (success) {
                    resolveJsPromise(promiseId, "true")
                } else {
                    Toast.makeText(context, errorMsg ?: "课表配置导入失败", Toast.LENGTH_LONG).show()
                    rejectJsPromise(promiseId, errorMsg ?: "课表配置导入失败")
                }
            }
        }
    }

    /** JS 调用：将预设时间段数据传回 Android 端进行保存。 */
    @JavascriptInterface
    fun savePresetTimeSlots(timeSlotsJsonString: String, promiseId: String) {
        Log.d(TAG, "接收到预设时间段数据，大小: ${timeSlotsJsonString.length / 1024} KB")
        handler.post {
            callback.onSavePresetTimeSlots(timeSlotsJsonString) { success, errorMsg ->
                if (success) {
                    resolveJsPromise(promiseId, "true")
                } else {
                    Toast.makeText(context, errorMsg ?: "时间段导入失败", Toast.LENGTH_LONG).show()
                    rejectJsPromise(promiseId, errorMsg ?: "时间段导入失败")
                }
            }
        }
    }

    /** JS 调用：通知任务已完成（用于在 finally 块中重置解析状态）。 */
    @JavascriptInterface
    fun notifyTaskCompletion() {
        Log.d(TAG, "JS 调用 notifyTaskCompletion")
        handler.post {
            callback.onTaskCompleted()
        }
    }

    /** 在 JS 环境中解决 Promise。 */
    private fun resolveJsPromise(promiseId: String, result: String?, wrapInQuotes: Boolean = false) {
        val safeResult = if (wrapInQuotes) {
            org.json.JSONObject.quote(result ?: "")
        } else {
            result ?: "null"
        }
        Log.d(TAG, "Resolving JS Promise: $promiseId with result: $safeResult")
        handler.post {
            webView.evaluateJavascript(
                "window._resolveAndroidPromise('$promiseId', $safeResult);", null
            )
        }
    }

    /** 在 JS 环境中拒绝 Promise。 */
    private fun rejectJsPromise(promiseId: String, error: String?) {
        val escapedError = org.json.JSONObject.quote(error ?: "Unknown error")
        Log.d(TAG, "Rejecting JS Promise: $promiseId with error: $escapedError")
        handler.post {
            webView.evaluateJavascript(
                "window._rejectAndroidPromise('$promiseId', $escapedError);", null
            )
        }
    }

    /** 
     * 针对通用和第三方适配库的旧版 XiaoAi native 接口模拟: window.app.postData()
     * 这些脚本（特别是星链/拾光的脚本）会把最终解析结果封装在 JSON 中并调用 postData / closeWebView.
     */
    @JavascriptInterface
    fun postData(msg: String) {
        Log.d(TAG, "接收到 postData 消息，原教务导入模式调用: $msg")
        try {
            val rootJson = org.json.JSONObject(msg)
            
            // 旧版 XiaoAi 在 LocalStorage 存入 "presetData" 或发送 Action
            if (rootJson.has("storage")) {
                val storageNode = rootJson.getJSONObject("storage")
                if (storageNode.optString("key") == "presetData") {
                    val urlEncodedValue = storageNode.optString("value")
                    val decodedValue = java.net.URLDecoder.decode(urlEncodedValue, "UTF-8")
                    val decodedJson = org.json.JSONObject(decodedValue)
                    if (decodedJson.has("importData")) {
                        val importData = org.json.JSONObject(decodedJson.getString("importData"))
                        if (importData.has("parserRes")) {
                            val parserRes = importData.getString("parserRes")
                            Log.d(TAG, "从 postData 成功剥离 parserRes 数据，大小: ${parserRes.length} 字节")
                            val promiseId = "postData_" + System.currentTimeMillis()
                            saveImportedCourses(parserRes, promiseId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解析 postData 失败: ${e.message}", e)
        }
    }

    /**
     * 模拟 window.app.closeWebView()，配合 postData 使用。
     */
    @JavascriptInterface
    fun closeWebView() {
        Log.d(TAG, "脚本请求关闭 WebView：执行关闭操作。")
        handler.post {
            if (context is android.app.Activity) {
                // 等待 Toast 和异步处理完成再关闭
                handler.postDelayed({
                    context.finish()
                }, 500)
            }
        }
    }
}
