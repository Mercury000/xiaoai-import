package me.padi.xiaoai

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import top.sacz.xphelper.ext.toClass

/**
 * 宿主兼容性工具类 - 仅保留超级小爱适配 (com.miui.voiceassist)
 */
object HostCompat {
    const val APP_ID = "326813440150602752"

    fun getAppId(): String = APP_ID

    fun getAccessToken(): String? {
        return try {
            val bClass = "c30.b".toClass()
            val instance = bClass.getDeclaredConstructor().newInstance()
            instance.asResolver().firstMethod {
                name = "getOauthV2AccessToken"
                parameterCount = 1
            }.invoke<String>(false)
        } catch (e: Exception) {
            null
        }
    }

    fun isLogin(): Boolean = getAccessToken() != null

    fun getDeviceId(context: Context): String? {
        return try {
            "q70.j".toClass().resolve().firstMethod {
                name = "getDeviceId"
                parameterCount = 1
            }.invoke<String>(context)
        } catch (e: Exception) {
            null
        }
    }
}
