
# 🛠️ R2Droid 项目结构索引 (Project Structure Index)

## 1. 核心架构与底层通信 (Core & Communication)
这是应用运行的基础，负责 Radare2 引擎的安装、启动以及指令交互。

*   **Radare2 资源安装器**: `app/src/main/java/top/wsdx233/r2droid/util/R2Installer.kt`
    *   负责将 `assets` 中的 `r2.tar` 和 `r2dir.tar` 解压到应用私有目录，并设置执行权限。
*   **R2Pipe 实现 (底层)**: `app/src/main/java/top/wsdx233/r2droid/util/R2pipe.kt`
    *   封装了通过 `ProcessBuilder` 启动 r2 进程，并利用 stdin/stdout/stderr 进行通信的原始逻辑。
*   **R2Pipe 全局管理器 (单例)**: `app/src/main/java/top/wsdx233/r2droid/util/R2PipeManager.kt`
    *   **关键文件**：控制全局唯一的 R2 会话。使用 `Mutex` 确保命令执行的串行化，管理会话状态（空闲、执行中、错误）。
*   **依赖注入 (Hilt)**: `app/src/main/java/top/wsdx233/r2droid/di/AppModule.kt`
    *   配置数据源绑定。

## 2. 功能模块 (Feature Modules)
应用采用按功能划分的包结构，每个文件夹通常包含 `data` (Repository/Model) 和 `ui` (Compose Screens)。

### 📁 项目管理 (Home & Project)
*   **主页/文件选择**: `feature/home/`
    *   `HomeScreen.kt`: 最近项目列表和打开新文件。
    *   `HomeViewModel.kt`: 处理文件 URI 解析和 Session 初始化。
*   **项目概览 & 生命周期**: `feature/project/`
    *   `ProjectViewModel.kt`: 管理当前会话的全局状态（光标位置、分析级别）。
    *   `ProjectScaffold.kt`: 项目界面的主框架（底部导航栏、顶部状态栏）。
    *   `AnalysisConfigScreen.kt`: 文件打开后的 `aaa` 等级选择界面。
    *   `SavedProjectRepository.kt`: 将分析结果保存为 `.r2` 脚本并持久化元数据。

### 📁 十六进制视图 (Hex Viewer)
*   **文件**: `feature/hex/`
    *   **虚拟化核心**: `data/HexDataManager.kt` (处理超大文件，按需加载 4KB 数据块，LRU 缓存)。
    *   **UI**: `ui/HexScreen.kt` & `ui/HexComponents.kt`。
    *   **交互**: `ui/HexKeyboard.kt` (自定义十六进制输入法)。

### 📁 反汇编视图 (Disassembly)
*   **文件**: `feature/disasm/`
    *   **虚拟化核心**: `data/DisasmDataManager.kt` (处理无限滚动反汇编，指令流合并逻辑)。
    *   **UI渲染**: `ui/DisasmComponents.kt` (精细化的机器码、操作码、注释和跳转箭头绘制)。
    *   **对话框**: `core/ui/dialogs/XrefsDialog.kt` (显示交叉引用信息)。

### 📁 流程图 (Graph View)
*   **文件**: `feature/graph/`
    *   **布局算法**: `ui/GraphViewer.kt` (包含 **Sugiyama 层次布局算法** 的实现，负责节点排序、坐标计算和正交边缘路由)。
    *   **视图层**: `ui/GraphScreen.kt` (支持缩放、平移和节点点击跳转)。

### 📁 AI 助手 (AI Assistant)
*   **文件**: `feature/ai/`
    *   **引擎交互**: `data/R2ActionExecutor.kt` (解析 AI 输出，执行其中的 `[[cmd]]` 或 `<js>` 脚本)。
    *   **通信**: `data/AiRepository.kt` (支持 OpenAI 兼容格式的流式输出)。
    *   **UI**: `ui/AiChatScreen.kt` (对话界面)。

### 📁 终端 (Terminal)
*   **文件**: `feature/terminal/`
    *   `ui/TerminalScreen.kt`: 嵌入式命令行，支持执行任意 r2 指令。
    *   `activity/TerminalActivity.kt`: 基于 `termux-view` 的原生终端 Activity（用于更复杂的交互）。

### 📁 搜索 (Search)
*   **文件**: `feature/search/`
    *   支持字符串、十六进制、正则表达式和 ROP Gadgets 搜索，映射 r2 的 `/` 指令。

## 3. 公共组件与数据模型 (Common & Models)
*   **核心数据模型**: `core/data/model/`
    *   `AnalysisModels.kt`: 包含指令、函数、段、符号、交叉引用的数据类。
    *   `ProjectModels.kt`: 保存的项目元数据定义。
*   **全局设置**: `data/SettingsManager.kt`
    *   管理深色模式、自定义字体路径、语言和 `.radare2rc` 内容。
*   **公共 UI 库**: `core/ui/components/`
    *   `AutoHideScrollbar.kt`: 针对虚拟化列表定制的快速滚动条。
    *   `UnifiedListItemWrapper.kt`: 统一的长按/点击菜单包装器。
*   **日志系统**: `util/LogManager.kt`
    *   捕获 r2 的 stdout/stderr 并实时显示在 "Logs" 标签页。

## 4. 资源文件 (Resources)
*   **多语言**: 
    *   英文: `res/values/strings.xml`
    *   中文: `res/values-zh-rCN/strings.xml`
*   **主题与颜色**: 
    *   `ui/theme/Color.kt`: 定义终端风格、反汇编高亮等配色。
    *   `ui/theme/Theme.kt`: 实现 Material 3 动态配色适配。
*   **资产文件 (Assets)**:
    *   `r2.tar`: 预编译的 Radare2 静态二进制。
    *   `r2dir.tar`: 必要的插件、sleigh 文件和库文件。

## 5. 构建配置文件 (Build Config)
*   **Gradle 配置**: `app/build.gradle.kts` (包含依赖管理、签名配置和过时 API 的 Lint 豁免)。
*   **权限声明**: `AndroidManifest.xml` (包含 `MANAGE_EXTERNAL_STORAGE` 权限，用于读写外部 SD 卡上的二进制文件)。

---

### 快速定位技巧
1.  **想修改 R2 执行的逻辑？** 找 `R2PipeManager.kt`。
2.  **想增加一种图形显示？** 找 `GraphType` 枚举和 `GraphRepository.kt`。
3.  **想修复反汇编显示错误？** 找 `DisasmComponents.kt`。
4.  **想优化大文件读取速度？** 找 `HexDataManager.kt` 或 `DisasmDataManager.kt`。