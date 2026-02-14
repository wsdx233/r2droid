
![R2droid](preview/icon.png)


![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue.svg)
![Radare2](https://img.shields.io/badge/Engine-Radare2-orange.svg)

**R2Droid** 是一个基于 [Radare2](https://github.com/radareorg/radare2) 逆向工程框架的现代化 Android 原生 GUI 工具。它使用 Kotlin 和 Jetpack Compose 构建，旨在为移动设备提供强大且界面友好的二进制分析体验。


![R2droid](preview/preview.png)

## ✨ 主要功能

*   **项目管理**：保存、加载和恢复分析会话。支持自定义项目保存路径。
*   **十六进制视图**：高性能、虚拟化的十六进制编辑器，支持数据修改（`wx`, `w`, `wa`）。
*   **反汇编视图**：支持无限滚动的反汇编列表，包含语法高亮、跳转箭头和导航历史。
*   **伪代码反编译**：查看由 r2 分析插件生成的伪代码。
*   **内置终端**：基于 `r2pipe` 和 `termux-view` 的全功能终端，支持执行原生 r2 命令。
*   **分析工具**：
    *   二进制概览（架构、位数、操作系统等）
    *   段 (Sections)、符号 (Symbols)、导入 (Imports)、重定位 (Relocations)
    *   字符串和函数列表
    *   交叉引用 (Xrefs)
*   **个性化设置**：深色/浅色主题、自定义字体、多语言切换（中/英）、自定义 `.radare2rc` 配置。

## 🚀 开发计划 (TODO)

*   [x] **优化 hex 编辑器的性能**：针对超大文件改进虚拟化加载逻辑。
*   [ ] **添加调用图 VV 查看**：实现函数调用和基本块的可视化图形视图。
*   [ ] **添加手动分析按钮**：按需触发特定的分析命令（如 `aa`, `aaa`）。
*   [ ] **终端辅助键**：在键盘上方添加快捷键栏（ESC, Tab, Ctrl, 方向键等）。
*   [x] **增强跳转历史**：优化前进/后退的导航栈体验。
*   [x] **搜索界面**：支持全局搜索字节、字符串和指令。
*   [ ] **函数交叉引用 axf**：更详细的函数调用关系查看。
*   [ ] **ESIL 模拟执行**：使用 ESIL 进行代码模拟运行和调试的 UI。
*   [x] **防抖机制**：在进行高负载操作时优化 UI 响应，防止重复触发。

## 🛠️ 构建说明

1.  克隆本仓库。
2.  使用 Android Studio (推荐 Ladybug 或更新版本) 打开。
3.  确保 Gradle JVM 设置为 JDK 21。
4.  连接 Android 设备（最低 SDK 24）进行编译运行。

> **注意**：应用内包含预编译的 `radare2` 二进制文件和资源，会在首次启动时自动解压安装。

## 📝 更新日志

查看 [CHANGELOG_CN.md](CHANGELOG_CN.md) 了解详细的版本发布说明和历史记录。

## 📄 许可证

本项目开源。详情请参阅 [LICENSE](LICENSE) 文件。
致谢 **Radare2** 团队和 **Termux** 项目提供的底层技术支持。