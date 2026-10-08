package io.livekit.android.nrc.voiceassistant.call

/** Monotonic times in milliseconds; silence without an outstanding question is not a timeout. */
class CallTimeoutPolicy {
    companion object {
        const val REPLY_TIMEOUT_MS = 5 * 60 * 1000L
        const val LOCKED_TIMEOUT_MS = 5 * 60 * 1000L
    }

    private var waitingSince: Long? = null
    private var lockedWaitingSince: Long? = null

    fun question(now: Long) {
        waitingSince = now
        lockedWaitingSince = null
    }

    fun reply() {
        waitingSince = null
        lockedWaitingSince = null
    }

    fun update(now: Long, locked: Boolean): Decision {
        val overdue = waitingSince?.let { now - it >= REPLY_TIMEOUT_MS } ?: false
        if (overdue && locked) {
            if (lockedWaitingSince == null) lockedWaitingSince = now
        } else {
            lockedWaitingSince = null
        }
        return Decision(
            keepScreenOn = !overdue,
            endCall = lockedWaitingSince?.let { now - it >= LOCKED_TIMEOUT_MS } ?: false,
        )
    }

    data class Decision(val keepScreenOn: Boolean, val endCall: Boolean)
}
