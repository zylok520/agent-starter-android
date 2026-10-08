# Filament + GLB 本地 3D 原型

## AvatarSample_A（当前默认）

默认资源为 `models/avatar-sample-a.glb`（7,135,576 字节），完整身体、原始彩色贴图、双臂下放。模型来自 VRoid 官方样例的公开再分发镜像；来源、哈希和使用条件见 `avatar-source/AvatarSample_A-LICENSE.md`，不适用项目代码许可证。

`python tools/convert_vroid_avatar.py` 从保留的 `avatar-source/AvatarSample_A.vrm` 重建 GLB，需要 NumPy。保留完整三维几何，以标准 unlit 材质近似原 MToon 外观；不包含描边、头发物理和骨骼动画。静态身体姿态已烘焙，脸部保留两个独立变形。

渲染器按 `mesh.extras.targetNames` 查找 `mouthOpen` 和 `blink` 的实际索引，一次提交同一网格的全部权重，避免眨眼覆盖嘴型。兼容保留的旧原型。

已验证：GLB 验证器 0 errors / 0 warnings，11 条未使用 sampler 提示；控制器以真实 Filament/gltfio 1.77.0 依赖独立编译通过。实际网格离线预览见 `avatar-source/avatar-sample-a-preview.png`，不是真机截图。重新尝试 APK 构建仍遇到 `Unable to establish loopback connection`；真机显示、性能与同步尚未验收。

## 照片参考卡通版（已保留，非默认）

旧模型 `models/portrait-avatar.glb` 仍保留。黑色长发、白色翻领上衣和面部比例参考用户照片；这是程序建模的卡通半身像，不是照片级人脸重建，也不是照片贴片。

- 64 个三维网格，约 2.1 MiB，包含头部、后脑、分层长发、鼻部、眼睛、衣领、纽扣和上臂。
- `mouthOpen` 同时驱动口腔、上唇、下唇；`blink` 同时驱动眼白、虹膜、瞳孔、高光和上眼线。
- 实际模型四视图见 `avatar-source/portrait-3d-preview.png`，由 CPU 预览器直接读取 GLB 网格渲染，非 AI 效果图，也非 Android 真机截图；Filament 的灯光和色彩会有所不同。
- 生成：`python tools/generate_portrait_avatar.py`；预览：`python tools/preview_avatar.py`。需要 NumPy 和 Pillow。
- GLB 验证器检查无错误、无警告；渲染控制器独立编译通过。完整 APK 和真机验收仍未完成，先前 Gradle 回环连接错误尚未解决。

下文的 `prototype-avatar.glb` 是保留的旧版基础原型。

通话页面现在默认显示本地三维卡通人物。现有远端音频 PCM → `PcmLipSyncMeter` → 0–1 嘴巴开合值，直接驱动 GLB 的 `mouthOpen` Morph Target。保留当前 Kokoro、识别、LLM、LiveKit 音频播放及双方文字配色。

## 使用

- 开始语音通话，Agent 发声时嘴巴随音量开合，静音后闭嘴。
- 人物自动眨眼、轻微上下起伏和转头；左右拖动可在 ±35° 内转动人物。
- 界面不处于 RESUMED 状态时停止帧回调；锁屏通话继续由现有服务处理。
- 退出界面后释放 surface、模型、材质、灯光、相机和渲染引擎。
- 模型读取、初始化或可捕获的渲染异常会回退到原有 2D 人像。原生驱动进程崩溃无法由该回退捕获。
- 若服务端发布数字人视频，沿用现有逻辑优先显示视频。

## 文件与模型约定

- `ui/avatar/FilamentAvatar.kt`：Compose 与 TextureView 适配、异步模型读取、生命周期及 2D 回退。
- `ui/avatar/FilamentAvatarRenderer.kt`：Filament 1.77.0、灯光、相机、约 30 FPS 渲染、嘴型和眨眼权重更新。
- `app/src/main/assets/models/prototype-avatar.glb`：项目自有的程序生成卡通半身人物，19 个网格、588,336 字节，无外部贴图。
- `tools/generate_avatar.py`：仅依赖 Python 标准库的可重复生成脚本。执行 `python tools/generate_avatar.py` 重建模型。

这个模型是验证同步和渲染的简易原型，不是写实人物。生成几何没有引入第三方人物资产。

当前加载器约定：在 `mesh.extras.targetNames` 中标记 `mouthOpen` 和 `blink`，可共用同一网格，索引不必为 0。模型 Y 向上、面向 +Z；转换器把完整人物高度归一化为 2.7、中心 Y 为 0.2。替换任意其他 GLB/VRM 时仍需检查材质、表情、朝向和取景。

## 已完成验证与限制

- Khronos `gltf-validator` 2.0.0-dev.3.10：0 errors、0 warnings、0 infos、0 hints。
- 使用真实 Filament / gltfio 1.77.0 AAR 与 Android API 桩，独立编译 `FilamentAvatarRenderer.kt` 成功。此项不等同于完整 Android 或 Compose 构建。
- 两个 Filament AAR 的最低 API 为 21，项目最低 API 24 无需因此提升。
- 完整 `testDebugUnitTest assembleDebug --no-daemon` 被本机 `Unable to establish loopback connection` 阻挡；尚未生成本次 APK，未完成真机画面、性能和音画同步验证。

真机验收需覆盖：模型正常显示、中文与英文停顿／打断、拖动转头、深浅主题、聊天缩略布局、旋转屏幕、反复进入退出、锁屏再解锁、蓝牙音频，以及持续通话的发热和内存情况。可以临时使用不存在的模型路径验证 2D 回退。

参考：[Filament 官方项目](https://github.com/google/filament)。当前仍为音量驱动开合，不是音素级唇形。
