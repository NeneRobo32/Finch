import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Room schema 历史（迁移测试与备份校验的依据）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 可选签名：口令优先取环境变量 / gradle.properties（如 ~/.gradle/gradle.properties，不随项目走），
// 其次仓库根 keystore.properties（已被 .gitignore 排除）；都没有则照常产出未签名 APK（新克隆/CI 可构建）。
// 建议：明文口令只放环境变量或 GRADLE_USER_HOME 级 gradle.properties，项目目录内不落明文。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun signingValue(envKey: String, propKey: String): String? =
    (providers.environmentVariable(envKey).orNull ?: providers.gradleProperty(envKey).orNull)
        ?.takeIf { it.isNotBlank() }
        ?: keystoreProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

val signingStoreFile = signingValue("FINCH_KEYSTORE_FILE", "storeFile")
val signingStorePassword = signingValue("FINCH_KEYSTORE_PASSWORD", "storePassword")
val signingKeyAlias = signingValue("FINCH_KEY_ALIAS", "keyAlias")
val signingKeyPassword = signingValue("FINCH_KEY_PASSWORD", "keyPassword")
val hasSigning = signingStoreFile != null && signingStorePassword != null &&
    signingKeyAlias != null && signingKeyPassword != null

android {
    namespace = "dev.cao.finch"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.cao.finch"
        minSdk = 26
        targetSdk = 36
        versionCode = 68
        versionName = "0.15.14"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
    buildFeatures {
        compose = true
    }
    lint {
        // 显式 lint 策略：错误阻断构建（CI 跑完整 lint，不再只靠 release 的 lintVital）；
        // 警告先不阻断（无障碍描述等在 G 组逐步补齐后可升 warningsAsErrors）
        abortOnError = true
        warningsAsErrors = false
        checkAllWarnings = true
        lintConfig = file("lint.xml")
    }
    testOptions {
        // Robolectric 需要真实资源表（迁移测试用 application context 挂 Room）
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // 版本统一在 gradle/libs.versions.toml 维护（E.2）；Compose 系列由 BOM 管版本
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // okhttp 被所有数据客户端直接使用，显式声明，不依赖 Coil 传递引入
    implementation(libs.okhttp)
    // Liquid Glass（backdrop 2.0.0：新版液态玻璃，API 与 1.0.6 二进制兼容；2.0.1 需 Kotlin 2.4.10 无 KSP 配套不可用）
    implementation(libs.backdrop)
    implementation(libs.shapes)
    // 桌面小组件
    implementation(libs.glance.appwidget)
    // 后台任务（v0.15 发售提醒 + v0.16 自动备份/版本检查共用）
    implementation(libs.work.runtime.ktx)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    // Robolectric：JVM 上跑 Room 迁移测试与 org.json 解析测试（android.jar stub 测不到这些路径）
    testImplementation(libs.robolectric)
}