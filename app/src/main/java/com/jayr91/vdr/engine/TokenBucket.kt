package com.jayr91.vdr.engine

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Global bandwidth throttle shared across all active segments/tasks.
 * Port of VDR's TokenBucket in engine.py.
 */
class TokenBucket(rateBytesPerSec: Long?) {
    @Volatile
    var rate: Long? = rateBytesPerSec
        private set

    private var tokens: Double = min((rateBytesPerSec ?: 0L).toDouble(), 65536.0)
    private var last = System.nanoTime()
    private val lock = Any()

    fun setRate(rateBytesPerSec: Long?) {
        synchronized(lock) {
            rate = rateBytesPerSec
            tokens = (rateBytesPerSec ?: 0L).toDouble()
            last = System.nanoTime()
        }
    }

    fun consume(amount: Int, isCancelled: () -> Boolean, isRunning: () -> Boolean) {
        if (rate == null || rate == 0L) return
        while (true) {
            if (isCancelled()) return
            if (!isRunning()) {
                Thread.sleep(50)
                continue
            }
            val waitMs: Long
            synchronized(lock) {
                val currentRate = rate ?: return
                val now = System.nanoTime()
                val elapsed = (now - last) / 1_000_000_000.0
                last = now
                tokens = min(currentRate.toDouble(), tokens + elapsed * currentRate)
                if (tokens >= amount) {
                    tokens -= amount
                    return
                }
                val needed = amount - tokens
                waitMs = ((needed / currentRate) * 1000).toLong().coerceAtMost(200)
            }
            Thread.sleep(waitMs.coerceAtLeast(1))
        }
    }
}

class SpeedTracker {
    private val bytes = AtomicLong(0)
    @Volatile private var startNs = System.nanoTime()
    @Volatile private var baseline = 0L

    fun markStart(alreadyDownloaded: Long) {
        baseline = alreadyDownloaded
        startNs = System.nanoTime()
        bytes.set(alreadyDownloaded)
    }

    fun setDownloaded(total: Long) {
        bytes.set(total)
    }

    fun bytesPerSecond(): Double {
        val elapsed = (System.nanoTime() - startNs) / 1_000_000_000.0
        if (elapsed <= 0) return 0.0
        return (bytes.get() - baseline) / elapsed
    }
}
