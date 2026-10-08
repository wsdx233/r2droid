"""Build the Termux PRoot executable and its static Android dependencies.

The caller supplies a clean, locked PRoot checkout and the Android NDK
cross-toolchain environment.  This module deliberately does not know an NDK
location or a Termux prefix: all compiler/linker tools and flags come from
``env`` and the PRoot loader is embedded by leaving ``PROOT_UNBUNDLE_LOADER``
unset.
"""
from __future__ import annotations

import shlex
import shutil
import tarfile
from pathlib import Path
from typing import Callable, Mapping, Sequence

# These are also consumed by the runtime manifest writer.  Keep the source
# coordinates here, next to the code which actually builds each dependency.
PROOT_SOURCE = "https://github.com/termux/proot"
TALLOC_VERSION = "2.5.0"
TALLOC_SOURCE = "https://www.samba.org/ftp/talloc/talloc-2.5.0.tar.gz"
TALLOC_SHA256 = "912afa237510ae542a7733998eb18a12bcda35ab6729c8e2ddb43e8d0ebab007"
SHMEM_VERSION = "0.7"
SHMEM_SOURCE = "https://github.com/termux/libandroid-shmem"
SHMEM_ARCHIVE_URL = "https://github.com/termux/libandroid-shmem/archive/refs/tags/v0.7.tar.gz"
SHMEM_SHA256 = "1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867"

Run = Callable[[list[str], Path, dict[str, str] | None], object]
Download = Callable[[str, Path, str | None], object]


def _extract(archive: Path, destination: Path) -> Path:
    """Extract an upstream tarball and return its one top-level directory."""
    destination.mkdir(parents=True, exist_ok=True)
    # Archives are fetched over HTTPS and are fixed by checksum, but reject
    # traversal nevertheless because this is build infrastructure.
    with tarfile.open(archive, mode="r:gz") as tar:
        root: str | None = None
        for member in tar.getmembers():
            name = Path(member.name)
            if name.is_absolute() or ".." in name.parts:
                raise RuntimeError(f"unsafe dependency archive member: {member.name}")
            first = name.parts[0] if name.parts else ""
            if first and root is None:
                root = first
            elif first and root != first:
                raise RuntimeError(f"dependency archive has multiple roots: {archive}")
        tar.extractall(destination)
    if root is None:
        raise RuntimeError(f"dependency archive is empty: {archive}")
    extracted = destination / root
    if not extracted.is_dir():
        raise RuntimeError(f"dependency archive did not contain directory: {archive}")
    return extracted


def _source_dir(
    archive: Path,
    destination: Path,
    download: Download,
    url: str,
    checksum: str,
) -> Path:
    """Download and unpack a dependency once in this build/cache directory."""
    destination.mkdir(parents=True, exist_ok=True)
    # Keep the archive outside the extracted tree so a failed extraction can be
    # retried without asking the network again.
    download(url, archive, checksum)
    marker = destination / ".extracted"
    if marker.is_file():
        roots = [item for item in destination.iterdir() if item.is_dir()]
        if len(roots) == 1:
            return roots[0]
        marker.unlink()
    extracted = _extract(archive, destination)
    marker.write_text(extracted.name + "\n", encoding="utf-8")
    return extracted


def _run(run: Run, args: Sequence[str], cwd: Path, env: Mapping[str, str]) -> None:
    # A fresh dict prevents make/configure from mutating the caller's mapping.
    run(list(args), cwd, dict(env))


