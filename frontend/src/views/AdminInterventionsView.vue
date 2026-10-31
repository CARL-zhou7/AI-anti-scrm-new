<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/http'
import type { InterventionRecord, RuleDraft } from '../api/types'
import AdminNav from '../components/AdminNav.vue'

// Waiting-for-knowledge-base records (API-50/51). Only adopted interventions of this administrator appear here; a
// proposal is read-only until the administrator submits it, and a failure never claims "已入库".
const records = ref<InterventionRecord[]>([])
const loading = ref(false), busy = ref(false)
const error = ref(''), success = ref('')
const selected = ref<string[]>([])
const draft = ref<RuleDraft | null>(null)
const title = ref(''), ruleText = ref('')

const groups = computed(() => {
  const scenes = [...new Set(records.value.map(record => record.sceneCode))]
  return scenes.map(scene => ({ scene, items: records.value.filter(record => record.sceneCode === scene) }))
})
const selectedScene = computed(() => {
  const scenes = new Set(records.value.filter(r => selected.value.includes(r.interventionId)).map(r => r.sceneCode))
  return scenes.size === 1 ? [...scenes][0]! : null
})

function sceneName(scene: string) {
  return ({ TASK_REBATE: '刷单返利', FAKE_INVESTMENT: '虚假投资', IMPERSONATION_LOAN: '冒充亲友借款' } as Record<string, string>)[scene] ?? scene
}
function message(e: unknown) { return e instanceof ApiError ? e.message : (e instanceof Error ? e.message : '操作失败，请重试') }
function isSelected(id: string) { return selected.value.includes(id) }
function toggle(id: string) {
  selected.value = isSelected(id) ? selected.value.filter(value => value !== id) : [...selected.value, id]
  error.value = ''; success.value = ''
}
function selectScene(scene: string, items: InterventionRecord[]) {
  const ids = items.map(item => item.interventionId)
  const all = ids.every(isSelected)
  selected.value = all ? selected.value.filter(id => !ids.includes(id)) : [...new Set([...selected.value, ...ids])]
  error.value = ''
}

async function load() {
  loading.value = true; error.value = ''
  try {
    records.value = await adminApi.interventions()
    selected.value = selected.value.filter(id => records.value.some(record => record.interventionId === id))
  } catch (e) { error.value = message(e) } finally { loading.value = false }
}

async function induce() {
  if (selected.value.length === 0 || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    const proposal = await adminApi.induce(selected.value)
    draft.value = proposal; title.value = proposal.title; ruleText.value = proposal.ruleText
  } catch (e) { error.value = message(e) } finally { busy.value = false }
}

function cancel() { draft.value = null; error.value = '' }

async function submit() {
  if (!draft.value || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try {
    const rule = await adminApi.createRule({ scenarioCode: draft.value.scenarioCode, title: title.value,
      ruleText: ruleText.value, sourceInterventionIds: draft.value.sourceInterventionIds })
    success.value = `规则已提交（编号 ${rule.ruleId}，版本 ${rule.version}），${rule.sourceCount} 条来源已归入该规则`
    draft.value = null; selected.value = []
    await load()
  } catch (e) { error.value = message(e) } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="home-layout admin-layout" data-testid="admin-interventions" :aria-busy="loading || busy">
    <AdminNav />
    <span class="eyebrow">知识库 · 待入库</span>
    <h1>待入库记录</h1>
    <p class="admin-intro">只显示本人演示测试场次中已采用、尚未归入规则的干预记录。勾选同一场景的多条记录后可归纳为一条场景规则；系统只合并，不拆分，一条来源只能归入一条规则。</p>

    <p v-if="loading" role="status">正在加载待入库记录…</p>
    <p v-if="error" class="error-message" role="alert" data-testid="rule-error">{{ error }}</p>
    <p v-if="success" class="success-message" role="status" data-testid="rule-success">{{ success }}</p>

    <form v-if="draft" class="admin-panel" data-testid="rule-draft" @submit.prevent="submit">
      <div class="admin-row-heading"><h2>规则草稿</h2><span class="quiet-label">{{ sceneName(draft.scenarioCode) }} · {{ draft.sourceInterventionIds.length }} 条来源</span></div>
      <p class="field-help">来源：{{ draft.sourceInterventionIds.join('、') }}。请确认规则说明“上一轮出现什么情况、骗子应如何回应”，不含固定金额、人名、余额或本场临时状态，提交后仍可编辑或停用。</p>
      <label for="rule-title">规则标题</label>
      <input id="rule-title" v-model="title" data-testid="draft-title" required maxlength="128" :disabled="busy" />
      <label for="rule-text">规则正文</label>
      <textarea id="rule-text" v-model="ruleText" data-testid="draft-rule-text" rows="6" required :disabled="busy" />
      <div class="admin-actions">
        <button class="button button-primary" data-testid="rule-submit" :disabled="busy || !title.trim() || !ruleText.trim()">{{ busy ? '提交中…' : '提交规则' }}</button>
        <button type="button" class="button button-light" data-testid="rule-cancel" :disabled="busy" @click="cancel">取消</button>
      </div>
    </form>

    <p v-if="!loading && records.length === 0" class="field-help" data-testid="interventions-empty">暂无待入库记录。在演示测试中采用一条改写的骗子台词后，这里会出现可归纳的来源。</p>

    <section v-for="group in groups" :key="group.scene" class="admin-panel" data-testid="intervention-group">
      <div class="admin-row-heading">
        <h2>{{ sceneName(group.scene) }}</h2>
        <span class="quiet-label">{{ group.items.length }} 条待入库</span>
      </div>
      <div class="admin-actions">
        <button class="button button-light" :disabled="busy || loading" data-testid="intervention-select-all" @click="selectScene(group.scene, group.items)">
          {{ group.items.every(item => isSelected(item.interventionId)) ? '取消全选' : '全选本场景' }}
        </button>
      </div>
      <article v-for="record in group.items" :key="record.interventionId" class="admin-panel" data-testid="intervention-row">
        <label class="intervention-check">
          <input type="checkbox" data-testid="intervention-select" :checked="isSelected(record.interventionId)" :disabled="busy" @change="toggle(record.interventionId)" />
          第 {{ record.roundNo }} 轮 · 编号 {{ record.interventionId }}
        </label>
        <p class="field-help">上一轮对话</p><p class="admin-description" style="white-space: pre-line">{{ record.previousContext || '暂无上一轮对话' }}</p>
        <p class="field-help">原台词</p><p class="admin-description">{{ record.originalContent }}</p>
        <p class="field-help">干预要求</p><p class="admin-description">{{ record.interventionNote || '（未填写）' }}</p>
        <p class="field-help">最终台词</p><p class="admin-description">{{ record.finalContent }}</p>
      </article>
    </section>

    <div v-if="!draft && records.length > 0" class="admin-actions">
      <button class="button button-primary" data-testid="intervention-induce" :disabled="busy || loading || selectedScene === null" @click="induce">
        {{ busy ? '归纳中…' : `归纳选中的 ${selected.length} 条记录` }}
      </button>
      <span v-if="selected.length > 0 && selectedScene === null" class="field-help">只能归纳同一场景的来源。</span>
    </div>
  </div>
</template>
