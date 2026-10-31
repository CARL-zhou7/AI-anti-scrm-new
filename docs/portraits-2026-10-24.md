# 人物形象图片与生成提示词

2026-10-24 使用内置 imagegen 生成，五个人物共十张 PNG，均为 1254 × 1254，每人分别提供闭嘴和张嘴两帧，沿用柔和半写实插画风格。
用户端、管理员端共用画像表的 `avatar_key` / `speaking_avatar_key`；演绎沿用现有两帧嘴型切换。
V15 迁移更新陈启明、林晓的图片引用，V16 迁移更新梁悦、赵诚、周国良的图片引用，并增加画像版本号；历史演绎快照及其旧文件保留。

| 人物 | 当前画像依据 | 闭嘴 | 张嘴 |
| --- | --- | --- | --- |
| 陈启明 | 35 岁男性，话务人员，耐心、善于观察 | [PNG](../backend/var/avatars/48d6c708ae546f4d0ca4d17d9f98eda89177ddcc480133070a89f7e42dc642ec.png) | [PNG](../backend/var/avatars/d32624d7261baaeb14726fdcc1332a1b1865b57341d6c0e6b1f940f7b2b849e5.png) |
| 梁悦 | 28 岁女性，话务人员，表达直接 | [PNG](../backend/var/avatars/0a4aa6f76d42c7c40c3646efab6a46af918b808596907e3853c4cd9ea90e0201.png) | [PNG](../backend/var/avatars/fb78d9424abf461f7163d9958fb9f4b9ce1587e6b6e823f8d5792149dc02163f.png) |
| 林晓 | 22 岁女性，应届毕业生，正在求职 | [PNG](../backend/var/avatars/f2069504f87a663eabb24812791b5d6df296327342162c46d6fdd84f5557b9f9.png) | [PNG](../backend/var/avatars/be9986d6be1a73dfb392811fb0d75b9b020854e44e55b1a2efb4adf306678c07.png) |
| 赵诚 | 41 岁男性，小型商店经营者，自信果断 | [PNG](../backend/var/avatars/54db4e6e556bf56bbc3b01d61d2de990afbbfec64739d51bd6e98e1f179add18.png) | [PNG](../backend/var/avatars/3085547af195e0e19fb69e7f9df96d5c1fe4ab796ef7e903bda568ef9fb9a63e.png) |
| 周国良 | 66 岁男性，退休会计，谨慎、有耐心、重视家庭 | [PNG](../backend/var/avatars/39e82bce528e303444502835278ae3123fc15d204539dfd1e6ea011dda82e439.png) | [PNG](../backend/var/avatars/08b6ff4d5d6a9e5d5a72c5d777c4c6d1396e87679b0bc7f76701d833f3c24c01.png) |

## 生成提示词

### 陈启明闭嘴图

Use case: illustration-story. Generate a production character portrait for a Chinese anti-fraud roleplaying web app, fictional 陈启明 (Chen Qiming), Chinese man aged 35, patient articulate online caller, observant expression, neat short black side-parted hair, clean-shaven, dark muted teal smart-casual collared shirt over light undershirt. Polished contemporary hand-painted 2D narrative game illustration, restrained warm colors, refined face and fabric detail, gentle natural shading, no caricature, no villain stereotypes. Square 1024x1024 canvas, centered front-facing head and upper torso down to waist, shoulders and hair fully inside frame with generous top margin. Arms resting down outside lower crop, no hands or props. Neutral friendly expression with lips CLOSED, eyes looking straight ahead. Plain warm ivory background with subtle pale sage circular halo, no scenery. This will become a two-frame talking portrait; head/body must remain static. No text, no watermark, no borders.

### 林晓闭嘴图

