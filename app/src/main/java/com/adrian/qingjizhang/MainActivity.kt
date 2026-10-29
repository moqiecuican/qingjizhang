package com.adrian.qingjizhang

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adrian.qingjizhang.theme.QingJiZhangTheme
import com.adrian.qingjizhang.theme.tagIconRes
import com.adrian.qingjizhang.ui.CategoriesScreen
import com.adrian.qingjizhang.ui.LedgerScreen
import com.adrian.qingjizhang.ui.LedgerViewModel
import com.adrian.qingjizhang.ui.RecurringRulesScreen
import com.adrian.qingjizhang.ui.StatsScreen

class MainActivity : ComponentActivity() {

    companion object {
        /** 小组件「记一笔」深链 extra（V1.4 §0.1） */
        const val EXTRA_QUICK_ADD = "com.adrian.qingjizhang.extra.QUICK_ADD"

        /**
         * 小组件卡片主体点击（V1.4）：要求直接看流水页，**不弹**冷启动记账框。
         *
         * 背景：v1.2 的「打开 app 就记一笔」是产品行为（LedgerScreen 的 LaunchedEffect），
         * 但点卡片主体的语义是"看账"，两者冲突 → 用显式 extra 抑制这一次冷启动弹窗。
         * 只影响本次冷启动：标记仍会消费掉，避免切 Tab 回来时补弹。
         */
        const val EXTRA_SKIP_COLD_START = "com.adrian.qingjizhang.extra.SKIP_COLD_START"
    }

    /** 卡片主体点击带过来的"别弹记账框"标记（只在本次冷启动有效） */
    private var skipColdStartDialog = false

    /**
     * quick_add 事件计数：冷启动（onCreate）与 app 驻后台（onNewIntent，靠 launchMode=singleTask）
     * 双路都写入，AppRoot 收集后直达记账框。
     */
    private val quickAddEvents = kotlinx.coroutines.flow.MutableStateFlow(0)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 读一次就移除：配置变更（旋屏/深色切换）重建时 intent 仍带着旧 extra，
        // 不清掉会让后续重建也误抑制弹窗
        skipColdStartDialog = intent?.getBooleanExtra(EXTRA_SKIP_COLD_START, false) == true
        intent?.removeExtra(EXTRA_SKIP_COLD_START)
        handleQuickAdd(intent)
        enableEdgeToEdge()
        setContent {
            QingJiZhangTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val widthSize = calculateWindowSizeClass(this).widthSizeClass
                    AppRoot(widthSize, quickAddEvents, skipColdStartDialog)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 热启动不会走冷启动弹窗（VM 已 markColdStartShown），清掉标记防污染
        intent.removeExtra(EXTRA_SKIP_COLD_START)
        handleQuickAdd(intent)
    }

    /** 消费 quick_add：读一次就移除，避免配置变更重建时重复触发 */
    private fun handleQuickAdd(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_QUICK_ADD, false) == true) {
            intent.removeExtra(EXTRA_QUICK_ADD)
            quickAddEvents.value = quickAddEvents.value + 1
        }
    }

    /** 分享 CSV（ViewModel 生成 content Uri 后回调到这里） */
    fun shareCsv(uri: android.net.Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享账本 CSV"))
    }
}

internal enum class Tab(val title: String) { Ledger("流水"), Stats("统计"), Categories("分类") }

/** 从 Compose 的 Context 沿 ContextWrapper 链向上找 Activity */
private fun Context.findActivity(): MainActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is MainActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
private fun AppRoot(
    widthSize: WindowWidthSizeClass,
    quickAddEvents: kotlinx.coroutines.flow.StateFlow<Int>,
    skipColdStartDialog: Boolean,
) {
    val vm: LedgerViewModel = viewModel()

    // V1.4 修复（审查 1.1）：从后台回到前台时补录周期账。
    // 原实现只在 ViewModel.init 里补一次；而 V1.4 用 moveTaskToBack 让进程常驻，
    // 导致跨天不补账、流水页与小组件一起缺数（小组件还显示成合法空态，用户看不出）。
    // 判重在 VM 内部（同日只跑一次），此处只负责在 ON_RESUME 时通知。
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(Tab.Ledger) }
    // V1.3：周期记账管理页作为覆盖层（不占底部 Tab，入口在流水页顶栏菜单）
    var showRecurring by remember { mutableStateOf(false) }
    val wide = widthSize != WindowWidthSizeClass.Compact
    val activity = LocalContextActivity()

    // V1.4 quick_add：小组件「记一笔」→ 直达记账框（冷启动/后台双路，经 launchMode=singleTask）
    androidx.compose.runtime.LaunchedEffect(quickAddEvents) {
        quickAddEvents.collect { n -> if (n > 0) vm.onQuickAdd() }
    }
    // quick_add 模式下对话框关闭（保存/取消）→ 退回桌面（§0.1）
    // 注意：必须用 combine 直接收集两个 StateFlow —— snapshotFlow 只追踪 Compose 快照，
    // 读 StateFlow.value 不会触发（首版踩坑：保存后停在流水页没退桌面）
    androidx.compose.runtime.LaunchedEffect(vm) {
        kotlinx.coroutines.flow.combine(vm.quickAddActive, vm.dialogMode) { active, mode ->
            active && mode == null
        }.collect { shouldFinish ->
            if (shouldFinish) {
                vm.consumeQuickAdd()
                // 不退栈销毁，只把任务移到后台：进程存活 → widget 刷新（内联 await）能跑完，
                // 体验与"退桌面"一致；下次点小组件走 onNewIntent 再回前台
                activity?.moveTaskToBack(true)
            }
        }
    }

    // 全 app 唯一的提示消息收集点（修复双 Snackbar：之前 LedgerScreen 内层也挂了一个 Host）
    androidx.compose.runtime.LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t && !showRecurring,
                        onClick = { tab = t; showRecurring = false },
                        icon = {
                            when (t) {
                                Tab.Ledger -> Icon(Icons.Default.List, contentDescription = t.title)
                                Tab.Stats -> Icon(
                                    painter = androidx.compose.ui.res.painterResource(com.adrian.qingjizhang.R.drawable.ic_chart),
                                    contentDescription = t.title,
                                )
                                Tab.Categories -> Icon(
                                    painter = androidx.compose.ui.res.painterResource(tagIconRes),
                                    contentDescription = t.title,
                                )
                            }
                        },
                        label = { Text(t.title) },
                    )
                }
            }
        },
    ) { padding ->
        if (showRecurring) {
            RecurringRulesScreen(
                viewModel = vm,
                wide = wide,
                contentPadding = padding,
                onBack = { showRecurring = false },
            )
        } else {
            when (tab) {
                Tab.Ledger -> LedgerScreen(vm, widthSize, padding,
                    skipColdStartDialog = skipColdStartDialog,
                    onExported = { uri -> activity?.shareCsv(uri) },
                    onOpenRecurring = { showRecurring = true })
                Tab.Stats -> StatsScreen(vm, wide, padding,
                    onJumpToLedger = { fromDay: Long, toDay: Long ->
                        vm.jumpToLedgerWindow(fromDay, toDay)
                        tab = Tab.Ledger
                    })
                Tab.Categories -> CategoriesScreen(vm, wide, padding)
            }
        }
    }
}

@Composable
private fun LocalContextActivity(): MainActivity? =
    androidx.compose.ui.platform.LocalContext.current.findActivity()
