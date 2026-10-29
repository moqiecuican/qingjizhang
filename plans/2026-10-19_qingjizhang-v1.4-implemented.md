# 「轻记账」V1.4 开发计划书（最终版 · 已实施）

> **状态**：Implemented（2026-10-19）
> **代码范围**：`53d3f14..cbed851`，分支 `feat/1.4-widget-xml`
> **取代**：`2026-10-18_2356-qingjizhang-v1.4.md` / `2026-10-19_2011-…-rewrite.md` / `2026-10-19_2035-…-rewrite-v3.md`
> （三份原件已移入 `plans/archive/v1.4/`；逐日流水与失败尝试见 `DEVLOG.md`）
>
> **读法**：要施工或复现 → §2.1；要知道"为什么和最初计划不一样" → §2.2；要避坑 → §2.3；
> 要核对数字出处 → §6。

---

## 0. 一页速览

| 项 | 结果 |
|---|---|
| 交付物 | 桌面小组件 2×2（XML + RemoteViews）· 自定义图标 · 行距 4dp · perf 构建类型 |
| 版本号 | versionCode **6** / versionName **1.3.1**（未提升，见 §2.5） |
| APK | **13,334,435 字节**（clean debug；基线 13,000,742，+333KB / +2.6%） |
| 单测 | **113 绿**（0 失败 / 0 错误 / 0 跳过） |
| 真机 | Android 16 测试手机/ Android 16 / HyperOS 3.0 / 1200×2670 @480dpi（400×890dp） |
| 主要遗留 | 4×2 / 4×4 档暂缓；刷新钩子 ④⑤ 未端到端实测；版本号未提升 |

---

## 1. 交付状态表

| 项目 | 初版计划 | 最终实现 | 状态 |
|---|---|---|---|
| 桌面小组件 | Glance 1.2，2×2 + 2×1 两档 | XML + RemoteViews，**只做 2×2** | ✅ 重写（范围经用户收缩） |
| 自定义图标 | D 方向「记一笔」自适应三件套 + monochrome | 同（`#A8C686` 底 + 白色前景） | ✅ 完成 |
| 行距试验 | 2dp → 4dp | 保留 4dp（pitch 193→199px） | ⏳ 待用户主观验收 |
| perf 构建类型 | 非 debug + debug 签名 | 同（AOT speed-profile 生效） | ✅ 完成 |

---

## 2. 小组件（本版主菜）

### 2.1 最终技术方案（已实现，可直接照此复现）

#### 2.1.1 文件清单

**新增**

```
ui/widget/TodayExpenseWidgetProvider.kt   receiver + 渲染 + layout 选择 + PendingIntent
ui/widget/WidgetRefresh.kt                刷新出口 + WidgetRefreshWorker + WidgetMidnightWorker
res/layout/widget_today_2x2_large.xml     大卡（≥136dp）
res/layout/widget_today_2x2_small.xml     小卡（<136dp，隐藏辅助行）
res/layout/widget_loading.xml             initialLayout 加载态（半透明占位块）
res/drawable/widget_bg.xml                卡片背景 shape（圆角 20dp，色走 @color）
res/drawable/widget_btn_bg.xml            胶囊按钮 shape（圆角 20dp）
res/drawable-nodpi/widget_preview.png     预览图 450×450（文泉驿渲染）
res/values-night/colors.xml               深色色板
```

**修改**：`AndroidManifest.xml`（进程/action/版本号）、`res/xml/today_expense_widget_2x2_info.xml`、
`values/colors.xml`、`values/strings.xml`、`MainActivity.kt`（skip 冷启动）、`ui/LedgerScreen.kt`、`app/build.gradle.kts`

**删除**：`ui/widget/TodayExpenseWidget.kt`（Glance 版 337 行）、`res/xml/today_expense_widget_2x1_info.xml`、
`glance-appwidget` / `glance-material3` 依赖

#### 2.1.2 provider 配置

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_loading"
    android:previewImage="@drawable/widget_preview"
    android:description="@string/widget_2x2_desc"
    android:widgetCategory="home_screen" />