Use case: illustration-story. Production square 1024x1024 character portrait for a Chinese anti-fraud roleplaying web app. Fictional 林晓 (Lin Xiao), Chinese young woman aged 22, recent university graduate searching for first job. Soft oval face, dark brown eyes, neat natural black hair in a low ponytail with light side bangs, no eyewear. Light cream collared shirt under muted sage cardigan, modest everyday clothing. Polished contemporary hand-painted 2D narrative game illustration with refined facial details, natural proportions, subtle warm paper texture, gentle shaded fabric, restrained warm colors. Neutral attentive friendly expression, lips firmly CLOSED, eyes looking straight ahead. Centered front-facing head and upper torso to waist, shoulders fully inside square with generous margin above head, arms resting down outside crop, no hands or props. Plain warm ivory background with subtle pale sage circular halo; no scenery. Static two-frame talking game portrait so balanced symmetrical composition. No text, watermark, border, headset, jewelry, caricature, or sexualization.

### 陈启明张嘴图（以闭嘴图为编辑输入）

Use case: precise-object-edit. Edit target is the supplied Chen Qiming portrait. Create the OPEN-MOUTH talking frame for this two-frame game portrait. Change ONLY the lips and inside of the mouth to a natural small speaking opening, clearly visible dark mouth interior and a subtle upper teeth edge. Keep the man, face identity, eyes, nose, jaw, hair, head position, pose, clothing, lighting, all background and canvas dimensions EXACTLY identical. No smiling wider, no change outside the mouth, no camera or crop change. The output must register perfectly over the original closed-mouth portrait; only the mouth moves. Preserve square aspect and same resolution.

### 林晓张嘴图（以闭嘴图为编辑输入）

Use case: precise-object-edit. Edit target is the supplied Lin Xiao portrait. Create the OPEN-MOUTH talking frame for this two-frame game portrait. Change ONLY the lips and inside of the mouth to a natural small speaking opening, clearly visible dark mouth interior and a subtle upper teeth edge. Keep the woman, face identity, eyes, nose, jaw, hair, head position, pose, clothing, lighting, all background and canvas dimensions EXACTLY identical. No smiling wider, no change outside the mouth, no camera or crop change. The output must register perfectly over the original closed-mouth portrait; only the mouth moves. Preserve square aspect and same resolution.

### 梁悦闭嘴图

Use case: illustration-story. Production character portrait for a Chinese anti-fraud roleplaying web app, fictional 梁悦. Subject: Chinese woman aged 28, online call operator. Direct, alert, composed expression; natural shoulder-length straight dark hair in a tidy bob, black lightweight telephone headset with small microphone, muted warm gray-green office cardigan over an ivory collared blouse. Visually distinct from Lin Xiao's ponytail; confident mature young adult. Match a coherent set of refined, softly shaded semi-realistic hand-painted narrative game portraits: realistic Chinese face, natural anatomy, gentle warm paper texture and fine fabric detail, restrained warm colors. Square canvas, centered straight-on head and upper torso to waist, shoulders and hair fully inside frame, generous top margin. Arms resting down outside lower crop, no visible hands or held props. Eyes look straight ahead, neutral attentive friendly expression, lips CLOSED. Plain warm ivory background with a subtle pale sage circular halo, no scenery. Static two-frame talking game portrait; head and body remain still. No text, watermark, border, caricature, dramatic pose, or villain stereotypes.

首次输出出现双人拼图，未用于项目。以下修正提示词生成最终单人闭嘴图：

Use case: compositing. The reference image accidentally contains two side-by-side portraits of Liang Yue. Make ONE SINGLE portrait of ONLY the LEFT closed-mouth woman, centered on the ENTIRE square canvas. There must be exactly ONE head and ONE body in the whole output. NO split panels, NO collage, NO side-by-side comparison, NO contact sheet. Preserve her identity, closed lips, black bob haircut, black telephone headset with boom microphone, sage cardigan and ivory shirt, gentle semi-realistic painted paper style and ivory background with pale sage circular halo. Recompose for a square single portrait showing head and upper torso to waist, entire shoulders inside the frame with margin, head centered near upper middle. Natural proportions, relaxed still front-facing pose, eyes looking ahead. Remove the entire right open-mouth person. No text, no border. Output one square 1254x1254 portrait.

### 梁悦张嘴图（以最终闭嘴图为编辑输入）

