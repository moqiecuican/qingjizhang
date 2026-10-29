# 轻记账 · QingJiZhang

> 一个专注「记」这一件事的 Android 记账应用。
> 没有账号系统，没有网络权限，没有云同步——你的账本只在你的手机里。

**这是我第一次开发 Android app，全程使用 vibe coding（AI 辅助编程），纯练手项目。**
私用为主，源码开放——如果它恰好解决你的某个痛点，欢迎试用；不合适也没关系，它本来就只为一个人写的。

---

## 记一笔，三秒

轻记账最核心的体验：**打开就是记账框**。

不是「打开 app → 找到记账按钮 → 点进去 → 输入 → 保存 → 返回」这一串，而是：

1. **点开 app**，直接就是记账框——金额输入框已聚焦，键盘已弹出
2. **输入数字**，按回车
3. **自动回到桌面**——连「保存成功」的提示都不用看

从掏出手机到记完一笔，三秒。

**为什么能做到**：冷启动自动弹记账框是产品设计，不是 bug。但点小组件的卡片主体进流水页时又不会弹（因为你点的是「看账」）——两个入口，两种行为，各取所需。

## 桌面小组件：连 app 都不用打开

![浅色模式 · 小组件与图标](screenshots/light-mode.jpg)

把今日支出放在桌面，右下角一个按钮：

- **点「＋ 记一笔」** → 直达记账框，保存完直接回桌面。冷启动也不弹窗——因为你点的就是「记账」
- **点卡片主体** → 进流水页看明细。冷启动只显示流水，不弹记账框——你点的是「看账」

小组件显示今日支出、日期、笔数和最近一笔时间；深色模式自动适配；手机和平板各有一套版式（不是缩放，是各自排版）。

![深色模式](screenshots/dark-mode.jpg)

## 为什么做这个

市面上的记账 app 大多在做一件我不需要的事：把你留在它的生态里。
云同步、社交、理财推荐、订阅制……功能越堆越多，「记一笔」反而越来越远。

我只想在掏出手机的**三秒内**完成一笔记账，然后忘掉这件事。
所以有了轻记账：单机、本地、克制。

## 设计原则

| 原则 | 落地 |
|---|---|
| **数据主权** | 无网络权限、无账号系统、无埋点。数据全在本地 SQLite，备份是一份可读的 JSON |
| **准确性优先** | 金额以「分」存储（Long），杜绝浮点误差；日期一律 `LocalDate.toEpochDay()` |
| **幂等不靠小心** | 补录重复插入由数据库唯一索引拦住，不依赖调用方逻辑正确 |
| **克制** | 无推送、无社交、无广告、无订阅 |
| **随手可达** | 打开就记、小组件直达、保存即回桌面 |

## 功能

**记账**
- 冷启动直接进记账框，自动聚焦金额，回车即保存
- 保存后自动回桌面，全程无多余操作

**流水**
- 增删改查、搜索筛选、按日分组、月度汇总

**统计**
- 环形占比图、柱状图、周/月/年粒度、自定义区间

**分类**
- 自定义分类；删除保护（有流水的分类不允许删，历史数据永不悬空）

**预算**
- 月度预算、进度可视化、超支提醒

**周期记账**
- 周/月/年周期，打开 app 时自动补录（无后台进程、无系统调度）
- 幂等靠数据库唯一索引，重复打开不会重复入账

**备份**
- JSON 导出/导入（可迁移）；CSV 导出给人看（Excel/WPS 可直接打开）

**桌面小组件**
- 2×2，今日支出 + 一键记一笔
- 深色模式适配；手机/平板自适应

**性能**
- 独立 perf 构建（AOT 编译，滑动 90th 帧耗时 5ms）

## 当前版本明确不做

以下功能不在当前开发计划内。不排除未来根据实际需要重新考虑，但目前把它们当作产品的边界：

- 云同步、多设备
- 社交、账本分享
- 理财、投资、推荐
- 订阅制、内购
- 统计埋点、用户行为分析

---

## 技术栈

| 层 | 选型 | 理由 |
|---|---|---|
| 语言 | Kotlin 2.3 | Android 官方首选 |
| UI | Jetpack Compose + Material 3 | 声明式、可读 |
| 架构 | MVVM（AndroidViewModel + StateFlow） | 界面与数据分离 |
| 存储 | Room 2.8 (SQLite) | 本地持久化，写查询都是编译期检查 |
| 序列化 | kotlinx-serialization 1.8.1 | JSON 备份（版本被 force 对齐，见 build.gradle.kts 注释） |
| 自适应 | material3-window-size-class | 手机/平板一套 UI |
| 图表 | 手写 Canvas | 零新依赖（环形图/柱状图） |
| 桌面小组件 | AppWidgetProvider + XML + RemoteViews | 按小米 1584/1664/1585 规范；Glance 无法满足根布局系统 ID、深色静态资源、独立进程三项硬要求（V1.4 重写） |

