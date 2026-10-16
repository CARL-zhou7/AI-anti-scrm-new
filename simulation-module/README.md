# AI情景演绎模块（前端）

对应 PRD V3.1 §8.4「AI情景演绎模块」FR-SIM-01～07、PC-P04、UX-01～05，按《接口文档 V1.0》API-14～21、API-42 与第5章 SSE 实现。
后端（Agent 编排、TTS、持久化）不在本模块内；本模块是 Vue 3 页面 + 组合式函数，可直接并入主工程。

## 功能概览

| 需求 | 实现 |
|---|---|
| FR-SIM-01 开场 | 场景名称/简介、双方人物入场动画；风险显示“尚未评分”；完成后提交 API-17 回执 |
| FR-SIM-02 顺序呈现 | 只呈现服务端已保存且 `PRESENTING` 的消息，骗子→受害者，用户只观看 |
| FR-SIM-03 基础动画 | 静态立绘 + 通用待机（呼吸）/ 说话（起伏、光晕、声波）动画；当前发言角色高亮，对方变暗；适用于任意上传头像 |
| FR-SIM-04 角色语音 | 一次只播一条，双方不重叠；字幕随语音进度加深（字声一致）；声音开关为静音播放同一时间轴，不跳过、不算故障 |
| FR-SIM-05/06 解说与风险 | 文字解说（阶段/话术/手段/心理/依据）；五档风险条与逐轮分值；未评分不补 0 |
| FR-SIM-07 结束与中断 | 5 秒阅读停留倒计时；正式结束后按 `readingUntil` 进入复盘；中断显示真实原因；到 600 秒立即停止播放，等待服务端确认 |
| 媒体故障 | 头像/文字/动画/语音加载/播放失败分别上报 `AVATAR_LOAD_FAILED`、`TEXT_RENDER_FAILED`、`ANIMATION_FAILED`、`AUDIO_LOAD_FAILED`、`AUDIO_PLAYBACK_FAILED`，不降级为纯文字继续 |
| 退出 | “退出演绎”二次确认后 API-20；页面内导航离开自动退出；关闭/刷新用 keepalive 尽力通知，刷新后的页面补发 `REFRESH` 并显示中断，不续演 |
| 计时 | 配置页预取 API-42 样本，点击“开始”时记录单调时钟，按冻结协议提交 `StartRequest.timing` |

说话动画在浏览器支持 Web Audio 时随语音音量变化（静音用 GainNode 实现，静音时动画仍跟随语音节奏）；不支持时退回固定节奏动画。

## 目录

```
simulation-module/
├── src/                         ← 并入主工程的模块代码（建议放到 src/modules/simulation/）
│   ├── index.js                 对外出口
│   ├── api/                     http 封套 + CSRF、演绎接口、SSE
│   ├── core/                    状态合并、呈现控制器、语音播放、动画检测、计时协议、常量
│   ├── composables/             useSimulation（演绎页）、useSimulationStarter（配置页开始按钮）
│   ├── components/              SimulationPage 及其子组件
│   └── styles/simulation.css    全部样式（类名、keyframes 均以 sim- 为前缀）
├── dev/                         仅本地联调：模拟后端（Vite 插件）、示意头像、演示外壳
└── tests/                       单元测试（vitest）
```

## 并入主工程

1. 复制 `src/` 到主工程，例如 `src/modules/simulation/`。依赖只有 `vue@3`，不需要 vue-router、axios 或动画库。
2. 注册路由（示例，vue-router 4）：

   ```js
   import { SimulationPage } from '@/modules/simulation'

   { path: '/simulations/:id', name: 'simulation', component: SimulationPage, props: true }
   ```

   在父级监听页面事件完成跳转：

   ```vue
   <SimulationPage
     :id="route.params.id"
     @report="(id) => router.push(`/simulations/${id}/report`)"
     @history="(id) => router.push(`/history/${id}`)"
     @restart="router.push('/config')"
     @home="router.push('/')"
     @unauthenticated="router.push('/login')"
   />
   ```