def _build_talloc(
    work: Path,
    env: dict[str, str],
    jobs: int,
    run: Run,
    download: Download,
) -> Path:
    deps = work / "deps"
    source = _source_dir(
        work / "downloads" / "talloc-2.5.0.tar.gz",
        deps / "talloc",
        download,
        TALLOC_SOURCE,
        TALLOC_SHA256,
    )
    include = source / "bin" / "default"
    archive = work / "deps" / "lib" / "libtalloc.a"
    archive.parent.mkdir(parents=True, exist_ok=True)
    header = work / "deps" / "include" / "talloc.h"
    header.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / "talloc.h", header)
    if archive.is_file() and archive.stat().st_size:
        return archive

    # This is the cross-answers file used by Termux's package recipe.  In
    # particular, no configure probe is run with the host compiler.
    answers = source / "cross-answers.txt"
    answers.write_text(
        'Checking uname sysname type: "Linux"\n'
        'Checking uname machine type: "dontcare"\n'
        'Checking uname release type: "dontcare"\n'
        'Checking uname version type: "dontcare"\n'
        'Checking simple C program: OK\n'
        'building library support: OK\n'
        'Checking for large file support: OK\n'
        'Checking for -D_FILE_OFFSET_BITS=64: OK\n'
        'Checking for WORDS_BIGENDIAN: OK\n'
        'Checking for C99 vsnprintf: OK\n'
        'Checking for HAVE_SECURE_MKSTEMP: OK\n'
        'rpath library support: OK\n'
        '-Wl,--version-script support: FAIL\n'
        'Checking correct behavior of strtoll: OK\n'
        'Checking correct behavior of strptime: OK\n'
        'Checking for HAVE_IFACE_GETIFADDRS: OK\n'
        'Checking for HAVE_IFACE_IFCONF: OK\n'
        'Checking for HAVE_IFACE_IFREQ: OK\n'
        'Checking getconf LFS_CFLAGS: OK\n'
        'Checking for large file support without additional flags: OK\n'
        'Checking getconf large file support flags work: OK\n'
        'Checking for working strptime: OK\n'
        'Checking for HAVE_SHARED_MMAP: OK\n'
        'Checking for HAVE_MREMAP: OK\n'
        'Checking for HAVE_INCOHERENT_MMAP: OK\n',
        encoding="utf-8",
    )
    configure_env = dict(env)
    configure_env["CFLAGS"] = configure_env.get("CFLAGS", "")
    _run(
        run,
        [
            "./configure",
            "--prefix=/usr",
            "--disable-rpath",
            "--disable-python",
            "--cross-compile",
            "--cross-answers=cross-answers.txt",
        ],
        source,
        configure_env,
    )
    _run(run, ["make", "-j", str(max(1, jobs))], source, env)

    objects = sorted(include.glob("talloc*.o"))
    replace_objects = sorted((include / "lib/replace").glob("replace.c.*.o"))
    if not objects:
        raise RuntimeError(f"talloc build produced no objects in {include}")
    if not replace_objects:
        raise RuntimeError(f"talloc build produced no replacement object in {include / 'lib/replace'}")
    objects.append(replace_objects[-1])
    # Termux intentionally archives the objects itself instead of relying on
    # the shared-library install target.  Thus no libtalloc.so reaches APKs.
    _run(
        run,
        [env["AR"], "rcs", str(archive), *(str(obj) for obj in objects)],
        source,
        env,
    )
    if not archive.is_file() or archive.stat().st_size == 0:
        raise RuntimeError("talloc archive was not produced")
    return archive


def _build_shmem(
    work: Path,
    env: dict[str, str],
    jobs: int,
    run: Run,
    download: Download,
) -> tuple[Path, Path]:
    del jobs  # libandroid-shmem has one translation unit.
    source = _source_dir(
        work / "downloads" / "libandroid-shmem-v0.7.tar.gz",
        work / "deps" / "libandroid-shmem",
        download,
        SHMEM_ARCHIVE_URL,
        SHMEM_SHA256,
    )
    archive = work / "deps" / "lib" / "libandroid-shmem.a"
    header = work / "deps" / "include" / "sys" / "shm.h"
    archive.parent.mkdir(parents=True, exist_ok=True)
    header.parent.mkdir(parents=True, exist_ok=True)
    if not archive.is_file() or archive.stat().st_size == 0:
        obj = source / "shmem.o"
        compat_header = work / "deps" / "include" / "android-shmem-compat.h"
        compat_header.write_text(
            '#include <fcntl.h>\n'
            '#ifndef _PATH_TMP\n'
            '#define _PATH_TMP "/data/local/tmp/"\n'
            '#endif\n',
            encoding="utf-8",
        )
        cflags = env.get("CFLAGS", "")
        extra = "-fPIC -std=c11 -D__ANDROID_API__=24"
        merged = f"{cflags} {extra}".strip()
        compile_env = dict(env)
        compile_env["CFLAGS"] = merged
        _run(
            run,
            [env["CC"], *shlex.split(merged), "-include", str(compat_header), "-I", str(source), "-c", "shmem.c", "-o", str(obj)],
            source,
            compile_env,
        )
        _run(run, [env["AR"], "rcs", str(archive), str(obj)], source, env)
    upstream_header = source / "shm.h"
    if not upstream_header.is_file():
        raise RuntimeError("libandroid-shmem build did not provide sys/shm.h")
    shutil.copyfile(upstream_header, header)
    return archive, header