## 架构分层（自上而下）

```
MainActivity (唯一 Activity)
 └─ AppRoot：底部导航（流水/统计/分类）+ WindowSizeClass 检测 + 全 app 唯一 Snackbar 收集点
     ├─ LedgerScreen       流水页：月份切换、汇总卡(含预算进度)、搜索筛选、按日分组列表、
     │                     菜单(周期记账/预算/CSV/JSON备份/恢复)；自动账带「自动」小标
     ├─ StatsScreen        统计页：环形占比图(<5% 合并「其他」)+图例+柱状图+粒度(周/月/年)+自定义区间
     ├─ CategoriesScreen   分类页：增删改分类
     ├─ RecurringRulesScreen 周期记账页：规则增删改/暂停；空态引导
     ├─ AddRecordDialog    记一笔/编辑对话框（冷启动自动弹出+自动聚焦金额；回车=保存；imePadding 避让键盘）
     ├─ RecurringRuleDialog 周期规则新增/编辑（名称手输不预设；周期三档 + 参数；备注自动写入生成账）
     ├─ BudgetDialog       设置/清除本月预算（清空金额保存=取消预算）
     └─ LedgerViewModel    全部业务状态与动作（StateFlow 单向数据流）
          └─ LedgerRepository   UI 层不直接碰 DAO；备份恢复=单事务清空重灌；周期补录=单事务
               └─ AppDatabase (Room, version 4)
                   ├─ RecordDao        流水增删改查 + 月度/按天/按分类聚合 + 补录用 IGNORE 插入
                   ├─ CategoryDao      分类 CRUD + 重名检查
                   ├─ BudgetDao        月度预算 upsert/observe
                   └─ RecurringRuleDao 周期规则 CRUD + observe + 推进补录游标
ui/widget/（V1.4）
 ├─ TodayExpenseWidget2x2Receiver.kt  2×2 桌面小组件（AppWidgetProvider + RemoteViews；
 │                                    跑在 :widgetProvider 独立进程，只读 Room；
 │                                    两套布局按 OPTION_APPWIDGET_MIN_WIDTH 切）
 └─ WidgetRefresh.kt                  刷新出口：写入口统一调 refresh()；WidgetRefreshWorker 兜底 +
                                      WidgetMidnightWorker 跨天零点自刷
core/（纯 Kotlin，零 Android 依赖 —— 为将来 KMP 迁移预留）
 ├─ Money.kt          元↔分转换：全 app 金额一律以「分」(Long) 存储，杜绝浮点误差
 ├─ MonthlyStats.kt   月度汇总模型（结余 = 收入 - 支出）
 ├─ BudgetProgress.kt 预算进度纯计算（剩余/超支/ratio）
 ├─ DateRanges.kt     月份→epochDay 区间；formatDayHeader/formatMonth/formatTimeOfDay(HH:mm)
 ├─ CsvBuilder.kt     CSV 文本生成（BOM + RFC4180 转义，Excel/WPS 打开不乱码）
 ├─ ChartModels.kt    统计分桶（周一起点/跨年周归属）、<5% 合并「其他」、柱数封顶 12（V1.2）
 ├─ RecordFilter.kt   搜索筛选状态 + LIKE 通配符转义（V1.2）
 ├─ Recurrence.kt     周期到期日纯函数：月末截断(31→28/29/30)、闰年、跨年、周对齐（V1.3）
 ├─ Catchup.kt        补录计划纯函数：算出待补的 (规则,日期)，并把规则备注复制进生成账（V1.3）
 └─ BackupJson.kt     JSON 备份格式 v2（四表：categories/records/budgets/recurringRules）
```

## 关键设计决策

1. **金额单位是「分」(Long)**，`dateEpochDay` 是 `LocalDate.toEpochDay()` (Long)。
   数据库里永远不存浮点数、不存格式化字符串。
2. **冷启动弹记一笔**：开关放在 ViewModel（`_coldStartShown`），ViewModel 在旋屏/
   深色切换等配置变更中存活，所以只有真正冷启动（新进程）才弹。
   弹窗打开自动聚焦金额框（FocusRequester + 150ms 延迟等窗口就绪）；
   键盘回车键 = 保存（ImeAction.Done）；编辑模式不自动弹键盘（防误触）。
