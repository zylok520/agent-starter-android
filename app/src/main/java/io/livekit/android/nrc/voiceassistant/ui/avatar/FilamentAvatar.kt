package io.livekit.android.nrc.voiceassistant.ui.avatar

import android.util.Log
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FilamentAvatar(mouthOpening: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val model by produceState<Result<ByteArray>?>(null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("models/avatar-sample-a.glb").use { it.readBytes() } }
        }
    }
    var failed by remember { mutableStateOf(false) }
    var controller by remember { mutableStateOf<FilamentAvatarRenderer?>(null) }
    val bytes = model?.getOrNull()
    if (failed || bytes == null) {
        LocalAvatar(mouthOpening, modifier)
    } else {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                TextureView(ctx).also { texture ->
                    try {
                        controller = FilamentAvatarRenderer.create(texture, bytes) {
                            Log.w("FilamentAvatar", "Rendering failed; using 2D avatar", it)
                            failed = true
                        }
                    } catch (failure: Exception) {
                        Log.w("FilamentAvatar", "Could not load 3D avatar", failure)
                        failed = true
                    } catch (failure: LinkageError) {
                        Log.w("FilamentAvatar", "Filament unavailable on this device", failure)
                        failed = true
                    }
                }
            },
            update = { controller?.mouthOpening = mouthOpening },
            onRelease = { controller?.close(); controller = null },
        )
    }
    DisposableEffect(controller, lifecycle) {
        val current = controller
        fun updateActivity() { current?.setActive(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        val observer = LifecycleEventObserver { _, _ -> updateActivity() }
        lifecycle.addObserver(observer)
        updateActivity()
        onDispose {
            lifecycle.removeObserver(observer)
            current?.setActive(false)
        }
    }
}
