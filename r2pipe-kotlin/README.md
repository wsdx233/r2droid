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

## Build and test

From this directory, with JDK 17 on your path, run `./gradlew build` (Windows:
`gradlew.bat build`). This builds the JAR in `build/libs/` and runs the tests.
The wrapper and settings are self-contained: no Android SDK, R2Droid checkout,
system Gradle, or radare2 installation is needed for the build or unit tests.
Running the examples does require radare2. Inside R2Droid the module is still
available as `:r2pipe-kotlin`.

Import the Kotlin binding from `org.radare.r2pipe.kotlin`. If migrating from an
earlier R2Droid version, update the Kotlin binding imports; `org.radare.r2pipe.R2Pipe`
is the **Java** binding and can now be used on the same classpath.

## Core types

- `LaunchSpec`
- `R2PipeSession`
- `R2Pipe`
- `R2PipeHttp`
- `R2PipeLogger`
- `ProcessKiller`

## Example: stdio

```kotlin
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.R2Pipe
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

After spawning, `R2Pipe.open(spec)` waits up to 30 seconds for the initial NUL
from `r2 -q0`. Use `R2Pipe.open(spec, startupTimeoutMs = 60_000)` for slower
startup. A missing marker raises an `IOException` and the owned process is
terminated. This deadline does not apply to commands or response reads.

`R2Pipe` serializes blocking commands. A response stream owns the session until
its NUL terminator has been consumed; another `cmd()` or `cmdStream()` call is
rejected while that stream is active.

- Read each response stream until EOF before closing it. JSON parsers must also
  consume document EOF, not stop immediately after the final object or array.
- Closing a stream before its terminator invalidates the session and requests
  forcible termination. When r2 is the direct child and the only stdout writer,
  this unblocks `read()` with an `IOException`. For wrappers, see [Process control](#process-control).
  Open a new session rather than reusing it.
- EOF before the response terminator is an error, not a successful partial result.
  I/O failures invalidate the session and release its process and streams.
- `close()` and `forceClose()` are safe to repeat. `forceClose()` can still
  terminate a process that has not exited after a graceful `close()`.
- LF, CRLF, and CR in commands are normalized to `;` so a multiline command is
  sent as one request and produces one response frame. Embedded NUL is rejected
  without invalidating an otherwise healthy session.

`r2 -q0` uses NUL as the response terminator, so **output must not contain NUL**.
`cmdStream()` streams the same frames as `cmd()`; it is not a raw binary channel.
To read arbitrary bytes, use `r2.cmd("p8 3 @ 0").trim()` (hex text, e.g. `410042`
for bytes `41 00 42`) instead of `pr 3 @ 0`. A payload NUL looks like a successful
end of frame; the next command may receive the previous reply's tail. If this
happens, close the session and open a new one before sending more commands.

## Example: HTTP

```kotlin
import org.radare.r2pipe.kotlin.LaunchSpec
import org.radare.r2pipe.kotlin.R2PipeHttp
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

`connect(url)` accepts an HTTP(S) URL with an optional path prefix: a base of
`https://host/instance/` sends commands to `/instance/cmd/`. A query or fragment
(even an empty `?` or `#`) raises `IllegalArgumentException` at `connect()`,
before any request. Closing this client does not send `q` or stop the remote process.

`spawn(spec, port)` owns its process; the port must be free before launch.
Startup requires the process to remain alive and the
selected port to answer `/cmd/?V` with a radare2 version. Failed startup
terminates the launched process. Closing a spawned client requests shutdown
and terminates that process.

`close()` and `forceClose()` cancel in-flight commands and close outstanding
response streams, including reads blocked waiting for headers or body data.
Once closed, the client rejects new commands. Close a `cmdStream()` response
when finished; reaching EOF also releases it. Closing one response early
cancels only that request, not the HTTP session. Each `cmd()` or `cmdStream()`
sends at most one request: redirects and non-200 responses (including `503`
with `Retry-After: 0`) are reported as errors, not followed or retried. If a
reply is lost, the command may already have run. Connection timeout is 5 seconds;
read timeout is 10 minutes.

Normally commands are UTF-8 percent-encoded as URL path data, including reserved
characters such as `?`, `#`, `%`, `/`, and line breaks. The exact commands `.`
and `..` are sent as UTF-8 request bodies to `POST /cmd/` instead: HTTP URLs
normalize dot segments. A server that refuses the POST returns an error; the
command is not reissued as a GET. As with stdio, an embedded NUL is rejected
before dispatch without closing the session.

## Logging

```kotlin
val logger = R2PipeLogger { level, message ->
    println("[$level] $message")
}
```

## Process control

The default `ProcessKiller` terminates only the process created by `LaunchSpec`.
Start r2 directly, or make the last line of a POSIX wrapper replace the shell:

```sh
exec radare2 -q0 "$@"
```

If the wrapper must stay alive, supply a `ProcessKiller` that terminates the
wrapper **and** the owned descendants holding its pipes. Killing just the
wrapper can leave r2 running after a startup timeout or a forced close, with
another thread still blocked in `read()` until the last writer closes stdout.

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
