package org.radare.r2pipe

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
        assertEquals("/cmd/px%208%20@%200x1000", capturedPath.get())
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