```

**不写** `targetCellWidth/targetCellHeight`（上一轮平板卡片尺寸异常的根因）。

#### 2.1.3 两套布局与断点

| 布局 | 适用宽 | padding | 标签 | 金额 | 辅助行 | 按钮高 | 按钮字 |
|---|---|---|---|---|---|---|---|
| `large` | ≥136dp | 15dp | 14dp | **27dp** | 11dp | 44dp | 14dp |
| `small` | <136dp | 12dp | 12dp | 20dp | **隐藏** | 34dp | 13dp |

切换依据 `OPTION_APPWIDGET_MIN_WIDTH`；`onAppWidgetOptionsChanged` 里用 SharedPreferences 记录上次
`MAX_WIDTH × MAX_HEIGHT` 比对，未变直接 return（1585 的坑：任何 option 变化都会回调）。

#### 2.1.4 金额字号推导（不照抄 44dp 的原因）

规范 44dp 的示例是「处暑」「28°」（2~4 字符）。我们的 `¥126.50` 是 **8 字符**：

```
可用宽 = 卡宽 - 2×padding
字号上限 = 可用宽 / (字符数 × 0.58)      // MiSans 数字宽 ≈0.58em
large: (162-30) / (8×0.58) = 28.4dp  → 取 27dp
small: (121-24) / (8×0.58) = 20.9dp  → 取 20dp
```

固定字号（避免每笔记账后数字跳大小），仅当字符数 >8 时按 `base × 8/len` 缩小兜底。
实现：`RemoteViews.setTextViewTextSize(id, COMPLEX_UNIT_DIP, size)`。

#### 2.1.5 颜色与深色

| 资源 | 浅色 | 深色 |
|---|---|---|
| `widget_bg_color` | `#EEF2E2` | `#2E3324` |
| `widget_text_primary` | `#1B2110` | `#EDF1DC` |
| `widget_text_secondary` | `#4A5236` | `#B9C39B` |
| `widget_text_tertiary`（40% alpha） | `#664A5236` | `#66B9C39B` |
| `widget_btn_color` | `#97C459` | `#639922` |
| `widget_btn_text` | `#173404` | `#EDF1DC` |

全部走 `values` / `values-night` 限定符，**代码里不判断夜间模式**（1584 §12.5）。
shape drawable 只写一份（`solid=@color/...`），颜色随限定符切换。

#### 2.1.6 点击与 quick_add

| 热区 | 动作 | PendingIntent |
|---|---|---|
| 卡片主体（`@+id/widget_root`） | 进流水页 | `Intent(MainActivity)` + `EXTRA_SKIP_COLD_START=true`，reqCode 1001 |
| 底部胶囊（`@+id/widget_button`） | 直达记账框 | `Intent(MainActivity)` + `EXTRA_QUICK_ADD=true`，reqCode 1002 |

flag 一律 `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`。
**注意**：1584 §11.2 提到的 `FLAG_MUTABLE` 只针对「编辑页大屏预览」（Host 要回填 `miuiWidgetScreenBound`），
本小部件没有编辑页，不适用。

#### 2.1.7 刷新链路

```
写入口（LedgerRepository）→ notifyWidgets()
  ├ 立即：WidgetRefresh.refresh() → AppWidgetManager.updateAppWidget(id, views)  ← 按 id 循环
  └ 兜底：enqueueWidgetRefresh() → WidgetRefreshWorker（unique work，REPLACE）
零点：WidgetMidnightWorker → refresh + 重排下一次（ViewModel init 里 scheduleMidnightRefresh）
系统：updatePeriodMillis=1800000（非小米设备的原生兜底）
```

**必须按 widgetId 循环**并逐个读 options：多实例可能落在不同尺寸（large/small），
不能一次 `updateAppWidget(component, views)` 套给所有实例。

| 钩子 | 位置 | 实测 |
|---|---|---|
| ① 记账保存 | `LedgerRepository:47` | ✅ |
| ② 编辑流水 | `:51` | ✅ |
| ③ 删除流水 | `:56` | ✅ |
| ④ 周期补录 | `:169 if (inserted > 0)` | ⏳ 代码审计 |
| ⑤ 恢复备份 | `:202` | ⏳ 代码审计 |

### 2.2 与初版计划的差异（改了什么、为什么）