3. 配置页的“开始演绎”按钮使用 `useSimulationStarter`（负责 API-42 采样与 API-14）：

   ```js
   const starter = useSimulationStarter()
   async function onStartClick() {           // 直接作为 @click 处理函数，不要先 await 其他操作
     const r = await starter.start({ scenarioId, scammerProfileId, victimProfileId })
     if (r.ok) router.push({ name: 'simulation', params: { id: r.sessionId } })
     else if (r.activeSessionId) { /* 已有进行中的演绎 */ }
     else showError(r.message)
   }
   ```

   按钮可用状态绑定 `starter.canStart`。开始成功后会在 sessionStorage 标记“本标签页发起”，演绎页据此区分正常进入、刷新与其他标签页打开。

4. 若主工程已有 CSRF / 登录态管理，在应用启动时注入：

   ```js
   import { configureSimulationHttp } from '@/modules/simulation'
   configureSimulationHttp({
     getCsrfToken: () => authStore.csrfToken,         // 建议同步返回，页面关闭时的退出通知需要同步取 token
     refreshCsrfToken: () => authStore.refreshCsrf(),
     onUnauthenticated: () => router.push('/login'),
   })
   ```

   不注入时模块自行调用 `GET /auth/csrf` 并缓存。

5. 页面最小宽度 1024px（PC 端），样式全部挂在 `.sim-root` 下，不影响主工程其他页面。

## 本地联调与测试

```bash
cd simulation-module
npm install
npm run dev      # http://127.0.0.1:5173 ，自带模拟后端（固定剧本 + 合成伪语音 + SSE）
npm test         # 单元测试
```

演示页可通过 URL 参数控制模拟后端：`?speed=fast`（加快节奏）、`?fail=audio|tts|avatar|animation`（故障注入）、`?limit=30`（缩短时长上限）。
模拟后端只用于前端联调，返回字段与接口文档一致，但不代表真实后端行为；音频用 WAV 代替 MP3。

已在 Chromium 下用模拟后端跑通并核对回执序列：正常完整演绎（10 个回执后进入复盘）、静音、退出、页面内导航离开、刷新、到时中断、语音加载失败、服务端 TTS 失败、头像加载失败、动画失效。

## 尚未关闭、需要联调时确认的事项

- **V-03 / TBD-07**：真实 Chrome/Edge 目标版本下的 MP3 播放、自动播放策略、后台标签页、静音切换仍需实测。浏览器阻止有声自动播放时，本模块会切换为静音继续，并明确提示用户可重新开启声音；这一处理需要和产品确认。
- **TBD-01a / V-02**：计时样本有效期、可接受误差由后端锁定；前端在样本剩余不足 5 秒时拒绝使用并重新采样。
- **TBD-05**：刷新/关闭识别目前是 keepalive 退出通知 + 刷新后补发 `REFRESH`；失联判定由服务端负责。
- **素材**：五张初始头像需按 PRD §11.3 描述制作（`dev/mock/avatars.js` 只是示意简笔画）；场景背景目前是 CSS 绘制，可替换为插画素材。

## 与“《拣爱》式 2D 动画”讨论的关系

讨论稿中的方案 A（Vue + 视觉小说式立绘、对话框、简单说话动画、Web Audio 驱动）已按 MVP 范围落地。以下几点按基线文档处理：

- 情绪表情、动作增强、AI 控制的动作指令（`emotion` / `animation` 字段）在 PRD V3.1 中属于“未来方向”，接口里也没有这些字段，本期不做。`CharacterFigure` 的状态由 `state` 属性统一驱动，以后可以在不改动呈现控制器的前提下换成 Lottie / PixiJS 实现。
- 语音失败不能“回退到纯文字演绎”（PRD §8.4 MVP 边界、§12.2），必须中断。
- 时长上限以 600 秒为准（2026-10-16 修订）。
- TTS 方案（CosyVoice / EmotiVoice / MeloTTS 等）属于后端选型，前端只要求 API-21 返回可播放的 `audio/mpeg`。
