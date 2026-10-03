# r2pipe-kotlin

A small Kotlin/JVM implementation of the `r2pipe` protocol extracted from R2Droid.

It provides two backends:

- `R2Pipe` for stdio / `r2 -q0`
- `R2PipeHttp` for HTTP / `r2 -qc=H`

This module is intentionally JVM-only and does not depend on Android APIs.
The HTTP backend uses OkHttp 5.3.2, included transitively by Gradle. Stdio
commands do not invoke it, though the Gradle module still declares the dependency.

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

`cmdj(command)` sends the supplied command unchanged and returns its raw response
as a `String`. Pass the complete JSON-producing r2 command, including `j` in the
command name (for example, `cmdj("pdj 10 @ 0x1000")`). It does not add `j` to a
command or parse/validate JSON; callers can use their preferred JSON library.

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

### Stdio command and stream lifecycle

`R2Pipe` serializes blocking commands. A response stream owns the session until
its NUL terminator has been consumed; another `cmd()` or `cmdStream()` call is
rejected while that stream is active.

- Read each response stream until EOF before closing it. JSON parsers must also
  consume document EOF, not stop immediately after the final object or array.
- Closing a stream before its terminator invalidates the session and forcibly
  terminates its owned process. Open a new session rather than reusing it.
- EOF before the response terminator is an error, not a successful partial result.
  I/O failures invalidate the session and release its process and streams.
- `close()` and `forceClose()` are safe to repeat. `forceClose()` can still
  terminate a process that has not exited after a graceful `close()`.
- LF, CRLF, and CR in commands are normalized to `;` so a multiline command is
  sent as one request and produces one response frame. Embedded NUL is rejected
  without invalidating an otherwise healthy session.

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

### HTTP lifecycle

`connect(url)` uses an existing server: closing the client does not send `q`
or stop the remote process. `spawn(spec, port)` owns its process; the port must
be free before launch. Startup requires the process to remain alive and the
selected port to answer `/cmd/?V` with a radare2 version. Failed startup
terminates the launched process. Closing a spawned client requests shutdown
and terminates that process.

`close()` and `forceClose()` cancel in-flight commands and close outstanding
response streams, including reads blocked waiting for headers or body data.
Once closed, the client rejects new commands. Close a `cmdStream()` response
when finished; reaching EOF also releases it. Closing one response early
cancels only that request, not the HTTP session. A request that fails does not
automatically retry: commands can modify the r2 session. Connection timeout
is 5 seconds; read timeout is 10 minutes.

Commands are UTF-8 percent-encoded as URL path data, including reserved
characters such as `?`, `#`, `%`, `/`, and line breaks.

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
