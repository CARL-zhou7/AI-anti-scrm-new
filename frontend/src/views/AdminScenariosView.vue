<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { adminApi } from '../api/admin'
import { ApiError } from '../api/http'
import type { AdminScenario, FieldError } from '../api/types'
import FormFieldError from '../components/FormFieldError.vue'
import VersionConflictNotice from '../components/VersionConflictNotice.vue'

const rows = ref<AdminScenario[]>([]), loading = ref(false), saving = ref(false), error = ref(''), success = ref('')
const editing = ref<AdminScenario | null>(null), name = ref(''), description = ref(''), fields = ref<FieldError[]>([])
const conflict = ref(false), currentVersion = ref<number | null>(null)
function edit(row: AdminScenario) {
  editing.value = row; name.value = row.name; description.value = row.description
  error.value = ''; success.value = ''; fields.value = []; conflict.value = false
}
function cancel() { editing.value = null; error.value = ''; fields.value = []; conflict.value = false }
async function load() {
  loading.value = true; error.value = ''; success.value = ''
  try { rows.value = await adminApi.listScenarios(); cancel() }
  catch (e) { error.value = e instanceof Error ? e.message : '场景加载失败，请重试' }
  finally { loading.value = false }
}
async function save() {
  if (!editing.value || saving.value || conflict.value) return
  saving.value = true; error.value = ''; fields.value = []; success.value = ''
  try {
    const row = await adminApi.updateScenario(editing.value.id, { name: name.value, description: description.value, version: editing.value.version })
    rows.value = rows.value.map(old => old.id === row.id ? row : old)
    editing.value = null; success.value = '场景已保存'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败，请重试'
    if (e instanceof ApiError) { fields.value = e.fieldErrors; conflict.value = e.code === 'VERSION_CONFLICT'; currentVersion.value = e.currentVersion }
  } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="home-layout admin-layout" data-testid="admin-scenarios" :aria-busy="loading || saving">
    <h1>场景管理</h1><p class="admin-intro">维护三个固定场景的名称与简介。</p>
    <p v-if="loading" role="status">正在加载场景…</p>
    <VersionConflictNotice v-if="conflict" :current-version="currentVersion" :disabled="loading || saving" @reload="load" />
    <p v-else-if="error" class="error-message" role="alert">{{ error }}</p>
    <button v-if="error && !editing" class="button button-light" :disabled="loading" @click="load">重新加载</button>
    <p v-if="success" class="success-message" role="status">{{ success }}</p>
    <div class="admin-split">
      <section class="admin-panel admin-group" aria-labelledby="fixed-scenarios">
        <h2 id="fixed-scenarios">固定场景</h2>
        <article v-for="row in rows" :key="row.id" class="admin-record admin-list-row" :class="{ 'admin-row-active': editing?.id === row.id }" data-testid="admin-scenario-row">
          <div>
            <h3>{{ row.name }}</h3>
            <p class="admin-description">{{ row.description }}</p>
            <p class="quiet-label">{{ row.focus }} · 版本 {{ row.version }} · 更新于 {{ row.updatedAt }}</p>
          </div>
          <button class="button button-light" data-testid="scenario-edit" :disabled="saving || loading" @click="edit(row)">编辑文案</button>
        </article>
      </section>
      <form v-if="editing" class="admin-panel admin-inspector" @submit.prevent="save">
        <h2>编辑场景文案</h2>
        <p class="quiet-label">{{ editing.name }} · 版本 {{ editing.version }}</p>
        <label for="scenario-name">场景名称</label><input id="scenario-name" v-model="name" required :disabled="saving" aria-describedby="name-error" :aria-invalid="fields.some(e => e.field === 'name')" />
        <FormFieldError field="name" :errors="fields" />
        <label for="scenario-description">场景简介</label><textarea id="scenario-description" v-model="description" rows="4" required :disabled="saving" aria-describedby="description-error" :aria-invalid="fields.some(e => e.field === 'description')" />
        <FormFieldError field="description" :errors="fields" />
        <div class="admin-actions"><button class="button button-primary" data-testid="scenario-save" :disabled="saving || loading || conflict">{{ saving ? '保存中…' : '保存' }}</button><button type="button" class="button button-light" :disabled="saving" @click="cancel">取消</button></div>
      </form>
    </div>
  </div>
</template>
