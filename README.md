![R2droid](preview/icon.png)
# R2Droid

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)
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

- [x] Plugin manager UI for optional toolchain/extensions.
- [ ] Debug backend expansion and UX polish for native/frida debugging paths.
- [ ] More analysis automation templates (AI + report presets + action macros).

## 📦 Build Instructions

1. Clone the repository.
2. Open with Android Studio (Ladybug or newer recommended).
3. Use **JDK 17** as the Gradle JVM.
4. Build and run a debug variant on an Android device/emulator (**minSdk 24**).

Release variants refresh the ARM64 native runtime before packaging. The shared local/GitHub Actions build resolves the latest stable official `radare2`, `termux/proot`'s `master` commit, and the bundled `r2ghidra` / `r2dec` sources, then cross-compiles with Android NDK `28.2.13676358` / API 24.

Native Release builds require a Linux x86_64 environment (including WSL), Python **3.11+** with venv support, `curl`, Git, a C/C++ compiler, Make, GNU tar, zlib development headers, Ninja, `pkg-config`, and the Android SDK/NDK above. Install the pinned Meson in an active virtual environment:

```bash
python3 -m venv "$HOME/.venvs/r2droid-native"
. "$HOME/.venvs/r2droid-native/bin/activate"
python -m pip install meson==1.7.2
./gradlew :app:assembleFullRelease :app:assembleProotOnlyRelease
```

Each default Release invocation refreshes one shared source lock; unchanged sources reuse checksum-verified native artifacts. The resolved lock is `app/build/generated/nativeRuntime/source-lock.json`. Pass `-PnativeRuntimeLock=/absolute/path/source-lock.json` to build from an existing immutable lock without resolving newer upstream commits. `-PnativeRuntimeJobs=N` controls native parallelism; `-PnativeRuntimePython=/path/to/python` selects the interpreter (Meson must remain on `PATH`). `GITHUB_TOKEN` is optional for local GitHub API rate limits.

Pushing a `v*` tag runs the same Gradle tasks and publishes both signed APKs, the source lock, and each variant's runtime manifest. Manifests record source commits, toolchain versions, and staged asset hashes. Android's asset merger may expand staged `.tar.gz` archives into `.tar` entries in the APK; the installer supports both.

Debug native preparation also requires Python 3.11+, but uses the checked-in binaries and does not refresh upstream sources or compile radare2/plugins.

> The Full APK includes radare2 and rebuilt decompiler plugins; PRoot-only includes only the bundled PRoot runtime. Startup refreshes installed resources when their content identity changes, including plugin-only updates, and preserves the user's `.radare2rc`.

## 📄 License

This project is open-source under the [MIT License](LICENSE).

Thanks to the **Radare2**, **Frida**, and **Termux** communities.

## 🤝 Contributors

- [@wsdx233](https://github.com/wsdx233)
- [@binx6](https://github.com/binx6)
- [@AbhiTheModder](https://github.com/AbhiTheModder)

See the latest list on GitHub: [Contributors](https://github.com/wsdx233/r2droid/graphs/contributors)

![Alt](https://repobeats.axiom.co/api/embed/139f78104e5784eb504768fe892367694f5ed0b7.svg "Repobeats analytics image")

---
## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=wsdx233/r2droid&type=date&legend=top-left)](https://www.star-history.com/#wsdx233/r2droid&type=date&legend=top-left)
