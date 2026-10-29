# 轻记账 DEVLOG — 开发日志

> 项目的"航海日志"：按时间线记录决策、踩坑、根因、验证方法与**犯过的错**（不美化）。
> 约定：每轮迭代在文末追加一节；git log 记"改了什么"，本文件记"为什么、怎么发现的、怎么验的"。
> 参与者：Adrian（产品/验收/真机）、Hermes（开发/测试/报告）。

---

## 2026-09-30 凌晨 · 立项与规划

- **需求来源**：用户是编程小白，想要私用单机记账 app；自己不写代码，成品后研究架构。
- **选型决策（方案 A）**：Kotlin + Jetpack Compose + Room + MVVM 单模块，无网络权限。
  - 讨论过 Flutter / KMP：都被"没有 macOS 就无法验证 iOS 端"否决；结论是**记账 app 的资产是数据不是代码**，
    跨平台保险改为"CSV/JSON 导出进 MVP + 纯逻辑层与 Android API 解耦（为将来 KMP 留路）"。
  - 用户后来明确：此类成熟项目**豁免 prior-art-first 社区调研**（训练数据已足够），日志记录此豁免。
- **两条铁律写进设计**：金额一律以「分」(Long) 存储；日期用 LocalDate.toEpochDay()。浮点与字符串日期都是雷。
- **用户三条硬需求**：小米平板真机测试（不用模拟器）；UI 屏幕自适应；冷启动即弹记一笔、默认分类「餐饮」。
- 计划书：`~/.hermes/plans/2026-09-30_012326-qingjizhang-mvp.md`（6 Phase / 9 条验收）。
  命名/目录/CSV 范围三个问题经用户拍板：名「轻记账」、目录 `D:\AndroidDev\projects\qingjizhang`、CSV 进 MVP。

## 2026-09-30 白天 · Phase 0-3：骨架、数据层、UI、首绿

- **环境事实**（adb 实测）：平板 Android 16 测试平板，Android SDK 36，2032×3048 @400dpi；
  `android create empty-activity` 模板 = AGP 9.0.1 / Kotlin 2.3.20 / Gradle wrapper 9.1.0 / Compose BOM 2026.03.01 / Navigation3。
  验证 SDK 全程落 D 盘、C 盘零污染（计划书风险项）。
- **依赖版本调研结论**：KSP 自 2.3.0 起**不再与 Kotlin 版本绑定**（新版号体系），取 2.3.12；Room 2.8.5；material3-adaptive 1.2.0。
- **首绿前的编译坑清单**（每个都真修真验，commit `dad566a`）：
  1. Gradle version-catalog 保留字：别名含 `class` 直接报错 → `compose-material3-windowsize`。
  2. Room 索引 `Index(uniquePairs=...)` 不存在 → `Index(value=[...], unique=true)`。
  3. 新版 material3 **不再传递** material-icons-core → 显式加依赖。
  4. 嵌套 Scaffold 无 `contentPadding` 参数 → padding 移到外层 Box。
  5. `arrayOf(n, i)` 触发 reified 交集类型错误 → `arrayOf<Any>(...)`。
  6. **Compose 图标 DSL 删了字符串 pathData 重载**（新 BOM），试了 lambda 版/命名参数版均失败 →
     放弃代码内 vector，改 XML vector drawable（`res/drawable/ic_tag.xml`）。教训：新 BOM 的破坏性变更不预告。
- **36 个单元测试首绿**（Money 28 / CsvBuilder 5 / MonthStats 3），APK 12.6MB。
- **安装被拦**：`INSTALL_FAILED_USER_RESTRICTED` = 小米「USB 安装」开关，用户手动打开后 Success。
- **仪器化测试首跑 5/6 红** → 挖出本项目最大暗坑：
  **Room 2.8 默认新 SQLite 驱动不再触发 `RoomDatabase.Callback.onCreate`**，预置分类静默丢失。
  修复：幂等 `seedPresetCategories()`（空表才插），ViewModel init 于 IO 线程调用 + 测试 setup 显式调用。6/6 绿（commit `52a8a09`）。
- **真机冒烟**（adb + uiautomator dump + 截图三重）：冷启动弹窗/默认餐饮/数字键盘/记 12.34/编辑 88.88/删除/空态/
  CSV 分享（文件名带日期、BOM+CRLF+两位小数）/分类增删与「有流水禁删」保护 —— 全过。

## 2026-09-30 下午 · 用户反馈轮 1：键盘与双 Snackbar

- **Bug：冷启动不弹键盘**。根因：金额框没绑 FocusRequester（计划书写了、实现漏了）。
  修复：Add 模式 `FocusRequester` + 延迟 150ms（等 Dialog 窗口就绪，否则 IME 不弹）；Edit 模式故意不弹防误触。
- **Bug：删除后两条「已删除」**。根因：AppRoot 与 LedgerScreen **两层 Scaffold 挂同一个 SnackbarHostState**，一条消息渲染两次。
  修复：全 app 唯一收集点+唯一 Host 上移到 AppRoot。验证：uiautomator 文本扫描「已删除」计数 2→1 + 截图复核。

## 2026-09-30 下午 · 插曲：视觉路径与 Gradle 缓存答疑

- 用户问"截图识别是否原生多模态"。如实区分：多数走 native（工具首行 `Image loaded into your context`）；
  中途模型 flash→max 切换后**静默掉进 aux 转述有损路径**。诊断 = image-recognition 技能脚本：
  上游 bug `PROVIDER_TO_MODELS_DEV` 缺 alibaba-cn 别名。修复：`model_overrides.alibaba-cn.qwen3\.8-max.supports_vision: true`
  （点号转义、yaml 读回验无假嵌套），并登记入技能 confirmed-vision-models.md。
- 用户质疑"Gradle 是否重复下载"：查证 wrapper dists 时间戳 = 8月10日（工具链安装日），**零重复下载**；
  实际新增 = SDK platform android-36 + Maven 依赖（gradle-home/caches）。**更正了自己先前"首建下载 Gradle"的错误表述**。
- 顺带答疑：项目内 `gradle/` 只是 54KB wrapper 启动器；`GRADLE_USER_HOME=D:\AndroidDev\gradle-home` 全局共享缓存的分工。

## 2026-09-30 傍晚 · 用户想法轮 2：保存按钮与回车提交

- **保存按钮升级**：全宽填充 Button(52dp) + 下方安静「取消」，与键盘回车共用同一 `doSave` 路径。
- **回车即保存**：`ImeAction.Done` + `KeyboardActions(onDone)`。真机验证输 77 → 点 ↵ → 直接入账。
  **诚实边界**：键上文字由输入法决定（小米显示 ↵ 图标）；Compose 未暴露 EditorInfo.actionLabel，自定义"保存"二字做不到。
- **验证中揪出第三 bug**：Compose Dialog 窗口默认不随 IME 收缩 → 弹窗底部（含保存按钮）被键盘盖住。
  修复：`DialogWindowProvider.window.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)`，弹窗随键盘变矮、按钮永露。
- **⚠️ 66 元事件（我的误报，记录在案）**：我曾汇报"测试数据已清空"，数据库直读（含 **-wal 文件**，Room 默认 WAL 模式，
  只拉主库会得到空壳）证明 66 元从未被删——当时删除点击落空正是因为按钮被键盘盖住。
  最终 UI 逐条删 + DB 核验归零（0 流水 / 12 分类）。教训：**"删没删干净"以数据库为准，不信界面快照**。

## 2026-10-01（次日）· 轮 3：间距修复与一次撤回

- **「新增分类」双空格**：根因是 `Text("  新增分类")` 手打空格当间距 → 改 `Icon + Spacer(4dp) + Text`，截图复核通过。
- **撤回一条缺陷**：我曾列"分类对话框确定键被键盘遮挡"。用户说没遇到 → 真机复现：小对话框整体在键盘上方，
  **遮挡不存在**；当初是我在键盘弹出动画中点坐标落空，误归因。缺陷清单清零。
  教训：**操作失误与产品缺陷要分开归因**；被用户质疑时先复现再坚持或撤回。

---

## 验证方法论（沉淀，供后续迭代复用）

1. **坐标别靠肉眼换算**：uiautomator dump 拿 clickable 节点 bounds 算中心点；我曾两次坐标算错导致"功能坏了"的假象。
2. **数据断言查数据库**：`adb exec-out "run-as <pkg> cat databases/x.db[-wal|-shm]"` 三件套一起拉（WAL 模式！），sqlite3 直读。
3. **截图用原生视觉 + 裁剪**：`vision_analyze(region=...)` 看小字/间距；先确认工具首行是 native 路径再信细节判断。
4. **计数类验收用文本扫描**：dump 里 grep 关键词计数（如「已删除」出现次数），比看截图稳。
5. **测试分层**：纯逻辑单测（Money/CSV/统计）→ Room 仪器化（真机，跨月边界/排序/删除联动）→ 真机冒烟脚本化。

## 我的错误登记（不美化）

| 错误 | 发现方式 | 处置 |
|---|---|---|
| 测试常量 epochDay 手算错一天 | 自检 | 改用真实日期转换，消除手算 |
| "首建下载 Gradle 9.1" 错误表述 | 用户质疑 | 查时间戳更正 |
| "66 元已删除" 误报 | DB 直读翻案 | 清理+核验归零，记入本表 |
| "确定键遮挡" 误判 | 用户质疑+复现 | 撤回缺陷 |
| 跳过 prior-art 调研 | 用户点破 | 用户明确豁免，记录在案 |

## V1.1 待办（用户真机使用后攒需求）

预算与超支提示 / 搜索与筛选 / 分类占比与趋势图 / 周期记账 / 深色模式打磨 / 横屏与分屏专项实测。

## 2026-09-30 深夜 · V1.1 Phase 1：迁移地基与一次「假上游 bug」调查

- 版本范围由用户确认走方案 B 两版切分：1.1「保得牢」（迁移地基+时间戳/合并列+预算+JSON 备份），1.2「看得清」（统计+搜索）。
  新增需求：流水显示创建时间 HH:mm；加 clientUid/updatedAtMillis 为将来跨设备合并铺地基（本版不做合并本身）。