| 项 | 初版计划 | 最终实现 | 原因 |
|---|---|---|---|
| 实现方式 | Glance 1.2（Compose DSL） | XML + RemoteViews | Glance 有 4 条结构性冲突，调参救不回（§4） |
| 尺寸档位 | 2×2 主档 + 2×1 紧凑档 | 只做 2×2 | 用户拍板收缩范围；2×1 无实例，删除无副作用 |
| 取色 | Material You 动态取色（GlanceTheme） | 固定品牌绿 | 用户先撤销动态取色，后确认保留绿板 |
| 金额字号 | 规范 44dp | **27dp** | 44dp 是 2~4 字符场景；8 字符按宽度推导（§2.1.4） |
| 标签/辅助字号 | 14sp / 12sp | 14dp / 11dp | 规范单位是 dp 不是 sp；对齐 Display2 / Display4 |
| 圆角 | 28dp | 20dp | 规范：手机 55px ≈ 20dp |
| 内边距 | 18dp | 15dp | 规范安全距离 42px ≈ 15dp |
| 参考图定位 | 逐像素复刻（PIL 测量） | **只做视觉参考** | 用户明确纠正：效果图不是施工图；数值一律走规范 |
| PendingIntent | `FLAG_MUTABLE` | `FLAG_IMMUTABLE` | 1584 §11.2 只适用编辑页；普通启动用 IMMUTABLE 才是官方推荐 |
| 预览 | 无 | `previewImage`（PNG） | `previewLayout` 会让 provider 解析失败（§2.3 坑 1） |
| MIUI 标识 | `miuiWidget=true` | **false** | true 时小部件列表里根本看不到，用户无法添加（§2.3 坑 3） |
| 布局占位 | `<View>` | 空 `<LinearLayout>` | MIUI 包装层不认 `<View>`，整张布局 inflate 失败（§2.3 坑 2） |
| 版本号 | 1 → 2 | 1 → 2（保留） | 实现方式整体更换属"功能变动"（1584 §10） |

### 2.3 踩坑清单（现象 → 根因 → 规避）

**坑 1 · `android:previewLayout` 让 provider 解析失败**
- 现象：加上之后桌面显示「载入窗口小部件时出现问题」，`dumpsys appwidget` 里该 provider 的
  `min/minResize/updatePeriodMillis/initialLayout` **全变 0**，小部件列表里彻底消失
- 根因：MIUI 解析 `AppWidgetProviderInfo` 时对自定义 `previewLayout`（根节点带 `@android:id/background`）处理失败
- 规避：**只用 `previewImage`（PNG）**，删掉 `previewLayout` 后立刻恢复 `min=28161`（=110dp）

**坑 2 · `<View>` 节点让整张布局 inflate 失败**
- 现象：卡片位置显示「载入窗口小部件时出现问题」，app 侧日志正常但 launcher 不显示内容
- 根因：AOSP 的 RemoteViews 白名单允许 `View`，但 MIUI 的 `HomeMIUIWidgetFrameLayout/LinearLayout` 包装层不认
- 规避：占位块与弹性留白一律用空 `<LinearLayout>`（`layout_height="0dp" layout_weight="1"`）

**坑 3 · `miuiWidget=true` 导致小部件列表不可见**
- 现象：「支持小部件应用」和「安卓小部件」两个列表里都找不到本 app，用户无法添加
- 根因：MIUI 把 `miuiWidget=true` 归入「小米小部件」通道，该通道只展示通过开放平台审核的条目
- 规避：**`miuiWidget` 必须是 false**（或整条移除）。代价是失去曝光刷新
  （`miui.appwidget.action.APPWIDGET_UPDATE` 不再下发），由主动刷新 + 零点 Worker + 30min 兜底
- 验证注意：**必须滚到列表最底部**（本机 12 屏）才能下结论，只滚 10 屏会误判

**坑 4 · `dumpsys appwidget` 的 provider 缓存有延迟**
- 装机后立刻查，`min` 读到 0；需 `am force-stop com.miui.home` 后再等 6~10 秒才刷新
- 规避：验收脚本里加等待，别把"缓存没刷新"当成"配置没生效"

**坑 5 · 增量构建的 APK 体积假膨胀**
- 现象：`assembleDebug` 增量构建 19,849,448 字节，zip 条目合计仅 12.65MB，**overhead 6.28MB**
- 定位：`classes8.dex`（raw 1228 字节）后面挂了 4.39MB 空洞，`file_paths.xml` 后 0.75MB、
  `widget_preview.png` 后 0.49MB —— 非对齐值，是补丁式重打包的残留
- 规避：**体积验收先 `clean`**。clean 后 13,334,435 字节，overhead 0.06MB

**坑 6 · 独立进程 + Room 可用，但内存超规范**
- `:widgetProvider` 里 onReceive + `todayExpense` 查询正常（WAL 多连接读）
- 但 `dumpsys meminfo` 的 **TOTAL PSS = 71,654KB（70MB）**，规范 1584 §1 要求 ≤35MB
- 结论：Kotlin/Room 运行时基线所致；自用可接受，若上架需瘦身（换轻量查询层或去 Room）

