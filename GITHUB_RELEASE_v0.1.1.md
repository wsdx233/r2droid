# R2Droid v0.1.1 - Initial Release / 首次发布

## English

### 🎉 First Public Release

**R2Droid** is a modern Android GUI for [Radare2](https://github.com/radareorg/radare2) reverse engineering framework, built with Kotlin and Jetpack Compose (Material3).

### ✨ Key Features

- 📱 **Project Management** - Save/restore analysis sessions with r2 cache
- 🔍 **Hex Editor** - High-performance virtualized rendering with real-time editing (`wx`, `w`, `wa`)
- 📜 **Disassembly** - Infinite scrolling with syntax highlighting, jump arrows, and navigation history
- 📝 **Decompiler** - Pseudo-code generation with syntax highlighting
- 💻 **Terminal** - Full r2pipe terminal using termux-view components
- 📊 **Binary Info** - Sections, symbols, imports, strings, functions, xrefs
- 🔎 **Search** - Find bytes, strings, and instructions
- ⚙️ **Customization** - Dark/Light themes, custom fonts, `.radare2rc` editor, English/Chinese support

### 🛠️ Technical Highlights

- **Architecture**: MVVM with Hilt DI
- **Performance**: Virtualized lists with LRU caching handle unlimited file sizes
- **Included**: Pre-compiled Radare2 v5.9.x (~70MB, extracted on first launch)

### 📥 Installation

1. Download APK (min Android SDK 24)
2. Install and grant storage permissions
3. First launch extracts r2 assets (~70MB)
4. Open any binary file to start

### 📖 Documentation

- [README](https://github.com/wsdx233/r2droid/blob/main/README.md)
- [CHANGELOG](https://github.com/wsdx233/r2droid/blob/main/CHANGELOG.md)
- [中文文档](https://github.com/wsdx233/r2droid/blob/main/README_CN.md)

---

## 中文

### 🎉 首个公开版本

**R2Droid** 是一个基于 [Radare2](https://github.com/radareorg/radare2) 逆向工程框架的现代化 Android GUI 工具，使用 Kotlin 和 Jetpack Compose (Material3) 构建。

### ✨ 主要功能

- 📱 **项目管理** - 保存/恢复分析会话及 r2 缓存
- 🔍 **十六进制编辑器** - 高性能虚拟化渲染，支持实时编辑（`wx`, `w`, `wa`）
- 📜 **反汇编** - 无限滚动，语法高亮，跳转箭头和导航历史
- 📝 **反编译器** - 伪代码生成，语法高亮
- 💻 **终端** - 基于 termux-view 的完整 r2pipe 终端
- 📊 **二进制信息** - 段、符号、导入、字符串、函数、交叉引用
- 🔎 **搜索** - 查找字节、字符串和指令
- ⚙️ **自定义** - 深色/浅色主题、自定义字体、`.radare2rc` 编辑器、中英文支持

### 🛠️ 技术亮点

- **架构**: MVVM + Hilt 依赖注入
- **性能**: 虚拟化列表配合 LRU 缓存处理无限大小文件
- **包含**: 预编译 Radare2 v5.9.x（约 70MB，首次启动解压）

### 📥 安装说明

1. 下载 APK（最低 Android SDK 24）
2. 安装并授予存储权限
3. 首次启动解压 r2 资源（约 70MB）
4. 打开任意二进制文件开始分析

### 📖 文档

- [README](https://github.com/wsdx233/r2droid/blob/main/README_CN.md)
- [更新日志](https://github.com/wsdx233/r2droid/blob/main/CHANGELOG_CN.md)
- [English README](https://github.com/wsdx233/r2droid/blob/main/README.md)

---

## 🙏 Acknowledgments / 致谢

Special thanks to [Radare2](https://github.com/radareorg/radare2) and [Termux](https://github.com/termux/termux-app) projects.

特别感谢 [Radare2](https://github.com/radareorg/radare2) 和 [Termux](https://github.com/termux/termux-app) 项目。