- git 进入分支模式：master→main 改名；`feat/1.1-migration` 起步。
- **T1.1** exportSchema=true + ksp room.schemaLocation → `app/schemas/.../1.json` 入库（7 列与实体核对一致）。
- **T1.2 踩坑全程（重要教训）**：MigrationTestHelper 一跑就 `AbstractMethodError: FieldBundle$$serializer ... typeParametersSerializers()`。
  - 我先猜"版本混装"，全局 force 1.8.1 → 测试仍红；**没读全堆栈**（其实报错已变成我自己测试代码的 CursorIndexOutOfBounds），误判 force 无效；
  - 又反向 force 1.7.3（还错误收窄到 androidTest 配置）→ KSP 编译期也崩；两次方向乱试，白烧构建轮次。
  - 用户叫停："去深挖一下，看有没有用法错误"。**深挖后反转**：
    1. javap 字节码实证：room-migration 2.8.5 的 `FieldBundle$$serializer` 未实现 `typeParametersSerializers()`；
       core 1.7.3 该方法是 abstract、1.8.1 是 default → Room 构件按 1.8.x 编译，运行时 core 必须 ≥1.8.1。
       上游 bug 本体 = Room 2.8.5 自家 POM 约束（strictly 1.7.3）与其构件实际编译版本（1.8.x）矛盾（issuetracker 447154195）。
    2. 我的用法错误：`createDatabase(db,1)` 第二次调用是**删库重建**不是复用句柄 → 查空库 cursor 越界。第一轮 force 1.8.1 方向本来就对。
  - 修复：恢复全局 force 1.8.1（注释写明字节码依据）+ 测试改用 createDatabase 返回句柄查询。一轮构建全绿。
  - **教训**：报错变了≠没修好；grep 关键字不能代替读全堆栈。改技术方向前必须完整读当轮日志。
- 结果：平板 8/8 仪器化测试绿（DaoTest 6 + MigrationTest 2），单测 36/36 绿。commit `6747bb7`。
- 过程插曲：给用户的调查报告走邮件通道发出（biggaywhale@claw.163.com → 2643179600@qq.com，IMAP 已发送文件夹回读验证）。


## 2026-09-30 深夜 · V1.1 Phase 2-4：时间戳、预算、JSON 备份

- **Phase 2 时间戳 + 合并地基**（分支 feat/1.1-timestamp，merge 8878a0d）
  - 迁移 1→2：records 加 clientUid（UUID，唯一索引）+ updatedAtMillis。
    存量回填用 SQLite `randomblob` 表达式生成 UUID v4（单机不撞车），updatedAt=createdAt。
  - UI：流水行「餐饮 21:35」（分类名后小灰字，用户选定样式）；CSV 加「时间」列。
  - 踩坑：`schema JSON 合并进 androidTest assets 的时序`——merge*AndroidTestAssets 默认不等 ksp*Kotlin，
    同轮构建拿到旧快照（缺新版本号 json → 测试 FileNotFoundException）。
    修：显式声明 merge*AndroidTestAssets dependsOn ksp*Kotlin。
  - 真机验证：时间显示 ✓、编辑后 updatedAtMillis 变化而 clientUid 不变（数据库直读对比）✓。
- **Phase 3 月度预算**（分支 feat/1.1-budget，merge 8052f17）
  - 迁移 2→3：budgets 表（yearMonthKey 字符串主键 "2026-09"）。
  - 总览卡进度条（超支变红）+「已超支 ¥x」红字；设置对话框清空金额保存=取消预算。
  - 真机全流程：设 ¥30 预算 → 记 ¥35 → 显示「已超支 ¥5.00」→ 删除流水 → 回到「预算剩余 ¥30.00」→ 清空金额保存 → 提示「已清除 9 月预算」✓。
  - 顺带修：新增分类按钮文字里的两个手工空格改 Spacer(4dp)（用户提的）。
- **Phase 4 JSON 备份/恢复**（分支 feat/1.1-backup，merge a2b879c）
  - BackupJson 格式 v1：三表全量快照，formatVersion 拒绝更高版本、ignoreUnknownKeys 容错未来字段。
  - 恢复：SAF OpenDocument 选文件 → 校验 → 强警告确认框（显示备份统计）→ 单事务清空重灌（保留原 id）。
  - **真机抓到一个真 bug**：`encodeDefaults` 未开，导出的 JSON 里 `app`/`formatVersion` 被省略，
    恢复方的校验形同虚设。修 + 补测试断言这两个字段必须显式出现（单测 build_explicitlyWritesAppAndVersion）。
  - 真机全流程：备份→删流水→SAF 选回→确认→数据库直读 clientUid 与备份逐字节一致 ✓。
- 测试规模：**54 单测 + 11 仪器化**（DaoTest 6 / MigrationTest 3 / BackupRestoreTest 2）。
  ⚠️ 更正：feat/1.1-backup 的 commit message 里写了 "13 instrumented"，实际 11（当时未复核 XML 就写数字）。
  教训并入下方登记：commit/报告里的数字必须从测试结果文件读，不能凭印象。

## 2026-09-30 深夜 · V1.1 Phase 5：升级迁移真机演练（本版最重要验收）

流程：git worktree 拉出 v1.0.0 单独构建 → 卸载现版 → 装 v1.0.0 → 记真数据
（¥123.45 备注 upgrade-test-A + 自定义分类 shengji-test）→ 三件套备份留底 → 覆盖安装新版（触发 1→2→3 双迁移）。

断言结果（数据库直读，不靠 UI 猜）：
- `PRAGMA user_version` = 3 ✓（双迁移都跑了）
- 流水：id=1, amountCents=12345, note='upgrade-test-A' **原样健在** ✓
- createdAtMillis 保持原值；clientUid 已回填 UUID；updatedAtMillis=createdAtMillis（"从未编辑"语义）✓
- categories 13 个（12 预置 + shengji-test id=13 sortOrder=8）**零丢失** ✓
- budgets 表已建（迁移 2→3）✓
- records 列共 9 个，`index_records_clientUid` 唯一索引存在 ✓
- app 启动无崩溃、UI 正常显示该笔流水 ✓

演练后清理：删除测试流水与 shengji-test 分类，数据库回到 0 流水/12 分类/0 预算；
worktree 移除（含清理 WSL 下误创建的字面路径目录 `D:/AndroidDev/qjz-v100`）。

## V1.1 收尾状态

- 计划内功能全部完成：迁移地基 / 时间戳+合并列 / 月度预算 / JSON 备份恢复。
- 未做（按用户指示）：自定义 app 图标（推迟到开发后期）。
- 未做（明确排到 V1.2）：统计图表、搜索筛选、周期记账。
- 质量收尾里的「横屏/分屏/暗色专项实测」尚未执行（下一步待办）。

## 2026-09-30 深夜 · V1.1 Phase 5 质量收尾：竖屏/暗色/Compact 三档实测 + 一个藏了整晚的 bug

- **用户点破的盲点**：此前所有真机测试都在横屏下进行（截图 3048x2032），竖屏从未验证。
- **竖屏实测抓出真 bug**：平板限宽 600dp 从未生效——内容一直全宽。
  根因：`Modifier.fillMaxWidth().widthIn(max=600.dp)` 顺序写反。fillMaxWidth 先把 min 约束
  锁成父宽，widthIn(max) 被约束强制失效（min>max 时 max 被抬到 min）。
  对话框限宽之所以一直正常，是因为它写成 `widthIn(...).fillMaxWidth(0.94f)`（顺序对）。
  修：LedgerScreen/CategoriesScreen 两处改为 widthIn 在前；代码注释记为「限宽顺序铁律」。
  commit `5c8a491`（hotfix 直落 main：验收期发现的 bug，合理例外）。
- **三档 WindowSizeClass 全覆盖**（density 技巧：wm density 560 把 dp 宽压到 581 触发 Compact）：
  - Expanded/Medium（横/竖屏 400dpi）：限宽 600dp 居中 ✓
  - Compact（竖屏+560dpi=581dp）：内容全宽 + 圆形 FAB ✓（Extended FAB 只在非 Compact 出现）
  - 暗色模式：深底、绿/红收支数字对比充足、时间灰字可读、无同色隐形 ✓
- **HyperOS 限制记录**：`am start --windowingMode 3`（分屏/freeform）被系统拒绝（截图为桌面空景），
  无法程序化开分屏窗口；用 density 压 dp 宽做 Compact 等价验证。分屏真验证留给用户手动。
- 教训：「横屏/分屏还没测」这类话要先看截图尺寸再下结论——证据一直在手边（3048x2032=横屏）。

## 2026-09-30 深夜 · 收尾事故：仪器化测试会卸载被测 app

- 用户发现平板上 app 不见了。查实：`connectedDebugAndroidTest` 跑完会**卸载被测 app**（AGP 行为）。
  此前 Phase 2 备份数据库报 "unknown package" 就是这个行为的早期信号，当时没钉死因果。
  今晚最终全量回归（22:58）是最后一次碰设备，跑完又卸了，我漏了「回归后重装」一步。
- 处置：重装 v1.1.0 APK（versionName 1.1.0 核验通过）；无真实数据损失（平板上只有已清空的测试数据）。
- **铁律入册**：凡跑过 connectedDebugAndroidTest，收尾必须 `adb install -r` 重装，再向用户交付。

## 2026-10-01 凌晨 · V1.2「看得清」全程

- 范围（用户拍板）：统计页（周/月/年粒度+自定义区间+环形图<5%合并其他+柱状图）+ 搜索筛选；图标再次推迟；周期记账→V1.3。
- **架构关键决策**：SQL 只出按天聚合，周/月/年分桶全在 core 纯函数 → v1 计划担心的 strftime UTC 时区风险被设计直接消除。
- **Locale 坑**：WeekFields.of(Locale.CHINA) 实测一周首日=周日（JDK 行为），周一 12-28 被归入上一周；改显式 DayOfWeek.MONDAY。单测 3 红逼出。
- **测试手算错**：600/9600=0.0625 我写成 0.06——代码对测试错，改测试并留注释。
- **跨月周跳转 bug（真机抓）**：点 9/28-10/4 周柱跳到 9 月（桶起点月）但数据在 10 月；改为落桶内金额最大日所在月。
- **「其他」明细双通道**：点环形图灰扇区=触点锚定气泡（主方案，真机成功未降级）；点图例「其他」行=缩进小字列表（B 方案兜底，用户提议的台阶）。
- **搜索不用 SQL LIKE**：contains 内存语义，% 通配符危害从设计不存在（单测 keyword_percentIsLiteral 断言）。
- **升级冒烟重做教训**：首次"冒烟"实为全新安装——仪器化测试卸载了 app 连带清数据，v1.1→v1.2 数据保留根本没验到。重做：装 v1.1.0 备份包→记 ¥777→覆盖 v1.2.0→DB 直读 user_version=3、流水/clientUid/分类全在。
- 结果：单测 72/72、仪器化 14/14 绿；versionCode 3 / versionName 1.2.0。

## 2026-10-08 · V1.2 修复轮：用户真机反馈 5 项（竖屏验证）

用户报 5 项问题，逐项取证→修复→复验。**关键线索由用户补充**："抽搐只在小屏（手机）出现"——平板屏大不需滚动，回路不成立。

