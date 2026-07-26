package com.mercury.xiaoaiimport.screen

import androidx.appcompat.app.AppCompatActivity

/**
 * 独立应用的基础 Activity。
 * 替代原先依赖 Xposed 环境的 top.sacz.xphelper.activity.BaseActivity
 * （其 onCreate 会向宿主注入模块资源，脱离 Xposed 运行时会 NPE）。
 */
open class BaseActivity : AppCompatActivity()
