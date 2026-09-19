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

// 可选签名：仓库根放 keystore.properties（已被 .gitignore 排除）即自动签名 release；
// 没有该文件则照常产出未签名 APK（新克隆/CI 可构建）。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "dev.cao.finch"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.cao.finch"
        minSdk = 26
        targetSdk = 36
        versionCode = 54
        versionName = "0.15.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
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
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    // okhttp 被所有数据客户端直接使用，显式声明，不依赖 Coil 传递引入
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Liquid Glass（backdrop 2.0.0：新版液态玻璃，API 与 1.0.6 二进制兼容；2.0.1 需 Kotlin 2.4.10 无 KSP 配套不可用）
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
    // 桌面小组件
    implementation("androidx.glance:glance-appwidget:1.1.1")
    // 后台任务（v0.15 发售提醒 + v0.16 自动备份/版本检查共用）
    implementation("androidx.work:work-runtime-ktx:2.10.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}