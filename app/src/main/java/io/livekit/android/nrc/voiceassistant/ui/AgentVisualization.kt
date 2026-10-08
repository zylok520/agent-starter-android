package io.livekit.android.nrc.voiceassistant.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.livekit.android.annotations.Beta
import io.livekit.android.compose.state.Agent
import io.livekit.android.compose.state.AgentState
import io.livekit.android.compose.ui.ScaleType
import io.livekit.android.compose.ui.VideoTrackView
import io.livekit.android.nrc.voiceassistant.ui.anim.CircleReveal
import io.livekit.android.nrc.voiceassistant.ui.avatar.LocalAvatar
import io.livekit.android.nrc.voiceassistant.ui.avatar.rememberMouthOpening

private val revealSpringSpec = spring<Float>(stiffness = Spring.StiffnessVeryLow)
private val hideSpringSpec = spring<Float>(stiffness = Spring.StiffnessMedium)

@OptIn(Beta::class)
@Composable
fun AgentVisualization(agent: Agent, modifier: Modifier = Modifier) {
    val videoTrack = agent.videoTrack
    var hasFirstFrameRendered by remember(videoTrack) { mutableStateOf(false) }
    val revealed = videoTrack != null && hasFirstFrameRendered
    val mouthOpening by rememberMouthOpening(agent.audioTrack, enabled = agent.isConnected && !revealed)

    Box(modifier = modifier) {
        if (videoTrack != null) {
            VideoTrackView(
                trackReference = videoTrack,
                scaleType = ScaleType.FitInside,
                onFirstFrameRendered = { hasFirstFrameRendered = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        CircleReveal(
            revealed = revealed,
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                ) {
                    LocalAvatar(mouthOpening, Modifier.weight(1f).fillMaxWidth())
                    Text(
                        text = when (agent.agentState) {
                            AgentState.SPEAKING -> "正在回答"
                            AgentState.THINKING -> "正在思考"
                            AgentState.LISTENING -> "我在听，请说话"
                            AgentState.DISCONNECTED, AgentState.FAILED -> "连接已断开"
                            else -> "正在连接语音助手"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            animationSpec = if (revealed) revealSpringSpec else hideSpringSpec,
        )
    }
}
