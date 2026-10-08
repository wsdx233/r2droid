"""Build the locked radare2 Android runtime and native plugins.

The public entry point is intentionally callback based.  Source materialization,
command execution and archive creation belong to ``build_native_runtime``;
keeping those operations injected makes this module usable by the release
builder without introducing another network or subprocess implementation.
"""
from __future__ import annotations

import os
import re
import shlex
import shutil
import tarfile
from pathlib import Path
from typing import Callable, Mapping, Sequence


Run = Callable[..., object]
Checkout = Callable[[dict, Path], Path]
ArchiveDirectory = Callable[[Path, Path, str], None]

# This is the prefix used by radare2's upstream sys/android-build.sh.  The
# prefix is part of the Android runtime layout, not a source/version lock.
_R2_PREFIX = "/data/data/org.radare.radare2installer/radare2"


def _run(run: Run, args: Sequence[str], cwd: Path, env: Mapping[str, str]) -> None:
    """Invoke the caller's checked subprocess helper with a private env copy."""
    run([str(arg) for arg in args], cwd, dict(env))


def _require_directory(path: Path, description: str) -> Path:
    path = Path(path).resolve()
    if not path.is_dir():
        raise RuntimeError(f"missing {description}: {path}")
    return path


def _prepare_compatibility_tools(work: Path, env: Mapping[str, str]) -> Path:
    """Provide tools expected by old upstream Android scripts.

    ``android-build.sh`` checks for ``pax`` even though modern build hosts no
    longer ship it.  This small adapter emits a tar stream with the same
    invocation used by that script.  The script also names the old NDK strip
    binary; expose that name while still using the NDK tool selected by the
    caller.
    """
    tools = work / "tool-bin"
    tools.mkdir(parents=True, exist_ok=True)
    pax = tools / "pax"
    pax.write_text(
        "#!/bin/sh\n"
        "set -eu\n"
        "if [ \"${1:-}\" = -w ]; then shift; fi\n"
        "# android-build.sh uses pax -w data; GNU tar preserves links, modes, and long paths.\n"
        "exec tar -cf - \"$@\"\n",
        encoding="utf-8",
    )
    pax.chmod(0o755)

    strip = env.get("STRIP")
    if strip:
        old_strip = tools / "aarch64-linux-android-strip"
        old_strip.write_text(
            "#!/bin/sh\nexec " + shlex.quote(str(strip)) + " \"$@\"\n",
            encoding="utf-8",
        )
        old_strip.chmod(0o755)
    # Only install-path queries are meaningful on the build host; this is not
    # a replacement for running the generated ARM64 radare2 executable.
    radare2 = tools / "radare2"
    radare2.write_text(
        "#!/bin/sh\n"
        "set -eu\n"
        "query=\"\"\n"
        "if [ \"${1:-}\" = -H ]; then\n"
        "  query=\"${2:-}\"\n"
        "elif [ \"${1:-}\" != \"${1#-H}\" ]; then\n"
        "  query=\"${1#-H}\"\n"
        "fi\n"
        "if [ -n \"$query\" ]; then\n"
        "  case \"$query\" in\n"
        f"    R2_LIBR_PLUGINS) printf '%s\\n' '{_R2_PREFIX}/lib/radare2/plugins' ;;\n"
        f"    R2_LIBDIR) printf '%s\\n' '{_R2_PREFIX}/lib' ;;\n"
        f"    R2_INCDIR) printf '%s\\n' '{_R2_PREFIX}/include' ;;\n"
        "    *) exit 1 ;;\n"
        "  esac\n"
        "  exit 0\n"
        "fi\n"
        "echo 'unsupported build-time radare2 query' >&2; exit 1\n",
        encoding="utf-8",
    )
    radare2.chmod(0o755)
    return tools


def _safe_extract(archive: Path, destination: Path) -> None:
    """Extract a build-produced tarball while retaining symlinks safely."""
    destination.mkdir(parents=True, exist_ok=True)
    with tarfile.open(archive, mode="r:*") as tar:
        members = tar.getmembers()
        for member in members:
            name = Path(member.name)
            if not member.name or name.is_absolute() or ".." in name.parts:
                raise RuntimeError(f"unsafe radare2 archive member: {member.name!r}")
            if member.issym() or member.islnk():
                target = Path(member.linkname)
                if target.is_absolute() or ".." in target.parts:
                    raise RuntimeError(f"unsafe radare2 archive link: {member.name!r}")
        # All names and link targets have been checked above.  extractall is
        # used rather than copyfile so mode bits and symlinks survive intact.
        tar.extractall(destination)