| # | 现象 | 根因 | 修复 | 复验证据 |
|---|---|---|---|---|
| 1 | 搜索框退出输入后光标仍闪 | TextField 收键盘后仍持焦点 | FilterBar 监听 IME 可见性，收起即 clearFocus | focused: true → **false** |
| 2 | 记一笔备注每输一字跳回顶层 | 与 #3 同源（滚动位置被反复重置） | 同 #3 | 见 #3 |
| 3 | 编辑流水点备注框 → 窗口疯狂上下抽搐 | 窗口级 `SOFT_INPUT_ADJUST_RESIZE` + 弹窗内 `verticalScroll` 聚焦滚入 互相触发 → **布局反馈回路** | 删窗口 hack，改 `decorFitsSystemWindows=false` + 内容 `imePadding()`，键盘避让下沉到 Compose 布局层 | 模拟手机屏连拍 10 帧：修复前内容在 2 个滚动位置间来回跳；修复后帧间位移 **0** |
| 4 | 月份标题偏左 | `TopAppBar.actions`（⋮ 菜单 48dp）压缩 title 槽 | 菜单移入标题行 + 行首 32dp 占位（title 槽自带 16dp inset 需配平） | 标题中心 498 → **540**（屏宽 1080，正中） |
| 5 | 自定义区间无文案，不知要点两次 | DatePickerDialog 无标题 | 两段分别加「选择起始日期（第 1/2 步）」「选择截止日期（第 2/2 步）」+ 取消按钮 | 真机文本断言通过 |

**方法沉淀（重要）**：平板单设备测试会漏掉小屏问题。今后小屏场景一律用
`adb shell wm size 1080x2340 && wm density 420` 把平板模拟成手机屏复现，用完 `wm size reset && wm density reset` 还原。
"抽搐/抖动"类问题用**连拍 + ffmpeg 逐帧量化**（帧 md5 分布 + 弹窗顶边位置 + 拼接对比图）取证，不靠肉眼断言。

结果：72 单测 + 14 仪器化全绿；分支 `fix/v1.2-interaction`。

## 2026-10-08 · V1.3「自动记」：周期记账（打开时补录）+ 自动账标

范围：周期记账（每周/每月/每年，打开时补录）+ 自动账「自动」小标。
小组件推迟到 V1.4；自定义图标第三次推迟。

### 关键实现
- **迁移 3→4**：新增 recurring_rules 表 + records 加 recurringRuleId 可空列 + 唯一索引
  `(recurringRuleId, dateEpochDay)`；主键 `INTEGER PRIMARY KEY AUTOINCREMENT` → id 永不复用。
- **幂等靠数据库，不靠调用方小心**：计划书原写"部分唯一索引"，实现改为**普通唯一索引**——
  Room 注解无法声明部分索引（会过不了 schema 校验），而 SQLite 唯一索引里 NULL 互不相等，
  天然等价于 `WHERE recurringRuleId IS NOT NULL`：手动账同日可重复，自动账同规则同日至多一笔。
  补录插入用 `OnConflictStrategy.IGNORE`，冲突即跳过。
- **补录游标 lastOccurredEpochDay**：每次打开只生成 `(游标, 今天]`（含今天），生成后推进到今天
  → 删掉的自动账不复活；开始日之前不生成（新规则从"下一个到期日"起，不回溯历史）。
- **规则备注 → 生成账备注**（用户特别要求）：每次自动入账把规则 note 原样复制进生成账，
  生成后仍可像手动账一样再编辑。
- **删除规则不抹历史**（用户拍板）：历史自动账保留且「自动」标不消失。实现=不置空
  recurringRuleId，靠 AUTOINCREMENT（id 不复用）+ 不声明 FK（悬空无副作用）。
- **备份格式升 v2**：`BackupPayload` 加 recurringRules 并给旧 v1 备份留默认值；
  roundtrip 单测覆盖「规则与自动标完整回来」。**这是计划评审时自查出的疏漏**——
  不纳入备份会导致恢复后规则全丢、历史自动账 ruleId 悬空。

### 真机踩坑
- **内层 Scaffold 收不到外层 padding → FAB 被底部导航栏压住**（周期记账页）：
  Scaffold 作根节点时 FAB 定位相对自身，而我把 padding 只给了内容容器。
  修法：与 LedgerScreen 同款——padding 加在包裹 Scaffold 的 Box 上。
  诊断手法：uiautomator dump 找不到 FAB 节点 → 先截图目视确认"其实存在"→ 判定为被遮挡
  而非未渲染，避免了误改渲染逻辑。
- 覆盖安装冒烟（v1.2.1→v1.3.0，真机不卸载）：user_version 3→4、12 分类/2 流水/1 预算零丢失、
  recurring_rules 建表且旧流水 recurringRuleId=NULL、新规则当场补录 1 笔、流水页显示正常。
- 观察：v1.2.1 流水页的 ExtendedFAB 存在且在 uiautomator dump 里**不出现**（dump 抓取怪癖），
  后续 UI 自动化定位不到 FAB 时优先用截图坐标，不要断定"没渲染"。

### 测试
- 单测 **108**（+21 RecurrenceTest：月末截断/闰年/跨年/跨周/区间语义；+12 CatchupTest；+3 BackupJson v2 兼容）
- 仪器化 **23**（+4 MigrationTest 3→4 含幂等语义断言；+8 RecurringCatchupTest：幂等/游标回退仍拦重复/
  暂停不生成/备注复制/删除保标/备份恢复）

### 修复：⋮ 菜单跑到屏幕最左边（回归自「问题4」修复）
- **现象**：流水页点右上角 ⋮，下拉菜单渲染在屏幕最左侧（实测菜单项 x=70~417，而按钮在 x=2988）
- **根因**：Material3 `DropdownMenu` 锚定的是**父布局节点**的边界，不是某个按钮。修复「月份标题偏左」
  时把菜单从 `TopAppBar.actions`（内部那个只包按钮的小 Row）搬进了 `Modifier.fillMaxWidth()` 的
  标题行 → 弹窗按整行的 start 边界对齐，于是一路跑到屏幕左边
- **修法**：把 ⋮ 按钮与 DropdownMenu 一起包进小 `Box`，父节点恢复为「按钮大小」
- **复验**：菜单项 x=2671~3018（紧贴按钮下方），截图确认；6 个菜单项点击均正常
- **教训**：`DropdownMenu` 必须与其触发按钮同属一个"按钮大小"的父容器（Box）；
  放进任何 full-width 容器都会把弹窗拽到屏幕左边

### 修复：⋮ 菜单贴死屏幕右边缘，缺美观边距
- **现象**：菜单锚点修好后改为紧贴屏幕右边缘（像素扫描：面板延伸到 x=3047，即最后 1 个像素，边距 0dp）
- **根因**：右侧空间不足时 `DropdownMenu` 会把弹窗**末端对齐到锚点末端**，而锚点右端 = 屏幕边缘；
  且原状态下菜单还溢出屏幕约 8px
- **修法**：给 `DropdownMenu` 加 `offset = DpOffset(x = (-15).dp, y = 0.dp)`
  （不是想当然的 -12dp：要减掉原先溢出屏幕的那 8px，净可见边距才等于 12dp）
- **复验**：像素扫描面板右边界 = 3018px（屏宽 3048）→ 右边距 **29px ≈ 11.0dp**，
  与 ⋮ 图标右缘（3020）成一条线；三处扫描行结果一致
- **教训**：调"弹窗离边界的距离"不能只按目标 dp 设 offset——先量出当前实际边界，按差值补偿

## 2026-10-09 · V1.3.1 封版：2 个 UI 修复 + 一次性能调查

### 修复（都回归自「问题4 标题居中」那次改动）
- **⋮ 菜单跑到屏幕最左边**：`DropdownMenu` 锚定的是**父布局节点**的边界，不是某个按钮。
  修标题居中时把菜单从 `TopAppBar.actions`（小 Row）搬进了 `fillMaxWidth()` 的标题行 →
  弹窗按整行 start 对齐。修法：把按钮与菜单同包进一个小 `Box`。
  实测：菜单项 x 70~417 → **2671~3018**。
- **菜单贴死屏幕右边缘**：右侧空间不足时弹窗末端对齐锚点末端（= 屏幕边缘）。
  修法 `offset = DpOffset(-15.dp)`——**不是想当然的 -12dp**：原状态菜单还溢出屏幕约 8px，
  要减掉那 8px，净可见边距才等于 12dp。实测右边距 29px ≈ 11.0dp（手机竖屏 13.0dp）。
- 教训两条：① `DropdownMenu` 必须与触发按钮同属一个"按钮大小"的父容器（Box），
  放进任何 full-width 容器都会把弹窗拽到屏幕边；② 调"弹窗离边界多远"必须先量出当前实际
  边界，再按差值补偿，不能直接按目标 dp 设 offset。

### 滚动性能调查（用户报「手机滑动卡 + 上下轻微闪烁」）
- **实测数据（两台设备，均为 debug 包）**
  - 手机（骁龙 8 Gen 3 / 120Hz）：图层恒 120Hz、滑动期 1138 帧、jank 0.53%、
    帧耗时中位 7ms、GPU 2~3ms
  - 平板（骁龙 8 Gen 2 / 144Hz）：触摸/惯性期 144Hz、jank 0.1~0.5%、中位 5~8ms、GPU 2ms
  - 结论：**出帧层面全达标，瓶颈不在帧率**；"卡"的观感另有来源
- **关键发现：debug 构建 = `run-from-apk`（零 AOT）**
  `dumpsys package dexopt` 显示 `status=run-from-apk`；即使强制 `cmd package compile -m speed`
  也只给到 `verify` 档——**debuggable 应用被 ART 禁止做 speed 优化**。也就是说 debug 包永远
  跑在最慢的执行形态；SoC 强的设备余量能盖住，弱一些的就明显。要验证需另建非 debug 的
  `perf` 构建类型（非 debug + debug 签名，可覆盖安装保数据）。
- **「上下闪烁」**：用户判定为「卡片边缘/文字的细线状闪动，滑动全程都有，向下快滑更明显」。
  机理推断：每行 M3 `Card` 的默认阴影（模糊圆角）+ 行间距仅 2dp + 滑动时位置为亚像素 →
  阴影边缘逐帧重栅格化；向下快滑更易撞端点触发平台 stretch overscroll，整体缩放进一步放大。
  用户选择**暂不修**（保持现状）。修法备选：Card→扁平 Surface+分隔线、行间距 2dp→4dp、
  关掉该列表的 overscrollEffect。
- **工具坑（重要）**：**MIUI 的 screenrecord 在此机型只录到 ~1fps**（20 帧/20 秒，
  帧时间戳呈"2~3 帧一簇 + 静默 3 秒"），无法用于逐帧视觉取证 → 别浪费时间在录屏找 jank。
  可靠的替代读法：
  - `dumpsys gfxinfo <pkg>` → jank 帧数/百分位/HISTOGRAM（含 GPU 直方图）
  - `dumpsys SurfaceFlinger --latency '<pkg>/<Activity>#<id>' | head -1` → 该图层当前刷新周期
    （6944444=144Hz / 8333333=120Hz / 16666666=60Hz / 20000000=50Hz）
  - `dumpsys package dexopt` → 应用是否 AOT 编译（`run-from-apk` = 无机器码）
  - 注意：`screenrecord`+`wm size` 等改动后要还原；`input swipe` 的 duration **不能太短**
    （90ms 在这台 MIUI 上产生不了有效滑动手势 → 列表几乎不动，白测）