**坑 7 · 实测尺寸与规范表不一致**
- 规范 2×2 = 412×412px @2.75x = 150dp；本机 480dpi/1200×2670 实测 **486×563px @3.0 = 162×188dp**
- 原因：不同机型网格 cell 尺寸不同（162×188 的 options 值与 view 尺寸一致）
- 影响：断点 136dp 判定走 large ✓；高度多 25% 由 weight 弹性吸收
- 规避：**布局用 weight/相对定位，不写死总高**；字号按实测宽度推导

**坑 8 · 冷启动弹窗抢戏（收尾时发现的交互 bug）**
- 现象：点卡片主体应进流水页，但冷启动时仍弹出记账框
- 根因：v1.2 的「打开 app 就记一笔」写在 `LedgerScreen` 的 `LaunchedEffect(Unit)` 里，无条件执行
- 规避：`EXTRA_SKIP_COLD_START` + 卡片主 PendingIntent 带上；`LaunchedEffect` 里**仍消费
  `markColdStartShown()`** 但不 `openAddDialog()`（否则切 Tab 回来会补弹）

### 2.4 验收结果（真机实测）

| 项 | 期望 | 实测 |
|---|---|---|
| 数据一致性 | DB 今日合计/笔数/最近时刻 | `4800分 / 4笔 / 20:19` ↔ widget 完全一致 ✓ |
| 点「＋ 记一笔」 | 直达记账框 | 弹出 + 键盘自动聚焦 ✓ |
| 点卡片主体（冷启动） | 进流水页，不弹窗 | ✓（截图 `70_coldstart_card.png`） |
| 深色模式 | 背景/字/按钮跟随 | `#2E3324` / `#EDF1DC` / `#639922` ✓ |
| 钩子① 增 | widget 变 | `¥48.00→¥48.01 / 4→5笔` ✓ |
| 钩子② 改 | widget 变 | `¥48.01→¥48.03`（编辑 0.02→0.03）✓ |
| 钩子③ 删 | widget 变 | 回 `¥48.00 / 4笔 / 20:19` ✓ |
| 独立进程 | `ps -A` 见 `:widgetProvider` | ✓（PSS 70MB，见坑 6） |
| 测试数据残留 | 无 | `records` 65 条（与装机基线一致）✓ |

### 2.5 未完成 / 技术债

1. **4×2 / 4×4 档暂缓**（用户明确"暂时只做 2×2"）。`RecordDao.dayTotalsBetween` 已随之丢弃，
   将来做 4×4 柱状图需重新加。
2. **钩子 ④ 周期补录、⑤ 恢复备份未端到端实测**：④ 需造规则 + 改系统日期；⑤ 会清空真实账本。
   两者与 ①②③ 共用 `notifyWidgets()` 出口，代码审计通过。
3. **PSS 70MB 超规范 35MB**（坑 6）。
4. **版本号未提升**：仍是 `versionCode 6 / versionName 1.3.1`，装机无法区分新旧版本。
5. **small 布局未实测**：本机 2×2 固定 162dp，走不到 <136dp 分支（需切 5×8 桌面布局验证）。
6. **行距 4dp 待用户主观验收**（Phase 2）。

---

## 3. 图标 / 行距 / perf（摘要）

**Phase 1 · perf 构建类型**
新增 `perf`（`initWith(release)` + debug 签名 + minify 关）。实测 AOT 生效（`verify` → `speed-profile`）。
同数据 6 次交替滑动对比：**90th 5ms vs 11ms**、≤7ms 达标率 **93.1% vs 61.6%**、jank 0.35% vs 0.55%。
结论：debug 税真实且显著；性能问题一律用 perf 包复测。
坑：perf 非 debuggable → `run-as` 不可用，造数据必须先用 debug 包。

**Phase 2 · 行距 2dp → 4dp**
只改 `MonthBody` 的 `Arrangement.spacedBy(2.dp → 4.dp)`。实测相邻行 pitch **193px → 199px**（+6px≈+2.3dp）。
橡皮筋 overscroll 保留、Card 阴影未动。截图 `backups/v1.4-rowspacing/`。