def _archive_candidates(source: Path) -> list[Path]:
    return sorted(
        path for path in source.glob("*-android-aarch64.tar.gz")
        if path.is_file() and not path.is_symlink()
    )


def _find_runtime_root(extracted: Path) -> Path:
    candidates = [
        path
        for path in extracted.rglob("radare2")
        if path.is_dir() and (path / "bin" / "r2").is_file()
    ]
    if not candidates:
        # A future upstream archive may already use the desired root name.
        direct = extracted / "radare2"
        if direct.is_dir() and (direct / "bin" / "r2").is_file():
            return direct
        raise RuntimeError(
            "radare2 Android archive contains no usable radare2/bin/r2 runtime"
        )
    return min(candidates, key=lambda path: (len(path.parts), str(path)))


def _copy_tree(source: Path, destination: Path) -> None:
    if destination.exists() or destination.is_symlink():
        if destination.is_dir() and not destination.is_symlink():
            shutil.rmtree(destination)
        else:
            destination.unlink()
    shutil.copytree(source, destination, symlinks=True)


def _build_radare2(
    source: Path,
    work: Path,
    env: Mapping[str, str],
    ndk: Path,
    jobs: int,
    run: Run,
) -> Path:
    """Run upstream's Android build and return its normalized runtime tree."""
    script = source / "sys" / "android-build.sh"
    if not script.is_file():
        raise RuntimeError(f"locked radare2 source has no sys/android-build.sh: {source}")

    build_env = dict(env)
    tools = _prepare_compatibility_tools(work, build_env)
    build_env["PATH"] = f"{tools}:{build_env.get('PATH', os.environ.get('PATH', ''))}"
    # The Android script assumes a pre-generated checkout and calls the old
    # mrproper target, which no longer exists in current radare2 releases.
    # Its r2r regression binary is not part of the Android runtime and uses
    # APIs newer than minSdk 24, so omit it from this cross build.
    binr_makefile = source / "binr" / "Makefile"
    binr_text = binr_makefile.read_text(encoding="utf-8")
    if "BINS=r2r " in binr_text:
        binr_makefile.write_text(binr_text.replace("BINS=r2r ", "BINS=", 1), encoding="utf-8")
    script_text = script.read_text(encoding="utf-8")
    if "${MAKE} mrproper" in script_text:
        script.write_text(script_text.replace("${MAKE} mrproper", "${MAKE} clean", 1), encoding="utf-8")
    # NDK suppresses android-build.sh's android-shell re-exec.  Keep both names
    # because different locked radare2 revisions inspect different variables.
    build_env.update(
        {
            "NDK": str(ndk),
            "ANDROID_NDK": str(ndk),
            "ANDROID_NDK_ROOT": str(ndk),
            "NDK_ARCH": "aarch64",
            "ANDROID_API": "24",
            "API": "24",
            "STATIC_BUILD": "0",
        }
    )

    # Never mistake an archive left by a prior interrupted build for this one.
    for stale in _archive_candidates(source):
        stale.unlink()
    _run(run, ["sh", "sys/android-build.sh", "arm64"], source, build_env)
    archives = _archive_candidates(source)
    if not archives:
        raise RuntimeError(
            "radare2 Android build completed without an *-android-aarch64.tar.gz archive"
        )
    archive = archives[-1]
    extracted = work / "radare2-extracted"
    if extracted.exists():
        shutil.rmtree(extracted)
    _safe_extract(archive, extracted)
    return _find_runtime_root(extracted)


def _include_directories(source: Path) -> list[Path]:
    candidates = [source / "libr" / "include", source / "include"]
    candidates.extend(
        path for path in source.glob("**/include") if path.is_dir()
    )
    result: list[Path] = []
    seen: set[Path] = set()
    for path in candidates:
        path = path.resolve()
        if path.is_dir() and path not in seen:
            seen.add(path)
            result.append(path)
    return result