- 顺带确认：Compose BOM 2026.03.01 → 平台默认开启 stretch overscroll（橡皮筋）；
  列表短（如 20 条≈2~3 屏）时几乎每次 fling 都撞端点，这是"手机比平板更容易察觉"的可能原因之一。

### 封版
- `versionCode 6 / versionName 1.3.1`；回归 **108 单测 + 23 仪器化全绿**；
  tag `v1.3.1`；APK 备份 `qingjizhang-v1.3.1-debug.apk`（sha256 `776953af…`）。
- 封版包现场验收：⋮ 菜单在右侧（x=2634+）、面板右边距 29px≈11.0dp ✓

## 2026-10-19 · V1.4 Phase 1：perf 构建类型（"debug 税"实测）

- **T1.1** 新增 `perf` buildType（`initWith(release)` 非 debug + debug 签名 + minify 关）
  - 覆盖安装后 `dumpsys package dexopt`：`verify` → **`speed-profile` [reason=baseline]**（异步，约 5 秒后生效）
  - **坑：perf 非 debuggable → `run-as` 不可用**。凡需要"造数据/DB 直读"的步骤（含升级冒烟）必须先用 debug 包装数据，再覆盖装 perf（同签名同 versionCode 数据保留）
- **T1.2** 对比（平板模拟手机 1080×2340@420；同签名、同 60 条数据、各 6 次交替滑动 250ms）

| 指标 | perf（AOT speed-profile） | debug（run-from-apk） |
|---|---|---|
| 渲染帧数 / 11.9s | 1442 | 1272 |
| Janky | 5（0.35%） | 7（0.55%） |
| 50th | 5ms | 6ms |
| **90th** | **5ms** | **11ms** |
| 95th | 8ms | 17ms |
| 99th | 14ms | 26ms |
| ≤7ms 达标率 | **93.1%** | **61.6%** |
| GPU 90th | 2ms | 5ms |

- **结论：debug 税真实且显著**（90th 差 2.2×、144Hz 预算达标率 93% vs 62%）。日常开发仍用 debug 包，性能问题一律用 perf 包复测；手机上"卡"的观感有相当部分来自这个，不是代码慢。
- APK 体积：perf 8.4MB / debug 12.4MB（debug 含 tooling 与测试依赖）

## 2026-10-19 · V1.4 Phase 2：行距 2dp → 4dp（试验）

- 改动仅 1 处：`MonthBody` 中 LazyColumn 的 `Arrangement.spacedBy(2.dp)` → `4.dp`
- 实测（平板模拟手机 1080×2340@420）：相邻行 pitch **193px → 199px**（+6px≈+2.3dp，与 2dp→4dp 的理论 +5.25px 一致）
- 前后截图：`backups/v1.4-rowspacing/before_1.png` / `after_1.png`
- 橡皮筋 overscroll 保留、Card 阴影未动（按计划书 §0.5，用户拍板）
- **待用户真机主观验收**：改善则保留；觉得变丑或没改善 → `git revert` 本 commit（回退标准 §0.5）

## 2026-10-19 · V1.4 Phase 3：自定义图标（D 方向「记一笔」）

- 按计划书附录 A 落地（**不重绘图**）：`drawable/ic_launcher_foreground.xml`（账页三横线 + 斜铅笔，白色 4.5dp 圆角端点）
  + `drawable/ic_launcher_monochrome.xml`（同图形纯黑，供 Android 13+ 主题图标）
  + `values/colors.xml` 新增 `ic_launcher_background = #A8C686`
- 自适应图标按 API 分档（v2 审计修正）：`mipmap-anydpi-v26/` 只放 bg+fg（`ic_launcher` 与 `ic_launcher_round` 同步）；
  `mipmap-anydpi-v33/` 额外声明 `<monochrome>`
- 几何：5 个铅笔顶点 x-5dp 后全部 ≤ 安全圆（最远 r=31.92），实算核验
- 真机验收：平板桌面已显示新图标（绿底三条白线 + 斜铅笔，无裁切）
- 三 mask 预览与对比度：`backups/v1.4-icon/mask_preview.png`（圆/squircle/水滴）、`home_icon.png`
- **待用户确认**：背景色 `#A8C686`（§0.4 写的是"候选，定稿与用户确认"）；白色/绿底对比度 ≈ 1.9:1，偏弱，若要更醒目可换深色前景（如 `#173404`）
- 主题图标（单色层）需在系统开"主题图标"后目视确认——留给用户手动验收

## 2026-10-19 · V1.4 Phase 4：桌面小组件（Glance）

### 实现
- 依赖：`glance-appwidget` / `glance-material3` **1.2.0** + `work-runtime-ktx` 2.10.0（后者是钩子⑥跨天刷新必需，
  计划书 §0.3 未列，实施时补上）。依赖解析一次通过；APK 体积 13,000,742 → **15,207,068（+2.10MB，阈值 3MB 内）**
- 数据层：`RecordDao.todayExpense(day)` 一条 SQL 出「合计/笔数/最近时刻」；`TodayExpenseRow`
- 文案纯函数 `core/WidgetText.kt`（日期标签/辅助行/金额，空态占位）+ 5 条单测
- 两档：`TodayExpenseWidget2x2` / `2x1`（各一个 Provider + Receiver），共用数据层
- 刷新：`WidgetRefresh.refresh()` 统一出口；Repository 的 5 个写入口（增/改/删/补录/恢复）统一接线；
  跨天零点用 `WidgetMidnightWorker`（WorkManager 排本地零点，重排下一次）
- quick_add：`MainActivity` 加 `launchMode="singleTask"` + onCreate/onNewIntent 双路解析 + 「最多一个弹窗」去重

### 真机踩坑（4 个，都记下来）
1. **Glance 1.2 没有 `actionStartActivity(Intent)` 重载**（只有 ComponentName / Class + ActionParameters）。
   必须用 `actionStartActivity<MainActivity>(actionParametersOf(Key(EXTRA) to true))`，
   Glance 会把参数按 key 名塞进 Intent extras（实证：`ActionCallbackBroadcastReceiver.Companion.putParameterExtras`）
2. **`snapshotFlow` 不追踪 `StateFlow.value`**：首版用它判断"弹窗关闭→退桌面"，保存后**不退**。
   改成 `combine(vm.quickAddActive, vm.dialogMode)` 直接收 StateFlow ✓
3. **quick_add 保存后 Activity 立即 finish → `viewModelScope` 被取消**，刷新协程被掐断（DB 已更新、widget 仍旧数）。
   改为应用级 `CoroutineScope(SupervisorJob()+IO)` + WorkManager 兜底 + finish 前留 700ms
4. **MIUI 会快速回收进程**：即使上面三招，quick_add 路径下 widget 仍可能滞后数秒~数十秒
   （logcat 显示 Glance session 成功 `Worker result SUCCESS`，属 launcher 侧延迟）。
   现状：**数据一致、刷新最终一致，但非严格"秒级"**。若要严格即时，可改为"finish 前等待刷新完成"（需要 VM 暴露刷新完成的 await）

### 真机验收（已完成项）
- 2×2 上桌面：`今日支出 · 10月19日` / `¥38.30` / `共 2 笔 · 最近 19:19` / 胶囊按钮**贴底** ✓
- 数字随记账变化：38.30 → 50.64 → 58.30 → 63.30 → 65.30（每次安装/前台记账均正确）✓
- 点小组件「记一笔」→ **直达记账框**（冷启动路径）→ 保存 → **退回桌面** ✓
- 单测 **113** 条全绿（新增 WidgetTextTest 5 条）

### Phase 4 追加：用户评审后的重做（布局/刷新/手机尺寸）

**用户点评（照录结论）**：① 没用 Material You 动态取色；② 刷新要秒级；③ 2×2 文字全挤左上角、大量留空，按钮位置/大小不合理（要更宽更靠上）；
④ 2×1 视觉完全失衡；⑤ 没在手机尺寸下验证。

**已改**
1. **布局重做（两档）**
   - 2×2：文字块上下各留弹性空白 → 垂直居中；主数字 28sp（原 40sp 会溢出）；按钮 40dp 高、横向内边距 14dp（更宽）
   - 2×1：左列「今日支出 + 数字」两行、右侧 32dp 圆形＋，全部 `maxLines = 1` 防折行
2. **秒级刷新（记录修正 2026-10-29）**：`notifyWidgets()` 的实际实现是**发起即忘**，不是内联 await——
   `private fun` + `refreshScope.launch { WidgetRefresh.refresh() }`，不 join、不等刷新完成。
   原因是 `quick_add` 保存后 Activity 立刻 `moveTaskToBack`，挂在调用方协程会被取消。
   （原文「改回内联 await」与代码不符，已按代码更正；审查 2.1）WorkManager 仅兜底
3. **手机尺寸验证**：平板 `wm size 1080x2340` + `density 420` + 竖屏，截图 `backups/v1.4-widget/widget_phone_2x2.png`、`widget_phone_v2.png`

**手机尺寸下发现并修正的问题（重要）**
- 首版按平板的大格子设计字号：手机 2×2 里 **金额被截成「¥83.」、按钮被挤出卡片**；2×1 的标签折成三行 → 已按**最小格子**收敛字号（2×2 28sp/11sp、2×1 18sp/10sp）+ 全部单行

**未完成 / 待办（如实记录）**
- **Material You 动态取色没做成**：`glance-material3` 的 `colorProviders(ColorScheme, ColorScheme)` 在 1.2.0 里不是公开 API
  （编译期 `Unresolved reference`），核心 `GlanceTheme.colors` 走资源色不是壁纸取色 → 当前用 §0.2 定稿色板。
  可行路径：升级 Glance 到暴露该 API 的版本，或自建 `ColorProviders`（27 个 ColorProvider，工作量大且易错）
- 布局只做到"能容纳"，**视觉美观度仍需用户过目**（我已无法自证好看）

### Phase 4 追加二：小组件改为「尺寸自适应」（按用户指示）

- 用户指出：**平板模拟手机看桌面不可靠**（连小米自家图标/组件也会变形），但**自适应本就必须做**——否则换手机又会变样
- 实现：两个 Widget 都改 `SizeMode.Exact`，内容用 `LocalSize` 分三档：
  - **ShortRow**（高<95dp，≈2×1）：左「今日支出 + 金额(20sp)」/ 右 32dp ＋ 按钮
  - **SmallSquare**（95~160dp，≈小 2×2）：只放「今日支出 + 金额(24sp)」，不放按钮（整卡点按进流水页）→ 从根上消除"按钮被挤出卡片"
  - **Large**（≥160dp）：完整版（日期标签 + 30sp 金额 + 辅助行 + 40dp 胶囊按钮）
- provider XML 允许自由缩放（`resizeMode="horizontal|vertical"`）
- 手机尺寸（1080×2340@420）实测：截断/挤出问题已消除（截图 `backups/v1.4-widget/widget_phone_v3.png`）
- 仍未做：Material You 动态取色（Glance 1.2 未公开 colorProviders → 待议）

### Phase 4 追加三：外援提示（关键，下次照此重做）

