# Changelog

All notable changes to R2Droid will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [0.1.1] - 2026-02-10

### 🎉 Initial Release

This is the first public release of **R2Droid** - a modern, native Android GUI for the Radare2 reverse engineering framework. Built with Kotlin and Jetpack Compose (Material3), R2Droid brings powerful binary analysis capabilities to mobile devices.

### ✨ Core Features

#### 📱 Project Management
- **Save & Restore Projects**: Persistent project state with r2 analysis cache
- **Project Index**: Manage multiple projects with metadata (file size, analysis time, last opened)
- **Custom Project Directory**: Configure save location for your projects
- **Quick Access**: Recent projects displayed on home screen

#### 🔍 Analysis Views

**Hex Editor**
- High-performance virtualized list rendering for large files
- Real-time hex/ASCII editing with `wx`, `w`, `wa` commands
- Column-based selection and highlighting
- Custom hex keyboard for mobile editing
- LRU caching and chunked loading for optimal memory usage

**Disassembly Viewer**
- Infinite scrolling with virtualized rendering
- Syntax highlighting for opcodes, registers, and immediates
- Jump arrow visualization for control flow
- Function boundaries and comments display
- Navigation history (back/forward)
- Address-based search and jump

**Decompiler**
- Pseudo-code generation using r2 analysis plugins
- Syntax highlighting
- Clickable addresses for quick navigation
- Support for multiple decompiler backends

#### 📊 Binary Information

- **Overview**: Architecture, bits, OS, entry point, file size
- **Sections**: Load addresses, sizes, permissions, entropy
- **Symbols**: Functions, variables, imports, exports
- **Strings**: Searchable string table with addresses
- **Functions**: Complete function list with analysis details
- **Imports/Exports**: External dependencies and exposed APIs
- **Cross-references (Xrefs)**: Reference tracking between code and data

#### 🔎 Search Functionality
- Search for bytes, strings, and instructions
- Filter results by type
- Quick navigation to matches
- Context display for search results

#### 💻 Terminal Integration
- Full-featured terminal using termux-view
- Execute raw r2 commands via r2pipe
- Command history
- Custom command shortcuts
- Real-time output streaming

#### ⚙️ Settings & Customization

- **Theme**: Dark/Light mode support with Material3 design
- **Language**: English and Chinese (中文) support
- **Custom Fonts**: Load external font files for code views
- **R2 Configuration**: Built-in `.radare2rc` editor for r2 customization
- **Analysis Options**: Configure analysis depth (`aa`, `aaa`, `aaaa`)

### 🛠️ Technical Highlights

- **Architecture**: MVVM pattern with Hilt dependency injection
- **UI Framework**: Jetpack Compose with Material3 components
- **Performance**: Virtualized lists for hex and disassembly views handle files of any size
- **Memory Management**: Chunk-based loading with LRU caching
- **R2 Integration**: Native r2pipe implementation for Android
- **Termux Integration**: Terminal emulator backend and view components

### 📦 Included Components

- **Radare2**: Pre-compiled r2 binary and libraries (v5.9.x)
- **Terminal Emulator**: Based on Termux terminal components
- **R2 Libraries**: Native shared libraries (libc++, libz)

### 🐛 Known Issues

- First-time installation requires extracting ~70MB of r2 assets
- Some r2 commands may have limited functionality on mobile
- Graph view (VV) not yet implemented
- ESIL emulation UI pending

### 🔜 Coming Soon

- Visual call graph (VV) viewer
- Enhanced keyboard shortcuts for terminal
- ESIL debugger interface
- Improved analysis progress feedback
- Additional r2 plugin support

---

## Links

- [GitHub Repository](https://github.com/wsdx233/r2droid)
- [Download Latest Release](https://github.com/wsdx233/r2droid/releases/latest)
- [Report Issues](https://github.com/wsdx233/r2droid/issues)

