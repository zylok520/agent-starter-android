package io.livekit.android.nrc.voiceassistant.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.livekit.android.LiveKit
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.state.Session
import io.livekit.android.compose.state.SessionMessages
import io.livekit.android.nrc.voiceassistant.call.VoiceCallService
import io.livekit.android.nrc.voiceassistant.screen.VoiceAssistantRoute
import io.livekit.android.room.participant.isAgent
import io.livekit.android.token.TokenSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@OptIn(Beta::class)
class VoiceAssistantViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    val room = LiveKit.create(application)
    private val route = savedStateHandle.toRoute<VoiceAssistantRoute>()

    // Values are freshly issued by our server, despite the legacy field names.
    val tokenSource: TokenSource = TokenSource.fromLiteral(
        route.hardcodedUrl, route.hardcodedToken,
    )

    private var service: VoiceCallService? = null
    private var connection: ServiceConnection? = null
    private var starting = false
    private var connectJob: Job? = null
    private var messagesJob: Job? = null
    private val seenMessages = mutableMapOf<String, String>()
    private val _ended = MutableStateFlow(false)
    val ended = _ended.asStateFlow()
    private val _keepScreenOn = MutableStateFlow(false)
    val keepScreenOn = _keepScreenOn.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** Called only after permission is granted and the Activity is RESUMED. */
    fun startCall(session: Session) {
        if (starting || _ended.value) return
        starting = true
        val app = getApplication<Application>()
        val intent = Intent(app, VoiceCallService::class.java)
        val binding = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (_ended.value) return
                val callService = (binder as VoiceCallService.CallBinder).service
                service = callService
                try {
                    callService.attach(room)
                } catch (_: RuntimeException) {
                    failCall()
                    return
                }
                viewModelScope.launch { callService.keepScreenOn.collect { _keepScreenOn.value = it } }
                viewModelScope.launch { callService.ended.collect { if (it) endCall() } }
                connectJob = viewModelScope.launch {
                    try {
                        if (session.start().isFailure) failCall()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        failCall()
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) { endCall() }
            override fun onBindingDied(name: ComponentName) { endCall() }
            override fun onNullBinding(name: ComponentName) { failCall() }
        }
        try {
            ContextCompat.startForegroundService(app, intent)
            if (app.bindService(intent, binding, Context.BIND_AUTO_CREATE)) {
                connection = binding
            } else {
                failCall()
            }
        } catch (_: RuntimeException) {
            failCall()
        }
    }

    fun observeMessages(messages: SessionMessages) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            snapshotFlow { messages.messages }.collect { current ->
                current.forEach { message ->
                    if (message.message.isNotBlank() && seenMessages[message.id] != message.message) {
                        seenMessages[message.id] = message.message
                        val sender = message.fromParticipant
                        if (sender === room.localParticipant) service?.question()
                        else if (sender?.isAgent == true) service?.reply()
                    }
                }
            }
        }
    }

    private fun failCall() {
        _error.value = "无法启动语音通话，请检查麦克风权限和网络后重试。"
        endCall()
    }

    fun stopVisualMedia() {
        viewModelScope.launch {
            runCatching { room.localParticipant.setCameraEnabled(false) }
            runCatching { room.localParticipant.setScreenShareEnabled(false) }
        }
    }

    fun endCall() {
        if (_ended.value) return
        _ended.value = true
        _keepScreenOn.value = false
        connectJob?.cancel()
        messagesJob?.cancel()
        service?.endCall()
        service = null
        room.disconnect()
        val app = getApplication<Application>()
        connection?.let { app.unbindService(it) }
        connection = null
        app.stopService(Intent(app, VoiceCallService::class.java))
    }

    override fun onCleared() {
        endCall()
        super.onCleared()
        room.release()
    }
}
