# R2Droid 项目结构索引 (Project Structure Index)

> 更新日期：基于当前 `main` 工作区代码结构整理。

## 1) 核心架构与底层通信 (Core & Communication)

应用运行核心，负责 r2/r2frida 安装、会话管理、命令执行与状态同步。

- **Radare2 资源安装器**: `app/src/main/java/top/wsdx233/r2droid/util/R2Installer.kt`
  - 首次启动检查并解压 `assets/r2.tar`、`assets/r2dir.tar` 到应用私有目录，设置运行权限。
- **R2pipe 进程通信**: `app/src/main/java/top/wsdx233/r2droid/util/R2pipe.kt`
  - 使用 `ProcessBuilder` 启动 r2，封装 stdin/stdout/stderr 双向通信。
- **R2Pipe 全局会话管理**: `app/src/main/java/top/wsdx233/r2droid/util/R2PipeManager.kt`
  - 全局单会话入口；串行执行命令、维护会话状态（执行中/空闲/异常）、项目脏状态与待恢复参数。
- **R2 HTTP 管道（可选）**: `app/src/main/java/top/wsdx233/r2droid/util/R2pipeHttp.kt`
  - 提供 HTTP 方式访问 r2 的能力。
- **依赖注入模块**: `app/src/main/java/top/wsdx233/r2droid/di/AppModule.kt`
  - 仓库/数据源绑定。
- **数据库注入模块**: `app/src/main/java/top/wsdx233/r2droid/di/DatabaseModule.kt`
  - Room 数据库与 DAO 提供。

## 2) 应用入口与导航 (App Entry & Navigation)

- **应用入口 Activity**: `app/src/main/java/top/wsdx233/r2droid/activity/MainActivity.kt`
  - 处理安装检查、更新检查、权限流转、外部 Intent 打开文件、主界面导航。
- **保活服务**: `app/src/main/java/top/wsdx233/r2droid/service/KeepAliveService.kt`
  - 可选常驻通知，降低长耗时分析被系统杀后台的概率。
- **主导航屏幕枚举**: `app/src/main/java/top/wsdx233/r2droid/activity/MainActivity.kt`
  - Home / Project / About / Settings / Features / R2Frida。

## 3) 功能模块 (Feature Modules)

### 3.1 Home 与项目生命周期 (Home & Project)

- **主页与最近项目**: `app/src/main/java/top/wsdx233/r2droid/feature/home/HomeScreen.kt`
- **主页逻辑**: `app/src/main/java/top/wsdx233/r2droid/feature/home/HomeViewModel.kt`
  - 处理文件选择、恢复项目、删除项目。
- **项目主框架**: `app/src/main/java/top/wsdx233/r2droid/feature/project/ProjectScaffold.kt`
  - 分类导航（List/Detail/R2Frida/Project/AI）、顶部工具栏、底部标签体系。
- **项目状态机**: `app/src/main/java/top/wsdx233/r2droid/feature/project/ProjectScreen.kt`
  - Configuring / Analyzing / Success 切换，退出保存确认流程。
- **项目核心 ViewModel**: `app/src/main/java/top/wsdx233/r2droid/feature/project/ProjectViewModel.kt`
  - 分析会话启动、分页数据同步、图类型切换、保存/更新项目、反编译设置。
- **项目持久化仓库**: `app/src/main/java/top/wsdx233/r2droid/feature/project/data/SavedProjectRepository.kt`
  - 保存/恢复 `.r2` 脚本与项目元数据。
- **报告导出**: `app/src/main/java/top/wsdx233/r2droid/feature/project/ReportExporter.kt`
  - 导出 Markdown/HTML/JSON/Frida 模板。

### 3.2 十六进制视图 (Hex Viewer)

- **数据虚拟化核心**: `app/src/main/java/top/wsdx233/r2droid/feature/hex/data/HexDataManager.kt`
  - 大文件分块加载（4KB）+ 缓存策略。
- **Hex 界面**: `app/src/main/java/top/wsdx233/r2droid/feature/hex/ui/HexScreen.kt`
- **Hex 组件**: `app/src/main/java/top/wsdx233/r2droid/feature/hex/ui/HexComponents.kt`
- **Hex 键盘**: `app/src/main/java/top/wsdx233/r2droid/feature/hex/ui/HexKeyboard.kt`

### 3.3 反汇编与调试 (Disassembly & Debug)