3. **预置分类不用 Room `Callback.onCreate`**：Room 2.8 起默认新 SQLite driver，
   旧 SupportSQLite 回调不触发（实测踩坑）。改为 `seedPresetCategories()`
   幂等播种（空表才插），ViewModel init 时 IO 线程执行。
4. **删除分类保护**：分类下有流水则拒绝删除（`countForCategory > 0`），历史数据永不悬空。
5. **平板自适应**：WindowSizeClass ≥ Medium 时内容列限宽 600dp 居中 +
   FAB 变 Extended 样式；分屏/折叠屏实时响应。
6. **CSV 导出**（给人看/Excel）与 **JSON 备份**（换机恢复）分工明确：
   都写 `cache/exports/` 走 FileProvider 分享；JSON 恢复走 SAF 选文件 +
   强警告确认框 + 单事务清空重灌（保留原 id，categoryId/ruleId 引用天然一致）。
7. **跨设备合并地基**（V1.1 铺好，合并功能留给将来）：
   - `clientUid`：创建时 UUID，终身不变——多设备导入同一备份时靠它认「同一笔账」
   - `updatedAtMillis`：每次编辑刷新——将来冲突裁决「后写的赢」
8. **预算**：每月至多一条（yearMonthKey="2026-09" 字符串主键）；
   总览卡进度条超支变红 + 「已超支 ¥x」红字；不做推送提醒（私用 app 克制原则）。
9. **周期记账 = 打开时补录**（V1.3，无后台进程/无系统调度）：
   - 每次打开 app，把启用规则 `(游标, 今天]` 内到期的账补齐入库，再把游标推进到今天
     → 删掉的自动账不会复活；到期日**当天**打开就入账
   - **幂等靠数据库而不是靠调用方小心**：唯一索引 `(recurringRuleId, dateEpochDay)`。
     SQLite 唯一索引里 NULL 互不相等 → 手动账（NULL）同日可重复，自动账「同规则同日期」至多一笔；
     补录插入用 `OnConflictStrategy.IGNORE`，冲突即跳过
   - **规则备注自动写入生成账**：规则可设备注，每次自动入账原样复制进那笔账的 note；
     生成后仍可像手动账一样再编辑
   - **删除规则不抹历史**：只删规则本身，历史自动账保留且「自动」标记不消失
     （不置空 `recurringRuleId`；`recurring_rules.id` 用 AUTOINCREMENT 永不复用 + 不声明 FK，
     悬空引用无副作用）
   - 新建规则默认「从下一个到期日」开始，不回溯历史（防一建规则补录一串旧账）
10. **桌面小组件用 XML + RemoteViews，不用 Glance**（V1.4 重写，2026-10-19）：
    - Glance 拿不到根布局 `@android:id/background`（1584 §7.1）、深色只能走 `values-night` 静态资源（§12.5）、
      与独立进程 `:widgetProvider`（§1）冲突 → 结构性不合规，调参救不回来
    - **布局里不能用 `<View>`**：MIUI 的 HomeMIUIWidget 包装层会让整张布局 inflate 失败，桌面显示
      「载入窗口小部件时出现问题」；占位/分隔一律用空 `<LinearLayout>`
    - **`miuiWidget` 必须为 false**：声明 true 会被归入「小米小部件」通道，未过审条目在「安卓小部件」
      列表滚到底也找不到（实测 12 屏）。代价是失去曝光刷新，由主动刷新 + 零点 Worker + 30min 兜底
    - 尺寸不照抄规范的 44dp 字阶：`¥126.50` 是 8 字符，按 `可用宽 / (字符数×0.58)` 推导，
      实测 162dp 卡 → 27dp；两套布局断点 136dp

## 数据表（version 4）

- `records`: id, amountCents(分), isExpense, categoryId, dateEpochDay, note,
  createdAtMillis, **clientUid(UUID,唯一索引)**, **updatedAtMillis**,
  **recurringRuleId(可空：null=手动账，非空=该周期规则自动生成)**；
  唯一索引 **(recurringRuleId, dateEpochDay)**
- `categories`: id, name, isExpense, sortOrder；唯一索引 (isExpense, name)
- `budgets`: **yearMonthKey(PK,"2026-09")**, amountCents, updatedAtMillis
- `recurring_rules`: **id(PK AUTOINCREMENT)**, name, amountCents, isExpense, categoryId,
  period(WEEKLY/MONTHLY/YEARLY), dayOfWeek, dayOfMonth, monthOfYear,
  startDateEpochDay, lastOccurredEpochDay(补录游标), note, isActive, createdAtMillis, updatedAtMillis

### 版本历史与迁移纪律

