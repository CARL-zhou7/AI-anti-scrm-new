<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/http'
import type { FieldError, InterventionRecord, SceneRuleTargetRole, SceneRuleView } from '../api/types'
import FormFieldError from '../components/FormFieldError.vue'
import { ruleOverview } from '../utils/ruleOverview'
import VersionConflictNotice from '../components/VersionConflictNotice.vue'

// Scenario rules (API-52～54): list, edit, enable/disable and inspect the sources of one rule. Conflicts stay a
// manual decision: the platform never merges or reorders rules on its own.
const rows = ref<SceneRuleView[]>([])
const loading = ref(false), saving = ref(false)
const error = ref(''), success = ref('')
const editing = ref<SceneRuleView | null>(null)
const title = ref(''), ruleText = ref(''), enabled = ref(true)
const fields = ref<FieldError[]>([]), conflict = ref(false), currentVersion = ref<number | null>(null)
const expanded = ref<string | null>(null), sourcesOfRule = ref<InterventionRecord[]>([]), sourcesLoading = ref(false)
const sourcesError = ref('')
const scenes = ['TASK_REBATE', 'FAKE_INVESTMENT', 'IMPERSONATION_LOAN']
const selectedScene = ref('')
const columns: { role: SceneRuleTargetRole; title: string }[] = [
  { role: 'SCAMMER', title: '骗子场景规则' }, { role: 'VICTIM', title: '受害者场景规则' },
]
const creating = ref<SceneRuleTargetRole | null>(null), newScene = ref(scenes[0]!), newTitle = ref(''), newCondition = ref(''), newGuidance = ref('')
const canCreate = computed(() => newTitle.value.trim() && newCondition.value.trim() && newGuidance.value.trim())
const visibleRows = computed(() => rows.value.filter(row => !selectedScene.value || row.scenarioCode === selectedScene.value))

function changeScene() {
  cancel(); creating.value = null; expanded.value = null; sourcesOfRule.value = []
}

function startCreate(role: SceneRuleTargetRole) {
  cancel(); creating.value = role; newScene.value = selectedScene.value || scenes[0]!
  newTitle.value = ''; newCondition.value = ''; newGuidance.value = ''; success.value = ''
}

async function create() {
  if (!creating.value || !canCreate.value || saving.value) return
  const targetRole = creating.value
  saving.value = true; error.value = ''; fields.value = []; success.value = ''
  try {
    const row = await adminApi.createRule({ scenarioCode: newScene.value, title: newTitle.value.trim(),
      ruleText: `适用情况：${newCondition.value.trim()}\n行为指导：${newGuidance.value.trim()}`,
      targetRole, sourceInterventionIds: [] })
    rows.value = [row, ...rows.value]; selectedScene.value = row.scenarioCode; creating.value = null
    success.value = `${targetRole === 'SCAMMER' ? '骗子' : '受害者'}规则已添加，仅影响之后新开的同场景场次`
  } catch (e) {
    error.value = message(e)
    if (e instanceof ApiError) fields.value = e.fieldErrors
  } finally { saving.value = false }
}

function sceneName(scene: string) {
  return ({ TASK_REBATE: '刷单返利', FAKE_INVESTMENT: '虚假投资', IMPERSONATION_LOAN: '冒充亲友借款' } as Record<string, string>)[scene] ?? scene
}
function message(e: unknown) { return e instanceof ApiError ? e.message : (e instanceof Error ? e.message : '操作失败，请重试') }

async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    rows.value = (await adminApi.listRules()).items
    editing.value = null; creating.value = null; conflict.value = false
    if (expanded.value && !rows.value.some(row => row.ruleId === expanded.value)) {
      expanded.value = null; sourcesOfRule.value = []
    }
  } catch (e) { error.value = message(e) } finally { loading.value = false }
}

