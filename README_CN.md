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

1. 克隆本仓库。
2. 使用 Android Studio（建议 Ladybug 或更新版本）打开。
3. Gradle JVM 选择 **JDK 17**。
4. 在 Android 设备/模拟器上运行（**minSdk 24**）。

> 注意：应用内包含预编译 `radare2` 资源（`r2.tar`、`r2dir.tar`），首次启动会自动解压安装。

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
