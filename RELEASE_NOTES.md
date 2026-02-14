# R2Droid v0.1.1 Release Notes

## 🎉 Initial Release

**R2Droid** is a modern, native Android GUI for the [Radare2](https://github.com/radareorg/radare2) reverse engineering framework. Built with Kotlin and Jetpack Compose (Material3), it brings powerful binary analysis capabilities to mobile devices.

---

## ✨ Key Features

### 📱 Project Management
- Save and restore complete analysis sessions with r2 cache
- Project metadata tracking (file size, analysis time, last opened)
- Custom project directory support
- Recent projects quick access

### 🔍 Advanced Analysis Tools

#### Hex Editor
- ⚡ High-performance virtualized rendering for files of any size
- ✏️ Real-time editing with `wx`, `w`, `wa` commands
- 🎯 Column-based selection and highlighting
- ⌨️ Custom mobile hex keyboard
- 💾 Smart LRU caching with chunked loading

#### Disassembly Viewer
- 📜 Infinite scrolling with virtualization
- 🎨 Syntax highlighting (opcodes, registers, immediates)
- ➡️ Visual jump arrows for control flow
- 📍 Function boundaries and comments
- 🔄 Navigation history (back/forward)
- 🔎 Address-based search and jump

#### Decompiler
- 📝 Pseudo-code generation via r2 plugins
- 🎨 Syntax highlighting
- 🔗 Clickable address navigation
- 🔧 Multiple decompiler backend support

### 📊 Binary Information Views
- **Overview**: Architecture, bits, OS, entry point, file size
- **Sections**: Addresses, sizes, permissions, entropy
- **Symbols**: Functions, variables, imports, exports
- **Strings**: Searchable string table
- **Functions**: Complete function list with details
- **Xrefs**: Cross-reference tracking

### 🔎 Search System
- Search bytes, strings, and instructions
- Filter by type
- Quick navigation to results
- Context display

### 💻 Integrated Terminal
- Full r2pipe terminal using termux-view
- Execute raw r2 commands
- Command history
- Custom shortcuts
- Real-time output

### ⚙️ Customization
- 🌓 Dark/Light themes (Material3)
- 🌍 English and Chinese support
- 🔤 Custom font loading
- ⚙️ Built-in `.radare2rc` editor
- 🔬 Configurable analysis depth (`aa`, `aaa`, `aaaa`)

---

## 🛠️ Technical Details

- **Architecture**: MVVM with Hilt DI
- **UI**: Jetpack Compose + Material3
- **Performance**: Virtualized lists handle unlimited file sizes
- **Memory**: Chunk-based loading with LRU cache
- **Integration**: Native r2pipe for Android + Termux components
- **Included**: Pre-compiled Radare2 v5.9.x (~70MB)

---

## 📥 Installation

1. Download the APK from the [Releases page](https://github.com/wsdx233/r2droid/releases)
2. Install on Android device (min SDK 24)
3. Grant storage permissions
4. First launch will extract r2 assets (~70MB)
5. Open a binary file to start analyzing

---

## 🔜 Roadmap

- [ ] Visual call graph (VV) viewer
- [ ] Enhanced terminal keyboard shortcuts
- [ ] ESIL debugger UI
- [ ] Improved analysis progress feedback
- [ ] Additional r2 plugin support

---

## 🙏 Acknowledgments

Special thanks to:
- [Radare2](https://github.com/radareorg/radare2) team for the amazing reverse engineering framework
- [Termux](https://github.com/termux/termux-app) project for terminal components

---

## 📝 License

This project is open-source. See [LICENSE](LICENSE) for details.

---

## 🐛 Known Issues

- First-time installation requires ~70MB asset extraction
- Some r2 commands may have limited mobile functionality
- Graph view (VV) not yet implemented
- ESIL emulation UI pending

---

## 📸 Screenshots

See [preview/preview.png](preview/preview.png) for application screenshots.

---

## 🔗 Links

- **Repository**: https://github.com/wsdx233/r2droid
- **Issues**: https://github.com/wsdx233/r2droid/issues
- **Download**: https://github.com/wsdx233/r2droid/releases/latest

