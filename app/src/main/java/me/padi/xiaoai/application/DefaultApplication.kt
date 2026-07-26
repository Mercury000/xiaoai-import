package com.mercury.xiaoaiimport.application

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.tencent.smtt.sdk.CookieManager
import com.tencent.smtt.sdk.QbSdk

class DefaultApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        /**
         * 跟随系统夜间模式
         * Follow system night mode
         */
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        QbSdk.initX5Environment(this, null)
        // Enable cookies globally
        CookieManager.getInstance().setAcceptCookie(true)
    }
}
