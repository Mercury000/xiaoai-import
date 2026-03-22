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
        courses: List<Course>,
        schedule: ScheduleConfig? = null
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
                val newToken = HostCompat.getAccessToken(context, loader, forceRefresh = true)
                    ?: throw Exception("刷新令牌失败")
                block(newToken)
            }
        }

        // 1. 获取当前活跃课表及其设置（作为备份/默认项）
        val fromCtId = runWithRetry { tok ->
            ApiClient.fetchTables(appId, tok, deviceId)
                .firstOrNull { it.current == 1 }?.id ?: 0L
        }
        
        var activeSettingStr: String? = null
        if (fromCtId != 0L) {
            try {
                val activeTable = CourseTable(fromCtId, "")
                runWithRetry { tok ->
                    ApiClient.fetchTableDetail(activeTable, appId, tok, deviceId)
                }
                activeSettingStr = activeTable.settingStr
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

        // 5. 如果有时间表，更新它
        // 即使 JSON 没给时间表，我们也调用一次以同步“默认课表”的设置（按用户要求）
        try {
            updateTableSettings(context, appId, ctid, tableName, activeSettingStr, schedule)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 6. 设置强制重载标志
        HostCompat.isImportFinished = true
    }

    /**
     * 更新课表设置
     * 只修改 morningNum, afternoonNum, nightNum, sections
     * 其余字段必须取自默认设置，缺项也用默认值
     */
    suspend fun updateTableSettings(
        context: Context,
        appId: String,
        ctId: Long,
        name: String,
        sourceSettingStr: String? = null,
        customSchedule: ScheduleConfig? = null,
        preferSourceTermFields: Boolean = false
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
                val newToken = HostCompat.getAccessToken(context, loader, forceRefresh = true)
                    ?: throw Exception("刷新令牌失败")
                block(newToken)
            }
        }

        // 1. 获取目标表的当前详情
        val table = CourseTable(ctId, name)
        runWithRetry { tok ->
            ApiClient.fetchTableDetail(table, appId, tok, deviceId)
        }
        
        // 2. 更新设置
        // sourceSettingStr 是导入前活跃课表的设置（用于缺项补充）
        // table.settingStr 是此表当前的物理设置（作为底包，包含 ID 等）
        runWithRetry { tok ->
            ApiClient.updateTableSettings(
                ctId,
                name,
                sourceSettingStr ?: "{}",
                table.settingStr ?: "{}",
                customSchedule,
                preferSourceTermFields,
                appId,
                tok,
                deviceId
            )
        }
    }

    suspend fun getActiveTableId(context: Context, appId: String): Long? = withContext(Dispatchers.IO) {
        val loader = HostCompat.hostLoader
        val deviceId = HostCompat.getDeviceId(context, loader)
            ?: return@withContext null

        suspend fun <T> runWithRetry(block: suspend (String) -> T): T? {
            val token = HostCompat.getAccessToken(context, loader)
                ?: return null
            return try {
                block(token)
            } catch (e: ApiClient.UnauthorizedException) {
                val newToken = HostCompat.getAccessToken(context, loader, forceRefresh = true)
                    ?: return null
                block(newToken)
            }
        }

        runWithRetry { tok ->
            ApiClient.fetchTables(appId, tok, deviceId)
                .firstOrNull { it.current == 1 }?.id
        }
    }

    suspend fun getActiveTable(context: Context, appId: String): CourseTable? = withContext(Dispatchers.IO) {
        val loader = HostCompat.hostLoader
        val deviceId = HostCompat.getDeviceId(context, loader)
            ?: return@withContext null

        suspend fun <T> runWithRetry(block: suspend (String) -> T): T? {
            val token = HostCompat.getAccessToken(context, loader)
                ?: return null
            return try {
                block(token)
            } catch (e: ApiClient.UnauthorizedException) {
                val newToken = HostCompat.getAccessToken(context, loader, forceRefresh = true)
                    ?: return null
                block(newToken)
            }
        }

        runWithRetry { tok ->
            ApiClient.fetchTables(appId, tok, deviceId)
                .firstOrNull { it.current == 1 }
        }
    }
}
