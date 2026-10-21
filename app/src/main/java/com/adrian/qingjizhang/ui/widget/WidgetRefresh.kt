package com.adrian.qingjizhang.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 小组件刷新出口（V1.4 §0.3 刷新架构）。
 *
 * 写入口统一收在 LedgerRepository.notifyWidgets()：增/改/删/周期补录/恢复备份，
 * 主进程调这里的 refresh()；零点由 WidgetMidnightWorker 调。
 *
 * 实现要点：**按 widgetId 循环**，每个 id 单独读 options 再 update ——
 * 不同实例可能落在不同尺寸（large/small），不能用一次 updateAppWidget(component, views) 套给所有实例。
 */
object WidgetRefresh {
    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, TodayExpenseWidget2x2Receiver::class.java))
        if (ids.isEmpty()) return
        ids.forEach { id ->
            runCatching { renderWidget(app, mgr, id) }
        }
    }
}

/** 兜底刷新（写入口触发后的一层保险） */
class WidgetRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { WidgetRefresh.refresh(applicationContext) }
        return Result.success()
    }
}

fun enqueueWidgetRefresh(context: Context) {
    val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork("widget-refresh-now", ExistingWorkPolicy.REPLACE, request)
}

// ---------------- 跨天零点自刷（⑥） ----------------

private const val WORK_NAME = "widget-midnight-refresh"

fun scheduleMidnightRefresh(context: Context) {
    val zone = ZoneId.systemDefault()
    val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val delay = (nextMidnight - System.currentTimeMillis()).coerceAtLeast(60_000L)
    val request = OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
}

class WidgetMidnightWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        runCatching { WidgetRefresh.refresh(applicationContext) }
        scheduleMidnightRefresh(applicationContext)
        return Result.success()
    }
}