def _library_files(source: Path) -> list[tuple[str, Path]]:
    """Find public shared radare2 libraries, never private static dependencies.

    Linking every archive from the checkout pulls otezip's hidden ``inflate``
    into r2ghidra while ``inflateInit_`` still resolves to Android's libz.
    Their private stream states are incompatible.  Plugins must use the
    shared radare2 API and let their own zlib dependency resolve as a unit.
    """
    result: list[tuple[str, Path]] = []
    seen: set[tuple[str, Path]] = set()
    for path in source.rglob("libr_*.so*"):
        if path.is_file() and ".so" in path.name:
            item = (path.name[3:].split(".so", 1)[0], path.parent.resolve())
            if item not in seen:
                seen.add(item)
                result.append(item)
    return sorted(result)


def _stage_r2_library_directory(source: Path, work: Path) -> Path:
    """Flatten radare2's per-library build directories for Meson's find_library."""
    library_directory = work / "r2-libdir"
    if library_directory.exists():
        shutil.rmtree(library_directory)
    library_directory.mkdir(parents=True, exist_ok=True)
    for name, directory in _library_files(source):
        candidates = sorted(
            path for path in directory.glob(f"lib{name}.so*")
            if path.is_file() or path.is_symlink()
        )
        if not candidates:
            continue
        shared = next((path for path in candidates if path.name == f"lib{name}.so"), None)
        source_file = shared or candidates[0]
        destination = library_directory / f"lib{name}.so"
        if destination.exists():
            continue
        shutil.copy2(source_file.resolve(), destination)
    return library_directory


def _write_r2_pkgconfig(source: Path, work: Path, version: str) -> Path:
    """Make a hermetic pkg-config view for plugins.

    Some radare2 revisions generate .pc files only during ``make install`` and
    then remove them from the Android archive.  Recreating metadata from the
    just-built source tree lets both old and new plugin Meson files use the
    same dependency mechanism without searching host pkg-config directories.
    """
    pkgconfig = work / "r2-pkgconfig"
    if pkgconfig.exists():
        shutil.rmtree(pkgconfig)
    pkgconfig.mkdir(parents=True, exist_ok=True)
    include_dirs = _include_directories(source)
    libs = _library_files(source)
    include_flags = " ".join(f"-I{shlex.quote(str(path))}" for path in include_dirs)
    all_lib_flags: list[str] = []
    for name, directory in libs:
        all_lib_flags.extend((f"-L{shlex.quote(str(directory))}", f"-l{name}"))
    all_libs = " ".join(all_lib_flags)

    # Generate every package from the locked ARM64 build; copying tracked .pc
    # files could reintroduce a developer's host prefix or library directory.
    package_names = {"r_core", "r_util", "r_cons", "r_io", "r_anal", "r_bin", "r_arch"}
    package_names.update(name for name, _directory in libs)
    for name in sorted(package_names):
        destination = pkgconfig / f"{name}.pc"
        if destination.exists():
            continue
        destination.write_text(
            f"prefix={source}\n"
            f"exec_prefix=${{prefix}}\n"
            f"includedir={include_dirs[0] if include_dirs else source}\n"
            f"libdir={source}\n\n"
            f"Name: {name}\nVersion: {version or '0'}\n"
            f"Description: radare2 build dependency\n"
            f"Cflags: {include_flags}\n"
            f"Libs: {all_libs}\n",
            encoding="utf-8",
        )
    return pkgconfig


