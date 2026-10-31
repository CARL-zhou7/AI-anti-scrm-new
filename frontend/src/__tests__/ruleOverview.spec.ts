import { expect, it } from 'vitest'
import { ruleOverview } from '../utils/ruleOverview'

it('reads complete saved fields and recomputes after edits without rewriting execution text', () => {
  const text = '适用情况：已经建立亲属关系后。\n话术指导：沿用既定身份和称谓，避免关系错乱。'
  expect(ruleOverview(text)).toEqual({ condition: '已经建立亲属关系后。', guidance: '沿用既定身份和称谓，避免关系错乱。' })
  expect(ruleOverview(text.replace('沿用既定身份和称谓，避免关系错乱。', '只回应上一轮的实际疑问。'))?.guidance).toBe('只回应上一轮的实际疑问。')
  expect(ruleOverview('改为自由正文')).toBeNull()
  expect(ruleOverview('适用情况：' + '长'.repeat(81) + '\n话术指导：原文')).toBeNull()
  expect(text).toContain('沿用既定身份和称谓')
})
