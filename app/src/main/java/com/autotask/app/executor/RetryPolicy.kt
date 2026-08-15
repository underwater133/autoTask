package com.autotask.app.executor

/**
 * 重试策略（M4）：最多 3 次尝试，间隔统一 30 秒。
 * 纯逻辑，便于单元测试。
 */
object RetryPolicy {

    /** 最大尝试次数（首次 + 2 次重试） */
    const val MAX_ATTEMPTS = 3

    /** 每次失败后到下一次尝试的等待时长（毫秒），统一 30 秒 */
    val RETRY_DELAYS_MS = longArrayOf(30_000L, 30_000L, 30_000L)

    fun delayForAttempt(attempt: Int): Long =
        RETRY_DELAYS_MS.getOrElse(attempt - 1) { RETRY_DELAYS_MS.last() }
}
