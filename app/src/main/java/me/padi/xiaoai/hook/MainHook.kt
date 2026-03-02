package me.padi.xiaoai.hook

import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.highcapable.yukihookapi.hook.log.YLog
import me.padi.xiaoai.AlertDialogData
import me.padi.xiaoai.AndroidBridge
import me.padi.xiaoai.BridgeCallback
import me.padi.xiaoai.PromptDialogData
import me.padi.xiaoai.R
import me.padi.xiaoai.SingleSelectionDialogData
import me.padi.xiaoai.WebAppInterface
import me.padi.xiaoai.screen.readRawFile
import org.json.JSONArray
import org.json.JSONObject
import top.sacz.xphelper.XpHelper
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MainHook : YukiBaseHooker() {
    override fun onHook() {
        Application::class.java.resolve().firstMethod {
            name = "attach"
            parameterCount = 1
        }.hook {
            after {
                val application = instance<Application>()
                val appContext = instance<Context>()
                val loader = appContext.classLoader

                "com.xiaomi.aischedule.activity.MainActivity".toClass(loader).resolve()
                    .firstMethod {
                        name = "onCreate"
                        parameterCount = 1
                    }.hook {
                        after {
                            val context = instance<Context>()
                            XpHelper.injectResourcesToContext(context)
                        }
                    }

                "com.just.agentweb.DefaultWebCreator".toClass(loader).resolve().firstMethod {
                    name = "createWebView"
                    parameterCount = 0
                }.hook {
                    after {
                        val webView = result as WebView
                        val context = webView.context
                        XpHelper.injectResourcesToContext(context)
                        webView.addJavascriptInterface(
                            WebAppInterface(context), "Android"
                        )

                        val androidBridge =
                            AndroidBridge(context, webView, object : BridgeCallback {
                                private var latestTimerRes: JSONObject? = null
                                private var latestTimeSlotRes: JSONArray? = null

                                override fun onShowAlert(
                                    data: AlertDialogData, callback: (Boolean) -> Unit
                                ) {
                                    AlertDialog.Builder(context).setTitle(data.title)
                                        .setMessage(data.content)
                                        .setPositiveButton(data.confirmText) { _, _ -> callback(true) }
                                        .setNegativeButton("取消") { _, _ -> callback(false) }
                                        .show()
                                }

                                override fun onShowPrompt(
                                    data: PromptDialogData,
                                    callback: (String?) -> Unit,
                                    errorCallback: (String) -> Unit
                                ) {
                                    val input = EditText(context)
                                    input.setText(data.defaultText)
                                    input.hint = data.tip

                                    AlertDialog.Builder(context).setTitle(data.title).setView(input)
                                        .setPositiveButton("确定") { _, _ ->
                                            callback(input.text.toString())
                                        }.setNegativeButton("取消") { _, _ ->
                                            callback(null)
                                        }.show()
                                }

                                override fun onShowSingleSelection(
                                    data: SingleSelectionDialogData, callback: (Int?) -> Unit
                                ) {
                                    AlertDialog.Builder(context).setTitle(data.title)
                                        .setItems(data.items.toTypedArray()) { _, which ->
                                            callback(which)
                                        }.setNegativeButton("取消") { _, _ ->
                                            callback(null)
                                        }.show()
                                }

                                override fun onSaveImportedCourses(
                                    coursesJson: String, callback: (Boolean, String?) -> Unit
                                ) {
                                    try {
                                        val parsedData = JSONArray()
                                        val json = JSONArray(coursesJson)
                                        for (i in 0 until json.length()) {
                                            val outputCourse = JSONObject()
                                            val course = json.getJSONObject(i)
                                            val name = course.optString("name")
                                            val teacher = course.optString("teacher")
                                            val position = course.optString("position")
                                            val day = course.optInt("day")
                                            val startSection = course.optInt("startSection")
                                            val endSection = course.optInt("endSection")
                                            val weeks = course.optJSONArray("weeks")

                                            outputCourse.put("name", name)
                                            outputCourse.put("position", position)
                                            outputCourse.put("day", day)
                                            outputCourse.put("weeks", weeks)
                                            val sectionsArray = JSONArray()
                                            sectionsArray.put(startSection)
                                            sectionsArray.put(endSection)
                                            outputCourse.put("sections", sectionsArray)
                                            outputCourse.put("teacher", teacher)
                                            parsedData.put(outputCourse)
                                        }

                                        val parsed = parsedData.toString()
                                        YLog.debug(parsed)
                                        webView.post {
                                            webView.evaluateJavascript(
                                                "window.PARSED_DATA = JSON.parse(${
                                                    JSONObject.quote(
                                                        parsed
                                                    )
                                                });", null
                                            )
                                            callback(true, parsedData.toString())
                                        }
                                    } catch (e: Exception) {
                                        YLog.error("onSaveImportedCourses 处理失败: ${e.message}")
                                        callback(false, e.message ?: "课程数据处理失败")
                                    }
                                }

                                override fun onSaveCourseConfig(
                                    configJson: String, callback: (Boolean, String?) -> Unit
                                ) {
                                    try {
                                        val timerRes = JSONObject()
                                        val json = JSONObject(configJson)
                                        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
                                        val localDate = LocalDate.parse(
                                            json.optString("semesterStartDate"), formatter
                                        )
                                        val timestamp =
                                            localDate.atStartOfDay(ZoneId.systemDefault())
                                                .toInstant().toEpochMilli()
                                        timerRes.put("semesterStartDate", timestamp.toString())
                                        timerRes.put(
                                            "totalWeek", json.optInt("semesterTotalWeeks", 20)
                                        )
                                        timerRes.put(
                                            "firstDayOfWeek", json.optInt("firstDayOfWeek", 1) == 1
                                        )

                                        latestTimerRes = timerRes
                                        val timer = timerRes.toString()
                                        YLog.debug(timer)
                                        webView.post {
                                            webView.evaluateJavascript(
                                                "window.TIME_RES = JSON.parse(${
                                                    JSONObject.quote(
                                                        timer
                                                    )
                                                });", null
                                            )
                                            callback(true, timerRes.toString())
                                        }
                                    } catch (e: Exception) {
                                        YLog.error("onSaveCourseConfig 处理失败: ${e.message}")
                                        callback(false, e.message ?: "课表配置处理失败")
                                    }
                                }

                                override fun onSavePresetTimeSlots(
                                    timeSlotsJson: String, callback: (Boolean, String?) -> Unit
                                ) {
                                    try {
                                        val inputArray = JSONArray(timeSlotsJson)
                                        val targetArray = JSONArray()
                                        for (i in 0 until inputArray.length()) {
                                            val inputObj = inputArray.getJSONObject(i)
                                            val targetObj = JSONObject()
                                            targetObj.put("section", inputObj.getInt("number"))
                                            targetObj.put(
                                                "startTime", inputObj.getString("startTime")
                                            )
                                            targetObj.put("endTime", inputObj.getString("endTime"))
                                            targetArray.put(targetObj)
                                        }
                                        latestTimeSlotRes = targetArray
                                        callback(true, "时间段保存成功")
                                    } catch (e: Exception) {
                                        YLog.error("onSavePresetTimeSlots 处理失败: ${e.message}")
                                        callback(false, e.message ?: "时间段处理失败")
                                    }
                                }

                                override fun onTaskCompleted() {
                                    val mergedTimerRes =
                                        JSONObject(latestTimerRes?.toString() ?: "{}")
                                    mergedTimerRes.put("sections", latestTimeSlotRes ?: JSONArray())
                                    val mergedJson = mergedTimerRes.toString()

                                    webView.post {
                                        webView.evaluateJavascript(
                                            "window.TIME_RES = JSON.parse(${
                                                JSONObject.quote(
                                                    mergedJson
                                                )
                                            });", null
                                        )
                                        webView.evaluateJavascript(
                                            """    
                                                 var SCHOOL_NAME = "通用教务";
                                                  var send = function (e) {
                                                    var msg = (window.clientVersion >= 102001001 || window.app) ? JSON.stringify(e) : e;
                                                    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.postData) {
                                                      window.webkit.messageHandlers.postData.postMessage(msg);
                                                    }
                                                    if (window.app && window.app.postData) {
                                                      window.app.postData(msg);
                                                    }
                                                  };
                                                
                                                  var report = function (errorCode, data) {
                                                    data = data || {};
                                                    var importDataObj = {
                                                      isV2: true,
                                                      schoolName: SCHOOL_NAME,
                                                      errorCode: errorCode
                                                    };
                                                    Object.assign(importDataObj, data);
                                                
                                                    var presetData = encodeURIComponent(
                                                      JSON.stringify({
                                                        importData: JSON.stringify(importDataObj)
                                                      })
                                                    );
                                                
                                                    send({
                                                      storage: {
                                                        id: "presetData",
                                                        key: "presetData",
                                                        value: presetData,
                                                        action: "put"
                                                      }
                                                    });
                                                    send({
                                                      closeWebView: {
                                                        id: "closeWebView",
                                                        allPage: 1
                                                      }
                                                    });
                                                    send({
                                                      importJWCFinish: {
                                                        id: "importJWCFinish"
                                                      }
                                                    });
                                                  };

                                                report(0, {
                                                  parserRes: window.PARSED_DATA,
                                                  timerRes: window.TIME_RES,
                                                  feedbackId: 0,
                                                  source: 0,
                                                  status: "success"
                                                });
                                          """.trimIndent(), null
                                        )
                                        Toast.makeText(context, "任务完成", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }

                            })

                        webView.addJavascriptInterface(androidBridge, "AndroidBridge")
                    }
                }

                "android.webkit.WebViewClient".toClass(loader).resolve().firstMethod {
                    name = "onPageFinished"
                    parameters(WebView::class.java, String::class.java)
                }.hook {
                    after {
                        val webView = args().first().cast<WebView>()
                        val tools = webView?.context?.readRawFile(R.raw.tools) ?: ""
                        webView?.evaluateJavascript(
                            tools, null
                        )
                        webView?.evaluateJavascript(
                            """
        window._androidPromiseResolvers = {};
        window._androidPromiseRejectors = {};

        window._resolveAndroidPromise = function(promiseId, result) {
            if (window._androidPromiseResolvers[promiseId]) {
                window._androidPromiseResolvers[promiseId](result);
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window._rejectAndroidPromise = function(promiseId, error) {
            if (window._androidPromiseRejectors[promiseId]) {
                window._androidPromiseRejectors[promiseId](new Error(error));
                delete window._androidPromiseResolvers[promiseId];
                delete window._androidPromiseRejectors[promiseId];
            }
        };

        window.AndroidBridgePromise = {
            showAlert: function(title, content, confirmText) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'alert_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showAlert(title, content, confirmText, promiseId);
                });
            },
            showPrompt: function(title, tip, defaultText, validatorJsFunction) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'prompt_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showPrompt(title, tip, defaultText, validatorJsFunction, promiseId);
                });
            },
            showSingleSelection: function(title, itemsJsonString, defaultSelectedIndex) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'singleSelect_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.showSingleSelection(title, itemsJsonString, defaultSelectedIndex, promiseId);
                });
            },
            saveImportedCourses: function(coursesJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveCourses_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveImportedCourses(coursesJsonString, promiseId);
                });
            },
            saveCourseConfig: function(configJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveConfig_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.saveCourseConfig(configJsonString, promiseId);
                });
            },
            savePresetTimeSlots: function(timeSlotsJsonString) {
                return new Promise((resolve, reject) => {
                    const promiseId = 'saveTimeSlots_' + Date.now() + Math.random().toString(36).substring(2);
                    window._androidPromiseResolvers[promiseId] = resolve;
                    window._androidPromiseRejectors[promiseId] = reject;
                    AndroidBridge.savePresetTimeSlots(timeSlotsJsonString, promiseId);
                });
            }
        };
    """.trimIndent(), null
                        )
                        webView?.evaluateJavascript(
                            """
    (function() {
        if (window._eduButtonListenerAttached) return;
        
        const checkButton = function() {
            const eduButton = document.getElementById('ai-class-shedule-fe-setting-button-jiaoyu');
            if (eduButton && !window._eduButtonListenerAttached) {
                eduButton.addEventListener('click', function(event) {
                    event.preventDefault();
                    event.stopPropagation();
                    Android.navSchoolScreen();
                }, true);
                
                window._eduButtonListenerAttached = true;
                return true;
            }
            return false;
        };
        
        if (!checkButton()) {
            const observer = new MutationObserver(function(mutations) {
                checkButton();
                if (window._eduButtonListenerAttached) {
                    observer.disconnect();
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
        }
    })();
    """.trimIndent(), null
                        )
                    }
                }

            }
        }

    }
}