Use case: precise-object-edit. Edit target is the supplied SINGLE Liang Yue portrait. Output exactly ONE SINGLE person on the entire square canvas, NO split panels, no side-by-side comparison. Create her OPEN-MOUTH talking portrait: change ONLY the lips and mouth interior to a small natural speaking opening with dark interior and subtle upper teeth edge. Keep exact facial identity, expression, eyes, nose, jaw, bob haircut, black headset and microphone, body/head position, clothing, lighting, paper texture, background and square canvas dimensions. Do not move, resize or reframe anything; outside the mouth all pixels should stay identical so the frame overlays the original without flicker. No text or watermark.

### 赵诚闭嘴图

Use case: illustration-story. Production character portrait for a Chinese anti-fraud roleplaying web app, fictional 赵诚. Subject: Chinese man aged 41, owner of a small local shop. Confident, decisive, practical, approachable expression. Slightly broad face and sturdy average build, short neat black hair with a subtle mature hairline, clean-shaven, muted slate-blue casual collared shirt with sleeves down. No headset, no glasses, no suit or tie; ordinary working shopkeeper, visibly older than Chen Qiming. Match a coherent set of refined, softly shaded semi-realistic hand-painted narrative game portraits: realistic Chinese face, natural anatomy, gentle warm paper texture and fine fabric detail, restrained warm colors. Square canvas, centered straight-on head and upper torso to waist, shoulders and hair fully inside frame, generous top margin. Arms resting down outside lower crop, no visible hands or held props. Eyes look straight ahead, neutral attentive friendly expression, lips CLOSED. Plain warm ivory background with a subtle pale sage circular halo, no scenery. Static two-frame talking game portrait; head and body remain still. No text, watermark, border, caricature, dramatic pose, or villain stereotypes.

### 赵诚张嘴图（以最终闭嘴图为编辑输入）

Use case: precise-object-edit. Edit target is the supplied 赵诚 portrait. Create its OPEN-MOUTH talking frame for a two-frame game portrait. Change ONLY the lips and mouth interior to a small natural speaking opening with clearly visible dark mouth interior and a subtle upper teeth edge. Preserve the exact facial identity, expression, eyes, glasses if present, nose, jaw, hair, head/body position, clothing, paper texture, lighting and background. Keep the same square canvas and exact resolution. No camera movement, pose change, resizing or cropping. All pixels outside the mouth area should remain identical so the image overlays its closed-mouth frame without flicker. No text or watermark.

### 周国良闭嘴图

Use case: illustration-story. Production character portrait for a Chinese anti-fraud roleplaying web app, fictional 周国良. Subject: Chinese man aged 66, retired accountant, thoughtful patient family-oriented expression. Clearly elderly male with natural wrinkles and smile lines, gently receding short silver-gray hair, thin understated rectangular reading glasses. Muted charcoal sage knit cardigan over a pale cream collared shirt. Dignified ordinary retiree, no headset, no beard, no stereotype or exaggerated frailty. Match a coherent set of refined, softly shaded semi-realistic hand-painted narrative game portraits: realistic Chinese face, natural anatomy, gentle warm paper texture and fine fabric detail, restrained warm colors. Square canvas, centered straight-on head and upper torso to waist, shoulders and hair fully inside frame, generous top margin. Arms resting down outside lower crop, no visible hands or held props. Eyes look straight ahead, neutral attentive friendly expression, lips CLOSED. Plain warm ivory background with a subtle pale sage circular halo, no scenery. Static two-frame talking game portrait; head and body remain still. No text, watermark, border, caricature, dramatic pose, or villain stereotypes.

### 周国良张嘴图（以最终闭嘴图为编辑输入）

Use case: precise-object-edit. Edit target is the supplied 周国良 portrait. Create its OPEN-MOUTH talking frame for a two-frame game portrait. Change ONLY the lips and mouth interior to a small natural speaking opening with clearly visible dark mouth interior and a subtle upper teeth edge. Preserve the exact facial identity, expression, eyes, glasses if present, nose, jaw, hair, head/body position, clothing, paper texture, lighting and background. Keep the same square canvas and exact resolution. No camera movement, pose change, resizing or cropping. All pixels outside the mouth area should remain identical so the image overlays its closed-mouth frame without flicker. No text or watermark.
