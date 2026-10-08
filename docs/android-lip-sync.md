# Android 本地 2D 数字人（第一版）

使用现有 `Agent.audioTrack` 选出的远端音频轨道，经 `RemoteAudioTrack.addSink` 接收解码 PCM。在回调内计算 RMS 音量，再驱动 Compose Canvas 人像的嘴巴开合。不会采集本地麦克风来驱动数字人，也不新增音频播放器。

SenseVoice、Qwen、Kokoro、Turn Detector 及 Agent 发布流程无需修改；无需数字人 API key、视频生成模型或服务器算力。现有远端视频轨道若有画面，仍优先显示远端视频。

## 行为与参数

- 16-bit PCM，支持单声道及交错多声道；按每个采样的能量计算，避免反相声道抵消。
- `PcmLipSyncMeter` 中噪声门限为 0.008 RMS，完全张嘴阈值为 0.18 RMS；可根据实际 Kokoro 音量调整。
- 快速张嘴、平滑闭嘴：时间常数分别为 40 / 75 ms，画面约 30 Hz 更新。
- 150 ms 未收到音频帧后进入闭嘴衰减，避免断流后嘴巴停在张开状态。
- 静音、轨道替换、断线、界面退出或进入后台时移除音频 sink 并复位嘴型。回到前台重新挂载；后台语音通话保持原有行为。
- PCM 缓冲只在回调中读取，不修改、不缓存、不写文件；UI 仅读取音量和单调时间戳。
- 自己的文字使用绿色 `#86EFAC`，对方使用浅蓝色 `#D7E9FF`，深色气泡在浅色和深色主题中保持可读性。颜色集中在 `ui/theme/Color.kt`。

这是音量驱动开合，不是中文／英文音素嘴型。PCM 接收时点与设备实际扬声器输出之间仍可能存在播放延迟，蓝牙设备尤其需要真机确认。

## 验证

`PcmLipSyncMeterTest` 的 7 项独立 JVM 测试通过：静音／低噪声、不同音量、反相立体声及缓冲位置保护、削波及平滑、断流闭嘴、静音帧闭嘴、无效格式和短缓冲。

完整 `testDebugUnitTest assembleDebug` 被本机 Gradle 的 `Unable to establish loopback connection` 阻挡，尚未完成 Android 编译或真机画面与同步验证。

真机验收：

1. 让 Kokoro 连续说中文、英文和带停顿的句子，确认嘴型跟随声音强弱，停顿闭嘴。
2. 仅用户说话而 Agent 不发声时，数字人保持闭嘴；打断 Agent 后确认及时闭嘴。
3. 锁屏继续音频对话、解锁恢复动画；切后台／返回和旋转屏幕不产生重复 sink。
4. 断线、重连及重新进入通话后，验证轨道重新绑定和正常闭嘴。
5. 浅色／深色主题下检查双方文字颜色，并验证聊天小窗和全屏数字人的布局。
6. 分别使用扬声器和蓝牙耳机检查口型与实际播放声音的时差。

参考：[LiveKit Avatar 文档](https://docs.livekit.io/agents/models/avatar/)描述的服务端 Avatar worker 是另一条可选升级路径，本实现不需要接入该插件流程。