| 版本 | 变更 | 迁移 |
|---|---|---|
| v1 (1.0.0) | records + categories | — |
| v2 (1.1) | records 加 clientUid/updatedAtMillis | MIGRATION_1_2（回填 UUID+时间戳） |
| v3 (1.1) | budgets 新表 | MIGRATION_2_3 |
| v4 (1.3) | recurring_rules 新表 + records 加 recurringRuleId + 唯一索引 | MIGRATION_3_4 |

**铁律：任何表结构改动必须同时交 Migration + MigrationTest + schema JSON**
（`app/schemas/` 已入库；测试用 MigrationTestHelper 从 assets 读 schema）。
真机升级演练（v1.0.0→v1.1、v1.2.1→v1.3 装真数据→覆盖安装→数据库直读断言）均已通过，详见 [`plans/`](plans/)。

---

## 安装

从 [Releases](../../releases) 下载 APK 直接安装，或自行构建（历次版本变更见 [CHANGELOG.md](CHANGELOG.md)）：

```bash
# 构建（perf 构建型，AOT 优化，日常使用推荐）
cmd.exe /c "D:\AndroidDev\build-app.bat D:\AndroidDev\projects\qingjizhang assemblePerf"

# 安装（adb.exe 是 Windows 进程，APK 路径要用 Windows 格式）
/mnt/d/AndroidDev/Sdk/platform-tools/adb.exe install -r 'D:\AndroidDev\projects\qingjizhang\app\build\outputs\apk\perf\app-perf.apk'
```

**升级请用覆盖安装（`adb install -r`），不要卸载重装**——账本数据在应用私有目录里，卸载会清空。保险起见，升级前在 app 内「菜单 → JSON 备份」导出一份。

## 开发与测试

```bash
# 构建 + 单元测试（113 个）
cmd.exe /c "D:\AndroidDev\build-app.bat D:\AndroidDev\projects\qingjizhang assembleDebug testDebugUnitTest --no-daemon"

# Room 仪器化测试（23 个：DaoTest 6 + MigrationTest 4 + StatsDaoTest 3 + BackupRestoreTest 2 + RecurringCatchupTest 8）
cmd.exe /c "D:\AndroidDev\build-app.bat D:\AndroidDev\projects\qingjizhang connectedDebugAndroidTest --no-daemon"
```

**注意：`connectedDebugAndroidTest` 跑完会卸载被测 app（数据一并清除），收尾必须重装 APK。**

已知环境坑：
- Room 2.8.5 的 room-migration 与 kotlinx-serialization 版本冲突（上游 bug 447154195），
  已在 build.gradle.kts 全局 force 1.8.1 对齐，勿改。
- 小屏问题在平板上测不出来：用 `adb shell wm size 1080x2340 && wm density 420` 把平板
  模拟成手机屏复现，用完 `wm size reset && wm density reset` 还原。
- **debug 包永远没有 AOT**：`dumpsys package dexopt` 显示 `run-from-apk`，且 debuggable 应用
  被 ART 禁止 `speed` 编译（强制也只到 `verify`）→ 性能观感天然差于正式包，别把 debug 包的
  卡顿直接当成代码问题。要测性能用 perf 构建型（`assemblePerf`）。
- **MIUI 的 `screenrecord` 可能只录到 ~1fps**，不能用于逐帧视觉取证；改用
  `dumpsys gfxinfo <pkg>`（jank/百分位/直方图）与
  `dumpsys SurfaceFlinger --latency '<pkg>/<Activity>#<id>' | head -1`（当前刷新周期）。
- `input swipe` 的 duration 别给太短（<150ms 在部分 MIUI 上产生不了有效滚动，测试白跑）。

## git 分支策略

- `main` 只保留可发布状态；tag：`v1.0.0` / `v1.1.0` / `v1.2.0` / `v1.2.1` / `v1.3.0` / `v1.3.1` / `v1.4.0` / `v1.4.1`
- 每功能一分支（`feat/1.1-*`、`feat/1.2-stats`、`feat/1.2-search`、`fix/v1.2-interaction`、`feat/1.3-recurring`、`feat/1.4-widget-xml`）
- 构建+真机测试全绿后 `--no-ff` 合并（保留合并节点，`git log --graph` 可读）
- 回退粒度 = 功能：哪条分支翻车丢弃哪条，不污染已合入的

## 开发笔记

- [`plans/`](plans/)：各版本开发计划书与实施记录

> 完整开发流水（DEVLOG）保留在我私有仓库，未公开。若对某个实现细节感兴趣，欢迎提 issue。

## License

私用项目，源码开放供参考。请勿用于商业用途。
