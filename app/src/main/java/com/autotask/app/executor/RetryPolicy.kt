package com.autotask.app.executor

/**
 * 重试策略（M4）：最多 3 次尝试，间隔递增 30s / 60s / 120s。
 * 纯逻辑，便于单元测试。
 */
object RetryPolicy {

    /** 最大尝试次数（首次 + 2 次重试） */
    const val MAX_ATTEMPTS = 3

    /** 第 N 次尝试失败后，到第 N+1 次尝试的等待时长（毫秒） */
    val RETRY_DELAYS_MS = longArrayOf(30_000L, 60_000L, 120_000L)

    fun delayForAttempt(attempt: Int): Long =
        RETRY_DELAYS_MS.getOrElse(attempt - 1) { RETRY_DELAYS_MS.last() }
}
