package io.livekit.android.nrc.voiceassistant.ui.avatar

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/** Audio thread publishes an envelope; UI samples it without retaining WebRTC's buffer. */
class PcmLipSyncMeter {
    companion object {
        const val STALE_AFTER_MS = 150L
        private const val NOISE_FLOOR = 0.008f
        private const val FULL_OPEN_RMS = 0.18f
    }
    private data class Frame(val level: Float, val receivedAt: Long)
    @Volatile private var latest = Frame(0f, Long.MIN_VALUE)
    private var opening = 0f
    private var lastDrawAt: Long? = null

    fun accept(data: ByteBuffer, bitsPerSample: Int, channels: Int, frames: Int, now: Long) {
        // WebRTC delivers signed 16-bit interleaved native-endian PCM; fail closed otherwise.
        if (bitsPerSample != 16 || channels <= 0 || frames <= 0) {
            latest = Frame(0f, now)
            return
        }
        val buffer = data.duplicate().order(ByteOrder.nativeOrder())
        val samples = minOf(channels.toLong() * frames, (buffer.remaining() / 2).toLong()).toInt()
        if (samples == 0) {
            latest = Frame(0f, now)
            return
        }
        // Bound work for unusually large buffers.
        val stride = maxOf(1, ((samples + 4095L) / 4096).toInt())
        var energy = 0.0
        var count = 0
        for (i in 0 until samples step stride) {
            val sample = buffer.getShort(buffer.position() + i * 2) / 32768.0
            energy += sample * sample
            count++
        }
        val rms = sqrt(energy / count).toFloat()
        val normalized = ((rms - NOISE_FLOOR) / (FULL_OPEN_RMS - NOISE_FLOOR)).coerceIn(0f, 1f)
        latest = Frame(sqrt(normalized), now)
    }

    /** UI thread only. Time-based attack/release avoids dependence on PCM sample rate. */
    fun openingAt(now: Long): Float {
        val frame = latest
        val target = if (frame.receivedAt == Long.MIN_VALUE || now - frame.receivedAt > STALE_AFTER_MS) 0f else frame.level
        val elapsed = (lastDrawAt?.let { now - it } ?: 33L).coerceIn(0, 100)
        lastDrawAt = now
        val timeConstant = if (target > opening) 40.0 else 75.0
        opening += (target - opening) * (1.0 - exp(-elapsed / timeConstant)).toFloat()
        if (target == 0f && opening < 0.015f) opening = 0f
        return opening.coerceIn(0f, 1f)
    }
}
