<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/http'
import type { AdminVictimProfile, FieldError, TrustPolicy } from '../api/types'
import FormFieldError from '../components/FormFieldError.vue'
import VersionConflictNotice from '../components/VersionConflictNotice.vue'

const policy = ref<TrustPolicy | null>(null), loading = ref(false), saving = ref(false)
const error = ref(''), success = ref(''), errors = ref<FieldError[]>([]), conflict = ref(false), currentVersion = ref<number | null>(null)
const form = ref<{ thresholdAccept: number | ''; thresholdSmall: number | ''; thresholdLarge: number | '' }>({ thresholdAccept: '', thresholdSmall: '', thresholdLarge: '' })
const fields = [{ key: 'thresholdAccept', label: '接受进一步了解' }, { key: 'thresholdSmall', label: '小额行动' }, { key: 'thresholdLarge', label: '大额行动' }] as const
const profiles = ref<AdminVictimProfile[]>([]), profileError = ref('')
const scenarioNames: Record<string, string> = { TASK_REBATE: '刷单返利', FAKE_INVESTMENT: '虚假投资', IMPERSONATION_LOAN: '冒充亲友借款' }
const estimates = computed(() => profiles.value.flatMap(profile => profile.scenarioProfiles.map(scene => {
  const gain = Math.round(3 * scene.positiveFactor)
  const small = Number(form.value.thresholdSmall), large = Number(form.value.thresholdLarge)
  const steps = gain > 0 ? Math.ceil(Math.max(0, small - scene.initialTrustScore) / gain) : null
  const total = gain > 0 ? Math.ceil(Math.max(0, large - scene.initialTrustScore) / gain) : null
  const note = scene.initialTrustScore >= small ? '初始分已达到小额门槛'
    : gain === 0 ? '普通有效回答取整后不加分，难以推进'
    : total !== null && total >= 18 ? '到大额阶段预计需较多有效进展，可能挤压追加博弈和结局'
    : '仍需明确付款请求、足够资金及实际执行'
  return { key: `${profile.id}-${scene.scenarioCode}`, name: profile.name, scene: scenarioNames[scene.scenarioCode] ?? scene.scenarioCode, initial: scene.initialTrustScore, gain, steps, total, note }
})))
async function loadProfiles() {
  profileError.value = ''
  try {
    const rows: AdminVictimProfile[] = []
    let cursor: string | undefined
    do {
      const page = await adminApi.listProfiles('victim', cursor)
      for (const item of page.items) if ('scenarioProfiles' in item) rows.push(item)
      cursor = page.hasMore && page.nextCursor ? page.nextCursor : undefined
    } while (cursor)
    profiles.value = rows
  } catch { profileError.value = '人物参数读取失败，暂时无法估算推进次数。' }
}
async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    const value = await adminApi.getTrustPolicy()
    policy.value = value
    form.value = { thresholdAccept: value.thresholdAccept, thresholdSmall: value.thresholdSmall, thresholdLarge: value.thresholdLarge }
    errors.value = []; conflict.value = false; currentVersion.value = null
  } catch (e) { error.value = e instanceof Error ? e.message : '读取失败，请重试' }
  finally { loading.value = false }
}
async function save() {
  if (!policy.value || loading.value || saving.value || conflict.value) return
  errors.value = []; error.value = ''; success.value = ''
  const f = form.value
  for (const field of fields) {
    const value = f[field.key]
    if (typeof value !== 'number' || !Number.isInteger(value) || value < 0 || value > 100)
      errors.value.push({ field: field.key, code: 'VALUE_INVALID', message: '请填写0至100的整数' })
  }
  if (!errors.value.length && !(Number(f.thresholdAccept) < Number(f.thresholdSmall) && Number(f.thresholdSmall) < Number(f.thresholdLarge)))
    errors.value.push({ field: 'thresholdSmall', code: 'VALUE_INVALID', message: '三项阈值必须严格递增' })
  if (errors.value.length) return
  saving.value = true
  try {
    policy.value = await adminApi.updateTrustPolicy({ thresholdAccept: Number(f.thresholdAccept), thresholdSmall: Number(f.thresholdSmall), thresholdLarge: Number(f.thresholdLarge), version: policy.value.version })
    success.value = '已保存，仅影响新场次；进行中的场次继续使用开始时的配置。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败，请重试'
    if (e instanceof ApiError) { errors.value = e.fieldErrors; conflict.value = e.code === 'VERSION_CONFLICT'; currentVersion.value = e.currentVersion }
  } finally { saving.value = false }
}
onMounted(() => { void load(); void loadProfiles() })
</script>

