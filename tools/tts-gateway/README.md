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

## 音色库（voices.json）

复制 `voices.example.json` 为 `voices.json`，为每个音色准备一段参考录音（`referenceAudio`）及其逐字文本（`referenceText`）。音色 ID 必须与后端 `app.simulation.tts.voices` 一致：青年 / 中年 / 老年 × 男 / 女 / 中性，每类主选 `_a` 与备选 `_b`，共 18 个。

- 参考音频**可自行生成，也可从网上获取**；优先使用明确允许使用的公开素材，并在每条的 `source` 字段记录来源（决策 D-3）。
- 参考音频文件只放在 TTS 主机的网关目录，**不提交到仓库**。

## 实测记录（V-09 / TBD-10）

| 项目 | 结果 |
|---|---|
| CosyVoice 版本 / 模型 | 未执行：本机未部署 CosyVoice |
| 采样率、GPU 型号、显存占用 | 未执行 |
| 40 字台词合成耗时与实时率（目标 < 1） | 未执行 |
| `--dry-run` 联调 | 2026-10-19 本机通过：`/healthz` 返回 18 个音色；`/v1/synthesize` 返回 mp3 与逐句时长 |
