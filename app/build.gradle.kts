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


val nativeRuntimeNdkVersion = providers.gradleProperty("ndkVersion").getOrElse("28.2.13676358")
val nativeRuntimePython = providers.gradleProperty("nativeRuntimePython").orElse("python3")
val nativeRuntimeGithubToken = providers.gradleProperty("nativeRuntimeGithubToken")
    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
    .orElse("")
val nativeRuntimeCache = rootProject.layout.projectDirectory.dir("build/native-runtime-cache")
val nativeRuntimeScript = rootProject.layout.projectDirectory.file("tools/build_native_runtime.py")
val nativeRuntimeRecipes = rootProject.fileTree("tools") {
    include("build_native_runtime.py", "native_*.py")
}
val localPropertiesContent = providers.fileContents(
    rootProject.layout.projectDirectory.file("local.properties")
).asText
val envAndroidSdkRoot = providers.environmentVariable("ANDROID_SDK_ROOT")
val envAndroidHome = providers.environmentVariable("ANDROID_HOME")
val envAndroidNdkRoot = providers.environmentVariable("ANDROID_NDK_ROOT")
val envAndroidNdkHome = providers.environmentVariable("ANDROID_NDK_HOME")
val releaseKeystoreFile = providers.gradleProperty("KEYSTORE_FILE")
    .orElse(providers.environmentVariable("KEYSTORE_FILE"))
val releaseKeystorePassword = providers.gradleProperty("KEYSTORE_PASSWORD")
    .orElse(providers.environmentVariable("KEYSTORE_PASSWORD"))
val releaseKeyAlias = providers.gradleProperty("KEY_ALIAS")
    .orElse(providers.environmentVariable("KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("KEY_PASSWORD")
    .orElse(providers.environmentVariable("KEY_PASSWORD"))

fun nativeRuntimeSdkDirectory(): File {
    val properties = Properties()
    localPropertiesContent.orNull?.let { content ->
        content.reader().use(properties::load)
    }
    val sdk = envAndroidSdkRoot.orNull
        ?: envAndroidHome.orNull
        ?: properties.getProperty("sdk.dir")
        ?: throw GradleException("Android SDK location is required to locate NDK $nativeRuntimeNdkVersion")
    return File(sdk.replace("\\:", ":"))
}

fun nativeRuntimeNdkDirectory(): File {
    val pinned = File(nativeRuntimeSdkDirectory(), "ndk/$nativeRuntimeNdkVersion")
    // Hosted runners also export a default NDK, which may not be the one installed for this build.
    if (pinned.isDirectory) return pinned.absoluteFile
    val explicit = envAndroidNdkRoot.orNull ?: envAndroidNdkHome.orNull
    return (explicit?.let(::File) ?: pinned).absoluteFile
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
        versionCode = 2611190
        versionName = "0.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 1. 定义签名配置
    signingConfigs {
        create("release") {
            // 优先从 Gradle 属性或环境变量读取，避免在本地构建或 CI 命令参数暴露密钥
            storeFile = file(releaseKeystoreFile.getOrElse("keystore.jks"))
            storePassword = releaseKeystorePassword.orNull
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
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
            // 只有当提供了密码时才应用签名（避免本地构建或未签名 CI 构建报错）
            if (releaseKeystorePassword.isPresent) {
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
