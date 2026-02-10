R2Droid 是一个基于 Radare2 逆向工程框架的 Android 原生 GUI 工具，采用 **Kotlin** 和 **Jetpack Compose** (Material3) 构建，架构模式为 **MVVM**，并使用 **Hilt** 进行依赖注入。

该项目旨在为移动设备提供高性能的二进制分析体验，特别是针对 Hex 编辑和反汇编视图实现了**虚拟化列表（Virtualized List）**和**分块加载（Chunk Loading）**机制，以支持大文件处理。



---

# R2Droid 项目结构概览

## 1. 顶层结构 (Root)

项目采用标准的 Gradle 多模块结构：

*   **`app/`**: 核心应用程序模块，包含所有业务逻辑和 UI。
*   **`terminal-emulator/`**: 终端模拟器后端逻辑（源自 Termux）。
*   **`terminal-view/`**: 终端模拟器 UI 组件（源自 Termux）。
*   **`gradle/`**: Gradle 包装器和版本目录 (`libs.versions.toml`)。

---

## 2. APP 模块详解 (`app/src/main/java/top/wsdx233/r2droid`)

代码组织采用 **Feature-based** (按功能分包) 结构，混合了 Core 层。

### 2.1. 入口与核心架构 (`activity/`, `di/`, `R2DroidApplication.kt`)

*   **`R2DroidApplication.kt`**: Hilt 应用入口。
*   **`activity/`**
    *   `MainActivity.kt`: 应用主入口，处理全局导航、权限检查、语言设置和安装引导。
    *   `TerminalActivity.kt`: 独立 Activity，用于全屏运行 Termux 终端组件。
*   **`di/`**
    *   `AppModule.kt`: Hilt 依赖注入模块，绑定 `R2DataSource` 等单例。

### 2.2. 通用核心层 (`core/`)

包含跨功能复用的数据模型、UI 组件和数据源接口。

*   **`core/data/model/`**: JSON 解析的数据模型 (DTO)。
    *   `AnalysisModels.kt`: 定义 `BinInfo`, `Section`, `Symbol`, `FunctionInfo` 等 R2 分析结果模型。
    *   `ProjectModels.kt`: 定义 `SavedProject`，用于项目持久化。
*   **`core/data/source/`**: 数据源接口。
    *   `R2PipeDataSource.kt`: `R2PipeManager` 的封装，供 Repository 调用。
*   **`core/data/prefs/`**:
    *   `SettingsManager.kt`: 管理 SharedPreferences，处理自定义字体、语言、`.radare2rc` 配置。
*   **`core/ui/`**: 通用 UI 组件。
    *   `components/AutoHideScrollbar.kt`: **核心组件**，用于 Hex/Disasm 视图的自定义快速滚动条。
    *   `components/FilterableList.kt`: 带搜索功能的通用列表组件。
    *   `components/ListItemWrapper.kt`: 统一的列表项容器，处理长按菜单（复制、跳转）。
    *   `dialogs/`: 通用对话框 (`JumpDialog`, `CustomCommandDialog`, `ModifyDialog`, `XrefsDialog`)。
    *   `theme/`: Compose 主题定义 (`Color`, `Theme`, `Type`)。

### 2.3. 功能模块 (`feature/`)

这是业务逻辑的核心，按功能划分。

#### 2.3.1. 项目管理 (`feature/home/`, `feature/project/`)
*   **`home/`**:
    *   `HomeScreen.kt`: 首页，显示最近项目，提供“打开文件”入口。
    *   `HomeViewModel.kt`: 处理文件选择、项目列表加载、文件复制到缓存。
*   **`project/`**:
    *   `ProjectScreen.kt`: 项目主容器，管理分析配置、加载进度、以及主脚手架 (`ProjectScaffold`)。
    *   `ProjectScaffold.kt`: 定义底部导航栏（列表/详情/项目设置）和顶部栏。
    *   `ProjectViewModel.kt`: **核心 VM**，协调 Hex、反汇编、反编译视图的数据同步，处理全局跳转 (`JumpToAddress`)。
    *   `AnalysisConfigScreen.kt`: 分析前的配置页面（选择 `aaa` 级别等）。
    *   `data/SavedProjectRepository.kt`: 管理项目索引 JSON 和 `.r2` 脚本文件的保存/读取。

