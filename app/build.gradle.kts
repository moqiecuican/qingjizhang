plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

android {
    namespace = "com.adrian.qingjizhang"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.adrian.qingjizhang"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.4.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // V1.4 §0.6 性能验证专用：非 debug（ART 才会做 AOT）+ debug 签名（可直接覆盖安装、数据保留）
        // 日常开发仍用 debug 包；perf 只用于回答"卡是 debug 税还是真问题"
        create("perf") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

// Room schema 导出到 app/schemas/（入库，MigrationTest 用）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 版本对齐（上游 Room bug: issuetracker.google.com/issues/447154195）：
// room-migration 2.8.5 的 FieldBundle$$serializer 按 kotlinx-serialization 1.8.x 编译
// （字节码实证：它未实现 typeParametersSerializers()，该方法 1.8 起才是 default），
// 但依赖链 BOM 把 core strictly 钉在 1.7.3（1.7.3 里该方法是 abstract）→ 运行时 AbstractMethodError。
// 故全局 force 到 1.8.1，与 Room 构件的实际编译版本对齐。
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1")
    }
}

// MigrationTestHelper 从 androidTest assets 读取 schema JSON
android.sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

// 时序修复：schema JSON 由 ksp*Kotlin 生成，而 merge*AndroidTestAssets 默认不等它，
// 同轮构建会拿到旧快照（缺最新版本号的 json → 测试 FileNotFoundException）。
// 显式声明依赖：所有 androidTest 资产合并任务必须等对应 KSP 任务完成。
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("AndroidTestAssets")) {
        val variant = name.removePrefix("merge").removeSuffix("AndroidTestAssets")
        dependsOn(tasks.matching { it.name == "ksp${variant}AndroidTestKotlin" || it.name == "ksp${variant}Kotlin" })
    }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation("androidx.compose.material:material-icons-core")
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)

  // Navigation

  // Room (local SQLite persistence)
  implementation(libs.room.runtime)
  implementation(libs.room.ktx)
  ksp(libs.room.compiler)

  // Adaptive layouts (window size classes for tablet/foldable)
  implementation(libs.compose.material3.windowsize)

  // Room migration test support
  // D6：androidTest 里 4 个文件用 runTest，之前靠传递依赖，显式声明
  androidTestImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.room.testing)

  // JSON 备份/恢复（BackupJson）
  implementation(libs.kotlinx.serialization.json)

  // V1.4 桌面小组件：改用 XML + RemoteViews（1584/1664/1585），不再需要 Glance
  // （2026-10-19 重写：Glance 无法满足根布局 @android:id/background、深色静态资源、独立进程三项硬要求）
  implementation(libs.androidx.work.runtime.ktx)
}
