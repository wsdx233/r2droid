plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 2602271
        versionName = "0.2.5-fix"

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
            isCrunchPngs = true
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/native-image/**", // Optional: Prevents potential metadata conflicts
                "kotlin/**",
                "tables/**",
                "org/**",
                "**.properties",
                "DebugProbesKt.bin"
            )
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    //noinspection WrongGradleMethod
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
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
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Source: https://mvnrepository.com/artifact/org.apache.commons/commons-compress
    implementation(libs.commons.compress)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":terminal-view"))
    implementation(project(":terminal-emulator"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.room.paging)

    // Paging
    implementation(libs.paging.runtime)
    implementation(libs.paging.compose)

    // Markdown
    implementation(libs.compose.markdown)

    // AI Chat
    implementation(libs.openai.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.quickjs.kt)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.editor.bom))
    implementation(libs.editor)
    implementation(libs.language.textmate)


    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
