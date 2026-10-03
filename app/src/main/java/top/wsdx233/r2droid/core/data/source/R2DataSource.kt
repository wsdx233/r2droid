package top.wsdx233.r2droid.core.data.source

import java.io.InputStream

interface R2DataSource {
    suspend fun execute(cmd: String): Result<String>
    /** Executes a complete JSON-producing command unchanged and returns its raw response. */
    suspend fun executeJson(cmd: String): Result<String>
    suspend fun <T> executeStream(cmd: String, block: suspend (InputStream) -> T): Result<T>
}
