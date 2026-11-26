package top.wsdx233.r2droid.util

import kotlinx.coroutines.CancellationException

/**
 * Coroutine-safe alternative to [runCatching] that always rethrows [CancellationException].
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (throwable: Throwable) {
    Result.failure(throwable)
}
