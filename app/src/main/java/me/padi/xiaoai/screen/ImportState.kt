package com.mercury.xiaoaiimport.screen

sealed class ImportState {
    data object Idle : ImportState()
    data class Loading(val message: String = "正在解析脚本...") : ImportState()
    data class Parsing(val message: String = "正在导入课程...") : ImportState()
    data class Success(val message: String) : ImportState()
    data class Error(val message: String) : ImportState()
}
