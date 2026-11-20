package top.wsdx233.r2droid.feature.ai.data

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue

internal class LocalServer : AutoCloseable {
    data class SeenRequest(val path: String, val body: JSONObject)
    val requests = LinkedBlockingQueue<SeenRequest>()
    val handlers = LinkedBlockingQueue<(HttpExchange) -> Unit>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            try {
                val json = exchange.requestBody.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                requests.add(SeenRequest(exchange.requestURI.path, json))
                handlers.poll()?.invoke(exchange) ?: exchange.sendResponseHeaders(500, -1)
            } finally {
                exchange.close()
            }
        }
        start()
    }

    fun enqueue(body: String, code: Int = 200) {
        handlers.add { exchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", if (code == 200) "text/event-stream" else "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.write(bytes)
        }
    }

    fun provider(responses: Boolean = false) = AiProvider(name = "stub", baseUrl = "http://127.0.0.1:${server.address.port}/v1/",
        apiKey = "test-token", models = listOf("example-model"), useResponsesApi = responses)

    override fun close() = server.stop(0)
}
