package com.adrian.qingjizhang.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 数据库。version 历史：
 * - v1 (1.0.0)：records + categories
 * - v2 (1.1)：records 加 clientUid / updatedAtMillis（跨设备合并地基）
 * - v3 (1.1)：budgets 月度预算表
 * - v4 (1.3)：recurring_rules 周期规则表 + records 加 recurringRuleId（自动账来源标记）
 * 加字段必须同时交：Migration + MigrationTest + 新 schema JSON（计划书铁律）。
 */
@Database(
    entities = [RecordEntity::class, CategoryEntity::class, BudgetEntity::class, RecurringRuleEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 → v2：流水表加两列。
         * clientUid 存量回填用 SQLite 表达式生成 UUID v4（单机存量行天然不撞；
         * 曾手动复制过 DB 文件到多设备的场景，将来合并需按内容指纹兜底——DEVLOG 备忘）。
         * updatedAtMillis 回填 = createdAtMillis（"从未编辑过"语义）。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN clientUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE records ADD COLUMN updatedAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """UPDATE records SET clientUid = lower(
                           hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)),2)
                           || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)),2)
                           || '-' || hex(randomblob(6)))
                       WHERE clientUid = ''"""
                )
                db.execSQL("UPDATE records SET updatedAtMillis = createdAtMillis WHERE updatedAtMillis = 0")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_records_clientUid ON records (clientUid)")
            }
        }

        /** v2 → v3：新增月度预算表（纯新表，records/categories 零改动） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `budgets` (
                           `yearMonthKey` TEXT NOT NULL,
                           `amountCents` INTEGER NOT NULL,
                           `updatedAtMillis` INTEGER NOT NULL,
                           PRIMARY KEY(`yearMonthKey`)
                       )"""
                )
            }
        }

        /**
         * v3 → v4：周期记账（V1.3）。
         * - 新增 recurring_rules 表；主键 `INTEGER PRIMARY KEY AUTOINCREMENT` → id 永不复用
         *   （删除规则后，历史自动账的 recurringRuleId 不会因 id 复用而被新规则"误领"）
         * - records 加可空列 recurringRuleId（null=手动账，非空=该规则自动生成）；存量行默认 NULL
         * - 加唯一索引 (recurringRuleId, dateEpochDay)：唯一索引里 NULL 互不相等，
         *   故手动账互不冲突，自动账「同规则同日期」至多一笔 → 补录幂等的数据库层保险
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `recurring_rules` (
                           `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                           `name` TEXT NOT NULL,
                           `amountCents` INTEGER NOT NULL,
                           `isExpense` INTEGER NOT NULL,
                           `categoryId` INTEGER NOT NULL,
                           `period` TEXT NOT NULL,
                           `dayOfWeek` INTEGER NOT NULL,
                           `dayOfMonth` INTEGER NOT NULL,
                           `monthOfYear` INTEGER NOT NULL,
                           `startDateEpochDay` INTEGER NOT NULL,
                           `lastOccurredEpochDay` INTEGER NOT NULL,
                           `note` TEXT NOT NULL,
                           `isActive` INTEGER NOT NULL,
                           `createdAtMillis` INTEGER NOT NULL,
                           `updatedAtMillis` INTEGER NOT NULL
                       )"""
                )
                // 与 Room 规范形式逐字一致（可空列不加 DEFAULT，避免 schema 校验差异）
                db.execSQL("ALTER TABLE records ADD COLUMN recurringRuleId INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_records_recurringRuleId_dateEpochDay " +
                        "ON records (recurringRuleId, dateEpochDay)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "qingjizhang.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}

/**
 * 预置分类（幂等）：categories 表为空才插入。
 * 为什么不用 RoomDatabase.Callback.onCreate：Room 2.8 起默认走新 SQLite driver，
 * 旧的 SupportSQLite 回调不再被触发（仪器测试实测踩坑）。改为开库后在 IO 线程
 * 做一次空表检查——同时覆盖「首次建库」和「清数据后重建」两种场景。
 *
 * 「餐饮」sortOrder=0，即记一笔默认选中项。
 */
suspend fun AppDatabase.seedPresetCategories() = withContext(Dispatchers.IO) {
    if (categoryDao().countAll() > 0) return@withContext
    val expense = listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "教育", "其他支出")
    val income = listOf("工资", "奖金", "理财", "其他收入")
    val rows = expense.mapIndexed { i, n -> CategoryEntity(name = n, isExpense = true, sortOrder = i) } +
        income.mapIndexed { i, n -> CategoryEntity(name = n, isExpense = false, sortOrder = i) }
    categoryDao().insertAll(rows)
}
