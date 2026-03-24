package com.mercury.xiaoaiimport

import android.content.Context

fun Context.writablePrefs() = getSharedPreferences("xiaoai_module_preferences", Context.MODE_PRIVATE)

object HostCompat {
    private const val PREF_KEY_SHIGUANG_REPO_URL = "debug_shiguang_repo_url"
    private const val PREF_KEY_SHIGUANG_SCRIPT_BRANCH = "debug_shiguang_repo_branch"
    private const val DEFAULT_SHIGUANG_REPO_URL = "https://gitee.com/XingHeYuZhuan-gh/shiguang_warehouse"
    private const val DEFAULT_SHIGUANG_SCRIPT_BRANCH = "main"
    private const val SHIGUANG_INDEX_BRANCH = "index-pb-release"

    @Volatile
    var pendingCourseConfigJson: String? = null

    @Volatile
    var pendingTimeSlotSectionsJson: String? = null

    fun getShiguangRepoUrl(context: Context): String {
        return context.writablePrefs()
            .getString(PREF_KEY_SHIGUANG_REPO_URL, DEFAULT_SHIGUANG_REPO_URL)
            ?.trim()
            ?.trimEnd('/')
            .takeUnless { it.isNullOrBlank() }
            ?: DEFAULT_SHIGUANG_REPO_URL
    }

    fun getShiguangScriptBranch(context: Context): String {
        return context.writablePrefs()
            .getString(PREF_KEY_SHIGUANG_SCRIPT_BRANCH, DEFAULT_SHIGUANG_SCRIPT_BRANCH)
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
            ?: DEFAULT_SHIGUANG_SCRIPT_BRANCH
    }

    fun buildShiguangScriptRawUrl(context: Context, path: String): String {
        val normalizedPath = path.trim().trimStart('/')
        return "${getShiguangRepoUrl(context)}/raw/${getShiguangScriptBranch(context)}/$normalizedPath"
    }

    fun buildShiguangIndexRawUrl(context: Context): String {
        return "${getShiguangRepoUrl(context)}/raw/$SHIGUANG_INDEX_BRANCH/school_index.pb"
    }
}