1. **Material You 在 Glance 1.2 是支持的**，我之前判断错了：
   - 正确：`GlanceTheme { ... }`（**不传 colors**）→ Android 12+ 由 `DynamicThemeColorProviders` 映射系统资源 ID，自动壁纸取色 + 深浅色跟随
   - 只有 **API<31** 才传 `androidx.glance.material3.ColorProviders(light = ..., dark = ...)` 兜底
   - 卡片背景用 **`GlanceTheme.colors.widgetBackground`**（桌面专用、对比度调过），不要用 `surface`
   - 我踩的坑：无条件传静态色板 → 覆盖了动态取色；且当时误用函数 `colorProviders(...)`，真正公开的是**类构造** `ColorProviders(...)`
2. **自适应要用 `SizeMode.Responsive` 断点**（预渲染缓存、旋转零延迟）；`SizeMode.Exact` 每次微调都跨进程重绘、耗电易闪
   - 断点示例：2×2 → 110×110 / 150×150 / 250×250；2×1 → 110×50 / 200×110
   - 内容按 `LocalSize.current` 分支：紧凑档只留金额（防裁剪），非紧凑档才显示辅助行与按钮
3. **防溢出三铁律**：容器少用固定 dp、用 `defaultWeight()` 分配；所有 `Text` 显式 `maxLines`；字体缩放 130% 时也不能把按钮挤出去

**当前状态**：上述重写两次编译失败（Kotlin 报错细节未落进构建日志，只看到 "Compilation error"），已回退到可构建版本 `eb38a39`；
布局/自适应修复**未完成**，下次按上面三点重做，并**每次提交前必须 assembleDebug 通过**。

### Phase 4 追加四：动态取色 + Responsive 断点（编译通过、已装机）

- **动态取色**：内容统一包一层**裸 `GlanceTheme {}`**（不传 colors）→ Android 12+ 用壁纸 Material You；
  把所有 `Palette.*` 静态色换成 `GlanceTheme.colors.*`（surface / onSurface / onSurfaceVariant / primary / onPrimary）
  —— 这正是外援指出的关键：**无条件传自定义色板会覆盖动态取色**
- **自适应**：两档都改 `SizeMode.Responsive`（2×2：110/150/250dp；2×1：110×50 / 200×110）
- 编译验证：`BUILD SUCCESSFUL`（这次提交前先构建，不再犯上次的错）
- 真机：平板横屏原生尺寸（2032×3048 / 400dpi）截图 `backups/v1.4-widget/widget_tablet_landscape.png`
- 未做的细节：`widgetBackground` 专用 token 未试（先用 surface，避免再引入编译风险）；低版本 ColorProviders 兜底暂用默认 M3 基线

### Phase 4 追加五：字号随格子等比缩放（解决"大格子大量留空"）

- 平板横屏原生尺寸实测发现：格子很大（≈370dp 高）但字号写死 → 内容缩在中间、上下大片留空
- 改为按 `LocalSize` 高度等比计算（外援建议：Glance 无自动缩放字号）：
  `amountSp = h*0.17 (18~54)` / `labelSp = h*0.05 (9~15)` / `pad = h*0.055 (8~28)` / `btnH = h*0.12 (30~54)`
- 110dp 小格 → 金额 ≈19sp；370dp 大格 → 金额 ≈54sp
- `BUILD SUCCESSFUL` 后装机，截屏 `backups/v1.4-widget/widget_tablet_landscape_v2.png`

### Phase 4 追加六：回归固定色板 + 按参考图重做版式

- 用户决定：**取消动态取色，回到固定色板**；版式以 `小组件参考图/` 为准
- 参考图比例（相对卡片高）：内边距≈9%、金额≈17%、按钮≈16%、圆角≈9%
- 2×2：左上「今日支出 · 10月18日」→ 超大金额 → 「共 N 笔 · 最近 HH:mm」→ 弹性留白 → **底部通栏大胶囊「＋ 记一笔」**（浅绿底 #97C459 + 深绿字 #173404）
- 2×1：左「今日支出」+ 金额，右侧圆形 ＋ 按钮
- 编译通过后装机，截图 `backups/v1.4-widget/widget_v6_ref.png`
- 踩坑：`GlanceModifier.clickable()` 要的是 `Action` 不是 `() -> Unit`（`CardBg(radius, { action() })` 会报 Unit/Action 不匹配）

### Phase 4 追加七：比例按参考图像素实测重设（待装机验证）

用 PIL 对 `小组件参考图/` 做像素测量（2×2 卡片 352×336px）后得到的真实比例：
- 金额字形高 **11.9% 卡高** → 字号 ≈ 17.5% 卡高（此前我按 27% 卡宽算，实际大了近一半，这就是"比例不对"的根因）
- 标签字形 5.1% → 字号 6.5% 卡高；辅助行 4.6% → 6.3% 卡高
- 按钮高 **18.2% 卡高**；圆角 ≈ 11% 卡宽；内边距 8% 卡宽
- 2×1：金额字形 30% 卡高（字号 42%）、圆钮 49% 卡高、标签 19% 卡高

代码已改（`dims()` 全部改为按卡高算，2×1 独立一套），`assembleDebug` 通过。
**装机验证受阻：adb 掉线（`adb devices` 为空），待用户重新连接后安装截图。**

### Phase 4 追加八：按小米 HyperOS 规范更正（尺寸口径 / 两套固定 dp / MIUI 标识）

依据三篇官方文档（dev.mi.com pId=1584 技术规范、1664 设计规范、1585 适配建议，含全部配图）：

1. **尺寸口径**（1664 对应关系图）：手机 2×2 = 平板 1×1；手机 4×2 = 平板 2×1；手机 4×4 = 平板 2×2。
   → 删除两个 provider 的 `targetCellWidth/targetCellHeight`（它会让平板上按 2 格摆放，得到手机 4×4 的面积，
   这是"平板卡片巨大、字号显得小"的直接原因）；2×2 保持 110×110dp，紧凑档改为小米规格 **4×2 = 300×110dp**。
2. **两套固定 dp**（1585「两套布局」思路 + 1664 字阶）：不再用 `LocalSize × 系数` 等比缩放。
   大卡（≥200dp）：标签 14dp / 金额 44dp / 辅助 11dp / 内边距 16dp / 圆角 22dp / 按钮 44dp；
   小卡：10 / 20 / 9 / 10 / 14dp / 30dp。44dp 与参考图实测（金额字形 11.9% 卡高 ≈ 字号 44dp）一致。
3. **MIUI 系统能力**（1584）：receiver 加 `android:process=":widgetProvider"`、`miuiWidget=true`、
   `miuiWidgetRefresh=exposure` + `miuiWidgetRefreshMinInterval=10000`；application 加 `miuiWidgetVersion=1`；
   Receiver 内接管 `miui.appwidget.action.APPWIDGET_UPDATE` 做 `updateAll`（曝光刷新，10s 限流）。
4. **布局规范**：根 fillMaxSize + 背景色 + 内容居中 + 只用 weight（无绝对位置）。
5. 备份：`backups/v1.4-widget/pre-miui-spec/`（4 个文件 + SHA256 已记录）。
   `assembleDebug` 通过。**平板仍离线，装机验证待续。**

## 2026-10-19 · V1.4 Phase 4 重写：Glance → XML + RemoteViews

**决定**：小组件整体推倒，改按小米官方 1584/1664/1585 用 XML + RemoteViews。
计划书 `plans/2026-10-19_2035-qingjizhang-v1.4-widget-xml-rewrite-v3.md`；范围收缩为 **2×2 一档**（用户拍板）。
参考图只作视觉效果参考，字号/间距/圆角一律走规范，不按参考图量像素（用户明确纠正）。

**改动**
- 新增 `res/layout/widget_today_2x2_{large,small}.xml`、`widget_loading.xml`
- 新增 `drawable/widget_bg.xml`、`widget_btn_bg.xml`（颜色走 `values` / `values-night` 限定符，代码不判夜间）
- 新增 `drawable-nodpi/widget_preview.png`（450×450，文泉驿字体渲染）
- 新增 `ui/widget/TodayExpenseWidgetProvider.kt`（receiver + 渲染 + PendingIntent）、`WidgetRefresh.kt`（刷新出口 + 两个 Worker）
- 删除 Glance 版 `TodayExpenseWidget.kt`；`build.gradle.kts` 移除 `glance-appwidget` / `glance-material3`
- manifest：`miuiWidgetVersion` 1→2；只保留 2×2 provider
- PendingIntent 用 `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`
  （1584 §11.2 的 MUTABLE 仅针对编辑页大屏预览——Host 要回填 miuiWidgetScreenBound；本实现无编辑页）

**踩坑**
1. **`android:previewLayout` 会让 MIUI 的 AppWidgetProviderInfo 解析失败**：加上它之后 `dumpsys appwidget` 里
   `min/minResize/updatePeriodMillis/initialLayout` 全变 0，provider 从小部件列表彻底消失；删掉立刻恢复
   （`min=(28161x28161)` = 110dp）。**只能用 `previewImage`（PNG）**。
2. **provider 缓存有延迟**：装机后立刻 `dumpsys appwidget` 读到的是 0，必须 `am force-stop com.miui.home` 再等 6~10 秒
   才会重新解析。验收前必须等，否则会误判"配置没生效"。
3. **列表可见性**：`miuiWidget=true` + `android:process=":widgetProvider"` 同时在时，MIUI 3.0（Android 16 测试手机）的
   「支持小部件应用」和「安卓小部件」两个列表都找不到本 provider；移除这两项后立刻出现。
   具体是哪一个（或两者组合）待回归确认。

**待办**：widget 尚未添加到桌面，渲染/点击/刷新三项验收未开始。

### 真机验收结果（20:57–21:09，手机 Android 16 测试手机）

| 项 | 结果 |
|---|---|
| 添加 | 成功（前提：`miuiWidget=false`） |
| 数据一致性 | DB `4800分 / 4笔 / 最近20:19` ↔ widget `¥48.00 / 共4笔·最近20:19` ✓ |
| 点按钮 | 直达记账框（键盘自动弹出）✓；返回键关闭后退回桌面 ✓ |
| 点卡片主体 | 进流水页 ✓ |
| 深色模式 | 背景 `#2E3324` / 字 `#EDF1DC` / 按钮 `#639922` 自动跟随（`values-night` 生效）✓ |
| 实测尺寸 | view `486×563px`，density 3.0 → **162×188dp**（非规范表的 150×150；本机 480dpi / 1200×2670 的 4×6 网格如此） |
| 断点判定 | 162dp ≥ 136 → large 布局 ✓ |
| 独立进程 | `:widgetProvider` 内 onReceive + Room 查询正常；**TOTAL PSS 71,654KB（70MB）**（超规范 35MB，Kotlin/Room 运行时基线，自用可接受、上架需瘦身） |
| 曝光刷新 | `miuiWidget=true` 时能收到 `miui.appwidget.action.APPWIDGET_UPDATE`，但 true 会导致列表不可见 → 最终 false，失去该刷新 |

### 又两个坑

