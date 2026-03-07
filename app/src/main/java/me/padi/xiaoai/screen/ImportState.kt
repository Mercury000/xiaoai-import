package me.padi.xiaoai.screen

sealed class ImportState {
    object Idle : ImportState()
    object Loading : ImportState()
    object Parsing : ImportState()
    data class Success(val message: String) : ImportState()
    data class Error(val message: String) : ImportState()
}