def _meson_string(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


def _write_meson_files(
    source: Path,
    work: Path,
    env: Mapping[str, str],
    pkgconfig: Path,
) -> tuple[Path, Path]:
    """Write ARM64 cross and host-native Meson files for plugin generators."""
    cross = work / "android-arm64-cross.txt"
    c = env.get("CC", "cc")
    cpp = env.get("CXX", "c++")
    ar = env.get("AR", "ar")
    strip = env.get("STRIP", "strip")
    pkg_config = shutil.which("pkg-config")
    c_args = shlex.split(env.get("CFLAGS", "")) + ["-D__ANDROID_API__=24"]
    cpp_args = shlex.split(env.get("CXXFLAGS", env.get("CFLAGS", ""))) + [
        "-D__ANDROID_API__=24"
    ]
    link_args = shlex.split(env.get("LDFLAGS", ""))
    includes = _include_directories(source)
    c_args.extend(f"-I{path}" for path in includes)
    cpp_args.extend(f"-I{path}" for path in includes)
    cross.write_text(
        "[binaries]\n"
        f"c = {_meson_string(c)}\n"
        f"cpp = {_meson_string(cpp)}\n"
        f"ar = {_meson_string(ar)}\n"
        f"strip = {_meson_string(strip)}\n"
        + (f"pkg-config = {_meson_string(pkg_config)}\n" if pkg_config else "")
        + "[host_machine]\n"
        "system = 'android'\n"
        "cpu_family = 'aarch64'\n"
        "cpu = 'aarch64'\n"
        "endian = 'little'\n"
        "[properties]\n"
        "needs_exe_wrapper = true\n"
        f"c_args = [{', '.join(_meson_string(item) for item in c_args)}]\n"
        f"cpp_args = [{', '.join(_meson_string(item) for item in cpp_args)}]\n"
        f"c_link_args = [{', '.join(_meson_string(item) for item in link_args)}]\n"
        f"cpp_link_args = [{', '.join(_meson_string(item) for item in link_args)}]\n",
        encoding="utf-8",
    )
    native = work / "host-native.txt"
    host_binaries = {
        "c": shutil.which("cc") or shutil.which("gcc"),
        "cpp": shutil.which("c++") or shutil.which("g++"),
        "ar": shutil.which("ar"),
        "strip": shutil.which("strip"),
    }
    native.write_text(
        "[binaries]\n"
        + "".join(
            f"{name} = {_meson_string(path)}\n"
            for name, path in host_binaries.items()
            if path
        ),
        encoding="utf-8",
    )
    return cross, native


def _build_host_sleighc(source: Path, work: Path, run: Run) -> Path:
    """Build the host helper required to generate r2ghidra Sleigh data."""
    ghidra = source / "subprojects" / "ghidra-native"
    meson_text = (ghidra / "meson.build").read_text(encoding="utf-8")
    relative_sources: list[str] = []
    for variable in ("base_sources", "decompiler_sources", "slgh_sources", "sleigh_compiler_sources"):
        match = re.search(
            rf"(?ms)^{variable}\s*=\s*\[(.*?)^\]",
            meson_text,
        )
        if match is None:
            raise RuntimeError(f"ghidra-native Meson file has no {variable} list")
        relative_sources.extend(re.findall(r"'([^']+\.cc)'", match.group(1)))
    relative_sources = [
        path for path in dict.fromkeys(relative_sources)
        if path != "src/decompiler/string_ghidra.cc"
    ]
    sources = [ghidra / relative for relative in relative_sources]
    missing = [path for path in sources if not path.is_file()]
    if missing:
        raise RuntimeError("ghidra-native is missing sleighc source(s): " + ", ".join(map(str, missing)))
    cxx = shutil.which("c++") or shutil.which("g++")
    if cxx is None:
        raise RuntimeError("host C++ compiler is required to build r2ghidra sleighc")
    output = work / "host-tools" / "sleighc"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.unlink(missing_ok=True)
    _run(
        run,
        [
            cxx,
            "-std=c++14",
            "-O2",
            "-DNDEBUG",
            f"-I{ghidra / 'src' / 'decompiler'}",
            *(str(path) for path in sources),
            "-lz",
            "-o",
            str(output),
        ],
        work,
        {},
    )
    if not output.is_file():
        raise RuntimeError(f"host sleighc build produced no executable: {output}")
    output.chmod(0o755)
    return output


def _build_host_r2dec_tools(source: Path, work: Path, run: Run) -> tuple[Path, Path]:
    """Build host qjsc and modjs_gen used by r2dec's cross build."""
    quickjs = source / "subprojects" / "libquickjs"
    quickjs_meson = quickjs / "meson.build"
    meson_text = quickjs_meson.read_text(encoding="utf-8")
    match = re.search(r"(?ms)^sources\s*=\s*\[(.*?)^\]", meson_text)
    if match is None:
        raise RuntimeError("quickjs Meson file has no sources list")
    quickjs_sources = [quickjs / name for name in re.findall(r"'([^']+\.c)'", match.group(1))]
    quickjs_sources += [source / "tools" / "qjsc_mod.c"]
    missing = [path for path in quickjs_sources if not path.is_file()]
    modjs_source = source / "tools" / "modjs_gen.c"
    if not modjs_source.is_file():
        missing.append(modjs_source)
    if missing:
        raise RuntimeError("r2dec is missing host-tool source(s): " + ", ".join(map(str, missing)))
    cc = shutil.which("cc") or shutil.which("gcc")
    if cc is None:
        raise RuntimeError("host C compiler is required to build r2dec generators")
    output = work / "host-tools"
    output.mkdir(parents=True, exist_ok=True)
    qjsc = output / "r2dec-qjsc"
    modjs_gen = output / "r2dec-modjs-gen"
    qjsc.unlink(missing_ok=True)
    modjs_gen.unlink(missing_ok=True)
    _run(
        run,
        [
            cc,
            "-std=c11",
            "-O2",
            "-D_GNU_SOURCE=1",
            "-funsigned-char",
            f"-I{quickjs}",
            *(str(path) for path in quickjs_sources),
            "-pthread",
            "-ldl",
            "-lm",
            "-latomic",
            "-o",
            str(qjsc),
        ],
        work,
        {},
    )
    _run(run, [cc, "-O2", str(modjs_source), "-o", str(modjs_gen)], work, {})
    for tool in (qjsc, modjs_gen):
        if not tool.is_file():
            raise RuntimeError(f"r2dec host tool build produced no executable: {tool}")
        tool.chmod(0o755)
    return qjsc, modjs_gen


def _use_host_r2dec_tools(source: Path, tools: tuple[Path, Path]) -> None:
    """Use real host generators instead of trying to execute ARM64 build tools."""
    meson = source / "meson.build"
    text = meson.read_text(encoding="utf-8")
    for name, executable in zip(("qjsc", "modjs_gen"), tools):
        replacement = f"{name} = find_program({_meson_string(str(executable))})\n"
        if replacement in text:
            continue
        pattern = rf"(?ms)^{name} = executable\('{name}',.*?^\)\n"
        text, count = re.subn(pattern, lambda _: replacement, text, count=1)
        if count != 1:
            raise RuntimeError(f"unsupported r2dec Meson layout: {name} generator is not exposed")
    meson.write_text(text, encoding="utf-8")


def _use_host_sleighc(source: Path, host_sleighc: Path) -> None:
    """Make r2ghidra's data-generation command use the host helper."""
    meson = source / "meson.build"
    text = meson.read_text(encoding="utf-8")
    needle = "sleighc_exe = ghidra.get_variable('sleighc_exe')"
    replacement = f"sleighc_exe = {_meson_string(str(host_sleighc))}"
    if needle not in text:
        if replacement in text:
            return
        raise RuntimeError("unsupported r2ghidra Meson layout: sleighc command is not exposed")
    meson.write_text(text.replace(needle, replacement, 1), encoding="utf-8")


def _build_plugin(
    name: str,
    source: Path,
    work: Path,
    env: Mapping[str, str],
    jobs: int,
    run: Run,
    r2_source: Path,
    pkgconfig: Path,
    r2_libdir: Path,
    cross: Path,
    native: Path,
    version: str,
) -> Path:
    if not (source / "meson.build").is_file():
        raise RuntimeError(f"locked {name} source has no meson.build: {source}")
    build = work / "plugins-build" / name
    install = work / "plugins-install" / name
    if build.exists():
        shutil.rmtree(build)
    if install.exists():
        shutil.rmtree(install)
    build.parent.mkdir(parents=True, exist_ok=True)
    install.mkdir(parents=True, exist_ok=True)
    plugin_env = dict(env)
    compatibility_tools = _prepare_compatibility_tools(work, plugin_env)
    plugin_env["PATH"] = f"{compatibility_tools}:{plugin_env.get('PATH', os.environ.get('PATH', ''))}"
    # Only this generated directory and metadata from the locked r2 checkout
    # are visible to pkg-config; inherited host directories are excluded.
    plugin_env["PKG_CONFIG_LIBDIR"] = str(pkgconfig)
    plugin_env["PKG_CONFIG_PATH"] = str(pkgconfig)
    plugin_env["R2_PREFIX"] = _R2_PREFIX
    plugin_env["R2_VERSION"] = version
    # Keep a source include path for projects that use plain compiler checks.
    plugin_env["C_INCLUDE_PATH"] = os.pathsep.join(
        str(path) for path in _include_directories(r2_source)
    )
    if name == "r2ghidra":
        _run(run, ["meson", "subprojects", "download"], source, plugin_env)
        host_sleighc = _build_host_sleighc(source, work, run)
        _use_host_sleighc(source, host_sleighc)
    if name == "r2dec":
        _run(run, ["meson", "subprojects", "download"], source, plugin_env)
        _use_host_r2dec_tools(source, _build_host_r2dec_tools(source, work, run))
    prefix = _R2_PREFIX
    setup_args = [
        "meson",
        "setup",
        str(build),
        str(source),
        "--cross-file",
        str(cross),
        "--native-file",
        str(native),
        "--prefix",
        prefix,
        "--libdir",
        "lib",
        "-Dbuildtype=release",
    ]
    if name == "r2dec":
        r2_include = r2_source / "libr" / "include"
        if not r2_include.is_dir():
            raise RuntimeError(f"radare2 source has no libr/include directory: {r2_include}")
        setup_args.extend(
            [
                "-Dr2_incdir=" + str(r2_include),
                "-Dr2_libdir=" + str(r2_libdir),
                "-Dr2_plugdir=" + f"{prefix}/lib/radare2/plugins",
                "-Dstandalone=false",
            ]
        )
    _run(
        run,
        setup_args,
        work,
        plugin_env,
    )
    _run(run, ["meson", "compile", "-C", str(build), "-j", str(max(1, jobs))], work, plugin_env)
    _run(
        run,
        [
            "meson",
            "install",
            "-C",
            str(build),
            "--no-rebuild",
            "--destdir",
            str(install),
        ],
        work,
        plugin_env,
    )
    return install


def _copy_plugin_item(source: Path, destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if source.is_dir() and not source.is_symlink():
        _copy_tree(source, destination)
    elif source.is_symlink():
        destination.unlink(missing_ok=True)
        destination.symlink_to(os.readlink(source))
    else:
        shutil.copy2(source, destination)


def _collect_plugins(installs: Sequence[Path], destination: Path) -> None:
    """Flatten Meson prefix installs into the runtime's plugin directory."""
    destination.mkdir(parents=True, exist_ok=True)
    core_candidates = (
        ("r2ghidra", "libcore_r2ghidra.so", "core_r2ghidra.so", "libcore_ghidra.so", "core_ghidra.so"),
        ("r2dec", "libcore_pdd.so", "core_pdd.so"),
    )
    candidate_names = {name for _, *names in core_candidates for name in names}
    found: dict[str, Path] = {}
    data: dict[str, Path] = {}
    for install in installs:
        for path in install.rglob("*"):
            if path.is_file() and path.name in candidate_names:
                found[path.name] = path
            if path.is_dir() and not path.is_symlink():
                lowered = path.name.lower()
                if (
                    path.name == "r2ghidra_sleigh"
                    or "sleigh" in lowered
                    or lowered in {"r2dec", "r2dec-js", "pdd"}
                ):
                    data[path.name] = path
    missing = [label for label, *names in core_candidates if not any(name in found for name in names)]
    if missing:
        raise RuntimeError(
            "plugin installation missing required native artifact(s): "
            + ", ".join(missing)
        )
    if "r2ghidra_sleigh" not in data:
        raise RuntimeError("plugin installation missing r2ghidra_sleigh data directory")
    selected: dict[str, Path] = {}
    for _, *names in core_candidates:
        name = next(name for name in names if name in found)
        selected[name] = found[name]
    for name, path in selected.items():
        _copy_plugin_item(path, destination / name)
    for name, path in data.items():
        _copy_plugin_item(path, destination / name)

def _needs_library(root: Path, soname: bytes) -> bool:
    """Detect a DT_NEEDED soname without invoking a host readelf binary."""
    needle = soname + b"\0"
    for path in root.rglob("*.so"):
        if path.is_file():
            try:
                if needle in path.read_bytes():
                    return True
            except OSError:
                continue
    return False


def _copy_libz_if_needed(runtime: Path, plugins: Path, ndk: Path, output: Path) -> None:
    if not (_needs_library(runtime, b"libz.so.1") or _needs_library(plugins, b"libz.so.1")):
        return
    candidates = list(runtime.rglob("libz.so.1")) + list(plugins.rglob("libz.so.1"))
    candidates.extend(ndk.rglob("libz.so.1"))
    source = next((path for path in candidates if path.is_file()), None)
    if source is None:
        raise RuntimeError("runtime links libz.so.1 but no ARM64 libz.so.1 is available")
    (output / "libs").mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, output / "libs" / "libz.so.1")



def _copy_libcxx(ndk: Path, output: Path) -> None:
    candidates = [
        ndk
        / "toolchains"
        / "llvm"
        / "prebuilt"
        / "linux-x86_64"
        / "sysroot"
        / "usr"
        / "lib"
        / "aarch64-linux-android"
        / "libc++_shared.so",
        ndk
        / "toolchains"
        / "llvm"
        / "prebuilt"
        / "linux-x86_64"
        / "sysroot"
        / "usr"
        / "lib"
        / "aarch64-linux-android"
        / "24"
        / "libc++_shared.so",
    ]
    source = next((path for path in candidates if path.is_file()), None)
    if source is None:
        raise RuntimeError(
            "Android NDK ARM64 sysroot is missing libc++_shared.so (checked: "
            + ", ".join(str(path) for path in candidates)
            + ")"
        )
    libraries = output / "libs"
    libraries.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, libraries / "libc++_shared.so")


