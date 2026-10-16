// 动画呈现检测：切换待机/说话/入场状态后，确认对应 CSS 动画确实已在元素上运行。
// 使用 Web Animations API 的 getAnimations()，不依赖 requestAnimationFrame，后台标签页同样可检测。
// 浏览器不支持该 API 时无法核实，返回 true（不凭空制造故障）。

export function runningAnimationNames(el) {
  if (!el || typeof el.getAnimations !== 'function') return null
  // 读取一次计算样式，确保刚切换的 class 已生效
  getComputedStyle(el).animationName
  return el
    .getAnimations({ subtree: true })
    .filter((a) => a.playState !== 'idle')
    .map((a) => a.animationName)
    .filter(Boolean)
}

export function verifyAnimations(el, expectedNames) {
  if (!el) return false
  const names = runningAnimationNames(el)
  if (names === null) return true
  return expectedNames.every((name) => names.includes(name))
}

/** 预加载头像图片；失败或超时 reject。 */
export function loadImage(url, timeoutMs) {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const timer = setTimeout(() => {
      img.onload = img.onerror = null
      reject(new Error('timeout'))
    }, timeoutMs)
    img.onload = () => {
      clearTimeout(timer)
      if (img.naturalWidth > 0) resolve(img)
      else reject(new Error('empty image'))
    }
    img.onerror = () => {
      clearTimeout(timer)
      reject(new Error('load error'))
    }
    img.src = url
  })
}
