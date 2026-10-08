package io.livekit.android.nrc.voiceassistant.call

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.livekit.android.nrc.voiceassistant.MainActivity
import io.livekit.android.room.Room
import io.livekit.android.room.participant.isAgent
import io.livekit.android.util.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Started while the Activity is visible and microphone permission has been granted. */
class VoiceCallService : Service() {
    companion object {
        private const val CHANNEL = "voice_call"
        private const val NOTIFICATION_ID = 1001
        private const val END_CALL = "io.livekit.android.nrc.voiceassistant.END_CALL"
    }

    inner class CallBinder : Binder() {
        val service: VoiceCallService get() = this@VoiceCallService
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val policy = CallTimeoutPolicy()
    private val _keepScreenOn = MutableStateFlow(true)
    val keepScreenOn = _keepScreenOn.asStateFlow()
    private val _ended = MutableStateFlow(false)
    val ended = _ended.asStateFlow()
    private var room: Room? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundReady = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "语音通话", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 1,
            Intent(this, VoiceCallService::class.java).setAction(END_CALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("正在语音通话")
            .setContentText("锁屏后可继续对话")
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "挂断", end)
            .build()
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            // Keep the audio connection and timeout checks running while the screen is off.
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:VoiceCall")
                .apply { acquire() }
            foregroundReady = true
        } catch (_: RuntimeException) {
            endCall()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == END_CALL) endCall()
        return START_NOT_STICKY // Never restart microphone capture after process death.
    }

    override fun onBind(intent: Intent?): IBinder = CallBinder()

    fun attach(callRoom: Room) {
        check(foregroundReady && !_ended.value) { "Background microphone service could not start" }
        if (room === callRoom) return
        check(room == null)
        room = callRoom
        scope.launch {
            callRoom.localParticipant::isSpeaking.flow.collect { speaking ->
                // Reset while speaking so a long question cannot time out halfway through.
                if (speaking) question()
            }
        }
        scope.launch {
            var connectedOnce = false
            var wasSpeaking = false
            val power = getSystemService(PowerManager::class.java)
            val keyguard = getSystemService(KeyguardManager::class.java)
            while (!_ended.value) {
                connectedOnce = connectedOnce || callRoom.state == Room.State.CONNECTED
                if (connectedOnce && callRoom.state == Room.State.DISCONNECTED) {
                    endCall()
                    break
                }
                val speaking = callRoom.localParticipant.isSpeaking
                if (speaking || wasSpeaking) question()
                wasSpeaking = speaking
                if (!speaking && callRoom.remoteParticipants.values.any {
                        it.isAgent && (it.isSpeaking || it.attributes["lk.agent.state"] == "speaking")
                    }) reply()
                val locked = !power.isInteractive || keyguard.isKeyguardLocked
                val decision = policy.update(SystemClock.elapsedRealtime(), locked)
                _keepScreenOn.value = decision.keepScreenOn
                if (decision.endCall) endCall()
                if (locked) {
                    // These are idempotent; retry if locking raced with video publication.
                    runCatching { callRoom.localParticipant.setCameraEnabled(false) }
                    runCatching { callRoom.localParticipant.setScreenShareEnabled(false) }
                }
                delay(250)
            }
        }
    }

    fun question() { policy.question(SystemClock.elapsedRealtime()) }
    fun reply() { policy.reply() }

    fun endCall() {
        if (_ended.value) return
        _ended.value = true
        _keepScreenOn.value = false
        room?.disconnect()
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { endCall() }
    override fun onDestroy() {
        endCall()
        super.onDestroy()
    }
}
