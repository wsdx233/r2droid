![R2droid](preview/icon.png)
# R2Droid

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue.svg)
![Radare2](https://img.shields.io/badge/Engine-Radare2-orange.svg)

**R2Droid** 是一款基于 [Radare2](https://github.com/radareorg/radare2) 的 Android 原生逆向分析应用。项目使用 Kotlin + Jetpack Compose 开发，重点覆盖移动端静态分析、动态插桩与 AI 辅助分析流程。

[**🇺🇸 English README**](README.md)

![R2droid](preview/preview.png)

## ✨ 当前亮点

- 🤖 **AI 逆向助手（OpenAI 兼容）**：Chat Completions 与 Responses 接口均支持流式回复与结构化工具调用（`r2_command`、QuickJS `run_js` 与 Frida 模式 `run_frida`），危险或耗时命令支持卡片确认。
  - 不估算 token 数来提前压缩。只有服务端明确拒绝超长输入时，才让当前模型在不调用工具的情况下总结早期轮次，并用摘要和完整的当前轮次重试一次。摘要随会话缓存，下次超限时增量更新；重新打开聊天仍能查看原文。如果当前轮次本身就过长，需要缩短输入或切换模型。
  - 点按回复中的 `0x40`、`sym.main` 等地址或符号，可以复制、跳转到十六进制或反汇编视图，也可以查看交叉引用。只有操作需要地址时，才在当前项目中解析符号。切换标签后，每个聊天会回到原来的阅读位置；离开时若已在底部，回来仍会跟随新回复。
- 💉 **R2Frida 工作流**：内置 r2frida 安装器、本地/远程进程连接流程、自定义脚本管理与专用分析页面。
- 📊 **图形分析视图**：支持函数流程图、Xref 图、调用图、全局调用图和数据引用图，可触屏缩放和导出 PNG。
- 📑 **报告导出**：支持导出 Markdown / HTML / JSON 报告，并可根据分析结果生成 Frida Hook 模板。
- **Flutter AOT**：从「功能 → Flutter AOT」进入，可选择 APK 或 `libapp.so` 分析。

## 🛠️ 核心能力

- **项目生命周期管理**：支持文件选择器与外部 Intent 打开二进制，选择分析等级后可保存/恢复项目及元数据。
- **写缓存（按项目设置，默认关闭）**：在「配置分析 → 启动选项」中开启后，无需 `-w` 即可暂存 Hex、汇编和 r2 命令的写入。「项目 → 补丁」可对比原始与修改后的字节，回滚最近一次或指定的独立修改、清空缓存、确认写回原文件，也可只导出补丁副本。保存项目只记住开关，不保存待写入的补丁。遇到复杂重叠写入或额外的 r2 缓存层时，会限制无法安全执行的操作。
- **Hex + 反汇编编辑**：大文件虚拟化加载（分块 + 缓存）、`wx`/`wa`/字符串/注释修改、Xrefs 与函数信息弹窗、地址历史回退。
  - 点按已选中的字节或指令，打开「定义」可切换为代码、1/2/4/8 字节数据、字符串或已加载的结构体。反汇编的「操作数」可切换立即数表示，包括枚举成员和指针目标。这些操作只改分析标注，不改文件字节，无需 `-w`。
  - 「函数」可删除光标所在的函数，或调整其结束地址（不含该地址）。可对当前位置或跳转目标命名、添加或移除书签；从「列表 → 书签 / 注释 / 名称」可搜索标注并跳回原处。
  - Hex 每个列表项绘制 16 字节。快速滚动时优先读取可见的 4 KiB 数据块，再预读相邻块；占位行与实际行等高，数据到达时位置不跳动。
  - 反汇编快速滚动条按可执行、可写、只读给分区着色，空隙留白；半透明滑块是唯一的位置标记。拖动时，蓝色气泡第一行显示目标地址，第二行显示区内偏移和已缓存的指令。左侧圆角方块预览附近分区，边长随两行文字的总高度变化。
  - 分区概览每个会话只获取一次。未缓存的指令先显示占位行，在同一区域停留 300 毫秒或松手即加载目标，不重新查询分区，也不必等待相邻块预取。
  - 列表沿滚动方向提前排版最多半屏指令。短分页命令不会刷新整个项目界面；耗时命令仍会显示进度和停止按钮。查看器不强制切换系统刷新率。
- **反编译器切换**：支持 `r2ghidra`、`r2dec`、`native`、`aipdg`；提供内置编辑模式、缩放、换行、行号等显示选项。
- **调试能力（ESIL 优先）**：反汇编面板跟随架构定义的 PC（ARM32/Thumb 为 R15）。继续和运行到光标均按步数、时间限制分批执行；发生故障时可继续查看寄存器，面板显示故障指令地址及已知的内存访问地址。
  - 普通文件请使用标准错误流独立的本地 stdio 会话（非 HTTP，不加 `-1`）。无需开启 `-w` 或手动设置缓存；模拟写入使用已有补丁之上的临时缓存层，重置或关闭只撤销模拟写入。调试期间可查看 Hex 和反汇编；编辑文件或执行任意命令前请先关闭 ESIL。
- **搜索与分析列表**：总览 + 段/符号/导入/重定位/字符串/函数分页加载，支持搜索过滤。
- **终端体验**：内置终端支持额外按键栏（ESC/TAB/CTRL/ALT/方向键/PGUP/PGDN）与命令建议面板。
  - 手动执行 r2 指令时，命令页、快捷面板和自定义命令对话框会把本次诊断显示在 `[stderr]` 下，无需添加重定向。超长诊断保留末尾 256 Ki 个字符。程序化查询和流式读取仍只返回 stdout。
- **系统集成**：后台保活通知、版本更新检查弹窗、语言/主题/字体设置与自定义 `.radare2rc`。

## 截图

![preview_aichat](preview/preview_aichat.jpg)
![preview_graph](preview/preview_graph.jpg)

## 🚀 当前 TODO

- [x] 插件管理器 UI（扩展工具链/插件管理）。
- [ ] 调试后端扩展与 native/frida 调试路径体验完善。
- [ ] 更多分析自动化模板（AI + 报告预设 + 动作宏）。

## 📦 构建说明

1. 克隆仓库。
2. 使用 Android Studio（建议 Ladybug 或更新版本）打开。
3. Gradle 守护进程使用 JDK 21，项目使用 JDK 17 工具链编译 Kotlin/Java。
4. 运行设备需要 **Android 7.0+（API 24）、ARM64**；两个 APK 均含 ARM64 原生组件。

Release 变体会在打包前刷新 ARM64 原生运行时。本地与 GitHub Actions 共用构建入口，解析官方最新稳定版 `radare2`、`termux/proot` 的 `master` 提交及内置 `r2ghidra` / `r2dec` 源码，使用 Android NDK `28.2.13676358` / API 24 交叉编译。

Release 原生构建需要 Linux x86_64 环境（包括 WSL）、支持 venv 的 Python **3.11+**、`curl`、Git、C/C++ 编译器、Make、GNU tar、zlib 开发头文件、Ninja、`pkg-config`，以及上述 Android SDK/NDK。在已激活的虚拟环境中安装固定版本的 Meson：

```bash
python3 -m venv "$HOME/.venvs/r2droid-native"
. "$HOME/.venvs/r2droid-native/bin/activate"
python -m pip install meson==1.7.2
./gradlew :app:assembleFullRelease :app:assembleProotOnlyRelease
```

默认每次 Release 构建都会刷新一份两版共用的源码锁；源码未变化时复用经过校验的原生构建缓存。生成的源码锁位于 `app/build/generated/nativeRuntime/source-lock.json`。使用 `-PnativeRuntimeLock=/absolute/path/source-lock.json` 可按已有不可变源码锁构建，不再解析上游新提交。`-PnativeRuntimeJobs=N` 控制原生编译并行度；`-PnativeRuntimePython=/path/to/python` 指定解释器（Meson 仍须在 `PATH` 中）。本地可选设置 `GITHUB_TOKEN` 以提高 GitHub API 限额。

发版工作流先运行单元测试，再构建两版签名 APK、共用源码锁和各变体的运行时清单。手动运行或推送 `vX.Y.Z-rcN` 只上传 Actions 构件，**不创建 GitHub Release**；仅与两个 APK 的 `versionName` 完全匹配的 `vX.Y.Z` 标签才发布正式版。优先使用 `.github/release-notes/` 中对应版本的说明，没有时使用自动日志。清单记录源码提交、工具链版本及资源哈希；安装器兼容生成的 `.tar.gz` 和 APK 中展开的 `.tar`。

Debug 原生资源准备同样需要 Python 3.11+，但使用仓库内预编译文件，不刷新上游源码，也不编译 radare2/插件。
Full Debug 当前内置 radare2 6.2.6。

> Full APK 包含 radare2 和重新编译的反编译插件；PRoot-only 仅内置 PRoot 运行时。启动时按资源内容标识更新安装，包括仅插件变化的情况，并保留用户的 `.radare2rc`。

Full 版启动内置 r2 时会把 `R2_PREFIX` 指向应用私有的 radare2 目录，以加载 `r2dec` 所需的调用约定数据库。此设置不影响 PRoot 模式，也不需要修改 `.radare2rc`。

Full APK 按需安装 [r2frida](https://github.com/wsdx233/r2frida-android-arm64-build/releases) 和 [r2flutter](https://github.com/wsdx233/r2flutter-android-arm64-build/releases)，按内置 radare2 版本选择 `r2-<版本>` 发布标签。发布使用新版 radare2 的 Full APK 前，先交叉编译并发布与其匹配的两个 Android ARM64 插件。PRoot 模式仍通过 `r2pm` 安装。

新建 PRoot 环境默认从 Ubuntu 官方下载 [Ubuntu Base 24.04.5 LTS](https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/)。覆盖更新应用不会替换已安装的容器。

项目另存为、聊天记录迁移、未提交补丁以及按需插件重装须知见 [v0.4.0 升级说明](.github/release-notes/v0.4.0.md)。

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

感谢 **Radare2**、**Frida** 与 **Termux** 社区。

## 🤝 贡献者

- [@wsdx233](https://github.com/wsdx233)
- [@binx6](https://github.com/binx6)
- [@AbhiTheModder](https://github.com/AbhiTheModder)
- [@VictorGabriel7Dev](https://github.com/VictorGabriel7Dev)（巴西葡语）

最新完整列表请查看 GitHub: [Contributors](https://github.com/wsdx233/r2droid/graphs/contributors)

---
## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=wsdx233/r2droid&type=date&legend=top-left)](https://www.star-history.com/#wsdx233/r2droid&type=date&legend=top-left)
