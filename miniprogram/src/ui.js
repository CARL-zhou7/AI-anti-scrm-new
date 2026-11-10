export const scenes = {
  TASK_REBATE: { art: '/static/rebate.jpg', opening: '/static/rebate-opening.jpg', title: '一条兼职消息', caption: '以刷单兼职为名诱导转账' },
  FAKE_INVESTMENT: { art: '/static/investment.jpg', opening: '/static/investment-opening.jpg', title: '一份投资邀约', caption: '以高收益投资为名骗取资金' },
  IMPERSONATION_LOAN: { art: '/static/impersonation.jpg', opening: '/static/impersonation-opening.jpg', title: '一个熟悉的称呼', caption: '冒充熟人以急用借款为由诈骗' }
}
export const sceneArt = code => scenes[code]?.art || '/static/archive.jpg'
export function isRecommendedVictim(code, age) {
  if (!Number.isFinite(age)) return false
  switch (code) {
    case 'TASK_REBATE': return age < 35
    case 'FAKE_INVESTMENT': return age >= 35 && age < 60
    case 'IMPERSONATION_LOAN': return age >= 60
    default: return false
  }
}
const newsPictures = new Map([
  ['真实案例', '/static/news-cash.jpg'],
  ['反诈新闻', '/static/news-classroom.jpg'],
  ['防骗提醒', '/static/news-shopping.jpg']
])
export const newsArt = category => newsPictures.get(category) || '/static/news.jpg'
export const isOfficialNewsUrl = url => typeof url === 'string'
  && /^https:\/\/gaj\.(?:wuhan|taizhou)\.gov\.cn\/(?:[\w-]+\/)+[\w-]+\.html$/.test(url)
const portraitNames = new Map([
  ['1:陈启明', 'chen-qiming'],
  ['2:梁悦', 'liang-yue'],
  ['1:林晓', 'lin-xiao'],
  ['2:赵诚', 'zhao-cheng'],
  ['3:周国良', 'zhou-guoliang']
])
export function portraitArt(person) {
  const name = portraitNames.get(`${person?.id}:${person?.name}`)
  return name ? '/static/portraits/' + name : ''
}
const riskColors = ['#3f8f6f', '#3978b8', '#d9a23a', '#d9733a', '#c43d33']
export const riskColor = score => score == null ? '#8b929c' : riskColors[Math.max(0, Math.min(4, Math.round(score / 25)))]
export const statusLabel = status => ({ RUNNING: '进行中', FINISHED: '正常结束', INTERRUPTED: '已中断' })[status] || status
export const reportLabel = report => ({ PENDING: '等待生成', RUNNING: '报告生成中', SUCCEEDED: '报告已生成', FAILED: '报告生成失败' })[report?.status] || '无正式报告'
export const yuan = value => value == null ? '—' : '¥' + value
export function shanghai(value) {
  const time = Date.parse(value)
  return Number.isFinite(time) ? new Date(time + 8 * 3600000).toISOString().slice(0, 16).replace('T', ' ') : '—'
}
export const errorText = error => error?.message || '加载失败，请重试'
export function lightTap() {
  uni.vibrateShort?.({ type: 'light', fail: () => {} })
}
export const toast = error => uni.showToast({ title: errorText(error), icon: 'none' })
export function go(page) { uni.navigateTo({ url: '/pages/' + page }) }
export function home() { uni.reLaunch({ url: '/pages/home' }) }
