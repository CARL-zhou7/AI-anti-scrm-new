<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { adminApi } from '../api/admin'
import type { AvatarUpload } from '../api/types'

const props = defineProps<{ avatarKey: string; previewUrl: string; disabled: boolean }>()
const emit = defineEmits<{ uploaded: [value: AvatarUpload]; busy: [value: boolean]; ready: [value: boolean] }>()
const file = ref<File | null>(null), localUrl = ref(''), uploading = ref(false), error = ref(''), failed = ref(false)
const fileInput = ref<HTMLInputElement | null>(null), previewRevision = ref(0)
const source = computed(() => localUrl.value || props.previewUrl)
function release() { if (localUrl.value) URL.revokeObjectURL(localUrl.value); localUrl.value = '' }
function select(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = input.files?.[0]
  if (!selected) return
  input.value = ''
  file.value = selected
  release(); error.value = ''; failed.value = false; emit('ready', false)
  if (file.value) {
    if (file.value.size > 5 * 1024 * 1024) { error.value = '头像超过允许大小（5 MB）'; return }
    localUrl.value = URL.createObjectURL(file.value)
  }
}
function cancel() {
  file.value = null; release(); error.value = ''; failed.value = false
  previewRevision.value++; emit('ready', false)
}
function chooseOrUpload() {
  if (props.disabled || uploading.value) return
  if (file.value) void upload()
  else fileInput.value?.click()
}
async function upload() {
  if (!file.value || uploading.value || file.value.size > 5 * 1024 * 1024) return
  uploading.value = true; emit('busy', true); emit('ready', false); error.value = ''
  try {
    const result = await adminApi.uploadAvatar(file.value)
    file.value = null; release(); failed.value = false
    emit('uploaded', result)
  } catch (e) { error.value = e instanceof Error ? e.message : '头像上传失败，请重试' }
  finally { uploading.value = false; emit('busy', false) }
}
function loaded() { failed.value = false; emit('ready', !file.value && !!props.avatarKey) }
function imageError() { failed.value = true; emit('ready', false) }
onBeforeUnmount(release)
</script>

<template>
  <div class="avatar-uploader" data-testid="avatar-uploader">
    <span>头像（静态 JPG / PNG，最大 5 MB）</span>
    <input ref="fileInput" type="file" hidden accept="image/png,image/jpeg" data-testid="avatar-file-input" :disabled="disabled || uploading" @change="select" />
    <div class="admin-avatar-preview">
      <img v-if="source" :key="source + previewRevision" :src="source" alt="头像预览" data-testid="avatar-preview" @load="loaded" @error="imageError" />
      <p v-else class="field-help">请先选择并上传头像。</p>
      <button type="button" class="button button-light" data-testid="avatar-upload" :disabled="disabled || uploading || (file !== null && file.size > 5 * 1024 * 1024)" @click="chooseOrUpload">{{ uploading ? '上传中…' : file ? '确认上传' : '上传头像' }}</button>
      <button type="button" class="button button-light" data-testid="avatar-cancel" :disabled="!file || disabled || uploading" @click="cancel">取消</button>
    </div>
    <p v-if="file && !error" class="field-help">当前为本地预览，点击确认上传，或取消恢复原头像。</p>
    <p v-if="error || failed" class="field-error" role="alert" data-testid="avatar-upload-error">{{ error || '头像预览加载失败，请重新选择并上传' }}</p>
  </div>
</template>
