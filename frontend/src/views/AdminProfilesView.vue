<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/http'
import type { AdminProfile, AvatarUpload, FieldError, ProfileFormValues, ProfileKind, ScammerWrite, VictimWrite } from '../api/types'
import AdminNav from '../components/AdminNav.vue'
import AvatarUploader from '../components/AvatarUploader.vue'
import ProfileFormFields from '../components/ProfileFormFields.vue'
import ProfileDetail from '../components/ProfileDetail.vue'
import FormFieldError from '../components/FormFieldError.vue'
import VersionConflictNotice from '../components/VersionConflictNotice.vue'

const props = defineProps<{ kind: ProfileKind }>()
const title = computed(() => props.kind === 'scammer' ? '骗子画像' : '受害者画像')
const rows = ref<AdminProfile[]>([]), cursor = ref<string | null>(null), loading = ref(false), saving = ref(false), uploading = ref(false)
const error = ref(''), success = ref(''), fields = ref<FieldError[]>([]), conflict = ref(false), currentVersion = ref<number | null>(null)
const formOpen = ref(false), editing = ref<AdminProfile | null>(null), previewUrl = ref(''), avatarReady = ref(false), formKey = ref(0)
const expanded = ref<string | null>(null), failedAvatars = ref(new Set<string>())
const busy = computed(() => loading.value || saving.value || uploading.value)
function blank(): ProfileFormValues {
  return { name: '', age: '', gender: '', identity: '', description: '', contextTime: '', contextPlace: '', currentState: '', avatarKey: '',
    fraudExperience: 'MEDIUM', speechSkill: 'MEDIUM', manipulationSkill: 'MEDIUM', behaviorTraits: '',
    antiFraudAwareness: 'MEDIUM', riskTendency: 'MEDIUM', personalityTraits: '' }
}
const form = ref<ProfileFormValues>(blank())
function clearFeedback() { error.value = ''; success.value = ''; fields.value = []; conflict.value = false; currentVersion.value = null }
function failure(e: unknown) {
  error.value = e instanceof Error ? e.message : '操作失败，请重试'
  if (e instanceof ApiError) { fields.value = e.fieldErrors; conflict.value = e.code === 'VERSION_CONFLICT'; currentVersion.value = e.currentVersion }
}
function replace(row: AdminProfile) {
  const index = rows.value.findIndex(old => old.id === row.id)
  if (index === -1) rows.value.push(row)
  else rows.value[index] = row
  rows.value.sort((a, b) => a.id.length - b.id.length || a.id.localeCompare(b.id))
  failedAvatars.value.delete(row.id)
}
function fill(row?: AdminProfile) {
  editing.value = row ?? null; form.value = blank()
  if (row) {
    const common = { name: row.name, age: row.age, gender: row.gender, identity: row.identity, description: row.description,
      contextTime: row.contextTime, contextPlace: row.contextPlace, currentState: row.currentState, avatarKey: row.avatar.key }
    Object.assign(form.value, common, 'fraudExperience' in row
      ? { fraudExperience: row.fraudExperience, speechSkill: row.speechSkill, manipulationSkill: row.manipulationSkill, behaviorTraits: row.behaviorTraits }
      : { antiFraudAwareness: row.antiFraudAwareness, riskTendency: row.riskTendency, personalityTraits: row.personalityTraits })
  }
  previewUrl.value = row?.avatar.url ?? ''; avatarReady.value = false; formKey.value++; formOpen.value = true
}
function create() { clearFeedback(); fill() }
function cancel() { formOpen.value = false; editing.value = null; clearFeedback() }
async function load(more = false) {
  loading.value = true; clearFeedback()
  try {
    const page = await adminApi.listProfiles(props.kind, more ? cursor.value ?? undefined : undefined)
    if (more) for (const row of page.items) replace(row)
    else { rows.value = page.items; formOpen.value = false; editing.value = null; failedAvatars.value.clear() }
    cursor.value = page.nextCursor
  } catch (e) { failure(e) }
  finally { loading.value = false }
}
async function edit(row: AdminProfile) {
  loading.value = true; clearFeedback()
  try { const current = await adminApi.getProfile(props.kind, row.id); replace(current); fill(current) }
  catch (e) { failure(e) }
  finally { loading.value = false }
}
async function reloadConflict() {
  if (editing.value) await edit(editing.value)
  else await load()
}
function uploaded(value: AvatarUpload) { form.value.avatarKey = value.key; previewUrl.value = value.previewUrl; avatarReady.value = false }
async function save() {
  if (busy.value || !avatarReady.value || !form.value.avatarKey || conflict.value) return
  saving.value = true; clearFeedback()
  const f = form.value
  const common = { name: f.name, age: Number(f.age), gender: f.gender, identity: f.identity, description: f.description,
    contextTime: f.contextTime, contextPlace: f.contextPlace, currentState: f.currentState, avatarKey: f.avatarKey }
  const body: ScammerWrite | VictimWrite = props.kind === 'scammer'
    ? { ...common, fraudExperience: f.fraudExperience, speechSkill: f.speechSkill, manipulationSkill: f.manipulationSkill, behaviorTraits: f.behaviorTraits }
    : { ...common, antiFraudAwareness: f.antiFraudAwareness, riskTendency: f.riskTendency, personalityTraits: f.personalityTraits }
  try {
    const row = editing.value ? await adminApi.updateProfile(props.kind, editing.value.id, { ...body, version: editing.value.version })
      : await adminApi.createProfile(props.kind, body)
    replace(row); formOpen.value = false; editing.value = null; success.value = '画像已保存'
  } catch (e) { failure(e) }
  finally { saving.value = false }
}
async function toggle(row: AdminProfile) {
  if (busy.value || !window.confirm(`确认${row.enabled ? '停用' : '启用'}「${row.name}」？`)) return
  saving.value = true; clearFeedback()
  try { replace(await adminApi.setEnabled(props.kind, row.id, !row.enabled, row.version)); success.value = '可用状态已更新' }
  catch (e) { failure(e) }
  finally { saving.value = false }
}
async function remove(row: AdminProfile) {
  if (busy.value || !window.confirm(`确认删除「${row.name}」？删除后无法恢复。`)) return
  saving.value = true; clearFeedback()
  try {
    await adminApi.deleteProfile(props.kind, row.id, row.version)
    rows.value = rows.value.filter(item => item.id !== row.id)
    failedAvatars.value.delete(row.id)
    if (expanded.value === row.id) expanded.value = null
    success.value = '画像已删除'
  } catch (e) { failure(e) }
  finally { saving.value = false }
}
const displayedRows = computed(() => formOpen.value && !editing.value ? [null, ...rows.value] : rows.value)
onMounted(() => load())
</script>

