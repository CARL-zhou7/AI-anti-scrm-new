# CosyVoice 语音网关（tts-gateway）

供后端 `CosyVoiceGatewayClient` 调用的内网语音合成服务。只部署在 TTS 主机上，Python 依赖与 ffmpeg 不进入 Java / 前端依赖树；只监听内网地址，不对公网开放。

## 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/v1/synthesize` | `{"voiceId","speed","segments":[...]}` → `{"format":"mp3","audioBase64","segmentDurationsMs":[...],"totalDurationMs"}` |
| GET | `/healthz` | `{"model","voices":[...],"dryRun"}`，用于启动检查与 V-09 记录 |

- 后端已按句末与逗号类标点切好分句；网关逐句合成，按**实际 PCM 采样点数**计算每句时长，拼接后用 ffmpeg（libmp3lame，单声道）一次转码为 MP3。
- `speed` 被限制在 0.8～1.2；后端只把语气提示映射为语速（慢 0.9 / 常规 1.0 / 快 1.1），不改动台词文本。
- 网关日志不记录台词内容；错误只返回 `{"error": "..."}`，不透出模型内部异常。

## 运行

```bash
# 无 GPU 的开发机：联调格式（静音 MP3，按字数估算时长）
python app.py --dry-run --port 50080 --voices voices.example.json

# TTS 主机：先按 CosyVoice 官方仓库 README 安装 CosyVoice 与模型，再安装 numpy 与 ffmpeg
python app.py --host <内网地址> --port 50080 --voices voices.json --model-dir pretrained_models/CosyVoice2-0.5B
```

后端配置：`TTS_PROVIDER=cosyvoice`、`TTS_GATEWAY_URL=http://<内网地址>:50080`。

## 已安装的 Windows 本机环境

本机环境保存在被 Git 忽略的 `.tools/`，使用 Python 3.10.22、PyTorch/torchaudio 2.3.1+cu121、RTX 4060 Laptop 8GB，以及官方 CosyVoice 源码 `074ca6dc9e80a2f424f1f74b48bdd7d3fea531cc`。CosyVoice2-0.5B 推理文件已按官方 ModelScope 清单核对 SHA-256。当前源码的零样本接口接收参考音频路径，网关据此传参。

完成本机安装后，从仓库根目录启动，保持该窗口运行；已在后台运行时不要重复启动：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\tts-gateway\start-local.ps1
```

执行策略仅作用于这个 PowerShell 进程。脚本使用项目内的 ffmpeg、音色及缓存，仅监听 `127.0.0.1:50080`。重启电脑后需先重新启动网关，再启动后端。

`backend/.env` 的本机配置如下，修改后重启后端：

```properties
TTS_PROVIDER=cosyvoice
TTS_GATEWAY_URL=http://127.0.0.1:50080
app.simulation.tts.call-timeout-ms=60000
```

访问 `http://127.0.0.1:50080/healthz`，应返回 `dryRun=false` 和 18 个音色 ID。当前后台实例的日志在 `.tools/tts-local/gateway.stdout.log` 与 `gateway.stderr.log`。

本次先用 Windows 系统语音生成三段中文参考录音，配置在 `.tools/tts-local/voices.json`。18 个音色 ID 复用这些录音，年龄组及主备音色尚未分别校准；替换参考录音后需重启网关。

本机 WeText 0.0.4 的文本规范化资源已缓存；其安装文件 `wetext/wetext.py` 的资源加载调用加入 `local_files_only=True`，避免启动时依赖线上查询或用户目录权限。重新安装该依赖后需恢复此本机补丁。

2026-10-19 真实 HTTP 合成验证：两种声音的 MP3 解码后均非静音，分句时长与实际音频一致。约 32 字测试耗时 11.3～14.5 秒，实际场景首句（26 字、3 段）约 10.7 秒，120 字（11 段、慢速 0.9）约 40.4 秒。因此本机覆盖为 60 秒超时；这些测试的实时率尚未达到小于 1 的目标，不代表并发容量验收。

## 音色库（voices.json）

复制 `voices.example.json` 为 `voices.json`，为每个音色准备一段参考录音（`referenceAudio`）及其逐字文本（`referenceText`）。音色 ID 必须与后端 `app.simulation.tts.voices` 一致：青年 / 中年 / 老年 × 男 / 女 / 中性，每类主选 `_a` 与备选 `_b`，共 18 个。

- 参考音频**可自行生成，也可从网上获取**；优先使用明确允许使用的公开素材，并在每条的 `source` 字段记录来源（决策 D-3）。
- 参考音频文件只放在 TTS 主机的网关目录，**不提交到仓库**。

## 实测记录（V-09 / TBD-10）

| 项目 | 结果 |
|---|---|
| CosyVoice 版本 / 模型 | 已安装：官方源码 `074ca6dc9e80a2f424f1f74b48bdd7d3fea531cc` / CosyVoice2-0.5B |
| 采样率、GPU 型号、显存占用 | 24 kHz；RTX 4060 Laptop 8GB；两次测试后约 2853 MiB |
| 台词合成耗时与实时率（目标 < 1） | 真实合成及时间轴验证通过；耗时见上方，本机实时率目标尚未达到 |
| `--dry-run` 联调 | 2026-10-19 本机通过：`/healthz` 返回 18 个音色；`/v1/synthesize` 返回 mp3 与逐句时长 |
