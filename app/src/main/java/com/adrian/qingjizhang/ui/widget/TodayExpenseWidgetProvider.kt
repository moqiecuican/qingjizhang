package com.adrian.qingjizhang.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.adrian.qingjizhang.MainActivity
import com.adrian.qingjizhang.R
import com.adrian.qingjizhang.core.WidgetText
import com.adrian.qingjizhang.data.local.AppDatabase
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * V1.4 桌面小组件（XML + RemoteViews，仅 2×2 一档）。
 *
 * 规范对照：
 * - 独立进程 `:widgetProvider`（1584 §1，manifest 里声明）
 * - 曝光刷新：接 `miui.appwidget.action.APPWIDGET_UPDATE`（1584 §2）
 * - 尺寸：minWidth/minHeight 110dp（1584 §3）；两套布局按 options 切（1585 §2.1）
 * - 根布局 `@android:id/background` + 背景色（1584 §7.1）
 * - 深色只走 `values-night` 资源（1584 §12.5），本文件不判断夜间模式
 *
 * 类名保持不变（1584 §12.6：改名会让用户已添加的小组件在升级后消失）。
 */
class TodayExpenseWidget2x2Receiver : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG_WIDGET, "onReceive action=$action ids=${intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.joinToString()}")
        if (action == AppWidgetManager.ACTION_APPWIDGET_UPDATE || action == ACTION_MIUI_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: mgr.getAppWidgetIds(ComponentName(context, javaClass))
            if (ids.isEmpty()) return

            // 广播里要查 DB（suspend），用 goAsync 争取时间，别让系统把进程回收
            val pending = goAsync()
            val app = context.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ids.forEach { id ->
                        runCatching { renderWidget(app, mgr, id) }
                            .onFailure { Log.w(TAG_WIDGET, "render #$id failed", it) }
                    }
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    /**
     * 1585 §2.1 的坑：任何 option 变化都会回调这里，且无法凭 newOptions 判断尺寸是否真的变了。
     * 所以自己记录上次的 MAX_WIDTH × MAX_HEIGHT，没变就直接返回，避免无谓重绘。
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val w = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val h = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val signature = "$w x $h"
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "opt_$appWidgetId"
        if (prefs.getString(key, null) == signature) return
        prefs.edit().putString(key, signature).apply()

        // 这个回调不在 onReceive 栈里，不能 goAsync；查询只有一次且极快，直接阻塞等待完成
        runBlocking {
            runCatching { renderWidget(context.applicationContext, appWidgetManager, appWidgetId) }
                .onFailure { Log.w(TAG_WIDGET, "options change render #$appWidgetId failed", it) }
        }
    }

    /** 1584 §5：数据恢复（换机/备份还原）后重绘 */
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val mgr = AppWidgetManager.getInstance(context)
        runBlocking {
            newWidgetIds.forEach { id ->
                runCatching { renderWidget(context.applicationContext, mgr, id) }
                    .onFailure { Log.w(TAG_WIDGET, "restored render #$id failed", it) }
            }
        }
    }
}

// ---------------- 渲染 ----------------

/** 小卡断点：<136dp 用 small（5×8 桌面 121dp） */
private const val LARGE_MIN_WIDTH_DP = 136

/** 大屏断点：≥250dp 用 roomy（平板二号实测 292dp）；dp 是物理单位，卡片大了字号不会自己变大 */
private const val ROOMY_MIN_WIDTH_DP = 250

private data class WidgetData(
    val label: String,
    val amount: String,
    val aux: String,
    val isEmpty: Boolean,
)

