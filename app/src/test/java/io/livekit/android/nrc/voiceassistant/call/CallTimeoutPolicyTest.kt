package io.livekit.android.nrc.voiceassistant.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallTimeoutPolicyTest {
    private val fiveMinutes = CallTimeoutPolicy.REPLY_TIMEOUT_MS

    @Test fun idleSilenceNeverHangsUp() {
        val policy = CallTimeoutPolicy()
        assertFalse(policy.update(60 * 60 * 1000L, true).endCall)
    }

    @Test fun missingReplyAllowsSleepThenEndsAfterFiveLockedMinutes() {
        val policy = CallTimeoutPolicy()
        policy.question(0)
        assertTrue(policy.update(fiveMinutes - 1, false).keepScreenOn)
        assertFalse(policy.update(fiveMinutes, false).keepScreenOn)
        // Time spent waiting for the system screen timeout does not count as locked time.
        assertFalse(policy.update(fiveMinutes * 3, false).endCall)
        assertFalse(policy.update(fiveMinutes * 3, true).endCall)
        assertFalse(policy.update(fiveMinutes * 4 - 1, true).endCall)
        assertTrue(policy.update(fiveMinutes * 4, true).endCall)
    }

    @Test fun manualLockDoesNotShortenReplyGrace() {
        val policy = CallTimeoutPolicy()
        policy.question(0)
        assertFalse(policy.update(1, true).endCall)
        assertFalse(policy.update(fiveMinutes, true).endCall)
        assertTrue(policy.update(fiveMinutes * 2, true).endCall)
    }

    @Test fun replyWhileLockedCancelsHangup() {
        val policy = CallTimeoutPolicy()
        policy.question(0)
        policy.update(fiveMinutes, true)
        policy.reply()
        assertFalse(policy.update(fiveMinutes * 3, true).endCall)
        assertTrue(policy.update(fiveMinutes * 3, false).keepScreenOn)
    }

    @Test fun newQuestionRestartsBothTimers() {
        val policy = CallTimeoutPolicy()
        policy.question(0)
        policy.update(fiveMinutes, true)
        policy.question(fiveMinutes * 2 - 1)
        assertTrue(policy.update(fiveMinutes * 2, true).keepScreenOn)
        assertFalse(policy.update(fiveMinutes * 2, true).endCall)
    }

    @Test fun unlockingCancelsLockedCountdown() {
        val policy = CallTimeoutPolicy()
        policy.question(0)
        policy.update(fiveMinutes, true)
        policy.update(fiveMinutes * 2 - 1, false)
        assertFalse(policy.update(fiveMinutes * 2, true).endCall)
        assertTrue(policy.update(fiveMinutes * 3, true).endCall)
    }
}