- **反汇编虚拟化核心**: `app/src/main/java/top/wsdx233/r2droid/feature/disasm/data/DisasmDataManager.kt`
  - 无限滚动、跨块合并、跳转映射缓存。
- **反汇编主界面**: `app/src/main/java/top/wsdx233/r2droid/feature/disasm/ui/DisasmScreen.kt`
  - 指令列表、上下文菜单、多选、断点标记、调试底部面板。
- **反汇编渲染组件**: `app/src/main/java/top/wsdx233/r2droid/feature/disasm/ui/DisasmComponents.kt`
- **反汇编 ViewModel**: `app/src/main/java/top/wsdx233/r2droid/feature/disasm/DisasmViewModel.kt`
  - 写入修改、xrefs/函数详情、AI 指令解释、ESIL 初始化、步进控制。
- **调试仓库**: `app/src/main/java/top/wsdx233/r2droid/feature/debug/data/DebuggerRepository.kt`
  - ESIL / Native GDB / Frida 三类后端指令封装。
- **独立调试页（实验/工具）**: `app/src/main/java/top/wsdx233/r2droid/feature/debug/DebugScreen.kt`

### 3.4 反编译视图 (Decompiler)

- **反编译页面**: `app/src/main/java/top/wsdx233/r2droid/feature/decompiler/ui/DecompilerScreen.kt`
  - 支持 `r2ghidra` / `r2dec` / `native` / `aipdg` 切换与显示配置。

### 3.5 图形视图 (Graph)

- **图形容器页面**: `app/src/main/java/top/wsdx233/r2droid/feature/graph/ui/GraphScreen.kt`
- **图布局与绘制**: `app/src/main/java/top/wsdx233/r2droid/feature/graph/ui/GraphViewer.kt`
  - 含 Sugiyama 分层布局与边路由逻辑。

### 3.6 AI 助手 (AI Assistant)

- **聊天 UI**: `app/src/main/java/top/wsdx233/r2droid/feature/ai/ui/AiChatScreen.kt`
- **Provider 设置 UI**: `app/src/main/java/top/wsdx233/r2droid/feature/ai/ui/AiProviderSettingsScreen.kt`
- **提示词管理 UI**: `app/src/main/java/top/wsdx233/r2droid/feature/ai/ui/AiPromptsScreen.kt`
- **AI 通信仓库**: `app/src/main/java/top/wsdx233/r2droid/feature/ai/data/AiRepository.kt`
  - OpenAI 兼容接口与流式响应。
- **动作执行器**: `app/src/main/java/top/wsdx233/r2droid/feature/ai/data/R2ActionExecutor.kt`
  - 解析并执行 `[[cmd]]` / `<js>` 动作。

### 3.7 R2Frida (动态插桩)

- **R2Frida 入口页**: `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/R2FridaScreen.kt`
  - 安装流程 + 功能页切换。
- **R2Frida ViewModel**: `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/R2FridaViewModel.kt`
- **数据仓库**: `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/data/R2FridaRepository.kt`
- **UI 组件与列表**:
  - `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/ui/R2FridaScreens.kt`
  - `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/ui/R2FridaLists.kt`
  - `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/ui/FridaCustomScreens.kt`
- **安装器**: `app/src/main/java/top/wsdx233/r2droid/util/R2FridaInstaller.kt`

### 3.8 终端与命令执行 (Terminal)

- **嵌入式终端**: `app/src/main/java/top/wsdx233/r2droid/feature/terminal/ui/TerminalScreen.kt`
  - 基于 `termux-view`，支持输入法联动。
- **终端扩展按键栏**: `app/src/main/java/top/wsdx233/r2droid/feature/terminal/ui/ExtraKeysBar.kt`
  - ESC/TAB/CTRL/ALT/方向键/PGUP/PGDN。
- **原生终端 Activity**: `app/src/main/java/top/wsdx233/r2droid/activity/TerminalActivity.kt`

### 3.9 搜索、BinInfo 与其它页面

- **搜索模块**: `app/src/main/java/top/wsdx233/r2droid/feature/search/`
  - 字符串/十六进制/正则/ROP 与 r2 `/` 命令映射。
- **BinInfo 模块**: `app/src/main/java/top/wsdx233/r2droid/feature/bininfo/`
  - Binary overview、sections/symbols/imports/relocs/strings/functions 列表。
