package com.woocommerce.android.util

import com.woocommerce.android.AppConstants
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Cancellation
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Success
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

/**
 * A wrapper class for a [CancellableContinuation], which handles some of the most common errors when a continuation
 * is used.
 *
 * 1. First, before a continuation is resumed, it's checked if it's active.
 * 2. After a continuation is successfully resumed it's nulled.
 * 3. The whole async call is wrapped in a try-catch block which handles the [CancellationException].
 *
 */
class ContinuationWrapper<T> {
    private var timeout: Long = 0
    private var continuation: CancellableContinuation<T>? = null
    private val mutex = Mutex()

    suspend fun callAndWaitUntilTimeout(
        timeout: Long = AppConstants.REQUEST_TIMEOUT,
        asyncAction: () -> Unit
    ): ContinuationResult<T> {
        this.timeout = timeout
        return callAndWait(asyncAction)
    }

    suspend fun callAndWait(asyncAction: () -> Unit): ContinuationResult<T> {
        suspend fun suspendCoroutine(asyncRequest: () -> Unit) = suspendCancellableCoroutine<T> {
            continuation = it
            asyncRequest()
        }

        mutex.withLock {
            val result = try {
                continuation?.cancel()
                val continuationResult = if (timeout > 0) {
                    withTimeout(timeout) {
                        suspendCoroutine(asyncAction)
                    }
                } else {
                    suspendCoroutine(asyncAction)
                }
                Success(continuationResult)
            } catch (e: CancellationException) {
                Cancellation<T>(e)
            }

            continuation = null
            return result
        }
    }

    @Synchronized
    fun continueWith(value: T) {
        if (continuation?.isActive == true) {
            continuation?.resume(value)
        }
    }

    fun cancel() {
        continuation?.cancel()
    }

    sealed class ContinuationResult<T> {
        data class Success<T>(val value: T) : ContinuationResult<T>()
        data class Cancellation<T>(val exception: CancellationException) : ContinuationResult<T>()
    }
}
