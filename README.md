![R2droid](preview/icon.png)
# R2Droid

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-purple.svg)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-blue.svg)
![Radare2](https://img.shields.io/badge/Engine-Radare2-orange.svg)

![R2droid](preview/preview.png)

**R2Droid** is a native Android reverse engineering app powered by [Radare2](https://github.com/radareorg/radare2). It is built with Kotlin + Jetpack Compose and focuses on mobile-friendly static analysis, dynamic instrumentation, and AI-assisted workflows.

[**🇨🇳 中文说明**](README_CN.md)

## ✨ Highlights

- 🤖 **AI Assistant (OpenAI-compatible)**: Chat Completions and Responses APIs with streaming replies and structured tool calls (`r2_command`, QuickJS `run_js`, and `run_frida` in Frida mode), with user approval prompts for dangerous/heavy commands.
  - Long chats aren't shortened based on a guessed token count. Only an explicit input-window rejection starts compaction: the current model summarizes older turns without tools, then the chat retries once with that summary and the current turn intact. The summary is cached with the conversation; reopening the chat preserves the full transcript. If the current turn alone is too large, shorten it or switch models.
  - Tap an address such as `0x40` or an r2 symbol such as `sym.main` in a reply to copy it, jump to Hex or Disassembly, or inspect cross-references. Symbols are resolved against the current project only when an action needs an address. Switching tabs preserves each chat’s reading position; a chat left at the bottom keeps following new replies.
- 💉 **R2Frida Workflow**: in-app r2frida installer, local/remote process flow, custom script management, and Frida-focused analysis tabs.
- 📊 **Graph Viewer**: touch-friendly graphs with Sugiyama layout, zoom controls, and PNG export. Supports Function Flow, Xref, Call Graph, Global Call Graph, and Data Reference Graph.
- 📑 **Report Export**: export analysis as Markdown / HTML / JSON, or generate Frida hook templates from discovered functions/imports/exports.
- **Flutter AOT**: open **Features → Flutter AOT** to analyze an APK or `libapp.so`.

## 🛠️ Core Capabilities

- **Project Lifecycle**: open binaries from file picker or external intents, choose analysis level, then save/restore projects with metadata and replay scripts.
- **Write cache (per project, off by default)**: enable it in **Configure Analysis → Startup Options** to stage Hex, assembly and r2 writes without `-w`. **Project → Patches** shows original and changed bytes. Undo the latest edit or an eligible individual row, discard the cache, confirm a write to the original, or export a patched copy without committing. Saving the project remembers the mode, not pending edits. Complex overlapping writes and extra r2 cache layers restrict individual actions rather than risking unrelated changes.
- **Hex + Disassembly**: virtualized large-file hex/disasm viewers (chunked loading + cache), patching (`wx`/`wa`/string/comment), xrefs, function dialogs, and navigation history.
  - Tap the selected byte or instruction and choose **Define** to switch between code, 1/2/4/8-byte data, a string or a loaded structure. **Operand** in Disassembly changes how the immediate is shown, including enum members and pointer targets. These are analysis edits, not byte patches; no `-w` is needed.
  - **Function** can delete the containing function or set its exclusive end address. Name the current address or a branch target, or toggle a bookmark; **List → Bookmarks / Comments / Names** lets you search those annotations and jump back to them.
  - The hex grid draws 16 bytes per list item. Fast scrolling loads visible 4 KiB chunks before neighboring chunks; unloaded rows keep their height so bytes do not shift when data arrives.
  - The disassembly scrollbar colors executable, writable and read-only regions from a single session snapshot. Gaps stay blank; the translucent thumb is the position indicator. Dragging opens a blue bubble: the address is on the first line, the section offset and cached instruction share the second. A rounded square beside them previews nearby regions and scales to the height of both lines.
  - Uncached targets show placeholder rows. Pausing in one region for 300 ms or releasing loads the target without waiting for neighboring chunks; the overview itself is not queried again.
  - The list prepares up to half a viewport of rows ahead. Short paging commands do not recompose the whole project screen; long-running commands still show progress and a stop button. The viewer does not force the system refresh rate.
- **Decompiler**: switch between `r2ghidra`, `r2dec`, `native`, and `aipdg`; optional built-in editor mode, zoom/line-wrap/line-number controls.
- **Debugging (ESIL-first)**: the disassembly panel follows the architecture's actual PC (including ARM32/Thumb R15). Continue and Run to Cursor run in short, bounded batches. A fault leaves the registers inspectable and reports the faulting instruction and memory access address when available.
  - For ordinary files, use a local stdio session with separate stderr (not HTTP or `-1`). No `-w` or cache flag is needed. ESIL writes to a temporary cache layer above existing patches; Reset or Close discards only simulated writes. Hex/disassembly browsing remains available. Stop ESIL before editing the file or running unrestricted commands on that session.
- **Search + Analysis Lists**: overview plus sections/symbols/imports/relocs/strings/functions with search and paging-backed data loading.
- **Terminal Experience**: embedded terminal with extra keys (ESC/TAB/CTRL/ALT/arrows/PGUP/PGDN) and command console with suggestion panel.
  - The command tab, quick panel, and custom command dialog show each command’s diagnostics under `[stderr]` without redirection. Long diagnostics retain the last 256 Ki characters. Programmatic queries and streams still return stdout only.
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
3. The Gradle daemon uses JDK 21; the project compiles Kotlin/Java with a JDK 17 toolchain.
4. Run on **Android 7.0+ (API 24), ARM64**. Both APKs ship ARM64 native components.

Release variants refresh the ARM64 native runtime before packaging. The shared local/GitHub Actions build resolves the latest stable official `radare2`, `termux/proot`'s `master` commit, and the bundled `r2ghidra` / `r2dec` sources, then cross-compiles with Android NDK `28.2.13676358` / API 24.

Native Release builds require a Linux x86_64 environment (including WSL), Python **3.11+** with venv support, `curl`, Git, a C/C++ compiler, Make, GNU tar, zlib development headers, Ninja, `pkg-config`, and the Android SDK/NDK above. Install the pinned Meson in an active virtual environment:

```bash
python3 -m venv "$HOME/.venvs/r2droid-native"
. "$HOME/.venvs/r2droid-native/bin/activate"
python -m pip install meson==1.7.2
./gradlew :app:assembleFullRelease :app:assembleProotOnlyRelease
```

Each default Release invocation refreshes one shared source lock; unchanged sources reuse checksum-verified native artifacts. The resolved lock is `app/build/generated/nativeRuntime/source-lock.json`. Pass `-PnativeRuntimeLock=/absolute/path/source-lock.json` to build from an existing immutable lock without resolving newer upstream commits. `-PnativeRuntimeJobs=N` controls native parallelism; `-PnativeRuntimePython=/path/to/python` selects the interpreter (Meson must remain on `PATH`). `GITHUB_TOKEN` is optional for local GitHub API rate limits.

The release workflow runs the unit suites and builds both signed APKs, a shared source lock, and per-variant runtime manifests. A manual run or `vX.Y.Z-rcN` tag uploads Actions artifacts **without** creating a GitHub Release. Only an exact `vX.Y.Z` tag matching the APKs’ `versionName` publishes a stable release. Version-specific notes in `.github/release-notes/` take precedence over the generated changelog. The manifests identify source commits, toolchain versions, and asset hashes. The installer accepts both staged `.tar.gz` and APK-merged `.tar` assets.

Debug native preparation also requires Python 3.11+, but uses the checked-in binaries and does not refresh upstream sources or compile radare2/plugins.
Full Debug currently bundles radare2 6.2.6.

> The Full APK includes radare2 and rebuilt decompiler plugins; PRoot-only includes only the bundled PRoot runtime. Startup refreshes installed resources when their content identity changes, including plugin-only updates, and preserves the user's `.radare2rc`.

The Full build sets `R2_PREFIX` to the app-private radare2 directory when launching the bundled engine, so r2 can load the calling-convention database used by `r2dec`. This does not change PRoot mode or require editing `.radare2rc`.

The Full APK installs [r2frida](https://github.com/wsdx233/r2frida-android-arm64-build/releases) and [r2flutter](https://github.com/wsdx233/r2flutter-android-arm64-build/releases) on demand, selecting the `r2-<version>` release tag for its bundled radare2. When updating radare2 for a Full release, cross-compile and publish both matching Android ARM64 plugins first. PRoot mode continues to install them via `r2pm`.

New PRoot environments default to [Ubuntu Base 24.04.5 LTS](https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/) from the official Ubuntu download server. Updating the app does not replace an installed container.

See the [v0.4.0 upgrade notes](.github/release-notes/v0.4.0.md) for saved projects, chat migration, pending patches and reinstalling optional plugins.

## 📄 License

This project is open-source under the [MIT License](LICENSE).

Thanks to the **Radare2**, **Frida**, and **Termux** communities.

## 🤝 Contributors

- [@wsdx233](https://github.com/wsdx233)
- [@binx6](https://github.com/binx6)
- [@AbhiTheModder](https://github.com/AbhiTheModder)
- [@VictorGabriel7Dev](https://github.com/VictorGabriel7Dev) (Brazilian Portuguese)

See the latest list on GitHub: [Contributors](https://github.com/wsdx233/r2droid/graphs/contributors)

![Alt](https://repobeats.axiom.co/api/embed/139f78104e5784eb504768fe892367694f5ed0b7.svg "Repobeats analytics image")

---
## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=wsdx233/r2droid&type=date&legend=top-left)](https://www.star-history.com/#wsdx233/r2droid&type=date&legend=top-left)