internal suspend fun renderWidget(context: Context, mgr: AppWidgetManager, widgetId: Int) {
    val opts = mgr.getAppWidgetOptions(widgetId)
    val minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val minHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
    val layoutRes = layoutFor(minWidth)
    val views = RemoteViews(context.packageName, layoutRes)
    val data = loadWidgetData(context)
    Log.d(
        TAG_WIDGET,
        "render #$widgetId minW=${minWidth}dp minH=${minHeight}dp " +
            "layout=${context.resources.getResourceEntryName(layoutRes)} " +
            "amount=${data.amount} aux=${data.aux}",
    )

    views.setTextViewText(R.id.widget_label, data.label)
    views.setTextViewText(R.id.widget_amount, data.amount)
    views.setTextViewTextSize(
        R.id.widget_amount,
        TypedValue.COMPLEX_UNIT_DIP,
        amountTextSize(layoutRes, data.amount),
    )

    if (layoutRes == R.layout.widget_today_2x2_small) {
        views.setViewVisibility(R.id.widget_aux, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_aux, View.VISIBLE)
        views.setTextViewText(R.id.widget_aux, data.aux)
    }

    views.setOnClickPendingIntent(R.id.widget_root, ledgerPendingIntent(context))
    views.setOnClickPendingIntent(R.id.widget_button, quickAddPendingIntent(context))

    mgr.updateAppWidget(widgetId, views)
}

/** 1585 §2.1：按最小宽度切布局；options 缺失（0）时按大卡处理 */
private fun layoutFor(minWidth: Int): Int {
    return when {
        minWidth in 1 until LARGE_MIN_WIDTH_DP -> R.layout.widget_today_2x2_small
        minWidth >= ROOMY_MIN_WIDTH_DP -> R.layout.widget_today_2x2_roomy
        else -> R.layout.widget_today_2x2_large
    }
}

/**
 * 金额字号：规范 44dp 只适用 2~4 字符（示例「处暑」「28°」）。
 * 我们的「¥126.50」是 8 字符，按 MiSans 数字宽 ≈0.58em 反推：
 *   large 可用宽 150-30=120dp → 120/(8×0.58)=25.9dp → 取 25
 *   small 可用宽 121-24=97dp  → 97/(8×0.58)=20.9dp  → 取 20
 * 固定字号（避免每笔记账后数字跳大小），仅超长时按比例兜底。
 */
private fun amountTextSize(layoutRes: Int, amount: String): Float {
    val base = when (layoutRes) {
        R.layout.widget_today_2x2_small -> 20f
        R.layout.widget_today_2x2_roomy -> 44f   // 规范 Display1 上限；平板 292dp 卡放得下 8 字符
        else -> 27f
    }
    val len = amount.length
    return if (len <= 8) base else (base * 8f / len).coerceAtLeast(13f)
}

private suspend fun loadWidgetData(context: Context): WidgetData {
    val today = LocalDate.now().toEpochDay()
    // 1584 §9：清数据/未授权等异常场景不能崩，降级成空态
    val row = runCatching { AppDatabase.get(context).recordDao().todayExpense(today) }.getOrNull()
    val cents = row?.expenseCents ?: 0L
    val count = row?.count ?: 0
    return WidgetData(
        label = WidgetText.dateLabel(today),
        amount = WidgetText.amountLine(cents),
        aux = WidgetText.auxLine(count, row?.lastMillis),
        isEmpty = count <= 0,
    )
}

// ---------------- 点击 ----------------

private const val REQ_LEDGER = 1001
private const val REQ_QUICK_ADD = 1002

/**
 * 普通启动 Activity 用 FLAG_IMMUTABLE（Android 12+ 官方推荐、lint 默认要求）。
 * 注意：1584 §11.2 提到的 `FLAG_MUTABLE` 仅针对「编辑页大屏预览」——Host 需要向 Intent 回填
 * miuiWidgetScreenBound 等参数；本小部件没有编辑页，不适用该条款。
 */
private fun ledgerPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        // 点卡片主体 = 看账：抑制这次冷启动的自动记账框（v1.2 的「打开就记一笔」行为）
        .putExtra(MainActivity.EXTRA_SKIP_COLD_START, true)
    return PendingIntent.getActivity(
        context,
        REQ_LEDGER,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun quickAddPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_QUICK_ADD, true)
    return PendingIntent.getActivity(
        context,
        REQ_QUICK_ADD,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

// ---------------- 常量 ----------------

internal const val TAG_WIDGET = "QjzWidget"
internal const val ACTION_MIUI_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"
private const val PREFS_NAME = "widget_options"
