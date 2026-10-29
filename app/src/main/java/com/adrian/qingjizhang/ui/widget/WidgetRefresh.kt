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

    /**
     * refresh() 的安全包装：吞非取消异常，**放行 CancellationException**。
     *
     * 为什么不能直接用 runCatching —— 它 catch Throwable，会把协程取消也当成"刷新失败"吞掉，
     * 导致 Worker / 协程不响应取消（审查 U2 同类问题，2026-10-30 审计补齐）。
     */
    suspend fun refreshSafely(context: Context) {
        try {
            refresh(context)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (t: Throwable) {
            android.util.Log.w("WidgetRefresh", "refresh failed", t)
        }
    }
}

/** 兜底刷新（写入口触发后的一层保险） */
class WidgetRefreshWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        WidgetRefresh.refreshSafely(applicationContext)
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

fun scheduleMidnightRefresh(
    context: Context,
    // 故意不给默认值：两处调用需要相反的策略，给默认值等于留个未来会踩的坑
    //   · onAppResumed（高频）→ KEEP       ：有任务/在跑就不动，丢了才补
    //   · Worker 内部（自己正在跑）→ APPEND_OR_REPLACE：追加下一次，不取消自己
    policy: ExistingWorkPolicy,
) {
    val zone = ZoneId.systemDefault()
    val nextMidnight = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val delay = (nextMidnight - System.currentTimeMillis()).coerceAtLeast(60_000L)
    val request = OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(WORK_NAME, policy, request)
}

class WidgetMidnightWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        WidgetRefresh.refreshSafely(applicationContext)
        // 审查 U3 补：这里必须用 APPEND_OR_REPLACE，不能用 REPLACE 也不能用 KEEP。
        // - REPLACE：会取消正在跑的"自己"（状态记 CANCELLED，极端时序下中断 refresh）
        // - KEEP：语义是「存在未完成的同名 work 就不加」，而 RUNNING 也算未完成
        //   → 自己还在跑，续期会被静默忽略，第二天的零点任务永远不会排上
        // - APPEND_OR_REPLACE：当前 work 还在跑就追加到它后面（不取消），
        //   若已被取消/失败则替换 —— 正是「完成后排下一次」想要的行为
        scheduleMidnightRefresh(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