<template>
  <div class="home-layout admin-layout" data-testid="admin-trust-policy" :aria-busy="loading || saving">
    <h1>信任分机制</h1><p class="admin-intro">维护三项行动阈值，范围0至100且严格递增。更改只影响新场次，人物的初始分和系数在受害者画像中维护。</p>
    <p v-if="loading" class="motion-pending" role="status">正在读取…</p>
    <VersionConflictNotice v-if="conflict" :current-version="currentVersion" :disabled="loading || saving" @reload="load" />
    <p v-else-if="error" class="error-message" role="alert">{{ error }}</p>
    <button v-if="error && !conflict" class="button button-light" :disabled="loading || saving" @click="load">重新读取</button>
    <p v-if="success" class="success-message" role="status">{{ success }}</p>
    <section v-if="policy" class="admin-panel">
      <h2>行动阈值</h2>
      <p class="quiet-label">版本 {{ policy.version }} · {{ policy.rulesetVersion }}</p>
      <form @submit.prevent="save">
        <fieldset class="admin-form-fields threshold-fields" :disabled="loading || saving">
          <div v-for="field in fields" :key="field.key"><label :for="field.key">{{ field.label }}</label>
            <input :id="field.key" v-model.number="form[field.key]" type="number" min="0" max="100" step="1" required :data-testid="field.key" :aria-describedby="field.key + '-error'" :aria-invalid="errors.some(e => e.field === field.key)" />
            <FormFieldError :field="field.key" :errors="errors" /></div>
        </fieldset>
        <div class="threshold-scale" :style="{ '--accept': policy.thresholdAccept + '%', '--small': policy.thresholdSmall + '%', '--large': policy.thresholdLarge + '%' }" aria-label="已保存的行动阈值">
          <div class="threshold-track"></div>
          <span>0</span><span>100</span>
        </div>
        <button class="button button-primary" data-testid="policy-save" :aria-busy="saving" :disabled="loading || saving || conflict">{{ saving ? '保存中…' : '保存配置' }}</button>
      </form>
    </section>
    <section v-if="policy" class="admin-panel" aria-labelledby="progress-estimate-title">
      <h2 id="progress-estimate-title">推进节奏参考</h2>
      <p>按每次普通有效回答基础加3分、应用人物系数并四舍五入估算。以下是有效进展次数，不是保证付款的轮次；零分、扣分和缺少付款请求会延后，化解质疑或实际兑现可能加快。</p>
      <p>参考门槛为40／70／85。易受影响组合首次小额付款目标为第6—8轮，谨慎组合为第10—12轮；尽量18轮内形成完整结局，20轮兜底。改变门槛后应结合演绎结果检查节奏。</p>
      <p v-if="profileError" role="status">{{ profileError }}</p>
      <button v-if="profileError" class="button button-light" @click="loadProfiles">重新读取人物</button>
      <div v-if="estimates.length" class="estimate-table">
        <table><thead><tr><th>人物／场景</th><th>初始分</th><th>每次增加</th><th>达到小额</th><th>达到大额</th><th>提示</th></tr></thead>
          <tbody><tr v-for="row in estimates" :key="row.key" data-testid="progress-estimate">
            <td>{{ row.name }}／{{ row.scene }}</td><td>{{ row.initial }}</td><td>{{ row.gain }}</td>
            <td>{{ row.steps === null ? '无法估算' : `${row.steps}次` }}</td>
            <td>{{ row.total === null ? '无法估算' : `${row.total}次` }}</td><td>{{ row.note }}</td>
          </tr></tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.threshold-fields { grid-template-columns:repeat(3,minmax(0,1fr)); }
.threshold-scale { display:flex; flex-wrap:wrap; justify-content:space-between; gap:4px 14px; margin:16px 0 12px; color:var(--muted); font-size:13px; }
.threshold-track { flex-basis:100%; height:11px; border-radius:3px; background:linear-gradient(90deg,#bad6e7 0 var(--accept),#8fb7d0 var(--accept) var(--small),#f0c795 var(--small) var(--large),#e99475 var(--large) 100%); }
.estimate-table { overflow-x:auto; }
table { width:100%; border-collapse:collapse; }
th, td { padding:10px; text-align:left; border-bottom:1px solid var(--border); }
th { background:#e4eef5; color:#395875; font-size:13px; }
@media(max-width:640px) { .threshold-fields { grid-template-columns:1fr; } }
</style>
