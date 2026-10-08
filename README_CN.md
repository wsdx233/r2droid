![R2droid](preview/icon.png)
# R2Droid

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue.svg)
![Radare2](https://img.shields.io/badge/Engine-Radare2-orange.svg)

**R2Droid** 是一款基于 [Radare2](https://github.com/radareorg/radare2) 的 Android 原生逆向分析应用。项目使用 Kotlin + Jetpack Compose 开发，重点覆盖移动端静态分析、动态插桩与 AI 辅助分析流程。

[**🇺🇸 English README**](README.md)

![R2droid](preview/preview.png)

## ✨ 当前亮点

- 🤖 **AI 逆向助手（OpenAI 兼容）**：支持流式对话、可配置提示词，并可通过 `[[cmd]]` / `<js>` 在会话内执行动作。
- 💉 **R2Frida 工作流**：内置 r2frida 安装器、本地/远程进程连接流程、自定义脚本管理与专用分析页面。
- 📊 **图形分析视图**：触屏友好的图形界面，采用 Sugiyama 分层布局；支持函数流程图、Xref 图、调用图、全局调用图和数据引用图。
- 📑 **报告导出**：支持导出 Markdown / HTML / JSON 报告，并可根据分析结果生成 Frida Hook 模板。

## 🛠️ 核心能力

- **项目生命周期管理**：支持文件选择器与外部 Intent 打开二进制，选择分析等级后可保存/恢复项目及元数据。
- **Hex + 反汇编编辑**：大文件虚拟化加载（分块 + 缓存）、`wx`/`wa`/字符串/注释修改、Xrefs 与函数信息弹窗、地址历史回退。
- **反编译器切换**：支持 `r2ghidra`、`r2dec`、`native`、`aipdg`；提供内置编辑模式、缩放、换行、行号等显示选项。
- **调试能力（ESIL 优先）**：支持 ESIL 初始化、断点、步进/步过/继续/暂停、寄存器面板与 PC 自动跟随。
- **搜索与分析列表**：总览 + 段/符号/导入/重定位/字符串/函数分页加载，支持搜索过滤。
- **终端体验**：内置终端支持额外按键栏（ESC/TAB/CTRL/ALT/方向键/PGUP/PGDN）与命令建议面板。
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
3. Gradle JVM 选择 **JDK 17**。
4. 在 Android 设备/模拟器上构建并运行 Debug 变体（**minSdk 24**）。

Release 变体会在打包前刷新 ARM64 原生运行时。本地与 GitHub Actions 共用构建入口，解析官方最新稳定版 `radare2`、`termux/proot` 的 `master` 提交及内置 `r2ghidra` / `r2dec` 源码，使用 Android NDK `28.2.13676358` / API 24 交叉编译。

Release 原生构建需要 Linux x86_64 环境（包括 WSL）、支持 venv 的 Python **3.11+**、`curl`、Git、C/C++ 编译器、Make、GNU tar、zlib 开发头文件、Ninja、`pkg-config`，以及上述 Android SDK/NDK。在已激活的虚拟环境中安装固定版本的 Meson：

```bash
python3 -m venv "$HOME/.venvs/r2droid-native"
. "$HOME/.venvs/r2droid-native/bin/activate"
python -m pip install meson==1.7.2
./gradlew :app:assembleFullRelease :app:assembleProotOnlyRelease
```

默认每次 Release 构建都会刷新一份两版共用的源码锁；源码未变化时复用经过校验的原生构建缓存。生成的源码锁位于 `app/build/generated/nativeRuntime/source-lock.json`。使用 `-PnativeRuntimeLock=/absolute/path/source-lock.json` 可按已有不可变源码锁构建，不再解析上游新提交。`-PnativeRuntimeJobs=N` 控制原生编译并行度；`-PnativeRuntimePython=/path/to/python` 指定解释器（Meson 仍须在 `PATH` 中）。本地可选设置 `GITHUB_TOKEN` 以提高 GitHub API 限额。

推送 `v*` 标签后，GitHub Actions 会执行同一组 Gradle 任务，发布两版签名 APK、源码锁和各变体的运行时清单。清单记录源码提交、工具链版本及生成资源的哈希。Android 资源合并器可能将生成的 `.tar.gz` 展开为 APK 内的 `.tar`，安装器兼容两种格式。

Debug 原生资源准备同样需要 Python 3.11+，但使用仓库内预编译文件，不刷新上游源码，也不编译 radare2/插件。

> Full APK 包含 radare2 和重新编译的反编译插件；PRoot-only 仅内置 PRoot 运行时。启动时按资源内容标识更新安装，包括仅插件变化的情况，并保留用户的 `.radare2rc`。

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

感谢 **Radare2**、**Frida** 与 **Termux** 社区。

## 🤝 贡献者

- [@wsdx233](https://github.com/wsdx233)
- [@binx6](https://github.com/binx6)

最新完整列表请查看 GitHub: [Contributors](https://github.com/wsdx233/r2droid/graphs/contributors)

---
## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=wsdx233/r2droid&type=date&legend=top-left)](https://www.star-history.com/#wsdx233/r2droid&type=date&legend=top-left)
