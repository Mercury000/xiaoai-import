package com.mercury.xiaoaiimport.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Typeface
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.mercury.xiaoaiimport.R

class OpenSourceNoticesActivity : AppCompatActivity() {

    private data class OssItem(
        val name: String,
        val license: String,
        val url: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.open_source_notices_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val items = listOf(
            OssItem("shiguang_warehouse", "MIT", "https://github.com/XingHeYuZhuan/shiguang_warehouse"),
            OssItem("ai-schedule-import-app", "MIT", "https://gitee.com/litedream/ai-schedule-import-app"),
            OssItem("YuKiHookAPI", "Apache-2.0", "https://github.com/HighCapable/YuKiHookAPI"),
            OssItem("KavaRef", "Apache-2.0", "https://github.com/HighCapable/KavaRef"),
            OssItem("Hikage", "Apache-2.0", "https://github.com/BetterAndroid/Hikage"),
            OssItem("BetterAndroid", "Apache-2.0", "https://github.com/BetterAndroid/BetterAndroid"),
            OssItem("DrawableToolbox", "Apache-2.0", "https://github.com/duanhong169/DrawableToolbox"),
            OssItem("compose-webview", "Apache-2.0", "https://github.com/KevinnZou/compose-webview"),
            OssItem("DialogX", "Apache-2.0", "https://github.com/kongzue/DialogX"),
            OssItem("OkHttp", "Apache-2.0", "https://github.com/square/okhttp"),
            OssItem("Coil", "Apache-2.0", "https://github.com/coil-kt/coil"),
            OssItem("kotlinx.serialization", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
            OssItem("miuix", "Apache-2.0", "https://github.com/yukonga/miuix"),
            OssItem("Material Components", "Apache-2.0", "https://github.com/material-components/material-components-android"),
            OssItem("XpHelper", "No License", "https://github.com/suzhelan/XPHelper")
        )

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
        }

        items.forEach { item ->
            container.addView(createItemView(item))
        }

        val scrollView = ScrollView(this).apply { addView(container) }
        setContentView(scrollView)
    }

    private fun createItemView(item: OssItem): LinearLayout {
        val nameView = TextView(this).apply {
            text = item.name
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }
        val licenseView = TextView(this).apply {
            text = "License: ${item.license}"
            textSize = 13f
        }
        val urlView = TextView(this).apply {
            text = item.url
            textSize = 12f
            alpha = 0.8f
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16
            }
            setBackgroundResource(R.drawable.bg_permotion_round)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            }
            addView(nameView)
            addView(licenseView)
            addView(urlView)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
