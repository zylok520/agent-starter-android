package io.livekit.android.nrc.voiceassistant.ui.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmLipSyncMeterTest {
    private fun pcm(vararg samples: Int): ByteBuffer = ByteBuffer.allocate(samples.size * 2)
        .order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it.toShort()) }
            flip()
        }

    @Test fun silenceAndNoiseStayClosed() {
        val meter = PcmLipSyncMeter()
        assertEquals(0f, meter.openingAt(0), 0f)
        meter.accept(pcm(0, 0, 0), 16, 1, 3, 10)
        assertEquals(0f, meter.openingAt(33), 0f)
        meter.accept(pcm(100, -100), 16, 1, 2, 40)
        assertEquals(0f, meter.openingAt(66), 0f)
    }

    @Test fun louderPcmOpensMouthFurther() {
        val quiet = PcmLipSyncMeter()
        val loud = PcmLipSyncMeter()
        quiet.accept(pcm(1000, -1000), 16, 1, 2, 0)
        loud.accept(pcm(5000, -5000), 16, 1, 2, 0)
        assertTrue(loud.openingAt(33) > quiet.openingAt(33))
    }

    @Test fun stereoOppositePhasesDoNotCancelAndBufferIsUntouched() {
        val buffer = pcm(123, 4096, -4096, 4096, -4096)
        buffer.position(2)
        val limit = buffer.limit()
        val meter = PcmLipSyncMeter()
        meter.accept(buffer.asReadOnlyBuffer(), 16, 2, 2, 0)
        assertTrue(meter.openingAt(33) > 0f)
        assertEquals(2, buffer.position())
        assertEquals(limit, buffer.limit())
    }

    @Test fun clippingIsBoundedAndAttackIsSmoothed() {
        val meter = PcmLipSyncMeter()
        for (now in 0L..330L step 33) {
            meter.accept(pcm(-32768, 32767), 16, 1, 2, now)
            val opening = meter.openingAt(now)
            assertTrue(opening in 0f..1f)
            if (now == 0L) assertTrue(opening > 0f && opening < 1f)
        }
    }

    @Test fun missingFramesEventuallyCloseMouth() {
        val meter = PcmLipSyncMeter()
        meter.accept(pcm(6000), 16, 1, 1, 0)
        assertTrue(meter.openingAt(33) > 0f)
        for (now in 66L..1000L step 33) meter.openingAt(now)
        assertEquals(0f, meter.openingAt(1000), 0f)
    }

    @Test fun silenceFramesCloseMouth() {
        val meter = PcmLipSyncMeter()
        meter.accept(pcm(6000), 16, 1, 1, 0)
        assertTrue(meter.openingAt(33) > 0f)
        for (now in 66L..1000L step 33) {
            meter.accept(pcm(0), 16, 1, 1, now)
            meter.openingAt(now)
        }
        assertEquals(0f, meter.openingAt(1000), 0f)
    }

    @Test fun malformedFramesFailClosedWithoutReadingPastBuffer() {
        val meter = PcmLipSyncMeter()
        meter.accept(pcm(6000), 32, 1, 1, 0)
        assertEquals(0f, meter.openingAt(0), 0f)
        meter.accept(ByteBuffer.allocate(1), 16, 2, Int.MAX_VALUE, 33)
        assertEquals(0f, meter.openingAt(33), 0f)
        meter.accept(pcm(6000), 16, 0, 1, 66)
        assertEquals(0f, meter.openingAt(66), 0f)
        meter.accept(pcm(6000), 16, 2, Int.MAX_VALUE, 99)
        assertTrue(meter.openingAt(99) > 0f)
    }
}
