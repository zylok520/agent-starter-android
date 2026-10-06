package io.livekit.android.example.voiceassistant.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.livekit.android.example.voiceassistant.R
import io.livekit.android.example.voiceassistant.hardcodedToken
import io.livekit.android.example.voiceassistant.hardcodedUrl
import io.livekit.android.example.voiceassistant.homepageAgentEndpoint
import io.livekit.android.example.voiceassistant.tokenServerId
import io.livekit.android.example.voiceassistant.ui.theme.Blue500
import kotlinx.serialization.Serializable
import androidx.compose.ui.layout.ContentScale

@Serializable
object ConnectRoute

@Composable
fun ConnectScreen(
    navigateToVoiceAssistant: (VoiceAssistantRoute) -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
				painter = painterResource(R.drawable.customer_service),
				contentDescription = "语音客服",
				contentScale = ContentScale.Fit,
				modifier = Modifier
					.fillMaxWidth(0.75f)
					.size(height = 300.dp, width = 300.dp)
			)

            Spacer(Modifier.size(16.dp))
            Text(
                text = buildAnnotatedString {
                    append("准备就绪，点击下方按钮开始通话\n与您的机器女友聊天\n")
                    withLink(
                        LinkAnnotation.Url(
                            "#",
                            TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
                        )
                    ) {
                        append("")
                    }
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.8f)
            )

            var hasError by rememberSaveable { mutableStateOf(false) }
            var isConnecting by remember { mutableStateOf(false) }

            Spacer(Modifier.size(8.dp))

            AnimatedVisibility(hasError) {
                Text(
                    text = "连接错误。确保您的配置正确，然后重试。",
                    color = Color.Red,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }

            Spacer(Modifier.size(24.dp))

			val buttonColors = ButtonDefaults.buttonColors(
				containerColor = Color(0xFF34C759), // 拨号绿色
				contentColor = Color.White
			)

			Button(
				colors = buttonColors,
				shape = RoundedCornerShape(30.dp),
				modifier = Modifier
					.fillMaxWidth(0.55f)
					.size(height = 60.dp, width = 220.dp),
				onClick = {
					val route = VoiceAssistantRoute(
						tokenServerId = tokenServerId,
						hardcodedUrl = hardcodedUrl,
						hardcodedToken = hardcodedToken,
						homepageAgentEndpoint = homepageAgentEndpoint
					)
					navigateToVoiceAssistant(route)
				}
			) {
				Row(
					verticalAlignment = Alignment.CenterVertically
				) {
					AnimatedVisibility(isConnecting) {
						Row(verticalAlignment = Alignment.CenterVertically) {
							CircularProgressIndicator(
								modifier = Modifier.size(24.dp),
								color = Color.White,
								trackColor = Color.White.copy(alpha = 0.3f),
								strokeWidth = 2.dp
							)
							Spacer(Modifier.size(10.dp))
						}
					}

					Text(
						text = if (isConnecting) "拨通中..." else "拨 打",
						style = TextStyle(
							fontFamily = FontFamily.Monospace,
							fontSize = 18.sp,
							letterSpacing = 2.sp
						)
					)
				}
			}
        }
    }
}