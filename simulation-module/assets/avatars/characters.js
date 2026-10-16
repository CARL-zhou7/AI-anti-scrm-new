// 五位初始人物的半身插画（PRD §11.3 / PC-P02 头像描述）。SVG 为源文件，render.mjs 渲染成 PNG
// （头像上传只接受静态 JPG/PNG）。生成的 PNG 用于初始化数据与管理员上传，也供本地模拟后端使用。

const W = 400
const H = 500

function defs(id, c) {
  return `<defs>
    <linearGradient id="${id}-bg" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${c.bgTop}"/><stop offset="1" stop-color="${c.bgBottom}"/>
    </linearGradient>
    <radialGradient id="${id}-skin" cx="0.45" cy="0.38" r="0.75">
      <stop offset="0" stop-color="${c.skinLight}"/><stop offset="1" stop-color="${c.skin}"/>
    </radialGradient>
    <linearGradient id="${id}-cloth" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="${c.clothLight}"/><stop offset="1" stop-color="${c.cloth}"/>
    </linearGradient>
    <linearGradient id="${id}-hair" x1="0" y1="0" x2="0.3" y2="1">
      <stop offset="0" stop-color="${c.hairLight}"/><stop offset="1" stop-color="${c.hair}"/>
    </linearGradient>
  </defs>`
}

function eye(cx, cy, c, { lid = 0, lashes = false, look = 0 } = {}) {
  // lid: 0 正常，>0 眼睑略低（沉稳/放松）
  const top = cy - 9 + lid
  return `<g>
    <path d="M${cx - 15} ${cy} Q${cx} ${top - 4} ${cx + 15} ${cy} Q${cx} ${cy + 9} ${cx - 15} ${cy} Z" fill="#ffffff"/>
    <clipPath id="clip-${cx}-${cy}"><path d="M${cx - 15} ${cy} Q${cx} ${top - 4} ${cx + 15} ${cy} Q${cx} ${cy + 9} ${cx - 15} ${cy} Z"/></clipPath>
    <g clip-path="url(#clip-${cx}-${cy})">
      <circle cx="${cx + look}" cy="${cy + 1}" r="8.5" fill="${c.iris}"/>
      <circle cx="${cx + look}" cy="${cy + 1}" r="4.2" fill="#1b1416"/>
      <circle cx="${cx + look + 3}" cy="${cy - 2}" r="2.4" fill="#ffffff"/>
    </g>
    <path d="M${cx - 16} ${cy + 0.5} Q${cx} ${top - 5} ${cx + 16} ${cy - 1}" stroke="${c.line}" stroke-width="3.2" fill="none" stroke-linecap="round"/>
    ${lashes ? `<path d="M${cx + 13} ${cy - 2} l6 -4 M${cx + 10} ${cy - 5} l5 -5" stroke="${c.line}" stroke-width="2" stroke-linecap="round"/>` : ''}
  </g>`
}

function face(id, c, o) {
  const brow = (x, dir) =>
    `<path d="M${x - 17 * dir} ${o.browY + (o.browTilt || 0)} Q${x} ${o.browY - 8} ${x + 17 * dir} ${o.browY + 2}" stroke="${c.brow}" stroke-width="${o.browW || 4}" fill="none" stroke-linecap="round"/>`
  return `
    <ellipse cx="118" cy="232" rx="13" ry="20" fill="${c.skin}"/>
    <ellipse cx="282" cy="232" rx="13" ry="20" fill="${c.skin}"/>
    <ellipse cx="120" cy="234" rx="6" ry="10" fill="${c.skinShade}" opacity="0.5"/>
    <ellipse cx="280" cy="234" rx="6" ry="10" fill="${c.skinShade}" opacity="0.5"/>
    <path d="${o.facePath || 'M200 128 C255 128 284 168 284 222 C284 278 252 326 200 330 C148 326 116 278 116 222 C116 168 145 128 200 128 Z'}" fill="url(#${id}-skin)"/>
    ${o.blush ? `<ellipse cx="156" cy="268" rx="17" ry="9" fill="#f08c86" opacity="${o.blush}"/><ellipse cx="244" cy="268" rx="17" ry="9" fill="#f08c86" opacity="${o.blush}"/>` : ''}
    ${brow(165, -1).replace(`M${165 + 17}`, `M${165 + 17}`)}
    ${brow(235, 1)}
    ${eye(165, 236, c, o.eye)}
    ${eye(235, 236, c, o.eye)}
    <path d="M201 246 Q196 268 204 274 Q200 277 194 274" stroke="${c.skinShade}" stroke-width="3" fill="none" stroke-linecap="round"/>
    ${o.mouth}
    ${o.faceExtra || ''}`
}