def build_proot(
    source: Path,
    work: Path,
    env: dict[str, str],
    jobs: int,
    run: Run,
    download: Download,
) -> Path:
    """Build embedded-loader ARM64 PRoot and return its stripped executable."""
    source = Path(source).resolve()
    work = Path(work).resolve()
    if not (source / "src" / "GNUmakefile").is_file():
        raise ValueError(f"not a PRoot source checkout: {source}")
    work.mkdir(parents=True, exist_ok=True)
    build_env = dict(env)
    # An empty make variable also suppresses an inherited host value (the
    # callback merges our environment over the host's).
    build_env["PROOT_UNBUNDLE_LOADER"] = ""
    talloc = _build_talloc(work, build_env, jobs, run, download)
    _shmem_archive, header = _build_shmem(work, build_env, jobs, run, download)
    ashmem_source = source / "src/extension/ashmem_memfd/ashmem_memfd.c"
    if ashmem_source.is_file():
        ashmem_text = ashmem_source.read_text(encoding="utf-8")
        if "#include <string.h>" not in ashmem_text:
            ashmem_source.write_text(
                ashmem_text.replace("#include <stdlib.h>", "#include <stdlib.h>\n#include <string.h>", 1),
                encoding="utf-8",
            )

    libdir = talloc.parent
    include_dir = header.parent.parent
    cflags = build_env.get("CFLAGS", "")
    include_flags = f"-I{shlex.quote(str(include_dir))} -DARG_MAX=131072 -D__ANDROID_API__=24"
    build_env["CFLAGS"] = f"{cflags} {include_flags}".strip()
    # GNUmakefile adds -ltalloc/-landroid-shmem.  These private directories
    # contain archives only, preventing accidental host .so selection.  The
    # extra group also supplies symbols used by the static shmem implementation
    # (liblog and libandroid are NDK system libraries).
    ldflags = build_env.get("LDFLAGS", "")
    static_group = (
        f"-L{shlex.quote(str(libdir))} -Wl,--start-group -ltalloc -landroid-shmem "
        "-llog -landroid -Wl,--end-group"
    )
    build_env["LDFLAGS"] = f"{ldflags} {static_group}".strip()
    # The freestanding embedded loader has a separate linker invocation and
    # upstream does not feed LDFLAGS into it.  Propagate the Android page-size
    # flags there too, without importing PRoot's libc/static dependency group.
    page_flags = [
        flag for flag in shlex.split(ldflags)
        if "max-page-size=" in flag or "common-page-size=" in flag
    ]
    build_env["LOADER_LDFLAGS"] = " ".join(page_flags)
    _run(
        run,
        [
            "make", "-C", "src", "-j", str(max(1, jobs)),
            "PROOT_WITH_LIBANDROID_SHMEM=true", "PROOT_UNBUNDLE_LOADER=",
            f"GIT=git -C {shlex.quote(str(source))}",
        ],
        source,
        build_env,
    )
    built = source / "src" / "proot"
    if not built.is_file() or built.stat().st_size == 0:
        raise RuntimeError(f"PRoot build did not produce {built}")

    output = work / "proot"
    # Strip with the NDK tool supplied by the caller, not a host strip.  The
    # output copy leaves the locked checkout reusable for another cache key.
    _run(run, [build_env["STRIP"], "-o", str(output), str(built)], source, build_env)
    if not output.is_file() or output.stat().st_size == 0:
        raise RuntimeError("stripped PRoot executable was not produced")
    output.chmod(output.stat().st_mode | 0o111)
    return output


__all__ = [
    "PROOT_SOURCE",
    "TALLOC_VERSION",
    "TALLOC_SOURCE",
    "TALLOC_SHA256",
    "SHMEM_VERSION",
    "SHMEM_SOURCE",
    "SHMEM_ARCHIVE_URL",
    "SHMEM_SHA256",
    "build_proot",
]
