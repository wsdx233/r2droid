import org.gradle.api.GradleException
import org.gradle.api.tasks.Exec
import org.gradle.kotlin.dsl.register
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.serialization)
}


val nativeRuntimeNdkVersion = "28.2.13676358"
val nativeRuntimePython = providers.gradleProperty("nativeRuntimePython").orElse("python3")
val nativeRuntimeGithubToken = providers.gradleProperty("nativeRuntimeGithubToken")
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
    .orElse("")
val nativeRuntimeCache = rootProject.layout.projectDirectory.dir("build/native-runtime-cache")
val nativeRuntimeScript = rootProject.layout.projectDirectory.file("tools/build_native_runtime.py")
val nativeRuntimeRecipes = rootProject.fileTree("tools") {
    include("build_native_runtime.py", "native_*.py")
}

fun nativeRuntimeSdkDirectory(): File {
    val localProperties = rootProject.file("local.properties")
    val properties = Properties()
    if (localProperties.isFile) localProperties.inputStream().use(properties::load)
    val sdk = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
        ?: properties.getProperty("sdk.dir")
        ?: throw GradleException("Android SDK location is required to locate NDK $nativeRuntimeNdkVersion")
    return File(sdk.replace("\\:", ":"))
}

fun nativeRuntimeNdkDirectory(): File {
    val explicit = System.getenv("ANDROID_NDK_ROOT") ?: System.getenv("ANDROID_NDK_HOME")
    return (explicit?.let(::File) ?: File(nativeRuntimeSdkDirectory(), "ndk/$nativeRuntimeNdkVersion")).absoluteFile
}

android {
    namespace = "top.wsdx233.r2droid"
    ndkVersion = nativeRuntimeNdkVersion
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "top.wsdx233.r2droid"
        minSdk = 24
        targetSdk = 26
        versionCode = 2604280
        versionName = "0.3.1"

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
                "META-INF/native-image/**",
                "DebugProbesKt.bin"
            )
        }
    }

    buildFeatures {
        compose = true
        viewBinding = true
        buildConfig = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            buildConfigField("boolean", "PROOT_ONLY_BUILD", "false")
            buildConfigField("boolean", "FORCE_PROOT_MODE", "false")
            buildConfigField("boolean", "FORCE_MANUAL_PROOT_SETUP", "false")
            buildConfigField("boolean", "BUNDLED_R2_AVAILABLE", "true")
        }
        create("prootOnly") {
            dimension = "distribution"
            applicationIdSuffix = ".proot"
            buildConfigField("boolean", "PROOT_ONLY_BUILD", "true")
            buildConfigField("boolean", "FORCE_PROOT_MODE", "true")
            buildConfigField("boolean", "FORCE_MANUAL_PROOT_SETUP", "false")
            buildConfigField("boolean", "BUNDLED_R2_AVAILABLE", "false")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/shared/assets")
        }
        getByName("full") {
            // Runtime assets come only from the generated per-variant directory.
            assets.setSrcDirs(emptyList<String>())
        }
        getByName("prootOnly") {
            assets.srcDirs("src/prootOnly/assets")
        }
    }

    kotlin {
        jvmToolchain(17)
    }

    lint {
        // 即使报错也不终止构建
        abortOnError = false

        // 或者专门禁止检查过期的 targetSdk
        disable.add("ExpiredTargetSdkVersion")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

val nativeRuntimeExternalLock = project.findProperty("nativeRuntimeLock")?.toString()?.let {
    File(it).also { lock ->
        require(lock.isAbsolute) { "-PnativeRuntimeLock must be an absolute path" }
    }
}
val nativeRuntimeLock = nativeRuntimeExternalLock
    ?: layout.buildDirectory.dir("generated/nativeRuntime").get().asFile.resolve("source-lock.json")
// Both Release variants share one fresh lock per default Gradle invocation.

val prepareNativeRuntimeSourceLock = if (nativeRuntimeExternalLock == null) {
    tasks.register<Exec>("prepareNativeRuntimeSourceLock") {
        outputs.file(nativeRuntimeLock)
        outputs.upToDateWhen { false }
        workingDir(rootProject.projectDir)
        commandLine(
            nativeRuntimePython.get(), nativeRuntimeScript.asFile.absolutePath,
            "--resolve-lock", "--lock-file", nativeRuntimeLock.absolutePath
        )
        environment("GITHUB_TOKEN", nativeRuntimeGithubToken.get())
    }
} else {
    val lockFile = requireNotNull(nativeRuntimeExternalLock)
    tasks.register("prepareNativeRuntimeSourceLock") {
        inputs.file(lockFile)
        doLast {
            if (!lockFile.isFile) {
                throw GradleException("Configured nativeRuntimeLock does not exist: $lockFile")
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val release = variant.buildType == "release"
        val component = if (variant.flavorName == "full") "full" else "proot"
        val runtimeOutput = project.objects.directoryProperty().apply {
            set(layout.buildDirectory.dir("generated/nativeRuntime/$variantName"))
        }
        val runtimeCommand = mutableListOf(
            nativeRuntimePython.get(), nativeRuntimeScript.asFile.absolutePath,
            "--component", component, "--output-dir", runtimeOutput.get().asFile.absolutePath
        )
        if (!release) {
            runtimeCommand += "--prebuilt"
        } else {
            runtimeCommand += listOf(
                "--cache-dir", nativeRuntimeCache.asFile.absolutePath,
                "--ndk", nativeRuntimeNdkDirectory().absolutePath,
                "--lock-file", nativeRuntimeLock.absolutePath
            )
            project.findProperty("nativeRuntimeJobs")?.toString()?.let {
                runtimeCommand += listOf("--jobs", it)
            }
        }
        val runtimeTask = tasks.register<Exec>("prepare${variantName.replaceFirstChar { it.uppercase() }}NativeRuntime") {
            inputs.property("component", component)
            inputs.property("prebuilt", !release)
            inputs.property("pythonExecutable", nativeRuntimePython)
            inputs.files(nativeRuntimeRecipes)
            if (release) {
                inputs.file(nativeRuntimeLock)
            } else {
                inputs.file(rootProject.layout.projectDirectory.file("app/src/shared/assets/proot"))
                if (component == "full") {
                    inputs.files(
                        rootProject.layout.projectDirectory.file("app/src/full/assets/r2.tar.gz"),
                        rootProject.layout.projectDirectory.file("app/src/full/assets/r2dir.tar.gz")
                    )
                    inputs.dir(rootProject.layout.projectDirectory.dir("app/src/full/assets/libs"))
                }
            }
            outputs.dir(runtimeOutput)
            workingDir(rootProject.projectDir)
            commandLine(runtimeCommand)
            environment("GITHUB_TOKEN", nativeRuntimeGithubToken.get())
            if (release) dependsOn(prepareNativeRuntimeSourceLock)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(runtimeTask) { runtimeOutput }
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
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("org.json:json:20231013")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
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
    implementation(libs.xz)
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":r2pipe-kotlin"))
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
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")

    // AI Chat
    implementation(libs.openai.client)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.quickjs.kt)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.editor.bom))
    implementation(libs.editor)
    implementation(libs.language.textmate)


    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation("dev.chrisbanes.haze:haze-android:1.7.2")
    implementation("dev.chrisbanes.haze:haze-materials-android:1.7.2")
    implementation("cat.ereza:customactivityoncrash:2.4.0")
}
