package com.salvia.salviabrowxer.core.concurrent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A permit gate whose limit can be retuned while work is running.
 *
 * A bare `Semaphore` cannot be resized: replacing the instance leaves the old
 * permits held by in-flight downloads and can strand the queue forever. This
 * limiter keeps one owner for the whole service lifetime instead.
 *
 * Contract:
 * - [setLimit] only decides how many *new* transfers may start.
 * - A transfer that is already running keeps its permit until it finishes.
 * - Lowering the limit never drops or cancels an in-flight permit.
 * - Waiters are served in FIFO order.
 */
class SlotLimiter(initialLimit: Int) {

    private val mutex = Mutex()
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var limit: Int = initialLimit.coerceIn(MIN_LIMIT, MAX_LIMIT)
    private var inUse: Int = 0

    val activeCount: Int get() = inUse
    val currentLimit: Int get() = limit

    suspend fun acquire() {
        val ticket = CompletableDeferred<Unit>()
        val granted = mutex.withLock {
            if (inUse < limit) {
                inUse++
                true
            } else {
                waiters.addLast(ticket)
                false
            }
        }
        if (!granted) ticket.await()
    }

    suspend fun release() {
        val granted: List<CompletableDeferred<Unit>>
        mutex.withLock {
            if (inUse > 0) inUse--
            // Only admit as many waiters as the *current* limit allows, so lowering the
            // setting while transfers run cannot sneak extra work past it.
            val ready = mutableListOf<CompletableDeferred<Unit>>()
            while (inUse < limit) {
                val next = waiters.removeFirstOrNull() ?: break
                inUse++
                ready += next
            }
            granted = ready
        }
        granted.forEach { it.complete(Unit) }
    }

    suspend fun setLimit(newLimit: Int) {
        val granted: List<CompletableDeferred<Unit>>
        mutex.withLock {
            limit = newLimit.coerceIn(MIN_LIMIT, MAX_LIMIT)
            val ready = mutableListOf<CompletableDeferred<Unit>>()
            while (inUse < limit) {
                val next = waiters.removeFirstOrNull() ?: break
                inUse++
                ready += next
            }
            granted = ready
        }
        granted.forEach { it.complete(Unit) }
    }

    companion object {
        const val MIN_LIMIT = 1
        const val MAX_LIMIT = 5
    }
}
