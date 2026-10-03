package org.radare.r2pipe

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class R2PipeHttpTest {
    private var server: HttpServer? = null

    @AfterTest
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    @Test
    fun cmdUsesEncodedPathAndReturnsBody() {
        val capturedPath = AtomicReference<String>()
        val baseUrl = startServer { exchange ->
            capturedPath.set(exchange.requestURI.rawPath)
            val response = "ok"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        val client = R2PipeHttp.connect(baseUrl)
        val result = client.cmd("px 8 @ 0x1000")

        assertEquals("ok", result)
        assertEquals("/cmd/px%208%20%40%200x1000", capturedPath.get())
    }

    @Test
    fun commandsWithReservedCharactersReachTheServerIntact() {
        val command = "?e 中文?&+=#%/\n下一行"
        val capturedCommand = AtomicReference<String>()
        val capturedQuery = AtomicReference<String?>()
        val baseUrl = startServer { exchange ->
            capturedCommand.set(exchange.requestURI.path.removePrefix("/cmd/"))
            capturedQuery.set(exchange.requestURI.rawQuery)
            val response = "ok".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }

        R2PipeHttp.connect(baseUrl).use { client ->
            assertEquals("ok", client.cmd(command))
        }

        assertEquals(command, capturedCommand.get())
        assertEquals(null, capturedQuery.get())
    }

    @Test
    fun closingConnectedClientDoesNotQuitRemoteServer() {
        val quitReceived = AtomicBoolean(false)
        val baseUrl = startServer { exchange ->
            if (exchange.requestURI.path == "/cmd/q") quitReceived.set(true)
            val response = if (quitReceived.get()) "closed" else "ok"
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        R2PipeHttp.connect(baseUrl).use { client ->
            assertEquals("ok", client.cmd("ij"))
        }
        R2PipeHttp.connect(baseUrl).use { client ->
            assertEquals("ok", client.cmd("ij"))
        }

        assertFalse(quitReceived.get())
    }

    @Test
    fun occupiedPortCannotBeMistakenForSpawnedServer() {
        val requests = AtomicInteger()
        val baseUrl = startServer { exchange ->
            requests.incrementAndGet()
            val bytes = "radare2 6.1.4".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val port = java.net.URI(baseUrl).port

        assertFailsWith<IllegalStateException> {
            R2PipeHttp.spawn(LaunchSpec(listOf("not-a-real-r2-executable")), port)
        }
        assertEquals(0, requests.get())
    }

    @Test
    fun unrelatedHttpServerCannotPassReadinessProbe() {
        val port = ServerSocket(0).use { it.localPort }
        val receivedProbe = Files.createTempFile("r2pipe-http", ".probe")
        val startedProcess = AtomicReference<Process>()
        val killer = object : ProcessKiller {
            override fun terminate(process: Process, force: Boolean) {
                startedProcess.set(process)
                ProcessKiller.DEFAULT.terminate(process, force)
            }
        }
        val executable = if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java"
        val java = File(System.getProperty("java.home"), "bin/$executable").absolutePath
        val classpath = listOf(UnrelatedHttpPeer::class.java, Unit::class.java).joinToString(File.pathSeparator) {
            File(it.protectionDomain.codeSource.location.toURI()).absolutePath
        }
        var client: R2PipeHttp? = null
        try {
            assertFailsWith<IllegalStateException> {
                client = R2PipeHttp.spawn(
                    LaunchSpec(listOf(java, "-cp", classpath, UnrelatedHttpPeer::class.java.name,
                        port.toString(), receivedProbe.toString())),
                    port,
                    processKiller = killer,
                    maxRetries = 50,
                    intervalMs = 50
                )
            }
            assertTrue(Files.size(receivedProbe) > 0, "The unrelated server should have answered the readiness probe")
            val process = assertNotNull(startedProcess.get())
            process.onExit().get(5, TimeUnit.SECONDS)
            assertFalse(process.isAlive)
        } finally {
            client?.forceClose()
            startedProcess.get()?.destroyForcibly()
            Files.deleteIfExists(receivedProbe)
        }
    }

    @Test
    fun cmdStreamReturnsResponseBodyAndCloseMarksClientClosed() {
        val baseUrl = startServer { exchange ->
            val response = "stream-body"
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        val client = R2PipeHttp.connect(baseUrl)
        val result = client.cmdStream("ij").bufferedReader().use { it.readText() }
        client.close()

        assertEquals("stream-body", result)
        assertFalse(client.isRunning())
    }

    @Test
    fun closeCancelsCommandWaitingForHeadersAndRejectsNewCommands() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = R2PipeHttp.connect(startBlockedServer(false, arrived, release))
        val workers = Executors.newFixedThreadPool(2)
        try {
            val result = workers.submit(Callable { runCatching { client.cmd("blocked") } })
            assertTrue(arrived.await(2, TimeUnit.SECONDS))
            workers.submit { client.close() }.get(2, TimeUnit.SECONDS)
            assertNotNull(result.get(2, TimeUnit.SECONDS).exceptionOrNull())
            assertFailsWith<IllegalStateException> { client.cmd("ij") }
            client.close()
        } finally {
            release.countDown()
            client.forceClose()
            workers.shutdownNow()
        }
    }

    @Test
    fun forceCloseCancelsCommandBlockedReadingResponseBody() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = R2PipeHttp.connect(startBlockedServer(true, arrived, release))
        val workers = Executors.newFixedThreadPool(2)
        try {
            val result = workers.submit(Callable { runCatching { client.cmd("blocked") } })
            assertTrue(arrived.await(2, TimeUnit.SECONDS))
            workers.submit { client.forceClose() }.get(2, TimeUnit.SECONDS)
            assertNotNull(result.get(2, TimeUnit.SECONDS).exceptionOrNull())
            assertFalse(client.isRunning())
        } finally {
            release.countDown()
            client.forceClose()
            workers.shutdownNow()
        }
    }

    @Test
    fun closingResponseStreamCancelsItsReaderWithoutClosingSession() {
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        val client = R2PipeHttp.connect(startBlockedServer(true, arrived, release))
        val workers = Executors.newFixedThreadPool(2)
        try {
            val stream = client.cmdStream("blocked")
            assertTrue(arrived.await(2, TimeUnit.SECONDS))
            assertEquals('X'.code, stream.read())
            val result = workers.submit(Callable { runCatching { stream.read() } })
            workers.submit { stream.close() }.get(2, TimeUnit.SECONDS)
            assertTrue(result.get(2, TimeUnit.SECONDS).exceptionOrNull() is IOException)
            release.countDown()
            assertEquals("ok", client.cmd("ij"))
        } finally {
            release.countDown()
            client.forceClose()
            workers.shutdownNow()
        }
    }

    @Test
    fun closingSessionInvalidatesEvenBufferedResponseBytes() {
        val baseUrl = startServer { exchange ->
            val bytes = "abcdef".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        R2PipeHttp.connect(baseUrl).use { client ->
            val stream = client.cmdStream("ij")
            assertEquals('a'.code, stream.read())
            client.close()
            assertFailsWith<IOException> { stream.read() }
            stream.close()
        }
    }

    @Test
    fun droppedReplyDoesNotRepeatCommandAndSessionRemainsUsable() {
        val executions = AtomicInteger()
        val baseUrl = startServer { exchange ->
            if (exchange.requestURI.path == "/cmd/af") {
                executions.incrementAndGet()
                exchange.close()
            } else {
                val bytes = "ok".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        R2PipeHttp.connect(baseUrl).use { client ->
            assertFailsWith<RuntimeException> { client.cmd("af") }
            assertEquals(1, executions.get())
            assertEquals("ok", client.cmd("ij"))
        }
    }

    private fun startBlockedServer(
        sendBody: Boolean,
        arrived: CountDownLatch,
        release: CountDownLatch
    ): String = startServer { exchange ->
        try {
            if (exchange.requestURI.path == "/cmd/blocked") {
                if (sendBody) {
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.write('X'.code)
                    exchange.responseBody.flush()
                }
                arrived.countDown()
                release.await(10, TimeUnit.SECONDS)
                if (!sendBody) exchange.sendResponseHeaders(200, -1)
            } else {
                val bytes = "ok".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
            }
        } finally {
            exchange.close()
        }
    }

    private fun startServer(handler: (com.sun.net.httpserver.HttpExchange) -> Unit): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/cmd/") { exchange ->
            handler(exchange)
        }
        httpServer.executor = null
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}"
    }
}

internal object UnrelatedHttpPeer {
    @JvmStatic
    fun main(args: Array<String>) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", args[0].toInt()), 0)
        server.createContext("/cmd/") { exchange ->
            Files.writeString(java.nio.file.Path.of(args[1]), "probe received")
            val response = "not-radare2".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            Thread.sleep(30_000)
        } finally {
            server.stop(0)
        }
    }
}
