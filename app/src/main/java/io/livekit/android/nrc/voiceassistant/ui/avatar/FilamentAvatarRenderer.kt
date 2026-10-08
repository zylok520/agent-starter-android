package io.livekit.android.nrc.voiceassistant.ui.avatar

import android.opengl.Matrix
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.sin

/** All Filament calls, including teardown, stay on the Android main thread. */
internal class FilamentAvatarRenderer private constructor(private val engine: Engine) : AutoCloseable {
    companion object {
        fun create(texture: TextureView, bytes: ByteArray, onFailure: (Throwable) -> Unit): FilamentAvatarRenderer {
            Filament.init()
            Gltfio.init()
            val controller = FilamentAvatarRenderer(Engine.create())
            try {
                controller.initialize(texture, bytes, onFailure)
                return controller
            } catch (failure: Throwable) {
                controller.close()
                throw failure
            }
        }
    }

    private val releases = mutableListOf<() -> Unit>({ engine.destroy() })
    private val choreographer = Choreographer.getInstance()
    private val helper = UiHelper(UiHelper.ContextErrorPolicy.CHECK)
    private var swapChain: SwapChain? = null
    private var running = false
    private var closed = false
    private var lastFrame = 0L
    private var rootInstance = 0
    private data class FaceMorphs(val instance: Int, val weights: FloatArray, val mouth: Int, val blink: Int)
    private val faces = mutableListOf<FaceMorphs>()
    private val transform = FloatArray(16)
    private var yaw = 0f
    private var touchX = 0f
    private var texture: TextureView? = null
    private var onFailure: (Throwable) -> Unit = {}
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    var mouthOpening = 0f

    private fun <T> own(value: T, release: (T) -> Unit): T = value.also {
        releases += { release(value) }
    }

    private fun entity(): Int = own(EntityManager.get().create()) {
        engine.destroyEntity(it)
        EntityManager.get().destroy(it)
    }