#### 2.3.2. 十六进制视图 (`feature/hex/`)
*   **`HexScreen.kt`**: 这里的 `HexViewer` 实现了虚拟化列表逻辑。
*   **`HexViewModel.kt`**: 管理 `HexDataManager`，处理 Hex/String/Asm 的写入操作。
*   **`data/HexDataManager.kt`**: **核心逻辑**，实现 LRU 缓存和分块加载（Chunking），将地址映射到行索引，支持无限滚动。
*   **`ui/HexComponents.kt`**: `HexVisualRow` 负责绘制具体的 Hex/ASCII 行，处理列高亮。
*   **`ui/HexKeyboard.kt`**: 自定义十六进制软键盘。

#### 2.3.3. 反汇编视图 (`feature/disasm/`)
*   **`ui/DisasmScreen.kt`**: 无限滚动的反汇编列表。
*   **`data/DisasmDataManager.kt`**: **核心逻辑**，类似 Hex 的分块加载管理器，负责计算虚拟索引和预加载指令块。
*   **`ui/DisasmComponents.kt`**: `DisasmRow` 负责渲染单条指令，包含跳转箭头、操作码高亮、注释等。

#### 2.3.4. 二进制信息 (`feature/bininfo/`)
*   **`ui/BinInfoLists.kt`**: 展示 Sections, Symbols, Imports, Strings 等列表。
*   **`data/BinInfoRepository.kt`**: 封装对应的 r2 命令（如 `iSj`, `isj`, `izzj`）。

#### 2.3.5. 其他功能 (`feature/decompiler/`, `feature/terminal/`, `feature/install/`)
*   **`decompiler/`**: `DecompilerScreen.kt` 展示伪代码，支持语法高亮和点击跳转。
*   **`terminal/`**: `TerminalScreen.kt` (Compose 版终端) 和 `CommandScreen.kt` (即时命令执行)。
*   **`install/`**: `InstallScreen.kt` 展示 Radare2 资源解压进度。

### 2.4. 工具类 (`util/`)

*   **`R2PipeManager.kt`**: **核心引擎**。单例对象，管理 R2 进程的生命周期，使用 `Mutex` 保证命令串行执行，维护连接状态。
*   **`R2pipe.kt`**: 底层封装，使用 `ProcessBuilder` 启动 `/system/bin/sh` 执行 `radare2`，处理输入输出流。
*   **`R2Installer.kt`**: 负责从 Assets 解压 `r2.tar` 和 `r2dir.tar` 到内部存储，并配置执行权限。
*   **`LogManager.kt`**: 简单的内存日志记录器，用于应用内日志查看。

---

## 3. 关键资源文件 (`app/src/main/res/`)

*   **`values/strings.xml`**: 定义了语言字符串（英文）。
*   **`values-zh-rCN/strings.xml`**: 定义了语言字符串（中文）。
*   **`values/colors.xml`**: 定义了 Hex 和反汇编视图的语法高亮颜色（适配深色/浅色模式）。
*   **`drawable/`**: 包含应用图标和终端背景配置。

---

## 4. 依赖库 (`gradle/libs.versions.toml`)

*   **UI**: Compose BOM (Material3).
*   **架构**: Hilt (DI), ViewModel, Activity-Compose.
*   **工具**: Commons Compress (解压 tar), Coroutines.
*   **本地模块**: `:terminal-view`, `:terminal-emulator`.

---

## 5. 快速定位指南

*   **想看如何与 Radare2 交互？**
    *   👉 `app/src/main/java/top/wsdx233/r2droid/util/R2PipeManager.kt`
*   **想看 Hex 编辑器是如何处理大文件的？**
    *   👉 `app/src/main/java/top/wsdx233/r2droid/feature/hex/data/HexDataManager.kt`
*   **想看反汇编视图的绘制逻辑？**
    *   👉 `app/src/main/java/top/wsdx233/r2droid/feature/disasm/ui/DisasmScreen.kt`
*   **想看项目是如何保存和恢复的？**
    *   👉 `app/src/main/java/top/wsdx233/r2droid/feature/project/data/SavedProjectRepository.kt`
*   **想看应用启动时的资源解压？**
    *   👉 `app/src/main/java/top/wsdx233/r2droid/util/R2Installer.kt`