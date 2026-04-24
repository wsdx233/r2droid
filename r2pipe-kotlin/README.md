# r2pipe-kotlin

A small Kotlin/JVM implementation of the `r2pipe` protocol extracted from R2Droid.

It provides two backends:

- `R2Pipe` for stdio / `r2 -q0`
- `R2PipeHttp` for HTTP / `r2 -qc=H`

This module is intentionally JVM-only and does not depend on Android APIs.

## Status

Current scope:

- blocking command execution
- `cmd()` / `cmdj()` / `cmdStream()`
- stdio backend
- HTTP backend
- pluggable logging
- pluggable process interrupt / termination strategy

Not included yet:

- async API
- JSON binding to a specific library
- JNI / `r_core_cmd_str()` backend
- RAP / TCP backends

## Coordinates inside this repository

- module: `:r2pipe-kotlin`
- package: `org.radare.r2pipe`

## Core types

- `LaunchSpec`
- `R2PipeSession`
- `R2Pipe`
- `R2PipeHttp`
- `R2PipeLogger`
- `ProcessKiller`

## Example: stdio

```kotlin
import org.radare.r2pipe.LaunchSpec
import org.radare.r2pipe.R2Pipe
import java.io.File

fun main() {
    val spec = LaunchSpec(
        command = listOf("radare2", "-q0", "/bin/ls"),
        workingDirectory = File(".")
    )

    R2Pipe.open(spec).use { r2 ->
        println(r2.cmd("ij"))
        println(r2.cmd("pd 10"))
    }
}
```

## Example: HTTP

```kotlin
import org.radare.r2pipe.LaunchSpec
import org.radare.r2pipe.R2PipeHttp
import java.io.File

fun main() {
    val spec = LaunchSpec(
        command = listOf("radare2", "-qc=H", "-e", "http.port=9090", "/bin/ls"),
        workingDirectory = File(".")
    )

    R2PipeHttp.spawn(spec, port = 9090).use { r2 ->
        println(r2.cmd("ij"))
    }
}
```

## Logging

```kotlin
val logger = R2PipeLogger { level, message ->
    println("[$level] $message")
}
```

## Process control

`ProcessKiller` lets platform code define how interrupt / terminate works.
This is useful on Android, where sending `SIGINT` to the spawned process tree may require custom shell logic.

```kotlin
val processKiller = object : ProcessKiller {
    override fun interrupt(process: Process) {
        process.destroy()
    }
}
```

## Relationship with R2Droid

R2Droid uses this module as the transport core and keeps Android-specific behavior in:

- `app/src/main/java/top/wsdx233/r2droid/util/AndroidR2PipeSupport.kt`
- `app/src/main/java/top/wsdx233/r2droid/util/R2PipeManager.kt`

## Examples

See:

- `examples/StdioExample.kt`
- `examples/HttpExample.kt`
