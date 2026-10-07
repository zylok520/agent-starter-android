package io.livekit.android.nrc.voiceassistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import io.livekit.android.LiveKit
import io.livekit.android.nrc.voiceassistant.screen.VoiceAssistantRoute
import io.livekit.android.token.TokenSource

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

    override fun onCleared() {
        super.onCleared()
        room.disconnect()
        room.release()
    }
}
