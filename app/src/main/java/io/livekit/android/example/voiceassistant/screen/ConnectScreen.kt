package io.livekit.android.example.voiceassistant.screen

import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.livekit.android.example.voiceassistant.R
import io.livekit.android.example.voiceassistant.tokenEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

@Serializable
object ConnectRoute

@Composable
fun ConnectScreen(navigateToVoiceAssistant: (VoiceAssistantRoute) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    // Password stays in memory; it is not written to saved state or navigation.
    var password by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.customer_service),
                contentDescription = "语音客服",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(180.dp),
            )
            Text("登录并开始语音通话", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text("账号") },
                singleLine = true,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                label = { Text("密码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !connecting,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !connecting && username.isNotBlank() && password.isNotEmpty(),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onClick = {
                    connecting = true
                    error = null
                    val login = username.trim()
                    val secret = password
                    scope.launch {
                        try {
                            val route = requestConnection(login, secret)
                            password = ""
                            navigateToVoiceAssistant(route)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: TokenRequestException) {
                            error = e.message
                        } catch (e: SocketTimeoutException) {
                            error = "连接超时，请检查服务器是否已启动。"
                        } catch (e: IOException) {
                            error = "无法访问登录服务，请检查局域网、8090端口和明文网络配置。"
                        } catch (e: Exception) {
                            error = "连接信息处理失败，请检查服务配置或重试。"
                        } finally {
                            connecting = false
                        }
                    }
                },
            ) {
                if (connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (connecting) "正在验证账号…" else "登录并呼叫")
            }
        }
    }
}

private class TokenRequestException(message: String) : Exception(message)

private suspend fun requestConnection(username: String, password: String): VoiceAssistantRoute =
    withContext(Dispatchers.IO) {
        val connection = URL(tokenEndpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            val basic = Base64.encodeToString(
                "$username:$password".toByteArray(Charsets.UTF_8), Base64.NO_WRAP,
            )
            connection.setRequestProperty("Authorization", "Basic $basic")
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw TokenRequestException(
                    when (status) {
                        401, 403 -> "账号或密码错误，或账号已被停用。"
                        429 -> "请求过于频繁，请稍后重试。"
                        503 -> "登录服务尚未配置好 LiveKit，请联系管理员。"
                        else -> "登录服务返回 HTTP $status，请检查接口地址和服务日志。"
                    }
                )
            }
            val data = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                JSONObject(it.readText())
            }
            val url = data.optString("server_url").trim()
            val token = data.optString("participant_token").trim()
            if ((!url.startsWith("ws://") && !url.startsWith("wss://")) || token.isBlank()) {
                throw TokenRequestException("登录服务返回的 LiveKit 地址或 Token 无效。")
            }
            // Keep the existing route schema to preserve the current call screen.
            VoiceAssistantRoute(
                tokenServerId = "",
                hardcodedUrl = url,
                hardcodedToken = token,
                homepageAgentEndpoint = "",
            )
        } finally {
            connection.disconnect()
        }
    }