def build_radare2(
    lock: dict,
    work: Path,
    output: Path,
    ndk: Path,
    env: dict[str, str],
    jobs: int,
    run: Run,
    checkout: Checkout,
    archive_directory: ArchiveDirectory,
) -> None:
    """Build and archive radare2, r2ghidra and r2dec for Android ARM64."""
    work = Path(work).resolve()
    output = Path(output).resolve()
    ndk = Path(ndk).resolve()
    work.mkdir(parents=True, exist_ok=True)
    output.mkdir(parents=True, exist_ok=True)
    try:
        r2_info = lock["radare2"]
        ghidra_info = lock["plugins"]["r2ghidra"]
        r2dec_info = lock["plugins"]["r2dec"]
    except (KeyError, TypeError) as exc:
        raise RuntimeError("source lock lacks radare2, r2ghidra, or r2dec entries") from exc

    sources = work / "sources"
    sources.mkdir(parents=True, exist_ok=True)
    r2_source = _require_directory(checkout(r2_info, sources / "radare2"), "radare2 checkout")
    ghidra_source = _require_directory(
        checkout(ghidra_info, sources / "r2ghidra"), "r2ghidra checkout"
    )
    r2dec_source = _require_directory(
        checkout(r2dec_info, sources / "r2dec"), "r2dec checkout"
    )

    runtime_source = _build_radare2(r2_source, work, env, ndk, jobs, run)
    runtime = work / "runtime" / "radare2"
    _copy_tree(runtime_source, runtime)
    if not (runtime / "bin" / "r2").is_file():
        raise RuntimeError(f"normalized radare2 runtime has no executable: {runtime / 'bin' / 'r2'}")
    output_core = output / "r2.tar.gz"
    output_core.unlink(missing_ok=True)
    archive_directory(runtime, output_core, "radare2")

    version = str(r2_info.get("version", "0")) if isinstance(r2_info, dict) else "0"
    pkgconfig = _write_r2_pkgconfig(r2_source, work, version)
    r2_libdir = _stage_r2_library_directory(r2_source, work)
    cross, native = _write_meson_files(r2_source, work, env, pkgconfig)
    install_ghidra = _build_plugin(
        "r2ghidra",
        ghidra_source,
        work,
        env,
        jobs,
        run,
        r2_source,
        pkgconfig,
        r2_libdir,
        cross,
        native,
        version,
    )
    install_r2dec = _build_plugin(
        "r2dec",
        r2dec_source,
        work,
        env,
        jobs,
        run,
        r2_source,
        pkgconfig,
        r2_libdir,
        cross,
        native,
        version,
    )
    plugin_stage = work / "runtime" / "plugins"
    if plugin_stage.exists():
        shutil.rmtree(plugin_stage)
    _collect_plugins((install_ghidra, install_r2dec), plugin_stage)
    output_plugins = output / "r2dir.tar.gz"
    output_plugins.unlink(missing_ok=True)
    archive_directory(plugin_stage, output_plugins, "plugins")

    _copy_libcxx(ndk, output)
    _copy_libz_if_needed(runtime, plugin_stage, ndk, output)


__all__ = ["build_radare2"]