4. **`<View>` 节点（占位/分隔）在 MIUI 的 RemoteViews 里会让整张布局 inflate 失败**——桌面显示
   「载入窗口小部件时出现问题」。把 `<View>` 全换成空 `<LinearLayout>` 后立刻正常。
   AOSP 的 RemoteViews 白名单里 `View` 是允许的，但 MIUI 的 `HomeMIUIWidgetFrameLayout` 包装层不认。
5. **验证 `miuiWidget` 可见性必须滚到「安卓小部件」列表最底部**（本机 12 屏）。只滚 10 屏会误判为"不可见"。
   滚到底复验：`true` 时列表末尾是 Via/Telegram，没有本 app；`false` 时在第 3 屏底部区域可见。

### 待办
- 视觉微调：实测 162×188dp（比规范 150dp 宽 8%、高 25%），金额 25dp 可上调到 27~28dp，按钮上方留白可收
- 空态（今天有数据，未验）
- 刷新钩子：增/改/删/周期补录/恢复备份（只有系统 UPDATE 路径验过）
- APK 体积异常：19,849,448 字节（基线 13,000,742），但 zip 条目合计仅 13.27MB → 差额在签名/对齐块，待查
- 单测回归、README 收尾、`--no-ff` 合并

### 五个刷新钩子验收（真机 21:36–21:38）

| 钩子 | 操作 | 结果 |
|---|---|---|
| ① 记账保存 | widget 按钮 → 记 0.01 → 保存 | widget 变 `¥48.01 / 共5笔 / 21:36` ✓ |
| ② 编辑流水 | 流水页点该笔 → 0.02 改 0.03 → 更新 | widget 变 `¥48.03 / 共5笔` ✓ |
| ③ 删除流水 | 垃圾桶 → 确认删除 | widget 回 `¥48.00 / 共4笔 / 20:19` ✓ |
| ④ 周期补录 | **未实测** | 代码审计：`LedgerRepository:169 if (inserted > 0) notifyWidgets()`，与①②③同一出口 |
| ⑤ 恢复备份 | **未实测**（会清空真实账本，风险不可接受） | 代码审计：`LedgerRepository:202 notifyWidgets()`，同一出口 |

测试账已清理干净：`records` 回到 65 条（与装机基线一致），今日 `4800分 / 4笔`。
截图存档：`backups/v1.4-widget/{42_tuned_light,37_dark,33_quick_add}.png`

### 视觉微调（21:35）

- 金额 `25dp → 27dp`：实测卡宽 162dp，内容宽 132dp，`8字符×0.58×27=125dp` 仍放得下
- 按钮 `40dp → 44dp`
- 标签↔金额 `6→8dp`，金额↔辅助 `4→6dp`
- 结果：金额更醒目、按钮上方留白从 65dp 收到约 34dp

### APK 体积：增量构建的假膨胀（已定位）

- 现象：`assembleDebug` 增量构建出的 APK 19,849,448 字节，zip 条目合计仅 12.65MB，**overhead 6.28MB**
- 定位：`classes8.dex`（raw 1228 字节）后面挂了 **4.39MB 空洞**，`res/xml/file_paths.xml` 后 0.75MB、
  `widget_preview.png` 后 0.49MB —— 都是非对齐值的 padding，是补丁式重打包留下的
- **`clean assembleDebug` 后：13,334,435 字节（overhead 0.06MB）**，与基线 13,000,742 比 +333KB（+2.6%），
  远低于计划书"涨超 3MB 停工"的闸门
- 教训：**APK 体积验收必须先 `clean`**，增量构建的 APK 体积不可信

### 单测回归
`testDebugUnitTest`：**113 条全绿**（0 失败 / 0 错误 / 0 跳过）。

### Bug 修复：冷启动点卡片主体误弹记账框（23:23）

- **现象**：点小组件卡片主体（非「记一笔」按钮）应进流水页，但**冷启动**时仍弹出记账框
- **根因**：v1.2 的「打开 app 就记一笔」写在 `LedgerScreen` 的 `LaunchedEffect(Unit)` 里无条件执行；
  卡片主体点击走的是普通 `MainActivity` 启动，被当成了正常冷启动
- **修复**
  - 新增 `MainActivity.EXTRA_SKIP_COLD_START`，卡片主体的 PendingIntent 带上它
  - `onCreate` 读一次即 `removeExtra`（否则配置变更重建会重复抑制），`onNewIntent` 也清掉
  - `AppRoot → LedgerScreen(skipColdStartDialog)`：`LaunchedEffect` 里**仍 `markColdStartShown()`**
    但不 `openAddDialog()` —— 先消费标记，避免用户切到统计页再切回来时"补弹"
- **验证**
  - 冷启动 + 点卡片主体 → 直接流水页，无对话框 ✓（截图 `70_coldstart_card.png`）
  - 冷启动 + 点「＋ 记一笔」→ 记账框 + 数字键盘 ✓（截图 `71_coldstart_button.png`）

## 2026-10-19 · V1.4 平板验证（Android 16 测试平板 / 2032×3048@400dpi）

**版本**：`versionCode 7 / versionName 1.4.0`（本次升级，覆盖安装保数据）

| 项 | 结果 |
|---|---|
| 升级安装 | `install -r` 成功；DB 三件套保留（69KB + 321KB WAL） |
| provider 注册 | `min=(28161x28161)` = 110dp ✓ |
| 已有实例刷新 | 旧实例 id=303 升级后自动重绘为新布局 ✓ |
| 实测尺寸 | **292×292dp**（options 与 view 尺寸一致；740×720px @2.5x） |
| 点「＋ 记一笔」 | 弹出记账框 + 键盘 ✓ |
| 点卡片主体 | 进流水页，无记账框 ✓ |
| 深色模式 | 背景 `#2E3324` / 字浅色 / 按钮 `#639922` ✓ |
| 数据一致性 | widget `¥83.30 / 15笔 / 19:19` ↔ 流水页今日支出 `¥83.30` ✓ |

**平板显示问题（功能正常，比例失衡）**
- 平板 2×2 是 **292×292dp**（手机是 162×188dp，宽 1.8 倍）
- 布局用固定字号（标签14/金额27/辅助11），卡片变大后字号不变 → 内容挤在顶部、
  中间约 40% 空白、按钮贴底，视觉重心严重偏上
- 规范 1584 §11.3 提到的"大屏全局缩放"在本机**没有体现**：292dp 就是 launcher 格子的实际尺寸，
  不是缩放后的结果（`miuiAutoScale` 默认开，但对本 widget 无缩放行为）
- 待决策的修法：① 按卡片宽度等比放大字号（金额封顶 44dp）+ 文字块垂直居中；
  ② 单独出 `layout-sw600dp/` 平板布局；③ 接受现状

### 平板功能验收（1.4.0 / Android 16 测试平板）

| 场景 | 结果 |
|---|---|
| 覆盖安装 1.4.0（install -r） | 数据保留（DB 69KB + WAL 321KB），旧实例自动重绘 ✓ |
| 冷启动 + 点「＋ 记一笔」 | 记账框 + 键盘 ✓ |
| 冷启动 + 点卡片主体 | 流水页，无记账框 ✓ |
| 后台 + 点卡片主体 | 流水页（onNewIntent），无记账框 ✓ |
| 后台 + 点「＋ 记一笔」 | 记账框 ✓（未新建第二个实例，任务栈 t2121 未变） |
| 记 0.01 → 保存 | 回桌面 ✓，widget 立即变 `¥83.31 / 16笔 / 23:43` ✓ |
| 删除该笔 | widget 回 `¥83.30 / 15笔 / 19:19` ✓，测试数据已清理 |
| 深色模式 | 背景 `#2E3324` / 字浅 / 按钮深绿 ✓ |
| 数据一致 | widget `¥83.30` ↔ 流水页今日支出 `¥83.30` ✓ |

**平板视觉问题（待决策）**：平板 2×2 = **292×292dp**（手机 162×188dp 的 1.8 倍宽），
固定字号（14/27/11dp）导致内容堆在顶部、中间约 40% 空白、按钮贴底。
候选修法：新增 **xlarge 档布局（卡宽 ≥250dp）**，内容垂直居中 + 字号按比例放大（金额封顶规范 44dp）。

### 平板为什么没变 1×1（实测结论）+ roomy 档

**用户提醒「手机 2×2 到平板应变 1×1」，实测确认不是**：
- 平板桌面是 **6 列网格**（图标一行 6 个），1 格 ≈ 135dp
- 实测 options = **292×292dp** = 2 格宽 × 2 格高 → 仍是 **2×2**
- 小部件中心里「今日支出」**没有尺寸标注**（别的 app 有 `2 x 2` / `4 x 2`），说明只给一档
- 之前那个 292dp 实例是 Glance 时期 `targetCellWidth/Height=2` 的遗留，与本次无关

**autoScale 只声明、未生效**：
- 平板 logcat 只有 `LauncherWidgetLayoutFactory: com.adrian.qingjizhang autoScale true`（6 条）
- **没有任何 `scaleDensity` 记录**（手机上有 `3.05625 → 2.7`）
- 结论：`miuiAutoScale` 只在"格子装不下设计尺寸"时向下压，**格子更大时不会放大内容**

**新增 `widget_today_2x2_roomy.xml`（卡宽 ≥250dp）**
| | large（手机） | roomy（平板 292dp） |
|---|---|---|
| 内边距 | 15dp | 24dp |
| 标签 / 金额 / 辅助 | 14 / 27 / 11dp | 20 / 44 / 16dp |
| 按钮高 / 字 | 44dp / 14dp | 64dp / 20dp |
| 文字块 | 贴顶 + 按钮贴底 | **垂直居中** + 按钮贴底 |

`layoutFor` 三档：`<136 → small` / `136~250 → large` / `≥250 → roomy`；
`amountTextSize` 同步三档（20 / 27 / 44dp，超 8 字符按比例缩）。

**顺带完成的验收项**（跨天后自然触发）：
- 空态：今日 0 笔 → `¥0.00` + 「今日还没有记账」✓
- 跨零点：标签从「10月19日」变「10月20日」✓（00:08 截图确认）

截图：`backups/v1.4-widget/30_tablet_before_roomy.png` / `31_tablet_roomy.png`

### 手机 1.4.0 视觉验收 + 标签截断修复（00:25）

- 手机实测：`minW=162dp minH=187dp` → 走 **large** 布局（roomy 不触发，手机视觉与 21:35 版本一致）
- 布局参数未变：文字贴顶 / 金额 27dp / 按钮 44dp / padding 15dp
- **发现缺陷**：标签 14dp 在「162-30=132dp」内容宽里临界，
  `今日支出 · 10月20日` 被 `ellipsize=end` 截成 `今日支出 · 10月2…`
  （21:35 那张截图是 10月19日，同样宽度但没截断——临界值，亚像素差异就会翻车）
- **修复**：标签 14dp → **13dp**（省约 7dp 余量）；规范 Display2 是 14dp，这里为防截断降 1dp，
  属有意偏离，记在此处