    private fun initialize(texture: TextureView, bytes: ByteArray, onFailure: (Throwable) -> Unit) {
        this.texture = texture
        this.onFailure = onFailure
        renderer = own(engine.createRenderer(), engine::destroyRenderer)
        scene = own(engine.createScene(), engine::destroyScene)
        view = own(engine.createView(), engine::destroyView)
        val cameraEntity = entity()
        camera = own(engine.createCamera(cameraEntity)) { engine.destroyCameraComponent(cameraEntity) }
        camera.setExposure(16f, 1f / 125f, 100f)
        view.scene = scene
        view.camera = camera
        scene.skybox = own(Skybox.Builder().color(0.035f, 0.06f, 0.09f, 1f).build(engine), engine::destroySkybox)
        scene.indirectLight = own(IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.6f, 0.7f, 0.9f)).intensity(25_000f).build(engine), engine::destroyIndirectLight)
        val light = entity()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.92f, 0.84f).intensity(100_000f)
            .direction(-0.5f, -0.7f, -1f).castShadows(false).build(engine, light)
        scene.addEntity(light)

        val materials = own(UbershaderProvider(engine)) { it.destroyMaterials(); it.destroy() }
        val loader = own(AssetLoader(engine, materials, EntityManager.get())) { it.destroy() }
        val resources = own(ResourceLoader(engine)) { it.destroy() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
        val asset = own(requireNotNull(loader.createAsset(buffer)) { "Invalid avatar GLB" }) {
            scene.removeEntities(it.entities)
            loader.destroyAsset(it)
        }
        resources.loadResources(asset)
        val renderables = engine.renderableManager
        for (e in asset.renderableEntities) {
            val instance = renderables.getInstance(e)
            val names = asset.getMorphTargetNames(e)
            val mouth = names.indexOf("mouthOpen")
            val blink = names.indexOf("blink")
            if (mouth >= 0 || blink >= 0) {
                faces += FaceMorphs(instance, FloatArray(names.size), mouth, blink)
            }
        }
        check(faces.any { it.mouth >= 0 }) { "Avatar has no mouthOpen morph target" }
        check(faces.any { it.blink >= 0 }) { "Avatar has no blink morph target" }
        rootInstance = engine.transformManager.getInstance(asset.root)
        scene.addEntities(asset.entities)
        asset.releaseSourceData()
        helper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                try {
                    swapChain?.let(engine::destroySwapChain)
                    swapChain = null
                    swapChain = engine.createSwapChain(surface)
                } catch (failure: Exception) {
                    setActive(false)
                    onFailure(failure)
                }
            }
            override fun onDetachedFromSurface() {
                swapChain?.let(engine::destroySwapChain)
                swapChain = null
                engine.flushAndWait()
            }
            override fun onResized(width: Int, height: Int) {
                if (width <= 0 || height <= 0) return
                view.viewport = Viewport(0, 0, width, height)
                val aspect = width.toDouble() / height
                camera.setProjection(40.0, aspect, 0.05, 100.0, Camera.Fov.VERTICAL)
                camera.lookAt(0.0, 0.3, max(3.7, 2.3 / aspect), 0.0, 0.2, 0.0, 0.0, 1.0, 0.0)
            }
        }
        helper.attachTo(texture)
        texture.contentDescription = "3D 语音助手，左右拖动可转动人物"
        texture.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { touchX = event.x; v.parent?.requestDisallowInterceptTouchEvent(true) }
                MotionEvent.ACTION_MOVE -> {
                    yaw = (yaw + (event.x - touchX) * 0.25f).coerceIn(-35f, 35f)
                    touchX = event.x
                }
                MotionEvent.ACTION_UP -> { v.performClick(); v.parent?.requestDisallowInterceptTouchEvent(false) }
                MotionEvent.ACTION_CANCEL -> v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            true
        }
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running || closed) return
            try {
                if (helper.isReadyToRender && frameTimeNanos - lastFrame >= 33_000_000L) {
                    lastFrame = frameTimeNanos
                    val seconds = frameTimeNanos / 1_000_000_000.0
                    val blinkPhase = seconds % 4.5
                    val blinkWeight = when {
                        blinkPhase < 0.09 -> (blinkPhase / 0.09).toFloat()
                        blinkPhase < 0.20 -> ((0.20 - blinkPhase) / 0.11).toFloat()
                        else -> 0f
                    }
                    // Submit both expressions together: mouth and eyes can share one mesh.
                    for (face in faces) {
                        if (face.mouth >= 0) face.weights[face.mouth] = mouthOpening.coerceIn(0f, 1f)
                        if (face.blink >= 0) face.weights[face.blink] = blinkWeight
                        engine.renderableManager.setMorphWeights(face.instance, face.weights, 0)
                    }
                    Matrix.setRotateM(transform, 0, yaw + sin(seconds * 0.7).toFloat() * 2f, 0f, 1f, 0f)
                    transform[13] = sin(seconds * 1.6).toFloat() * 0.012f
                    engine.transformManager.setTransform(rootInstance, transform)
                    swapChain?.let {
                        if (renderer.beginFrame(it, frameTimeNanos)) {
                            renderer.render(view)
                            renderer.endFrame()
                        }
                    }
                }
                choreographer.postFrameCallback(this)
            } catch (failure: Exception) {
                setActive(false)
                onFailure(failure)
            }
        }
    }

    fun setActive(active: Boolean) {
        if (closed || running == active) return
        running = active
        choreographer.removeFrameCallback(frame)
        if (active) { lastFrame = 0; choreographer.postFrameCallback(frame) }
        else mouthOpening = 0f
    }

    override fun close() {
        if (closed) return
        setActive(false)
        closed = true
        texture?.setOnTouchListener(null)
        texture = null
        helper.detach()
        releases.asReversed().forEach { it() }
        releases.clear()
    }
}