function edit(row: SceneRuleView) {
  creating.value = null
  editing.value = row; title.value = row.title; ruleText.value = row.ruleText; enabled.value = row.status === 'ENABLED'
  fields.value = []; conflict.value = false; error.value = ''; success.value = ''
}
function cancel() { editing.value = null; fields.value = []; conflict.value = false; error.value = '' }

async function save() {
  if (!editing.value || saving.value || conflict.value) return
  saving.value = true; error.value = ''; fields.value = []; success.value = ''
  try {
    const row = await adminApi.updateRule(editing.value.ruleId, {
      title: title.value, ruleText: ruleText.value, status: enabled.value ? 'ENABLED' : 'DISABLED',
      version: editing.value.version })
    rows.value = rows.value.map(old => old.ruleId === row.ruleId ? row : old)
    editing.value = null; success.value = '规则已保存，仅影响之后新开的同场景场次'
  } catch (e) {
    error.value = message(e)
    if (e instanceof ApiError) { fields.value = e.fieldErrors; conflict.value = e.code === 'VERSION_CONFLICT'; currentVersion.value = e.currentVersion }
  } finally { saving.value = false }
}

/** Disabling or enabling always bumps the version; an in-progress session keeps its frozen snapshot. */
async function toggle(row: SceneRuleView) {
  if (saving.value || loading.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const next = await adminApi.updateRule(row.ruleId, { status: row.status === 'ENABLED' ? 'DISABLED' : 'ENABLED', version: row.version })
    rows.value = rows.value.map(old => old.ruleId === next.ruleId ? next : old)
    success.value = next.status === 'ENABLED' ? '规则已启用' : '规则已停用，进行中的场次不受影响'
  } catch (e) { error.value = message(e) }
  finally { saving.value = false }
}