- 修复后完整显示 ✓（截图 `81_phone_140_after_label_fix.png`）

### 1.4.0 收尾验收（平板，00:36–00:45）

| 项 | 结果 |
|---|---|
| 仪器化测试 | **23/23 全绿**（0 失败 / 0 错误 / 0 跳过）：DaoTest 6 + MigrationTest 4 + StatsDaoTest 3 + BackupRestoreTest 2 + RecurringCatchupTest 8 |
| 重装 | 仪器化会卸载 app（DEVLOG 铁律）→ 已重装 1.4.0，空库自动播种 12 个分类 |
| 小部件档位 | 列表里明确标注 **`今日支出 2 x 2`** —— 平板本 provider 只有 2×2 一档（与 1664 §1.1 文字一致：平板最小 2×2，无 1×1） |
| **空态** | 新库 0 笔 → `¥0.00` + 「今日还没有记账」+「10月20日」✓（截图 `51_tablet_empty_state.png`） |
| **8 字符金额** | 记 126.50 → roomy 44dp 完整显示 `¥126.50`，不截断；辅助行「共 1 笔 · 最近 00:42」✓（截图 `52_tablet_amount_126_50.png`） |
| 保存后刷新 | `quick_add` 保存 → 回桌面 → widget 立即更新 ✓ |
| 测试数据 | 已删除，`records` 回到 0 ✓ |

**④⑤ 钩子的覆盖情况（最终）**
- 数据层：`RecurringCatchupTest`(8) + `BackupRestoreTest`(2) 仪器化全绿
- UI 层：补录/恢复后调用的 `notifyWidgets()` 与已实测的 ①②③ 是同一行代码，
  未做端到端点击验证（恢复备份需 SAF 交互且会清空真实数据，风险不对等）

## 2026-10-20 · V1.4 封版（v1.4.0）

- `main` 合并 `feat/1.4-widget-xml`（`--no-ff`，merge commit `e6c409a`），tag **`v1.4.0`**
- 版本：`versionCode 7 / versionName 1.4.0`

### perf 构建（封版产物）

| 项 | 值 |
|---|---|
| 产物 | `app/build/outputs/apk/perf/app-perf.apk` → **9,056,449 字节（8.64MB）** |
| 对比 | debug 13,334,435（12.72MB）→ perf 小 4.28MB（去掉 tooling/测试依赖） |
| 归档 | `backups/qingjizhang-v1.4.0-perf.apk` + `.sha256`（`ab0292a8…`） |
| dexopt | `status=speed-profile [reason=baseline]`，**非 run-from-apk** ✓ |
| 签名 | debug 签名（可直接覆盖安装、保数据；`pkgFlags` 无 DEBUGGABLE） |
| 桌面验证 | 覆盖安装后 widget 正常：`minW=303dp minH=303dp layout=roomy`，空态显示正确 |

⚠️ perf 包非 debuggable → **`run-as` 不可用**。后续需要造数据/直读 DB 的调试，先装 debug 包，
再覆盖装 perf（同签名同 versionCode，数据保留）。

### 封版包归档（clean 重建后）

- **发现**：`assemblePerf` 前那次 debug 包是增量构建的，13,872,717 字节（比 clean 多 537KB padding —— 坑 5 再次出现）
- `clean assembleDebug assemblePerf` 重建后：

| 包 | 体积 | sha256 |
|---|---|---|
| `qingjizhang-v1.4.0-debug.apk` | **13,335,370**（基线 13,000,742，+2.6%） | `61338bf3…97b11` |
| `qingjizhang-v1.4.0-perf.apk` | **9,056,449**（8.64MB） | `e9c83dce…e69` |

- 归档位置：`backups/`（同目录附 `README-v1.4.0.md` 说明安装/升级/校验、`SHA256SUMS-v1.4.0.txt`）
- **教训复述**：任何要归档/交付的包，必须先 `clean` 再 `assemble`，否则体积和内容都不代表真实产物

## 2026-10-21 · V1.4.1 图标重做（D2 + 重心微调）

**设计来源**：外部设计师稿（方案 2B → D2 定稿），用户提供完整 XML。

| 项 | v1.4.0 | **v1.4.1** |
|---|---|---|
| 底色 | `#A8C686`（柔和绿） | **`#7CB342`（鲜活嫩芽绿）** |
| 图形 | 三条账页横线 + 斜铅笔 | **2:3 敞口账单外框（30.5×45dp）+ 框内 ¥ + 底部流水线 + 6.2dp 平顶铅笔** |
| 线宽 | 4.5dp | 外框 3.9dp / ¥ 3.5dp |
| 版本 | 7 / 1.4.0 | **8 / 1.4.1** |

### 相对设计稿的三处实施偏差（供审核）

1. **背景色写进 `values/colors.xml` 现有条目，没有新建 `values/ic_launcher_background.xml`** ——
   同名资源在两个文件里定义会直接编译失败。
2. **adaptive-icon 实际是 4 个文件**（`mipmap-anydpi-v26|v33` × `ic_launcher` / `ic_launcher_round`），
   设计稿只提了 2 个；这 4 个内容本身已正确（v26 无 monochrome、v33 有），未改动。
3. **加了 `<group android:translateX="-1">` 重心微调**（设计稿没有这一层）：
   - 原始 D2 的 bbox 中心是 (57.3, 55.0)，比画布中心偏右 3.3dp
   - 纯几何居中（translateX=-3）实测左右留白 40/40 相等，但**视觉反而偏左**——
     左侧 L 形描边是"面"、右侧铅笔是"块"，视觉重量压在左边
   - 最终取 **-1**：PIL 实测留白 **26/22 px**（图形偏右 1.5dp），视觉上正好居中

### 验证

| 项 | 结果 |
|---|---|
| 最大半径（外框含 1.95dp 描边） | **31.3**（安全圆 33，余量 1.7dp） |
| 铅笔最远点 r | 29.3 |
| 左右留白（147px 图标） | 26 / 22 px |
| squircle mask（MIUI 默认） | 无裁切 ✓ |
| 深色壁纸 | 清晰 ✓ |
| 圆形 / 水滴 mask | **未验**（需改桌面图标形状设置） |
| Android 13+ 主题图标 | **未验**（需系统开"主题图标"开关） |

### 产物（clean 构建）

| 包 | 体积 | sha256 |
|---|---|---|
| `qingjizhang-v1.4.1-debug.apk` | 13,335,766 | 见 `SHA256SUMS-v1.4.1.txt` |
| `qingjizhang-v1.4.1-perf.apk` | 9,056,841 | 同上 |

平板已装 perf 版：`versionCode 8 / 1.4.1`，`dexopt status=speed-profile` ✓

### 一个字节层面的教训：改注释也会改 APK 字节

修 vector 注释里过时的半径数字（30.9/2.1 → 31.3/1.7）后重新构建，**体积完全没变**
（13,335,766 / 9,056,841），但 **sha256 全变了**：
debug `a6374ccc…` → `7c58a089…`，perf `51006cba…` → `a74bacec…`。

原因：aapt 编译后的二进制 XML 保留了行号/位置信息，注释虽不参与渲染，但改变了资源表内容。

**规矩**：已经归档/交付过的包，任何源码改动（哪怕只改注释）都必须重新构建 + 重新归档 + 重装设备，
不能拿「体积一样」推断「字节一样」。这也是「同代码 ≠ 同字节」的延伸——连"逻辑同"都不等于"字节同"。

## 2026-10-21 · 卡顿问题闭环（v1.3.1 debug → v1.4.1 perf）

**状态：已解决（用户实机确认）**

| 阶段 | 状态 | 依据 |
|---|---|---|
| v1.3.x debug | 卡顿（用户主诉） | 用户反馈滑动粘手 / 掉帧 |
| Phase 1 定位 | 根因 = **debug 税**，不是代码慢 | 同数据 6 次交替：90th **11ms vs 5ms**、≤7ms 达标率 **61.6% vs 93.1%** |
| v1.4.1 perf 装机 | **已解决** | 用户 2026-10-21 反馈「手机上使用已经几乎完全不卡顿了」 |

**归因比例**：约 95% 来自 debug 包无 AOT（`run-from-apk`）→ perf 的 `speed-profile` 把 90th 帧耗时砍半；
行距 2→4dp 与性能无关（视觉改善，性能中性甚至略负——行高变大、列表更长）。

**维护提醒**：判断"是不是变卡了"别凭感觉，查
`adb shell dumpsys package dexopt | grep -A2 com.adrian.qingjizhang` 的 `status=`：
`speed-profile` = 优化在位；`verify` / `run-from-apk` = 没生效（刚装完、系统清过 dexopt、或装成了 debug 包）。


## 2026-10-29 · 外部审查 13 条问题处理（跨 agent 交流室）

来源：`交流室/跨agent交流室.md`（监督者审查，逐条带行号）。处理结果：

| 编号 | 结果 | 关键动作 |
|---|---|---|
| 1.1 | **已修 + 真机验证** | `onAppResumed()` + `ON_RESUME` 监听；验证：跨天切回（进程 pid 未死）records 1→2，游标 20755→20756 |
| 1.2 | 已修（文档） | 注释澄清「刷新 ≠ 补录」，5 是写入口不是刷新机制 |
| 2.1 | 已修 | DEVLOG 改为「发起即忘」，与代码一致 |
| 2.2 | 已修 | 「钩子①~⑤」→「五个写入口」，内部 ①② → (a)(b) |
| 2.3 | 已修 | `MAX_CENTS` 注释 999,999.99 元（原写 99,999,999.99，错 100 倍） |
| 2.4 | 已修 | 三表 → 四表；createdAt/updatedAt 注释如实 |
| D1 | 不修（现状不成立） | 当前无 47 个权限位变更；采纳 `core.filemode false` 预防 |
| D2 | 已修 | 删 6 条零引用依赖 + toml 清理 |
| D3 | 已修 | 补 `proguard-rules.pro` 占位 |
| D4 | 已修 | `windowSoftInputMode` 移到 `<activity>` |
| D5 | 已修 | 新增 `values-night/themes.xml`（消除暗色白闪） |
| D6 | 已修 | androidTest 显式依赖 `coroutines-test` |
| D7 | 已修 | 删 3 个死方法 + 3 个死符号 + `isEmpty` 字段 + 未用参数；文件名与类名已一致（类名未动） |

**验证**：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 113/0/0。

### 2026-10-29 · 二次修复（外部审查复核 U2/U3）

监督者复核 13 条：12 通过、D7 部分通过（README 架构树仍旧文件名，他已代改）、D1 不修理由成立。
余下 4 条处理：

| 编号 | 处理 | 内容 |
|---|---|---|
| U1 | 已确认 | README 架构树 `TodayExpenseWidgetProvider.kt` → `TodayExpenseWidget2x2Receiver.kt`（监督者直接改，方向正确：只跟进文件名，不动类名） |
| U2 | **已修** | `lastCatchUpDay` 置位早于补录完成 → 失败时不重试。改 `runCatching` + `onFailure` 回滚判重（补录幂等，重试安全），并 `Log.w` |
| U3 | **已修** | 零点任务续期被「同日判重」吞掉 → 移到 `onAppResumed()` 开头，与补录解耦（`enqueueUniqueWork` 幂等） |
| U4 | 已更正 | 回执写 toml「62→48 行」，实际 `wc -l` = **47**（凭记忆写数字的错误） |

