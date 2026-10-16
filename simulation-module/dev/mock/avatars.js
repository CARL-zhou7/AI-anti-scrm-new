// 本地模拟用的五张示意头像（按 PRD §11.3 人物卡头像描述绘制的简笔插画）。
// 仅用于联调演示；正式头像由管理员按 PC-P02 上传 JPG/PNG。

function portrait({ bg, skin, hair, cloth, hairBack = '', hairFront = '', extra = '', mouth = 'M108 178 Q120 186 132 178' }) {
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 300" width="240" height="300">
  <rect width="240" height="300" fill="${bg}"/>
  <circle cx="200" cy="40" r="70" fill="#ffffff" opacity="0.08"/>
  ${hairBack.replaceAll('HAIR', hair)}
  <path d="M40 300 Q46 232 120 222 Q194 232 200 300 Z" fill="${cloth}"/>
  <rect x="106" y="190" width="28" height="38" rx="10" fill="${skin}"/>
  <ellipse cx="120" cy="148" rx="50" ry="60" fill="${skin}"/>
  <ellipse cx="70" cy="152" rx="8" ry="12" fill="${skin}"/>
  <ellipse cx="170" cy="152" rx="8" ry="12" fill="${skin}"/>
  ${hairFront.replaceAll('HAIR', hair)}
  <ellipse cx="100" cy="152" rx="5" ry="6" fill="#2b2230"/>
  <ellipse cx="140" cy="152" rx="5" ry="6" fill="#2b2230"/>
  <path d="M90 136 Q100 131 110 135" stroke="#2b2230" stroke-width="3" fill="none" stroke-linecap="round"/>
  <path d="M130 135 Q140 131 150 136" stroke="#2b2230" stroke-width="3" fill="none" stroke-linecap="round"/>
  <path d="M120 158 Q116 168 121 170" stroke="#c48a6a" stroke-width="2.5" fill="none" stroke-linecap="round"/>
  <path d="${mouth}" stroke="#9b4b44" stroke-width="3.5" fill="none" stroke-linecap="round"/>
  ${extra}
</svg>`
}

export const AVATARS = {
  avatar_chen_qiming: portrait({
    bg: '#45497a',
    skin: '#efc6a0',
    hair: '#1e1b22',
    cloth: '#1f2433',
    hairFront: '<path d="M70 132 Q72 82 120 80 Q170 82 170 132 Q160 104 120 102 Q84 102 70 132 Z" fill="HAIR"/>',
    extra: '<path d="M96 236 L120 262 L144 236" stroke="#3a4157" stroke-width="6" fill="none"/>',
    mouth: 'M108 180 L132 180',
  }),
  avatar_liang_yue: portrait({
    bg: '#5b4a7e',
    skin: '#f3cfae',
    hair: '#3a2621',
    cloth: '#5d6b8a',
    hairBack: '<path d="M62 150 Q60 84 120 80 Q180 84 178 150 L182 214 Q150 222 120 214 Q90 222 58 214 Z" fill="HAIR"/>',
    hairFront: '<path d="M68 140 Q74 86 122 84 Q168 88 172 140 Q150 108 112 110 Q86 114 68 140 Z" fill="HAIR"/>',
    extra:
      '<path d="M64 150 Q64 76 120 74 Q176 76 176 150" stroke="#222" stroke-width="6" fill="none"/><rect x="56" y="140" width="16" height="26" rx="6" fill="#222"/><rect x="168" y="140" width="16" height="26" rx="6" fill="#222"/><path d="M64 164 Q70 196 102 190" stroke="#222" stroke-width="4" fill="none"/><circle cx="104" cy="190" r="5" fill="#222"/>',
  }),
  avatar_lin_xiao: portrait({
    bg: '#e3ad6f',
    skin: '#f6d4b4',
    hair: '#2e211d',
    cloth: '#5aa0c6',
    hairBack: '<path d="M164 112 Q206 120 196 196 Q186 160 166 150 Z" fill="HAIR"/>',
    hairFront: '<path d="M68 142 Q70 86 120 84 Q172 86 172 140 Q156 100 118 104 Q82 108 68 142 Z" fill="HAIR"/><circle cx="168" cy="112" r="7" fill="#e45d6b"/>',
    mouth: 'M106 176 Q120 190 134 176',
  }),
  avatar_zhao_cheng: portrait({
    bg: '#c38a59',
    skin: '#e7b88f',
    hair: '#26201c',
    cloth: '#6f8f63',
    hairFront: '<path d="M68 130 Q70 84 120 82 Q170 84 172 130 Q164 108 120 106 Q78 108 68 130 Z" fill="HAIR"/>',
    extra:
      '<path d="M84 186 Q120 214 156 186" stroke="#7d5a43" stroke-width="2" fill="none" stroke-dasharray="2 4" opacity="0.7"/><path d="M98 232 L120 252 L142 232" stroke="#f1efe6" stroke-width="7" fill="none"/>',
    mouth: 'M108 180 Q120 184 132 180',
  }),
  avatar_zhou_guilan: portrait({
    bg: '#d6a487',
    skin: '#f0cdb0',
    hair: '#b8b6bd',
    cloth: '#a24f58',
    hairBack: '<path d="M64 150 Q60 86 120 82 Q180 86 176 150 Q178 182 166 190 L74 190 Q62 182 64 150 Z" fill="HAIR"/>',
    hairFront: '<path d="M68 138 Q76 88 122 86 Q166 90 172 138 Q152 106 112 112 Q86 116 68 138 Z" fill="HAIR"/>',
    extra:
      '<circle cx="100" cy="152" r="14" stroke="#4a3a40" stroke-width="3" fill="none"/><circle cx="140" cy="152" r="14" stroke="#4a3a40" stroke-width="3" fill="none"/><path d="M114 152 L126 152" stroke="#4a3a40" stroke-width="3"/><path d="M86 168 Q92 172 98 170" stroke="#c99a84" stroke-width="2" fill="none"/>',
    mouth: 'M108 180 Q120 188 132 180',
  }),
}
