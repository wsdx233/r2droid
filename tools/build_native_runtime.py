#!/usr/bin/env python3
"""Refresh upstream source locks or stage ARM64 assets for local Gradle and release CI."""
from __future__ import annotations

import argparse
import fcntl
import gzip
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tarfile
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NDK_VERSION = "28.2.13676358"
ANDROID_API = 24
ABI = "arm64-v8a"


def log(message: str) -> None:
    print(f"[native] {message}", flush=True)


def run(args: list[str], cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    log(shlex.join(map(str, args)))
    subprocess.run(args, cwd=cwd, env=os.environ | (env or {}), check=True)


def sha256(path: Path) -> str:
    with path.open("rb") as handle:
        return hashlib.file_digest(handle, "sha256").hexdigest()


def json_write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".part")
    temporary.write_text(json.dumps(value, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def request(url: str):
    headers = {"User-Agent": "R2Droid-native-builder"}
    if urllib.parse.urlparse(url).hostname == "api.github.com":
        headers["Accept"] = "application/vnd.github+json"
        token = os.environ.get("GITHUB_TOKEN")
        if token:
            headers["Authorization"] = f"Bearer {token}"
    return urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=120)


def download(url: str, destination: Path, sha256: str | None = None) -> Path:
    def valid(path: Path) -> bool:
        return path.is_file() and path.stat().st_size > 0 and (sha256 is None or globals()["sha256"](path) == sha256)

    if valid(destination):
        return destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    log(f"Downloading {url}")
    try:
        with request(url) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        if not valid(temporary):
            raise RuntimeError(f"download checksum mismatch: {url}")
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return destination


def api(path: str) -> dict:
    with request(f"https://api.github.com/repos/{path}") as response:
        return json.load(response)


def source(repository: str, ref: str) -> dict:
    commit = api(f"{repository}/commits/{urllib.parse.quote(ref, safe='')}")["sha"]
    return {"source": f"https://github.com/{repository}", "ref": ref, "commit": commit}


def resolve_sources(path: Path | None, refresh: bool = False) -> dict:
    if path is not None and not refresh:
        # Explicit locks are immutable inputs; a missing lock must fail offline.
        lock = json.loads(path.read_text(encoding="utf-8"))
    else:
        release = api("radareorg/radare2/releases/latest")
        if release["draft"] or release["prerelease"]:
            raise RuntimeError("radare2 latest endpoint did not return a stable release")
        lock = {
            "schema": 1,
            "abi": ABI,
            "build": {"ndk": NDK_VERSION, "androidApi": ANDROID_API},
            "radare2": source("radareorg/radare2", release["tag_name"]),
            "proot": source("termux/proot", "master"),
            "plugins": {
                "r2ghidra": source("radareorg/r2ghidra", "master"),
                "r2dec": source("wargio/r2dec-js", "master"),
            },
        }
        lock["radare2"]["version"] = release["tag_name"].removeprefix("v")
    if lock["schema"] != 1 or lock["abi"] != ABI or lock["build"] != {"ndk": NDK_VERSION, "androidApi": ANDROID_API}:
        raise RuntimeError("source lock has unsupported schema, ABI, NDK, or Android API")
    expected = [
        (lock["proot"], "termux/proot"), (lock["radare2"], "radareorg/radare2"),
        (lock["plugins"]["r2ghidra"], "radareorg/r2ghidra"),
        (lock["plugins"]["r2dec"], "wargio/r2dec-js"),
    ]
    for info, repository in expected:
        if info["source"] != f"https://github.com/{repository}" or not re.fullmatch(r"[0-9a-f]{40}", info["commit"]):
            raise RuntimeError(f"invalid immutable source lock: {repository}")
    if path is not None and refresh:
        json_write(path, lock)
    return lock


def checkout(info: dict, directory: Path) -> Path:
    marker = directory / ".source-commit"
    if marker.is_file() and marker.read_text().strip() == info["commit"]:
        return directory
    if directory.exists():
        shutil.rmtree(directory)
    directory.mkdir(parents=True)
    run(["git", "init", "-q", str(directory)])
    run(["git", "-C", str(directory), "fetch", "--depth", "1", info["source"] + ".git", info["commit"]])
    run(["git", "-C", str(directory), "checkout", "--detach", "-q", "FETCH_HEAD"])
    marker.write_text(info["commit"] + "\n")
    return directory


def ndk_environment(ndk: Path, work: Path) -> dict[str, str]:
    revision = re.search(r"^Pkg.Revision\s*=\s*(.+)$", (ndk / "source.properties").read_text(), re.MULTILINE)
    if revision is None or revision[1].strip() != NDK_VERSION:
        raise RuntimeError(f"native builds require NDK {NDK_VERSION}: {ndk}")
    if not sys.platform.startswith("linux"):
        raise RuntimeError("native release builds require Linux (or WSL)")
    binary = ndk / "toolchains/llvm/prebuilt/linux-x86_64/bin"
    cc = binary / f"aarch64-linux-android{ANDROID_API}-clang"
    if not cc.is_file():
        raise RuntimeError(f"missing Android ARM64 compiler: {cc}")
    wrappers = work / "toolchain-bin"
    wrappers.mkdir(parents=True, exist_ok=True)
    # Upstream mk/android.mk names its compiler ndk-gcc; use the current NDK.
    for name, target in (("ndk-gcc", cc), ("ndk-g++", binary / f"aarch64-linux-android{ANDROID_API}-clang++")):
        wrapper = wrappers / name
        wrapper.write_text(f"#!/bin/sh\nexec {shlex.quote(str(target))} \"$@\"\n")
        wrapper.chmod(0o755)
    return {
        "PATH": f"{wrappers}:{binary}:{os.environ['PATH']}",
        "CC": str(cc), "CXX": str(binary / f"aarch64-linux-android{ANDROID_API}-clang++"),
        "AR": str(binary / "llvm-ar"), "RANLIB": str(binary / "llvm-ranlib"),
        "STRIP": str(binary / "llvm-strip"), "OBJCOPY": str(binary / "llvm-objcopy"),
        "OBJDUMP": str(binary / "llvm-objdump"),
        "CFLAGS": "-fPIC -O2", "CXXFLAGS": "-fPIC -O2",
        "LDFLAGS": "-Wl,-z,max-page-size=16384", "NDK_ARCH": "aarch64",
        "PKG_CONFIG_LIBDIR": str(work / "empty-pkgconfig"), "PKG_CONFIG_PATH": "",
        "SOURCE_DATE_EPOCH": "0",
    }


def archive_directory(directory: Path, output: Path, name: str) -> None:
    # No hardlink entries: the Android extractor supports regular files/symlinks.
    with output.open("wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", mtime=0, filename="") as gz:
        with tarfile.open(fileobj=gz, mode="w", format=tarfile.PAX_FORMAT) as archive:
            for item in [directory, *sorted(directory.rglob("*"))]:
                archive.inodes.clear()
                member = archive.gettarinfo(str(item), arcname=str(Path(name) / item.relative_to(directory)))
                member.uid = member.gid = member.mtime = 0
                member.uname = member.gname = ""
                if member.isfile():
                    with item.open("rb") as handle:
                        archive.addfile(member, handle)
                else:
                    archive.addfile(member)


def manifest(lock: dict, directory: Path, component: str, prebuilt: bool = False) -> dict:
    result = {"schema": 1, "abi": ABI, "build": lock["build"], "proot": lock["proot"] | {"sha256": sha256(directory / "proot")}}
    if component == "full":
        hashes = {name: sha256(directory / path) for name, path in (
            ("sha256", "r2.tar.gz"), ("dataSha256", "r2dir.tar.gz"), ("libcxxSha256", "libs/libc++_shared.so"))}
        identity = dict(hashes)
        # Debug's legacy libz is part of its bundle identity, not used in Release.
        if (directory / "libs/libz.so.1").is_file():
            identity["libzSha256"] = sha256(directory / "libs/libz.so.1")
        hashes["bundleId"] = hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()
        result["radare2"] = lock["radare2"] | hashes
        result["plugins"] = {
            name: info["commit"] for name, info in lock["plugins"].items()
        }
    if not prebuilt:
        from native_proot import TALLOC_VERSION, TALLOC_SOURCE, TALLOC_SHA256, SHMEM_VERSION, SHMEM_SOURCE, SHMEM_SHA256
        result["dependencies"] = {
            "talloc": {"version": TALLOC_VERSION, "source": TALLOC_SOURCE, "sha256": TALLOC_SHA256},
            "libandroid-shmem": {"version": SHMEM_VERSION, "source": SHMEM_SOURCE, "sha256": SHMEM_SHA256},
        }
    return result


def artifact_valid(directory: Path, component: str) -> bool:
    try:
        metadata = json.loads((directory / "runtime-manifest.json").read_text())
        if sha256(directory / "proot") != metadata["proot"]["sha256"]:
            return False
        if component == "full":
            for field, file in (("sha256", "r2.tar.gz"), ("dataSha256", "r2dir.tar.gz"), ("libcxxSha256", "libs/libc++_shared.so")):
                if sha256(directory / file) != metadata["radare2"][field]:
                    return False
        return True
    except (OSError, KeyError, ValueError):
        return False


def cache_key(lock: dict, component: str) -> str:
    # Compiler flags are in these sources; changing them invalidates cached assets.
    recipe = {"build": lock["build"], "abi": ABI, "proot": lock["proot"]}
    scripts = [Path(__file__), ROOT / "tools/native_proot.py"]
    if component == "full":
        recipe |= {"radare2": lock["radare2"], "plugins": lock["plugins"]}
        scripts += [ROOT / "tools/native_radare2.py"]
    recipe["scripts"] = {path.name: sha256(path) for path in scripts}
    return component + "-" + hashlib.sha256(json.dumps(recipe, sort_keys=True).encode()).hexdigest()


def build_component(lock: dict, component: str, cache: Path, ndk: Path, jobs: int) -> Path:
    key = cache_key(lock, component)
    output = cache / "artifacts" / key
    if artifact_valid(output, component):
        log(f"Using verified cache: {key}")
        return output
    work = cache / "work" / key
    work.mkdir(parents=True, exist_ok=True)
    env = ndk_environment(ndk, work)
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    if component == "full":
        proot = build_component(lock, "proot", cache, ndk, jobs)
        shutil.copy2(proot / "proot", output / "proot")
        from native_radare2 import build_radare2
        build_radare2(lock, work, output, ndk, env, jobs, run, checkout, archive_directory)
    else:
        from native_proot import build_proot
        source_dir = checkout(lock["proot"], work / "sources/proot")
        built = build_proot(source_dir, work, env, jobs, run, download)
        shutil.copy2(built, output / "proot")
    json_write(output / "runtime-manifest.json", manifest(lock, output, component))
    return output


def prebuilt(component: str, output: Path) -> None:
    shared = ROOT / "app/src/shared/assets"
    full = ROOT / "app/src/full/assets"
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    shutil.copy2(shared / "proot", output / "proot")
    # Checked-in binaries have no upstream commit metadata; identify by contents.
    unknown = {"source": "checked-in", "ref": "checked-in", "commit": "checked-in"}
    lock = {"build": {"ndk": "checked-in", "androidApi": ANDROID_API}, "proot": unknown}
    if component == "full":
        for filename in ("r2.tar.gz", "r2dir.tar.gz"):
            shutil.copy2(full / filename, output / filename)
        shutil.copytree(full / "libs", output / "libs")
        with tarfile.open(full / "r2.tar.gz", "r:gz") as archive:
            versions = {match[1] for member in archive for match in [re.search(r"radare2/share/radare2/(\d+\.\d+\.\d+)(?:/|$)", member.name)] if match}
        if len(versions) != 1:
            raise RuntimeError("cannot identify checked-in radare2 version from asset archive")
        lock["radare2"] = unknown | {"version": versions.pop()}
        lock["plugins"] = {"r2ghidra": unknown, "r2dec": unknown}
    json_write(output / "runtime-manifest.json", manifest(lock, output, component, prebuilt=True))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--component", choices=("proot", "full"))
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--cache-dir", type=Path, default=ROOT / "build/native-runtime-cache")
    parser.add_argument("--ndk", type=Path)
    parser.add_argument("--jobs", type=int, default=min(8, os.cpu_count() or 1))
    parser.add_argument("--lock-file", type=Path)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--prebuilt", action="store_true")
    mode.add_argument("--resolve-lock", action="store_true", help="Refresh --lock-file from upstream without building assets")
    args = parser.parse_args()
    if args.resolve_lock:
        if args.lock_file is None:
            parser.error("--resolve-lock requires --lock-file")
        resolve_sources(args.lock_file, refresh=True)
        log(f"Resolved source lock: {args.lock_file}")
        return
    if args.component is None or args.output_dir is None:
        parser.error("Staging assets requires --component and --output-dir")
    output = args.output_dir.resolve()
    if args.prebuilt:
        prebuilt(args.component, output)
    else:
        if args.ndk is None or args.jobs < 1:
            parser.error("Release requires --ndk and positive --jobs")
        cache = args.cache_dir.resolve()
        cache.mkdir(parents=True, exist_ok=True)
        with (cache / "build.lock").open("a") as guard:
            fcntl.flock(guard, fcntl.LOCK_EX)
            lock = resolve_sources(args.lock_file)
            artifact = build_component(lock, args.component, cache, args.ndk.resolve(), args.jobs)
            if output.exists():
                shutil.rmtree(output)
            shutil.copytree(artifact, output)
    log(f"Staged {args.component} assets: {output}")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f"[native] ERROR: {error}", file=sys.stderr)
        sys.exit(1)
