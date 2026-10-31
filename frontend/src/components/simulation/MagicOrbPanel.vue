<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { InterventionRecord, ScammerDraft } from '../../api/types'

const props = defineProps<{ draft: ScammerDraft | null; busy: boolean; regenerating?: boolean; showPending?: boolean; error?: string; pendingRecords?: InterventionRecord[]; pendingLoading?: boolean }>()
const emit = defineEmits<{
  (e: 'editingChange', editing: boolean): void
  (e: 'regenerate', guidance: string): void
  (e: 'edit', content: string): void
  (e: 'openPending'): void
  (e: 'cancelEdit'): void
}>()

const editing = ref(false)
const expanded = ref(false)
const pendingView = ref(false)
const position = ref({ x: 1, y: 0.5 })
let drag: { id: number; x: number; y: number; left: number; top: number } | null = null
let dragged = false
const content = ref(props.draft?.draftContent ?? '')
const guidance = ref('')
let lastRoundNo = props.draft?.roundNo ?? null
watch([() => props.draft?.draftId, () => props.draft?.revisionNo], () => {
  const draft = props.draft
  content.value = draft?.draftContent ?? ''; editing.value = false
  if (draft && draft.roundNo !== lastRoundNo) {
    guidance.value = ''; if (!pendingView.value) expanded.value = false; lastRoundNo = draft.roundNo
  }
  if (!draft && !props.regenerating && !pendingView.value) expanded.value = false
})
watch(editing, value => emit('editingChange', value))