- **设置页**: `app/src/main/java/top/wsdx233/r2droid/feature/settings/SettingsScreen.kt`
- **关于页**: `app/src/main/java/top/wsdx233/r2droid/feature/about/AboutScreen.kt`
- **离线手册页**: `app/src/main/java/top/wsdx233/r2droid/feature/manual/R2ManualScreen.kt`
- **功能入口页**: `app/src/main/java/top/wsdx233/r2droid/screen/home/FeaturesScreen.kt`

## 4) 公共层与数据层 (Core / Common)

- **核心数据模型**: `app/src/main/java/top/wsdx233/r2droid/core/data/model/`
  - `AnalysisModels.kt`, `ProjectModels.kt`, `SearchModels.kt`, `UpdateModels.kt`。
- **数据源抽象**: `app/src/main/java/top/wsdx233/r2droid/core/data/source/`
  - `R2DataSource.kt`, `R2PipeDataSource.kt`。
- **Room 数据库**: `app/src/main/java/top/wsdx233/r2droid/core/data/db/`
  - `AppDatabase.kt` + Entity + DAO。
- **全局设置管理**: `app/src/main/java/top/wsdx233/r2droid/core/data/prefs/SettingsManager.kt`
  - 主题、字体、语言、反编译显示设置、保活开关、`.radare2rc` 等。
- **公共 UI 组件**: `app/src/main/java/top/wsdx233/r2droid/core/ui/components/`
  - `AutoHideScrollbar.kt`, `ListItemWrapper.kt`, `FilterableList.kt`, `SoraEditorView.kt` 等。
- **公共对话框**: `app/src/main/java/top/wsdx233/r2droid/core/ui/dialogs/`
  - Jump/Modify/Xrefs/FunctionInfo/Update 等。

## 5) 工具与系统能力 (Utilities)

- **日志管理**: `app/src/main/java/top/wsdx233/r2droid/util/LogManager.kt`
- **权限管理**: `app/src/main/java/top/wsdx233/r2droid/util/PermissionManager.kt`
- **外部文件解析**: `app/src/main/java/top/wsdx233/r2droid/util/IntentFileResolver.kt`
- **更新检查与状态管理**:
  - `app/src/main/java/top/wsdx233/r2droid/util/UpdateChecker.kt`
  - `app/src/main/java/top/wsdx233/r2droid/util/UpdateManager.kt`
- **命令帮助索引**: `app/src/main/java/top/wsdx233/r2droid/util/R2CommandHelp.kt`

## 6) 资源与构建配置 (Resources & Build)

- **多语言资源**:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-zh-rCN/strings.xml`
  - `app/src/main/res/values-ru/strings.xml`
- **主题与样式**: `app/src/main/java/top/wsdx233/r2droid/core/ui/theme/`
- **关键资产**: `app/src/main/assets/r2.tar`, `app/src/main/assets/r2dir.tar`
- **Manifest**: `app/src/main/AndroidManifest.xml`
  - 包含 `MANAGE_EXTERNAL_STORAGE`、`FOREGROUND_SERVICE`、外部文件 `VIEW/SEND` intent-filter。
- **Gradle 配置**: `app/build.gradle.kts`
  - `minSdk = 24`, `compileSdk = 36`, Java/Kotlin target 17。

---

## 快速定位技巧 (Quick Navigation)

1. **改 r2 执行与会话状态** -> `app/src/main/java/top/wsdx233/r2droid/util/R2PipeManager.kt`
2. **改项目主导航/标签结构** -> `app/src/main/java/top/wsdx233/r2droid/feature/project/ProjectScaffold.kt`
3. **改反汇编滚动/加载性能** -> `app/src/main/java/top/wsdx233/r2droid/feature/disasm/data/DisasmDataManager.kt`
4. **改 Hex 大文件读取策略** -> `app/src/main/java/top/wsdx233/r2droid/feature/hex/data/HexDataManager.kt`
5. **改 AI 动作执行规则** -> `app/src/main/java/top/wsdx233/r2droid/feature/ai/data/R2ActionExecutor.kt`
6. **改 R2Frida 安装与连接流程** -> `app/src/main/java/top/wsdx233/r2droid/feature/r2frida/R2FridaScreen.kt` 和 `app/src/main/java/top/wsdx233/r2droid/util/R2FridaInstaller.kt`
