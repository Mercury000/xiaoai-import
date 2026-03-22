package me.padi.xiaoai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OkHttpClientManager {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // GET 请求
    fun get(url: String, callback: Callback) {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(callback)
    }

    // POST 请求（JSON格式）
    fun post(url: String, json: String, callback: Callback) {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(callback)
    }

    /**
     * 挂起式 GET 请求
     */
    suspend fun getSync(url: String): String = suspendCancellableCoroutine { continuation ->
        get(url, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body.string()
                    continuation.resume(body)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }

    suspend fun getBytesSync(url: String): ByteArray = suspendCancellableCoroutine { continuation ->
        get(url, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body.bytes()
                    continuation.resume(body)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
}

inline fun OkHttpClientManager.get(
    url: String,
    crossinline onSuccess: (Response) -> Unit,
    crossinline onError: (IOException) -> Unit
) {
    get(url, object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e)
        }

        override fun onResponse(call: Call, response: Response) {
            onSuccess(response)
        }
    })
}

inline fun OkHttpClientManager.post(
    url: String,
    json: String,
    crossinline onSuccess: (Response) -> Unit,
    crossinline onError: (IOException) -> Unit
) {
    post(url, json, object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            onError(e)
        }

        override fun onResponse(call: Call, response: Response) {
            onSuccess(response)
        }
    })
}
