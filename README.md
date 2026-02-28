![R2droid](preview/icon.png)
# R2Droid

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-purple.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue.svg)
![Radare2](https://img.shields.io/badge/Engine-Radare2-orange.svg)

![R2droid](preview/preview.png)

**R2Droid** is a native Android reverse engineering app powered by [Radare2](https://github.com/radareorg/radare2). It is built with Kotlin + Jetpack Compose and focuses on mobile-friendly static analysis, dynamic instrumentation, and AI-assisted workflows.

[**🇨🇳 中文说明**](README_CN.md)

## ✨ Highlights

- 🤖 **AI Assistant (OpenAI-compatible)**: chat-based analysis with streaming output, custom prompts, and optional command/script execution through `[[cmd]]` and `<js>` actions.
- 💉 **R2Frida Workflow**: in-app r2frida installer, local/remote process flow, custom script management, and Frida-focused analysis tabs.
- 📊 **Graph Viewer**: touch-friendly graphs with Sugiyama layout and support for Function Flow, Xref, Call Graph, Global Call Graph, and Data Reference Graph.
- 📑 **Report Export**: export analysis as Markdown / HTML / JSON, or generate Frida hook templates from discovered functions/imports/exports.

## 🛠️ Core Capabilities

- **Project Lifecycle**: open binaries from file picker or external intents, choose analysis level, then save/restore projects with metadata and replay scripts.
- **Hex + Disassembly**: virtualized large-file hex/disasm viewers (chunked loading + cache), patching (`wx`/`wa`/string/comment), xrefs, function dialogs, and navigation history.
- **Decompiler**: switch between `r2ghidra`, `r2dec`, `native`, and `aipdg`; optional built-in editor mode, zoom/line-wrap/line-number controls.
- **Debugging (ESIL-first)**: ESIL init, breakpoints, step/step-over/continue/pause, register panel, and automatic PC-follow in disassembly.
- **Search + Analysis Lists**: overview plus sections/symbols/imports/relocs/strings/functions with search and paging-backed data loading.
- **Terminal Experience**: embedded terminal with extra keys (ESC/TAB/CTRL/ALT/arrows/PGUP/PGDN) and command console with suggestion panel.
- **System Integration**: background keep-alive service, update checker dialog, language/theme/font settings, and custom `.radare2rc` support.

## Screenshots

![preview_aichat](preview/preview_aichat.jpg)
![preview_graph](preview/preview_graph.jpg)

## 🚀 Current TODO

- [ ] Plugin manager UI for optional toolchain/extensions.
- [ ] Debug backend expansion and UX polish for native/frida debugging paths.
- [ ] More analysis automation templates (AI + report presets + action macros).

## 📦 Build Instructions

1. Clone the repository.
2. Open with Android Studio (Ladybug or newer recommended).
3. Use **JDK 17** as Gradle JVM.
4. Build and run on an Android device/emulator (**minSdk 24**).

> Note: The app bundles prebuilt `radare2` assets (`r2.tar`, `r2dir.tar`) and extracts them on first launch.

## 📄 License

This project is open-source under the [MIT License](LICENSE).

Thanks to the **Radare2**, **Frida**, and **Termux** communities.

## 🤝 Contributors

- [@wsdx233](https://github.com/wsdx233)
- [@binx6](https://github.com/binx6)

See the latest list on GitHub: [Contributors](https://github.com/wsdx233/r2droid/graphs/contributors)

---
## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=wsdx233/r2droid&type=date&legend=top-left)](https://www.star-history.com/#wsdx233/r2droid&type=date&legend=top-left)
