package me.padi.xiaoai

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import top.sacz.xphelper.ext.toClass

object HostCompat {
    const val NEW_APP_ID = "326813440150602752"
    const val OLD_APP_ID = "2882303761518539170"

    fun isNewHost(): Boolean {
        return try {
            "c30.b".toClass()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getAppId(): String {
        return if (isNewHost()) NEW_APP_ID else OLD_APP_ID
    }

    fun getAccessToken(): String? {
        return if (isNewHost()) {
            try {
                val bClass = "c30.b".toClass()
                val instance = bClass.getDeclaredConstructor().newInstance()
                instance.asResolver().firstMethod {
                    name = "getOauthV2AccessToken"
                    parameterCount = 1
                }.invoke<String>(false)
            } catch (e: Exception) {
                null
            }
        } else {
            try {
                "a.h.g.h".toClass().resolve().firstMethod {
                    name = "getInstance"
                    parameterCount = 0
                }.invoke()?.asResolver()?.firstMethod {
                    name = "getAccessToken"
                }?.invoke<String>()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun isLogin(): Boolean {
        return if (isNewHost()) {
            getAccessToken() != null
        } else {
            try {
                "a.h.g.h".toClass().resolve().firstMethod {
                    name = "getInstance"
                    parameterCount = 0
                }.invoke()?.asResolver()?.firstMethod {
                    name = "isLogin"
                }?.invoke<Boolean>() == true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getDeviceId(context: Context): String? {
        return if (isNewHost()) {
            try {
                "q70.j".toClass().resolve().firstMethod {
                    name = "getDeviceId"
                    parameterCount = 1
                }.invoke<String>(context)
            } catch (e: Exception) {
                null
            }
        } else {
            try {
                "a.h.a.j.m".toClass().resolve().firstMethod {
                    name = "getDeviceId"
                }.invoke<String>()
            } catch (e: Exception) {
                null
            }
        }
    }
}
