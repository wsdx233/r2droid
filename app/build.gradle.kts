plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "top.wsdx233.r2droid"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "top.wsdx233.r2droid"
        minSdk = 24
        targetSdk = 26
        versionCode = 2602130
        versionName = "0.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 1. 定义签名配置
    signingConfigs {
        create("release") {
            // 尝试从项目属性中读取，如果不存在则使用空字符串或本地调试配置
            // GitHub Action 会通过命令行参数传入这些属性 (-PKEYSTORE_FILE=...)
            storeFile = file(project.findProperty("KEYSTORE_FILE") ?: "keystore.jks")
            storePassword = project.findProperty("KEYSTORE_PASSWORD") as String?
            keyAlias = project.findProperty("KEY_ALIAS") as String?
            keyPassword = project.findProperty("KEY_PASSWORD") as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 2. 应用签名配置
            // 只有当提供了密码时才应用签名（避免本地构建报错）
            if (project.findProperty("KEYSTORE_PASSWORD") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    lint {
        // 即使报错也不终止构建
        abortOnError = false

        // 或者专门禁止检查过期的 targetSdk
        disable.add("ExpiredTargetSdkVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // Source: https://mvnrepository.com/artifact/org.apache.commons/commons-compress
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)


}
