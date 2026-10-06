plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // 注：org.jetbrains.kotlin.android 原为死配置（无模块 apply，AGP 9 内置 Kotlin），已随
    // version catalog 接线一并移除；Kotlin/KSP/AGP 版本统一在 gradle/libs.versions.toml 维护。
}
