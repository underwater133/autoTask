package com.autotask.app.executor

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun maxAttempts_isThree() {
        assertEquals(3, RetryPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun delays_increase() {
        assertEquals(30_000L, RetryPolicy.delayForAttempt(1))
        assertEquals(60_000L, RetryPolicy.delayForAttempt(2))
        assertEquals(120_000L, RetryPolicy.delayForAttempt(3))
    }

    @Test
    fun delay_outOfRange_fallsBackToLast() {
        // 超出数组时回退到最长间隔
        assertEquals(120_000L, RetryPolicy.delayForAttempt(4))
    }
}