function figure(id, c, o) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${W} ${H}" width="${W}" height="${H}">
  ${defs(id, c)}
  <rect width="${W}" height="${H}" fill="url(#${id}-bg)"/>
  <circle cx="200" cy="220" r="170" fill="#ffffff" opacity="0.28"/>
  ${o.hairBack || ''}
  <path d="M170 300 L170 362 Q200 384 230 362 L230 300 Z" fill="${c.skinShade}"/>
  <ellipse cx="200" cy="318" rx="44" ry="16" fill="#000000" opacity="0.08"/>
  <path d="M30 500 C40 412 96 372 172 360 Q200 378 228 360 C304 372 360 412 370 500 Z" fill="url(#${id}-cloth)"/>
  ${o.clothes || ''}
  ${face(id, c, o)}
  ${o.hairFront || ''}
  ${o.extra || ''}
</svg>`
}

const BASE_FACE = { line: '#2b2026', iris: '#4a3128', brow: '#2b2026' }

export const CHARACTERS = {
  // 骗子画像一：短发、深色衬衫、神态沉稳的成年男性
  avatar_chen_qiming: figure(
    'cqm',
    {
      ...BASE_FACE,
      bgTop: '#dcd8f4',
      bgBottom: '#b9b2e6',
      skin: '#e9b993',
      skinLight: '#f6d6b8',
      skinShade: '#c98e6a',
      cloth: '#1e2333',
      clothLight: '#323a52',
      hair: '#16131a',
      hairLight: '#3a3340',
    },
    {
      browY: 214,
      browW: 5,
      eye: { lid: 3 },
      facePath: 'M200 126 C258 126 284 166 284 222 C284 282 250 330 200 334 C150 330 116 282 116 222 C116 166 142 126 200 126 Z',
      mouth: '<path d="M178 294 Q196 300 218 291" stroke="#9a5a4c" stroke-width="4" fill="none" stroke-linecap="round"/>',
      hairFront:
        '<path d="M112 214 C104 150 140 104 204 102 C262 102 296 142 290 214 C282 182 270 160 248 152 C226 168 186 170 150 158 C134 172 120 190 112 214 Z" fill="url(#cqm-hair)"/><path d="M152 156 C178 140 214 136 246 150" stroke="#4a4352" stroke-width="3" fill="none" opacity="0.6"/>',
      clothes:
        '<path d="M164 362 L200 420 L236 362 L224 356 L200 392 L176 356 Z" fill="#2c3448"/><path d="M200 392 L200 500" stroke="#11141e" stroke-width="2"/><circle cx="200" cy="430" r="3" fill="#4b5470"/><circle cx="200" cy="466" r="3" fill="#4b5470"/><path d="M176 356 L200 392 L188 404 L160 368 Z" fill="#39425c"/><path d="M224 356 L200 392 L212 404 L240 368 Z" fill="#39425c"/>',
    },
  ),

  // 骗子画像二：齐肩发、简洁便装、佩戴耳麦的成年女性
  avatar_liang_yue: figure(
    'ly',
    {
      ...BASE_FACE,
      bgTop: '#e5dcf6',
      bgBottom: '#c6b5ea',
      skin: '#f0c4a2',
      skinLight: '#fbdcc4',
      skinShade: '#d39a78',
      cloth: '#6f7a99',
      clothLight: '#96a0bd',
      hair: '#2c1d19',
      hairLight: '#55382f',
      iris: '#3d2a24',
    },
    {
      browY: 212,
      browW: 3.4,
      blush: 0.25,
      eye: { lashes: true },
      facePath: 'M200 132 C252 132 280 170 280 224 C280 276 250 320 200 326 C150 320 120 276 120 224 C120 170 148 132 200 132 Z',
      mouth: '<path d="M180 292 Q200 302 220 290 Q200 298 180 292 Z" fill="#c9575a" stroke="#b44a50" stroke-width="2.5" stroke-linejoin="round"/>',
      hairBack:
        '<path d="M104 236 C92 150 136 100 202 100 C268 100 310 150 298 236 L306 360 C280 376 250 372 236 352 L164 352 C150 372 120 376 94 360 Z" fill="url(#ly-hair)"/>',
      hairFront:
        '<path d="M116 226 C112 160 150 116 204 114 C256 116 290 158 284 226 C272 188 250 160 220 150 C206 176 166 190 130 196 C124 206 120 216 116 226 Z" fill="url(#ly-hair)"/>',
      clothes:
        '<path d="M150 366 Q200 400 250 366" stroke="#c5ccdf" stroke-width="10" fill="none" stroke-linecap="round"/>',
      extra:
        '<path d="M108 226 C102 128 298 128 292 226" stroke="#2a2d36" stroke-width="9" fill="none" stroke-linecap="round"/><rect x="94" y="212" width="26" height="46" rx="12" fill="#2a2d36"/><rect x="280" y="212" width="26" height="46" rx="12" fill="#2a2d36"/><rect x="99" y="220" width="8" height="30" rx="4" fill="#5e6474"/><path d="M106 256 C110 296 140 306 168 300" stroke="#2a2d36" stroke-width="5" fill="none" stroke-linecap="round"/><ellipse cx="172" cy="299" rx="8" ry="6" fill="#2a2d36"/>',
    },
  ),

  // 青年受害者：马尾、休闲上衣的年轻女性
  avatar_lin_xiao: figure(
    'lx',
    {
      ...BASE_FACE,
      bgTop: '#fde8d3',
      bgBottom: '#f7c79c',
      skin: '#f3c9a8',
      skinLight: '#fde3cf',
      skinShade: '#d9a080',
      cloth: '#4e93c4',
      clothLight: '#7db6de',
      hair: '#231816',
      hairLight: '#4c332c',
      iris: '#402b22',
    },
    {
      browY: 210,
      browW: 3.4,
      blush: 0.35,
      eye: { lashes: true },
      facePath: 'M200 134 C250 134 278 172 278 224 C278 274 250 318 200 324 C150 318 122 274 122 224 C122 172 150 134 200 134 Z',
      mouth: '<path d="M178 286 Q200 312 222 286 Z" fill="#a8414a"/><path d="M184 288 Q200 296 216 288" fill="#ffffff"/>',
      hairBack:
        '<path d="M262 140 C320 128 338 190 318 252 C306 290 296 320 300 352 C276 330 270 290 280 250 C288 214 282 178 262 160 Z" fill="url(#lx-hair)"/>',
      hairFront:
        '<path d="M118 228 C110 156 150 112 204 112 C258 112 292 152 284 224 C276 196 266 176 250 164 C236 180 214 188 196 184 C176 190 160 182 150 170 C134 186 124 204 118 228 Z" fill="url(#lx-hair)"/><circle cx="270" cy="150" r="10" fill="#e8596b"/>',
      clothes:
        '<path d="M164 362 Q200 392 236 362" stroke="#ffffff" stroke-width="7" fill="none" stroke-linecap="round" opacity="0.9"/><path d="M120 440 l18 -8 l6 14 l-18 8 Z" fill="#ffd166"/>',
    },
  ),

  // 中年受害者：短发、日常衬衫的中年男性
  avatar_zhao_cheng: figure(
    'zc',
    {
      ...BASE_FACE,
      bgTop: '#fbe3cc',
      bgBottom: '#eab98c',
      skin: '#dfac83',
      skinLight: '#f0c9a6',
      skinShade: '#bd8460',
      cloth: '#4f7a5c',
      clothLight: '#6f9a7b',
      hair: '#231c18',
      hairLight: '#4a3d34',
    },
    {
      browY: 213,
      browW: 5.5,
      eye: { lid: 1 },
      facePath: 'M200 128 C260 128 288 168 288 224 C288 284 254 332 200 336 C146 332 112 284 112 224 C112 168 140 128 200 128 Z',
      mouth: '<path d="M174 290 Q200 308 226 290" stroke="#8c4a40" stroke-width="4.5" fill="none" stroke-linecap="round"/>',
      faceExtra:
        '<path d="M140 290 C150 322 176 336 200 337 C224 336 250 322 260 290" stroke="#8a6650" stroke-width="2" fill="none" stroke-dasharray="1 5" stroke-linecap="round" opacity="0.6"/>',
      hairFront:
        '<path d="M112 210 C108 146 146 106 204 106 C260 106 294 146 290 210 C284 186 274 168 260 160 C230 150 176 148 142 160 C128 172 118 190 112 210 Z" fill="url(#zc-hair)"/>',
      clothes:
        '<g opacity="0.35" stroke="#21392a" stroke-width="3"><path d="M60 430 H340 M48 470 H352 M110 380 V500 M160 372 V500 M240 372 V500 M290 380 V500"/></g><path d="M162 360 L200 404 L238 360 L230 352 L200 384 L170 352 Z" fill="#f2efe4"/><path d="M200 384 V500" stroke="#2f4a37" stroke-width="2"/>',
    },
  ),

  // 老年受害者：短灰发、戴眼镜的老年女性
  avatar_zhou_guilan: figure(
    'zgl',
    {
      ...BASE_FACE,
      bgTop: '#fde6d8',
      bgBottom: '#efbfa4',
      skin: '#efc6a8',
      skinLight: '#fbe0cc',
      skinShade: '#cf9b7c',
      cloth: '#93404e',
      clothLight: '#b45a68',
      hair: '#a9a6ab',
      hairLight: '#dedbe0',
      iris: '#4a3830',
      brow: '#8d8890',
    },
    {
      browY: 214,
      browW: 3.4,
      blush: 0.22,
      eye: { lid: 2 },
      facePath: 'M200 136 C252 136 280 174 280 226 C280 278 250 322 200 328 C150 322 120 278 120 226 C120 174 148 136 200 136 Z',
      mouth: '<path d="M180 292 Q200 304 220 292" stroke="#a85a58" stroke-width="4" fill="none" stroke-linecap="round"/>',
      faceExtra:
        '<path d="M164 276 Q170 290 176 296 M236 276 Q230 290 224 296" stroke="#c48e72" stroke-width="2" fill="none" stroke-linecap="round" opacity="0.8"/><path d="M146 246 q-6 4 -12 2 M254 246 q6 4 12 2" stroke="#c48e72" stroke-width="1.6" fill="none" opacity="0.8"/>',
      hairBack:
        '<path d="M106 240 C96 160 140 112 202 112 C266 112 306 160 296 240 C300 270 292 296 278 306 L122 306 C108 296 100 270 106 240 Z" fill="url(#zgl-hair)"/>',
      hairFront:
        '<path d="M118 226 C112 162 150 122 204 120 C258 122 292 162 284 226 C278 200 266 182 246 172 C224 186 190 186 172 174 C150 178 128 198 118 226 Z" fill="url(#zgl-hair)"/><g fill="none" stroke="#f1eff3" stroke-width="2.5" opacity="0.7" stroke-linecap="round"><path d="M150 150 q12 -10 24 -4"/><path d="M214 140 q14 -6 26 4"/><path d="M126 196 q6 -12 16 -16"/></g>',
      clothes:
        '<path d="M170 360 L200 420 L230 360 Z" fill="#f4ead8"/><path d="M150 364 L200 470 L188 500 L120 500 C110 440 124 390 150 364 Z" fill="#7d3341" opacity="0.55"/><path d="M250 364 L200 470 L212 500 L280 500 C290 440 276 390 250 364 Z" fill="#7d3341" opacity="0.55"/><circle cx="196" cy="452" r="3.5" fill="#f4ead8"/>',
      extra:
        '<g fill="none" stroke="#7a5638" stroke-width="3.2"><circle cx="165" cy="238" r="21"/><circle cx="235" cy="238" r="21"/><path d="M186 236 Q200 228 214 236"/><path d="M144 234 L120 226 M256 234 L280 226"/></g><g fill="#ffffff" opacity="0.18"><circle cx="165" cy="238" r="20"/><circle cx="235" cy="238" r="20"/></g>',
    },
  ),
}
