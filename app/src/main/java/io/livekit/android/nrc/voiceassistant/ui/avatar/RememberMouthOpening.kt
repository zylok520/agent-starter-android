package io.livekit.android.nrc.voiceassistant.ui.avatar

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.livekit.android.compose.state.rememberTrack
import io.livekit.android.compose.state.rememberTrackMuted
import io.livekit.android.compose.types.TrackReference
import io.livekit.android.room.track.RemoteAudioTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import livekit.org.webrtc.AudioTrackSink
import java.nio.ByteBuffer

@Composable
fun rememberMouthOpening(reference: TrackReference?, enabled: Boolean): State<Float> {
    val track = if (reference != null) {
        val observed by rememberTrack<RemoteAudioTrack>(reference)
        observed
    } else null
    val muted = if (reference != null) {
        val observed by rememberTrackMuted(reference)
        observed
    } else true
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(0f, track, enabled && !muted, lifecycle) {
        value = 0f
        if (track == null || !enabled || muted) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val meter = PcmLipSyncMeter()
            val sink = object : AudioTrackSink {
                override fun onData(
                    audioData: ByteBuffer,
                    bitsPerSample: Int,
                    sampleRate: Int,
                    numberOfChannels: Int,
                    numberOfFrames: Int,
                    absoluteCaptureTimestampMs: Long,
                ) {
                    meter.accept(audioData, bitsPerSample, numberOfChannels, numberOfFrames, SystemClock.elapsedRealtime())
                }
            }
            try {
                track.addSink(sink)
                while (isActive) {
                    value = meter.openingAt(SystemClock.elapsedRealtime())
                    delay(33)
                }
            } finally {
                track.removeSink(sink)
                value = 0f
            }
        }
    }
}
