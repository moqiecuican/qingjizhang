# 当前 isMinifyEnabled = false（debug/perf 都是），本文件仅作占位——
# build.gradle.kts 的 proguardFiles(...) 引用了它，缺失时一旦开启 R8 会直接构建失败（审查 D3）。
#
# 将来若开启 minify，需要在这里补充 keep 规则，至少包括：
#   - Room 生成的实现类（androidx.room:room-compiler 产物）
#   - kotlinx.serialization 的 @Serializable 类型与其 Companion
#   - Compose 的 @Composable 反射入口（一般由 AGP 自带规则覆盖）
