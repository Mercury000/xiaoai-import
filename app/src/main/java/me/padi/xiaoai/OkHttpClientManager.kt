package me.padi.xiaoai

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

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
        val body = RequestBody.Companion.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            json
        )

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        client.newCall(request).enqueue(callback)
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