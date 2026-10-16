// 将 characters.js 中的 SVG 渲染为 PNG（800×1000，用于初始化数据与管理员上传）。
// 用法：node assets/avatars/render.mjs   需要本机已安装 playwright（npx playwright install chromium）。
// 环境变量 CHROMIUM_PATH 可指定已有的 Chromium 可执行文件。

import { writeFile } from 'node:fs/promises'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'
import { CHARACTERS } from './characters.js'

const here = dirname(fileURLToPath(import.meta.url))
const require = createRequire(import.meta.url)

function loadPlaywright() {
  for (const base of [import.meta.url, process.env.PLAYWRIGHT_MODULE_DIR].filter(Boolean)) {
    try {
      return createRequire(base)('playwright')
    } catch {
      // 继续尝试
    }
  }
  return require('playwright')
}

const { chromium } = loadPlaywright()
const browser = await chromium.launch(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {})
const page = await browser.newPage({ viewport: { width: 400, height: 500 }, deviceScaleFactor: 2 })
for (const [key, svg] of Object.entries(CHARACTERS)) {
  await writeFile(join(here, `${key}.svg`), svg)
  await page.setContent(`<html><body style="margin:0">${svg}</body></html>`)
  await page.locator('svg').screenshot({ path: join(here, `${key}.png`) })
  console.log('rendered', key)
}
await browser.close()
