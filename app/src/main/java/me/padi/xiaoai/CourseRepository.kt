package me.padi.xiaoai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 课表导入编排仓库
 */
object CourseRepository {

    /**
     * 执行完整的课表导入流程：
     * 1. 获取当前活跃课表 ID
     * 2. 创建新课表
     * 3. 切换到新课表
     * 4. 上传课程数据
     */
    suspend fun importCourses(
        context: Context,
        appId: String,
        tableName: String,
        courses: List<Course>
    ) = withContext(Dispatchers.IO) {
        val loader = HostCompat.hostLoader ?: context.classLoader
        val deviceId = HostCompat.getDeviceId(context, loader)
            ?: throw Exception("无法获取设备 ID")

        suspend fun <T> runWithRetry(block: suspend (String) -> T): T {
            val token = HostCompat.getAccessToken(context, loader)
                ?: throw Exception("无法获取访问令牌")
            return try {
                block(token)
            } catch (e: ApiClient.UnauthorizedException) {
                // Token 过期，强制刷新
                val newToken = HostCompat.getAccessToken(context, loader, forceRefresh = true)
                    ?: throw Exception("刷新令牌失败")
                block(newToken)
            }
        }

        // 1. 获取当前活跃课表
        val fromCtId = runWithRetry { tok ->
            ApiClient.fetchTables(appId, tok, deviceId)
                .firstOrNull { it.current == 1 }?.id ?: 0L
        }

        // 2. 创建表
        val ctid = runWithRetry { tok ->
            ApiClient.createTable(tableName.ifBlank { "提取课表" }, appId, tok, deviceId)
        }

        // 3. 切换表
        runWithRetry { tok ->
            ApiClient.switchTable(fromCtId, ctid, appId, tok, deviceId)
        }

        // 4. 上传数据
        runWithRetry { tok ->
            ApiClient.uploadCoursesAll(courses, ctid, appId, tok, deviceId)
        }

        // 5. 设置强制重载标志
        HostCompat.isImportFinished = true
    }
}