<template>
  <div class="home-layout admin-layout" :data-testid="'admin-' + kind + 's'" :aria-busy="busy">
    <AdminNav /><div class="admin-row-heading"><h1>{{ title }}管理</h1><button class="button button-primary" :data-testid="kind + '-new'" :disabled="busy || formOpen" @click="create">新增{{ title }}</button></div>
    <p class="admin-intro">维护完整人物信息。每类至少保留一个可用画像，停用的画像可重新启用。</p>
    <p v-if="loading" role="status">正在加载…</p>
    <VersionConflictNotice v-if="conflict" :current-version="currentVersion" :disabled="busy" @reload="reloadConflict" />
    <p v-else-if="error" class="error-message" role="alert" :data-testid="kind + '-error'">{{ error }}</p>
    <button v-if="error && !formOpen && !conflict" class="button button-light" :disabled="busy" @click="load()">重新加载</button>
    <p v-if="success" class="success-message" role="status">{{ success }}</p>
    <section v-for="row in displayedRows" :key="row?.id ?? 'new'" class="admin-panel" data-testid="admin-profile-row">
      <template v-if="formOpen && (editing?.id ?? null) === (row?.id ?? null)">
      <h2>{{ editing ? '编辑' : '新增' }}{{ title }}</h2>
      <form :data-testid="kind + '-form'" @submit.prevent="save">
        <AvatarUploader :key="formKey" :avatar-key="form.avatarKey" :preview-url="previewUrl" :disabled="saving || loading" @uploaded="uploaded" @busy="uploading = $event" @ready="avatarReady = $event" />
        <FormFieldError field="avatarKey" :errors="fields" />
        <ProfileFormFields v-model="form" :kind="kind" :errors="fields" :disabled="saving || loading" />
        <p v-if="!avatarReady" class="field-help">请先上传有效头像，并等待头像预览加载成功后保存。</p>
        <div class="admin-actions"><button class="button button-primary" :data-testid="kind + '-save'" :disabled="busy || !avatarReady || !form.avatarKey || conflict">{{ saving ? '保存中…' : '保存画像' }}</button><button type="button" class="button button-light" :disabled="busy" @click="cancel">取消</button></div>
      </form>
      </template>
      <template v-else-if="row">
      <div class="admin-profile-heading">
        <img v-if="!failedAvatars.has(row.id)" :key="row.avatar.key" class="admin-profile-avatar" :src="row.avatar.url" :alt="row.name + '的头像'" @error="failedAvatars.add(row.id)" />
        <span v-else class="field-error">头像加载失败</span>
        <div class="admin-profile-copy"><h2>{{ row.name }}</h2><p>{{ row.identity }}</p><span :class="['admin-badge', { inactive: !row.enabled }]" data-testid="profile-enabled-badge">{{ row.enabled ? '启用' : '停用' }}</span> <span class="quiet-label" data-testid="profile-version">版本 {{ row.version }}</span></div>
        <div class="admin-actions"><button class="text-button" :aria-expanded="expanded === row.id" @click="expanded = expanded === row.id ? null : row.id">{{ expanded === row.id ? '收起详情' : '完整详情' }}</button><button class="button button-light" data-testid="profile-edit" :disabled="busy || formOpen" @click="edit(row)">编辑</button><button class="button admin-toggle" data-testid="profile-toggle" :disabled="busy || formOpen" @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</button><button class="button admin-delete" data-testid="profile-delete" :disabled="busy || formOpen" @click="remove(row)">删除</button></div>
      </div>
      <div v-if="expanded === row.id"><ProfileDetail :profile="row" @avatar-error="failedAvatars.add(row.id)" /><p class="field-help">创建时间：{{ row.createdAt }}<br />更新时间：{{ row.updatedAt }}</p></div>
      </template>
    </section>
    <p v-if="!loading && !error && !rows.length && !formOpen" class="empty-state">暂无画像，请新增。</p>
    <button v-if="cursor" class="button button-light" :disabled="busy || formOpen" @click="load(true)">加载更多</button>
  </div>
</template>