**Phase 3 · 自定义图标（D 方向「记一笔」）**
`ic_launcher_foreground.xml`（账页三横线 + 斜铅笔，白色 4.5dp 圆角端点）+ `ic_launcher_monochrome.xml`（纯黑，
主题图标用）+ 背景 `#A8C686`。按 API 分档：`mipmap-anydpi-v26` 只放 bg+fg，`mipmap-anydpi-v33` 额外声明
`<monochrome>`。铅笔 5 个顶点 x-5dp 后最远 r=31.92（安全圆 33dp 内）。
mask 预览 `backups/v1.4-icon/mask_preview.png`（圆/squircle/水滴）。

---

## 4. 附录 A：被否掉的 Glance 方案（为什么不用）

初版用 `glance-appwidget` + `glance-material3` 1.2.0，实装后 APK 从 13,000,742 → 15,207,068（+2.10MB）。
四条与小米规范的结构性冲突：

| # | 规范要求 | Glance 现状 | 后果 |
|---|---|---|---|
| 1 | 1584 §7.1 根布局必须 `@android:id/background` 且有背景色 | RemoteViews 根节点 ID 由框架分配，**无法指定** | 系统圆角/切换动画识别不到 |
| 2 | 1584 §12.5 深色只能 `values-night` 静态资源 | `DayNightColorProvider` 代码取值 | 模式切换行为不可控 |
| 3 | 1584 §1 必须跑在 `:widgetProvider` 独立进程（≤35MB） | Glance session 跨进程失效，加了进程又删 | 拿不到进程保护与曝光刷新 |
| 4 | 1585 按 options 切布局 + weight 弹性 | `SizeMode.Exact` 尺寸自算，与格子脱节 | 平板实测声明 300×110dp 却渲染 146×64dp |

另有实现层面的失败记录（`DEVLOG` Phase 4 追加一~八）：`actionStartActivity` 无 Intent 重载、
quick_add 保存后 `viewModelScope` 被取消、MIUI 快速回收进程导致刷新滞后、`GlanceTheme` 动态取色发灰等。

**结论**：不是调参问题，是"生成的 RemoteViews 结构不受控"。改用 XML 后上述四条全部消失，
APK 反而回落到 13.33MB。

---

## 5. 附录 B：回滚与备份

| 对象 | 位置 / 方法 |
|---|---|
| 改造前文件（9 个 + SHA256） | `backups/v1.4-widget/pre-xml-rebuild/` |
| 未提交改动快照 | 同目录 `uncommitted.diff`（对应 `git stash@{0}`） |
| 装机基线数据 | 同目录 `baseline/`（DB 三件套 + counts.txt：65/12/1/1，user_version=4） |
| 本版 APK | `backups/qingjizhang-v1.4-widget-xml-debug.apk`（+ `.sha256`） |
| 回滚代码 | 未合并：丢弃分支；已合并：`git revert <merge>` |
| 回滚设备 | 装回 `backups/qingjizhang-v1.3.1-debug.apk`（同签名同 versionCode，`install -r` 保数据） |
| 数据 | 本版无表结构变更，不触碰 DB |

---

## 6. 关键数字出处（便于核对，勿凭记忆改）

| 数字 | 出处 |
|---|---|
| 2×2 = 412×412px / 5×8 = 334px / 负一屏 440px | 小米 1664 §1.3 尺寸表（图上读出） |
| 圆角 55px≈20dp、安全距离 42px≈15dp | 1664 §2.1 / §2.2（图上读出） |
| 字阶 44/14/11dp、单位是 dp | 1664 §2.3（图上读出） |
| 2×2 信息量 ≤3 | 1664 §3 信息分层表 |
| minWidth/minHeight 110/110dp | 1584 §3.2 |
| 内存 ≤35MB、独立进程 | 1584 §1 |
| `miuiWidgetVersion` 1→2 | 1584 §10 + `git log -S` 确认原值 1 |
| 基线 APK 13,000,742 | v1.3.1 封版实测（DEVLOG） |
| Glance 版 15,207,068（+2.10MB） | DEVLOG Phase 4 实现段 |
| clean debug 13,334,435 / 增量 19,849,448 | 本轮实测（§2.3 坑 5） |
| 实测 486×563px @3.0 = 162×188dp | 本轮 logcat `LauncherWidgetLayoutFactory` + options |
| 单测 113 | `testDebugUnitTest` 报告（本轮） |
| PSS 71,654KB | `dumpsys meminfo com.adrian.qingjizhang:widgetProvider`（本轮） |
| 行距 pitch 193→199px | DEVLOG Phase 2 |
| perf 90th 5ms vs 11ms | DEVLOG Phase 1 |