function pointerDown(event: PointerEvent) {
  if (event.button !== 0) return
  drag = { id: event.pointerId, x: event.clientX, y: event.clientY,
    left: position.value.x * window.innerWidth, top: position.value.y * window.innerHeight }
  dragged = false
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function pointerMove(event: PointerEvent) {
  if (!drag || event.pointerId !== drag.id) return
  const dx = event.clientX - drag.x, dy = event.clientY - drag.y
  if (!dragged && Math.hypot(dx, dy) < 5) return
  dragged = true
  position.value = { x: Math.max(0, Math.min(1, (drag.left + dx) / window.innerWidth)),
    y: Math.max(0, Math.min(1, (drag.top + dy) / window.innerHeight)) }
}

function pointerUp(event: PointerEvent) {
  if (!drag || event.pointerId !== drag.id) return
  drag = null
  if (!dragged) return
  const { innerWidth: width, innerHeight: height } = window
  const x = position.value.x * width, y = position.value.y * height
  const distances = [x, width - x, y, height - y]
  const edge = distances.indexOf(Math.min(...distances))
  position.value = { x: Math.max(24, Math.min(width - 24, x)) / width,
    y: Math.max(24, Math.min(height - 24, y)) / height }
  if (distances[edge]! <= 24) {
    if (edge < 2) position.value.x = edge
    else position.value.y = edge - 2
  }
}

function toggle(event: MouseEvent) {
  if (dragged && event.detail !== 0) { dragged = false; return }
  expanded.value = !expanded.value
}

const canSave = computed(() => content.value.trim().length > 0 && !props.busy)

function save() { if (canSave.value) emit('edit', content.value.trim()) }

function toggleEdit() {
  if (!editing.value) { editing.value = true; return }
  save()
}

function cancelEdit() {
  content.value = props.draft?.draftContent ?? ''
  editing.value = false
  emit('cancelEdit')
}

function restoreOriginal() {
  content.value = props.draft?.originalContent ?? ''
  editing.value = true
  save()
}
</script>

<template>
  <aside class="orb" data-testid="magic-orb" aria-live="polite"
    :style="{ left: `calc(${position.x * 100}vw - 24px)`, top: `calc(${position.y * 100}dvh - 24px)` }">
    <section v-if="expanded" id="magic-orb-panel" class="panel" :class="{ 'panel-left': position.x < 0.5, 'panel-pending': pendingView }" aria-label="魔法球控制面板">
    <template v-if="pendingView">
      <header><button class="ghost" data-testid="magic-orb-back" aria-label="返回魔法球控制面板" @click="pendingView = false">←</button><span class="title">本场待入库</span></header>
      <div class="pending-scroll" tabindex="0" aria-label="待入库记录列表">
      <p v-if="pendingLoading" class="hint" role="status">正在加载…</p>
      <p v-else-if="!pendingRecords?.length" class="hint">暂无记录。修改或重新生成后，确认用于演绎的话术会显示在这里。</p>
      <article v-for="record in pendingRecords" :key="record.interventionId" class="pending-record" data-testid="magic-orb-pending-record">
        <strong>第 {{ record.roundNo }} 轮</strong>
        <p class="hint">上一轮对话</p><p class="context">{{ record.previousContext || '暂无上一轮对话' }}</p>
        <p class="hint">AI 原稿</p><p>{{ record.originalContent }}</p>
        <p class="hint">最终采用</p><p>{{ record.finalContent }}</p>
      </article>
      </div>
    </template>
    <p v-else-if="regenerating" class="hint" role="status" data-testid="magic-orb-regenerating">重新生成ing...</p>
    <template v-else-if="draft">
    <header>
      <span class="title">魔法球 · 第 {{ draft.roundNo }} 轮待确认</span>
      <span class="rev">修订 v{{ draft.revisionNo }}</span>
    </header>
    <p class="hint">待确认台词仅在此处预览，上方保留上一轮双方对话。点击下方解说框任意位置后，开始本轮演绎。未确认时刷新将按主动退出处理。</p>

    <div v-if="!editing" class="draft" data-testid="magic-orb-draft">{{ draft.draftContent }}</div>
    <textarea v-else v-model="content" class="editor" data-testid="magic-orb-editor" rows="4"
              aria-label="修改骗子台词" :disabled="busy" />

    <p v-if="draft.draftAction" class="action" data-testid="magic-orb-action">动作：{{ draft.draftAction }}</p>

    <div class="row">
      <button class="ghost" data-testid="magic-orb-edit" :disabled="busy || (editing && !canSave)" @click="toggleEdit">
        {{ editing ? (busy ? '保存中…' : '保存修改') : '直接修改' }}
      </button>
      <button v-if="editing" class="ghost" data-testid="magic-orb-cancel" :disabled="busy" @click="cancelEdit">取消</button>
      <button v-else-if="draft.draftContent !== draft.originalContent" class="ghost" data-testid="magic-orb-restore" :disabled="busy" @click="restoreOriginal">恢复 AI 原稿</button>
    </div>

    <div class="row regen">
      <input v-model="guidance" class="guidance" data-testid="magic-orb-guidance" maxlength="200"
             placeholder="话术和动作的重新生成方向（可留空）" aria-label="话术和动作的重新生成方向" />
      <button class="ghost" data-testid="magic-orb-regenerate" :disabled="busy || editing"
              @click="emit('regenerate', guidance.trim())">重新生成</button>
    </div>
    <p class="hint">重新生成会同时更新话术和动作；直接修改仅修改台词文字，保留原动作。</p>
    </template>
    <p v-else class="hint" role="status">等待骗子台词，准备好后可点击解说框继续对话。</p>
    <button v-if="showPending && !pendingView" class="ghost" data-testid="magic-orb-pending" @click="pendingView = true; emit('openPending')">待入库</button>
    <p v-if="error" class="error" role="alert" data-testid="magic-orb-error">{{ error }}</p>
    </section>
    <button class="orb-toggle" data-testid="magic-orb-toggle"
      :aria-expanded="expanded" aria-controls="magic-orb-panel" aria-label="魔法球，展开或收起控制面板"
      @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerUp"
      @click="toggle" />
  </aside>
</template>

<style scoped>
.orb { position:fixed; z-index:20; width:48px; height:48px; }
.panel { position:fixed; right:16px; bottom:84px; box-sizing:border-box; width:420px; max-width:calc(100vw - 32px); max-height:calc(100dvh - 100px); overflow-y:auto; padding:16px 18px; border-radius:14px; background:var(--sim-paper); color:var(--sim-ink);
  border:1px solid #cfc8b6; box-shadow:0 8px 24px rgb(13 16 30 / 0.12); }
.panel-left { left:16px; right:auto; }
.panel-pending { height:460px; display:flex; flex-direction:column; overflow:hidden; }
.pending-scroll { flex:1; min-height:0; overflow-y:auto; overscroll-behavior:contain; }
.panel-pending header { flex-shrink:0; }
.orb-toggle { width:48px; height:48px; padding:0; touch-action:none; user-select:none; border-radius:50%; border:1px solid #bcb3ff;
  background:radial-gradient(circle at 32% 25%, #eeebff, #9b83de 30%, #4e367f 68%, #241b48); color:#fff; box-shadow:inset -7px -8px 16px #211c58, 0 0 28px #aa8bdf80; }
button:focus-visible { outline:3px solid var(--sim-gold); outline-offset:3px; }
header { display:flex; justify-content:space-between; align-items:baseline; gap:12px; }
.title { font-family:var(--sim-display); font-size:18px; }
.rev { font-size:12px; color:#6b6f80; }
.hint { margin:8px 0 10px; font-size:13px; color:#6b6f80; line-height:1.6; }
.draft, .editor { width:100%; box-sizing:border-box; padding:10px 12px; border-radius:10px; border:1px solid #cfc8b6;
  background:#fbf9f4; font-size:15px; line-height:1.7; overflow-wrap:anywhere; }
.editor { resize:vertical; font-family:inherit; }
.action { margin:8px 0 0; font-size:13px; color:#5b6075; }
.row { display:flex; flex-wrap:wrap; gap:10px; margin-top:12px; align-items:center; }
.regen { margin-top:10px; }
.guidance { flex:1 1 220px; min-width:0; padding:8px 10px; border-radius:8px; border:1px solid #cfc8b6; font-size:13px; }
button { padding:9px 16px; border-radius:9px; font-size:14px; cursor:pointer; }
button:disabled { opacity:0.55; cursor:not-allowed; }
.ghost { background:transparent; border:1px solid #cfc8b6; color:var(--sim-ink); }
.error { margin:12px 0 0; font-size:13px; line-height:1.6; color:#a33328; overflow-wrap:anywhere; }
.pending-record { min-height:100%; box-sizing:border-box; padding:12px 0; border-bottom:1px solid #cfc8b6; font-size:14px; line-height:1.6; overflow-wrap:anywhere; }
.pending-record p { margin:6px 0; }
.context { white-space:pre-line; }
</style>