async function showSources(row: SceneRuleView) {
  if (expanded.value === row.ruleId) { expanded.value = null; return }
  expanded.value = row.ruleId; sourcesOfRule.value = []; sourcesError.value = ''; sourcesLoading.value = true
  try { sourcesOfRule.value = await adminApi.ruleSources(row.ruleId) }
  catch (e) { sourcesError.value = message(e) }
  finally { sourcesLoading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="home-layout admin-layout" data-testid="admin-scene-rules" :aria-busy="loading || saving">
    <span class="eyebrow">知识库 · 场景规则</span>
    <h1>场景规则</h1>
    <p class="admin-intro">按场景分别指导骗子与受害者。修改仅影响新开的场次，进行中的场次沿用开始时的规则快照；冲突规则请确认后再启用。</p>
    <label for="scene-rule-filter">选择场景</label>
    <select id="scene-rule-filter" v-model="selectedScene" data-testid="scene-rule-filter" :disabled="loading || saving" @change="changeScene">
      <option value="">全部场景</option>
      <option v-for="scene in scenes" :key="scene" :value="scene">{{ sceneName(scene) }}</option>
    </select>

    <p v-if="loading" role="status">正在加载规则…</p>
    <VersionConflictNotice v-if="conflict" :current-version="currentVersion" :disabled="loading || saving" @reload="load" />
    <p v-else-if="error" class="error-message" role="alert" data-testid="scene-rule-error">{{ error }}</p>
    <p v-if="success" class="success-message" role="status" data-testid="scene-rule-success">{{ success }}</p>
    <div class="rule-columns">
    <section v-for="column in columns" :key="column.role" class="rule-column" :data-testid="'scene-rule-column-' + column.role.toLowerCase()" :aria-label="column.title">
      <header class="column-heading"><h2>{{ column.title }}</h2>
        <button class="button button-primary" :data-testid="column.role.toLowerCase() + '-rule-add'" :disabled="loading || saving" @click="startCreate(column.role)">添加{{ column.role === 'SCAMMER' ? '骗子' : '受害者' }}规则</button>
      </header>
      <p class="field-help">{{ column.role === 'SCAMMER' ? '可直接添加指导规则，也可在「待入库记录」中从已采用的话术干预归纳经验。' : '填写循环卡点与行为指导；同一请求连续僵持两轮后，第三次符合条件时推进。' }}</p>
      <form v-if="creating === column.role" class="admin-panel" :data-testid="column.role.toLowerCase() + '-rule-create'" @submit.prevent="create">
        <h3>添加{{ column.role === 'SCAMMER' ? '骗子' : '受害者' }}规则</h3>
        <label :for="column.role.toLowerCase() + '-rule-scene'">所属场景</label>
        <select :id="column.role.toLowerCase() + '-rule-scene'" v-model="newScene" :disabled="saving" :data-testid="column.role.toLowerCase() + '-rule-scene'">
          <option v-for="scene in scenes" :key="scene" :value="scene">{{ sceneName(scene) }}</option>
        </select>
        <FormFieldError field="scenarioCode" :errors="fields" />
        <label :for="column.role.toLowerCase() + '-rule-title'">规则名称</label>
        <input :id="column.role.toLowerCase() + '-rule-title'" v-model="newTitle" required maxlength="128" :disabled="saving" :data-testid="column.role.toLowerCase() + '-rule-title'" placeholder="简短说明要解决的卡点" />
        <FormFieldError field="title" :errors="fields" />
        <label :for="column.role.toLowerCase() + '-rule-condition'">触发情境</label>
        <textarea :id="column.role.toLowerCase() + '-rule-condition'" v-model="newCondition" required rows="2" maxlength="1900" :disabled="saving" :data-testid="column.role.toLowerCase() + '-rule-condition'" placeholder="一句话说明何时适用" />
        <label :for="column.role.toLowerCase() + '-rule-guidance'">指导内容</label>
        <textarea :id="column.role.toLowerCase() + '-rule-guidance'" v-model="newGuidance" required rows="2" maxlength="1900" :disabled="saving" :data-testid="column.role.toLowerCase() + '-rule-guidance'" placeholder="一至两句话说明应怎样回应和行动" />
        <FormFieldError field="ruleText" :errors="fields" />
        <p class="field-help">无需干预来源。保留必要条件，不写具体人物、固定金额或本场临时事实。</p>
        <div class="admin-actions">
          <button class="button button-primary" :disabled="saving || !canCreate" :data-testid="column.role.toLowerCase() + '-rule-save'">{{ saving ? '保存中…' : '保存规则' }}</button>
          <button type="button" class="button button-light" :disabled="saving" @click="creating = null; fields = []; error = ''">取消</button>
        </div>
      </form>
      <p v-if="!loading && !visibleRows.some(row => (row.targetRole ?? 'SCAMMER') === column.role)" class="field-help" data-testid="scene-rules-empty">{{ column.role === 'SCAMMER' ? '当前场景暂无骗子规则。' : '当前场景暂无受害者规则，可直接添加。' }}</p>
    <article v-for="row in visibleRows.filter(row => (row.targetRole ?? 'SCAMMER') === column.role)" :key="row.ruleId" class="admin-panel" data-testid="scene-rule-row">
      <div class="admin-row-heading">
        <h2>{{ row.title }}</h2>
        <span class="quiet-label">{{ sceneName(row.scenarioCode) }} · 版本 {{ row.version }} · {{ row.status === 'ENABLED' ? '已启用' : '已停用' }}</span>
      </div>
      <form v-if="editing?.ruleId === row.ruleId" @submit.prevent="save">
        <label :for="'rule-title-' + row.ruleId">规则标题</label>
        <input :id="'rule-title-' + row.ruleId" v-model="title" required maxlength="128" :disabled="saving"
               aria-describedby="rule-title-error" :aria-invalid="fields.some(e => e.field === 'title')" />
        <FormFieldError field="title" :errors="fields" />
        <label :for="'rule-text-' + row.ruleId">规则正文</label>
        <textarea :id="'rule-text-' + row.ruleId" v-model="ruleText" rows="5" required :disabled="saving"
                  aria-describedby="rule-text-error" :aria-invalid="fields.some(e => e.field === 'ruleText')" />
        <FormFieldError field="ruleText" :errors="fields" />
        <label class="intervention-check"><input v-model="enabled" type="checkbox" data-testid="rule-enabled" :disabled="saving" /> 启用该规则</label>
        <div class="admin-actions">
          <button class="button button-primary" data-testid="rule-save" :disabled="saving || loading || conflict">{{ saving ? '保存中…' : '保存' }}</button>
          <button type="button" class="button button-light" data-testid="rule-edit-cancel" :disabled="saving" @click="cancel">取消</button>
        </div>
      </form>
      <template v-else>
        <div class="rule-overview">
          <p><strong>何时适用</strong>{{ ruleOverview(row.ruleText)?.condition ?? '未保存简短适用条件，请展开完整规则查看。' }}</p>
          <p><strong>规则作用</strong>{{ ruleOverview(row.ruleText)?.guidance ?? '未保存简短作用说明，请展开完整规则查看。' }}</p>
        </div>
        <details><summary>完整规则</summary><p class="admin-description">{{ row.ruleText }}</p></details>
        <p class="field-help"><template v-if="column.role === 'SCAMMER'">来源 {{ row.sourceCount }} 条 · </template>更新时间 {{ row.updatedAt }}</p>
        <div class="admin-actions">
          <button class="button button-light" data-testid="rule-edit" :disabled="saving || loading" @click="edit(row)">编辑</button>
          <button class="button button-light" data-testid="rule-toggle" :disabled="saving || loading" @click="toggle(row)">{{ row.status === 'ENABLED' ? '停用' : '启用' }}</button>
          <button v-if="column.role === 'SCAMMER'" class="button button-light" data-testid="rule-sources" :disabled="saving || loading" @click="showSources(row)">
            {{ expanded === row.ruleId ? '收起来源' : '查看来源' }}
          </button>
        </div>
        <div v-if="expanded === row.ruleId" data-testid="rule-source-list">
          <p v-if="sourcesLoading" role="status">正在加载来源…</p>
          <p v-else-if="sourcesError" class="error-message" role="alert">{{ sourcesError }}</p>
          <p v-else-if="sourcesOfRule.length === 0" class="field-help" data-testid="rule-source-empty">该规则暂无本人来源记录。</p>
          <article v-for="record in sourcesOfRule" :key="record.interventionId" class="admin-panel" data-testid="rule-source-row">
            <p class="field-help">第 {{ record.roundNo }} 轮 · 编号 {{ record.interventionId }} · 状态 {{ record.status }}</p>
            <p class="field-help">原台词</p><p class="admin-description">{{ record.originalContent }}</p>
            <p class="field-help">最终台词</p><p class="admin-description">{{ record.finalContent }}</p>
          </article>
        </div>
      </template>
    </article>
    </section>
    </div>
  </div>
</template>

<style scoped>
.rule-columns { display:grid; grid-template-columns:repeat(2, minmax(0, 1fr)); gap:1.5rem; margin-top:1.5rem; }
.rule-column { min-width:0; }
.column-heading { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:.75rem; }
.column-heading h2 { margin:0; }
.rule-column textarea, .rule-column input:not([type=checkbox]), .rule-column select { width:100%; box-sizing:border-box; }
.admin-row-heading { flex-wrap:wrap; gap:.5rem 1rem; }
.admin-row-heading h2 { font-size:22px; overflow-wrap:anywhere; }
.rule-overview p { font-size:17px; line-height:1.7; overflow-wrap:anywhere; }
.rule-overview strong { display:block; font-size:14px; color:var(--muted); }
summary { cursor:pointer; padding:.5rem 0; }
summary:focus-visible { outline:2px solid currentColor; outline-offset:3px; }
@media (max-width:600px) { .admin-row-heading h2 { font-size:20px; } .rule-overview p { font-size:16px; } }
@media (max-width:900px) { .rule-columns { grid-template-columns:1fr; } }
</style>