验证：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 113/0/0。
另：`.gitignore` 补 `/小组件参考图/`（设计素材不进仓库）。

### 2026-10-29 · 审查修复后的全量测试（U2/U3 之后）

| 测试 | 结果 |
|---|---|
| `assembleDebug` | BUILD SUCCESSFUL |
| `testDebugUnitTest` | **113 / 0 / 0** |
| `connectedDebugAndroidTest` | **23 / 0 / 0** |
| **D4** 键盘避让（新 APK 首启） | 冷启动直接弹记账框、金额框聚焦、键盘不遮挡对话框 ✓ |
| **D5** 暗色启动 | 切暗色 + force-stop + 冷启动：背景深色、**无白闪** ✓ |
| **D2** 删依赖后运行时 | 小组件正常渲染（`roomy / ¥0.00 / 今日还没有记账`），无缺类/无崩溃 ✓ |
| **U2/U3** 跨天回归 | 改日期到 10/30（周五）→ HOME → 切回：**pid 4411→4411（同进程）**，records 0→1（10/30, 500 分），游标 20754→20756 ✓ |

**顺带发现一个既有问题（非本次改动引入，未修）**

- **平板横屏 + 键盘弹出时，记账对话框被压缩 → 分类 FilterChip 文字被裁（显示为空白框）**。
  收起键盘后立即恢复正常（截图对照：`qjz_test/05_dark_dialog.png` vs `06_dark_nokeyboard.png`）。
  手机不受影响（屏幕比例不同，历史截图 `33_quick_add.png` 正常）。
- 相关线索：`theme/Theme.kt:36` 是 `dynamicColor = true`（Android 12+ 壁纸取色）。平板壁纸是深蓝，
  `onSurfaceVariant` 可能被算得偏浅，与 FilterChip label 的默认颜色叠加时对比度不足。
  两者叠加（空间压缩 + 颜色偏浅）是本机看到"空白框"的原因，需分别处理：
  ① 对话框加 `verticalScroll` 或用更紧凑的分类行；② 评估是否保留 dynamicColor。
- **未在本次范围内修**（审查要求「不要顺手扩大范围」），已记入此处待人类决定。

### 2026-10-30 · perf 包重建（发现平板装的是旧包）

**问题**：审查修复（D2-D7 + U2/U3）后只重建了 debug，**perf 仍是 V1.4.1 封版时（10/21）的旧包**。
平板装回 perf 后深色冷启动仍白闪 —— 不是 D5 失效，是包里没有 D5。

**证据（aapt2 dump resources）**
```
旧 perf：style/Theme.QingJiZhang  ()              parent=0x01030241      ← 无 night
新 perf：style/Theme.QingJiZhang  () + (night)    parent=0x0103022e      ← 有 D5
```

**重建结果**
| 项 | 旧 | 新 |
|---|---|---|
| 体积 | 9,056,841 | **8,799,618（8.40MB，-257KB）** |
| D5 夜间主题 | 无 | 有 |
| 依赖 | 6 条零引用 | 已删（D2） |

**验证**：暗色 + force-stop + 冷启动，0.9s / 1.5s 两张截图均为深色，无白屏。

**教训**：改了源码后必须重建**所有交付构建型**（debug + perf），不能只重建当前调试用的那个；
「装回上一版包」前先核对包时间戳与内容，否则会拿旧包验证新代码。

### 2026-10-30 · U2/U3 两个隐蔽问题（复核后确认 + 修正）

人类追问「runCatching 吞 CancellationException、REPLACE 取消正在跑的 Worker」是否真实，核实结论：**两者都真实存在**。

**U2-2 · `runCatching` 吞协程取消**
- `runCatching` 的实现是 `catch (Throwable)`，**连 `CancellationException` 一起吞**
- 后果：ViewModel 销毁时补录协程被取消 → 被当成"失败"→ 打误导日志 + 回滚判重（无害但不该发生），
  且取消信号不透传，违反结构化并发
- 修：改显式 `try/catch`，`kotlin.coroutines.cancellation.CancellationException` 原样重抛

**U3-2 · `REPLACE` 取消正在跑的 Worker**
- `ExistingWorkPolicy.REPLACE` 会取消同名 work，**RUNNING 的也算**
- `WidgetMidnightWorker.doWork()` 内部调用 `scheduleMidnightRefresh` → **取消自己** →
  `return Result.success()` 可能不执行，Worker 记成 `CANCELLED`；极端时序下中断 `refresh()`
- 修：`scheduleMidnightRefresh` 加 `policy` 参数；Worker 内改用 **`APPEND_OR_REPLACE`**

**⚠️ 关键教训：`KEEP` 不是替代方案**
- 先改成了 `KEEP`，复核才发现语义是「存在**未完成**的同名 work 就不加」，而 **RUNNING 也算未完成**
  → Worker 内用 KEEP，续期会被静默忽略，**第二天的零点任务永远排不上**（比原 bug 更糟）
- `APPEND_OR_REPLACE` 才是正解：还在跑就追加到它后面（不取消），已取消/失败则替换

**验证**：`assembleDebug` BUILD SUCCESSFUL；`testDebugUnitTest` 113/0/0。
时序类行为（协程取消瞬间、Worker 续期链）未做真机复现，属代码层修正。

### 2026-10-30 · U3 resume 路径补完（人类二次追问：只修了一半）

**人类指出**：`LedgerViewModel.kt:139` 仍是 `scheduleMidnightRefresh(context)`，走默认 `REPLACE`。
**核实：完全正确**——我只改了 Worker 内部那一处，`onAppResumed` 这条高频路径漏了。

**两处需要相反的策略（这是本次的关键认知）**

| 调用点 | 频率 | 正确策略 | 理由 |
|---|---|---|---|
| `onAppResumed` | 每次回前台 | **KEEP** | 有任务/在跑就不动，丢了才补。REPLACE 会取消正在跑的零点 Worker |
| Worker 内部 | 每天一次 | **APPEND_OR_REPLACE** | 自己正在 RUNNING，KEEP 会忽略续期（RUNNING 算未完成）；REPLACE 会取消自己 |

**根因修复：去掉 `policy` 的默认值**
原来 `policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE` —— 两处调用需要相反策略，
给默认值等于埋一个「将来谁加调用点谁踩」的坑。现在编译期强制显式选择。

**验证**
- `assembleDebug` BUILD SUCCESSFUL（去掉默认值后无编译错误 ⇒ 没有遗漏的调用点）
- `testDebugUnitTest` 113/0/0
- 调用点复核：2 处，均显式传策略

### 2026-10-30 · 主动审计：U2 同类问题还有 3 处（人类没提，自己查出来的）

修完 U2/U3 后主动 grep 同类模式，发现 **`WidgetRefresh.refresh()` 是 `suspend`**，
却有 3 处用 `runCatching` 包它 —— 与 U2 完全同一个坑（吞 `CancellationException`）：

| 位置 | 场景 |
|---|---|
| `WidgetRefreshWorker.doWork()` | 兜底刷新 Worker 被取消时不响应 |
| `WidgetMidnightWorker.doWork()` | 零点 Worker 被取消时不响应 |
| `LedgerRepository.notifyWidgets()` 的 `refreshScope.launch` | 应用级 scope 取消时协程不退出 |

**修法（收敛而不是复制粘贴）**：新增 `WidgetRefresh.refreshSafely()` ——
吞非取消异常并 `Log.w`，`CancellationException` 原样重抛；3 处统一调用它。

**保留 `runCatching` 的地方（分类依据已更正 —— 监督者 2026-10-30 指出）**

> ⚠️ 原写「这些是同步调用，不抛取消异常」**是错的**：`renderWidget`
> （`TodayExpenseWidget2x2Receiver.kt:116`）与 `loadWidgetData`（`:177`）**都是 `suspend`**。
> 与 48/47、20754/10-29 同属「结论对、依据错」。

实际保留的 5 处，宿主与危害：

| 位置 | 宿主 scope | 为什么危害低 |
|---|---|---|
| `WidgetRefresh.kt:31` | `suspend fun refresh` 内部 | 外层 `refreshSafely` 已处理取消，内层只影响单个 widget 的渲染 |
| `Receiver.kt:53` | `CoroutineScope(Dispatchers.IO).launch` | receiver 临时 scope，生命周期极短 |
| `Receiver.kt:85` | `runBlocking` | 阻塞式，无取消语义 |
| `Receiver.kt:95` | `runBlocking` | 同上 |
| `Receiver.kt:180` | `suspend fun loadWidgetData` | 同上，且只查单行 |

**结论**：不是 bug（正常路径不会被取消，最坏是取消瞬间显示空态），
但「同步/异步」的分类表述错误。按监督者建议**只改措辞、不动代码**（避免扩大范围）。

**原本正确的两类保留理由**
- `enqueueWidgetRefresh(...)`：确实同步（WorkManager 入队 API）
- `BackupJson` 的 `catch (e: Exception)`：确实同步（JSON 解析）

## 2026-10-30 · V1.4.2 封版

| 项 | 值 |
|---|---|
| versionCode / versionName | **9 / 1.4.2** |
| debug | 13,033,487 字节 / `1387b5c5…` |
| perf | 8,799,618 字节 / `fd6ddb77…` |
| 单元测试 | 113 / 0 / 0 |
| 归档 | `backups/qingjizhang-v1.4.2-{debug,perf}.apk` + `SHA256SUMS-v1.4.2.txt` |

**本版内容（相对 v1.4.1）**
- 修复：补录失败回滚判重、零点任务续期解耦、`runCatching` 吞取消（4 处）、
  零点重排 `APPEND_OR_REPLACE`、暗色启动白屏、键盘遮挡
- 优化：删 6 条依赖 + 7 处死代码（perf −257KB、debug −302KB）、proguard 兜底规则
- 测试：113 单测 + 23 仪器化 + 真机回归（跨天补录/键盘/暗色/小组件）

**⚠️ 版本边界（避免再次写错）**
- **v1.4.0** = 小组件 XML 重写 + 三档布局 + 行距 4dp + perf 构建型
- **v1.4.1** = 图标重做 + README 收尾
- **v1.4.2** = 本轮审查修复（本条目）

v1.4.0 / v1.4.1 的内容在 CHANGELOG 与 Release 说明中作为**历史补记**，
不属于 1.4.2 的变更项。

**发布动作**
- 公开仓库新增 `CHANGELOG.md`（含 v1.0.0 ~ v1.4.2 全部历史），README 安装章节加链接
- 新建 GitHub Release `v1.4.2`，附件上传 perf APK + SHA256SUMS；
  说明中补记 v1.4.1（首次公开 Release，此前从未发布过）
