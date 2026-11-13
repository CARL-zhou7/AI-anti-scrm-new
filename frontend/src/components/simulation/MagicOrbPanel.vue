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
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  drag = { id: event.pointerId, x: event.clientX, y: event.clientY,
    left: bounds.left + bounds.width / 2, top: bounds.top + bounds.height / 2 }
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
  const radius = 26
  const distances = [x, width - x, y, height - y]
  const edge = distances.indexOf(Math.min(...distances))
  position.value = { x: Math.max(radius, Math.min(width - radius, x)) / width,
    y: Math.max(radius, Math.min(height - radius, y)) / height }
  if (distances[edge]! <= radius) {
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
  <aside class="orb" :class="{ expanded }" data-testid="magic-orb" aria-live="polite"
    :style="{ '--orb-x': position.x * 100 + 'vw', '--orb-y': position.y * 100 + 'dvh' }">
    <section v-if="expanded" id="magic-orb-panel" class="panel motion-spotlight" :class="{ 'panel-pending': pendingView }" aria-label="魔法球控制面板">
    <button class="close" type="button" aria-label="收起魔法球面板" @click="expanded = false">×</button>
    <template v-if="pendingView">
      <header><button class="ghost" data-testid="magic-orb-back" aria-label="返回魔法球控制面板" @click="pendingView = false">←</button><span class="title">本场待入库</span></header>
      <div class="pending-scroll" tabindex="0" aria-label="待入库记录列表">
      <p v-if="pendingLoading" class="hint motion-pending" role="status">正在加载…</p>
      <p v-else-if="!pendingRecords?.length" class="hint">暂无记录。修改或重新生成后，确认用于演绎的话术会显示在这里。</p>
      <article v-for="record in pendingRecords" :key="record.interventionId" class="pending-record" data-testid="magic-orb-pending-record">
        <strong>第 {{ record.roundNo }} 轮</strong>
        <p class="hint">上一轮对话</p><p class="context">{{ record.previousContext || '暂无上一轮对话' }}</p>
        <p class="hint">AI 原稿</p><p>{{ record.originalContent }}</p>
        <p class="hint">最终采用</p><p>{{ record.finalContent }}</p>
      </article>
      </div>
    </template>
    <p v-else-if="regenerating" class="generation motion-pending motion-spotlight" role="status" data-testid="magic-orb-regenerating">正在重新生成话术与动作…</p>
    <template v-else-if="draft">
    <header>
      <span class="title">魔法球<small>第 {{ draft.roundNo }} 轮待确认</small></span>
      <span class="rev">修订 v{{ draft.revisionNo }}</span>
    </header>
    <p class="hint">下一轮台词仅在此处预览。舞台保留上一轮对话。</p>

    <p class="panel-label">待确认台词</p>
    <div v-if="!editing" class="draft" data-testid="magic-orb-draft">{{ draft.draftContent }}</div>
    <textarea v-else v-model="content" class="editor" data-testid="magic-orb-editor" rows="4"
              aria-label="修改骗子台词" :disabled="busy" />

    <p v-if="draft.draftAction" class="action" data-testid="magic-orb-action">动作：{{ draft.draftAction }}</p>

    <div class="row">
      <button class="ghost" data-testid="magic-orb-edit" :disabled="busy || (editing && !canSave)" :aria-busy="editing && busy" @click="toggleEdit">
        {{ editing ? (busy ? '保存中…' : '保存修改') : '直接修改' }}
      </button>
      <button v-if="editing" class="ghost" data-testid="magic-orb-cancel" :disabled="busy" @click="cancelEdit">取消</button>
      <button v-else-if="draft.draftContent !== draft.originalContent" class="ghost" data-testid="magic-orb-restore" :disabled="busy" @click="restoreOriginal">恢复 AI 原稿</button>
    </div>

    <div class="regen">
      <label for="orb-guidance" class="panel-label">重新生成方向</label>
      <input id="orb-guidance" v-model="guidance" class="guidance" data-testid="magic-orb-guidance" maxlength="200"
             placeholder="话术和动作的重新生成方向（可留空）" aria-label="话术和动作的重新生成方向" />
    </div>
    </template>
    <p v-else class="hint" role="status">等待骗子台词，准备好后可点击解说框继续对话。</p>
    <template v-if="!pendingView">
      <div v-if="(draft && !regenerating) || showPending" class="row">
        <button v-if="draft && !regenerating" class="ghost generate" data-testid="magic-orb-regenerate" :disabled="busy || editing"
                @click="emit('regenerate', guidance.trim())">重新生成话术与动作</button>
        <button v-if="showPending" class="ghost" data-testid="magic-orb-pending" @click="pendingView = true; emit('openPending')">本场待入库</button>
      </div>
      <p v-if="draft && !regenerating" class="hint">重新生成会同时更新话术和动作；直接修改仅修改台词文字，保留原动作。</p>
    </template>
    <p v-if="error" class="error" role="alert" data-testid="magic-orb-error">{{ error }}</p>
    <p v-if="!pendingView" class="panel-foot">可拖动球体并吸附到屏幕边缘。未确认时刷新将按主动退出处理。</p>
    </section>
    <button class="orb-toggle" data-testid="magic-orb-toggle"
      :aria-expanded="expanded" aria-controls="magic-orb-panel" aria-label="魔法球，展开或收起控制面板"
      @pointerdown="pointerDown" @pointermove="pointerMove" @pointerup="pointerUp" @pointercancel="pointerUp"
      @click="toggle">
      <svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" aria-hidden="true"><path d="m12 1 2.6 8.4L23 12l-8.4 2.6L12 23l-2.6-8.4L1 12l8.4-2.6Z"/></svg>
    </button>
  </aside>
</template>

<style scoped>
.orb { display:contents; }
.panel {
  position:relative; height:100%; min-height:0; display:flex; flex-direction:column;
  overflow:auto; overscroll-behavior:contain;
  padding:14px; border-radius:5px; background:#141e31; color:#f0f5fb;
  border:1px solid #8a6ac5; border-top:3px solid #baa0ff;
}
.orb.expanded { display:block; min-width:0; }
.panel > :not(.pending-scroll) { flex:none; }
.pending-scroll { flex:1; min-height:0; overflow-y:auto; overscroll-behavior:contain; }
.close { position:absolute; top:9px; right:9px; padding:2px 7px; border:0; background:transparent; color:#f6eeff; font-size:18px; line-height:1.2; }
.orb-toggle {
  position:fixed; z-index:20; left:clamp(26px,var(--orb-x),calc(100vw - 26px)); top:clamp(26px,var(--orb-y),calc(100dvh - 26px));
  transform:translate(-50%,-50%); display:grid; place-items:center; width:52px; height:52px; padding:0; border-radius:50%;
  touch-action:none; user-select:none; border:1px solid #decaff; color:#fff;
  background:radial-gradient(circle at 35% 29%,#f8ebff,#b697ff 15%,#8153d6 36%,#30204e 69%,#180f2e 83%);
  box-shadow:0 0 21px #aa83f18c,inset -6px -6px 12px #1c113b;
}
.orb-toggle::after { content:''; position:absolute; inset:7px -2px; border:1px solid #e8d8ffb0; border-inline-color:transparent; border-radius:50%; transform:rotate(-28deg); pointer-events:none; }
button:focus-visible { outline:3px solid #dfcdff; outline-offset:3px; }
header { display:flex; justify-content:space-between; align-items:center; gap:8px; margin:-14px -14px 10px; padding:10px 36px 10px 14px; background:#251d3d; border-bottom:1px solid #5c467d; }
.title { font-size:19px; font-weight:750; }
.title small { display:block; font-size:13px; color:#d7c8ee; font-weight:500; }
.rev { font-size:13px; color:#eadcff; background:#38284f; border:1px solid #7355af; border-radius:3px; padding:3px 6px; white-space:nowrap; }
.hint,.action,.panel-foot { margin:7px 0 9px; font-size:13px; color:#c1d2e3; line-height:1.5; overflow-wrap:anywhere; }
.panel-label { margin:0; font-size:14px; font-weight:700; color:#e3d5ff; }
.draft,.editor,.guidance {
  width:100%; padding:8px 10px; border-radius:4px; border:1px solid #59718c;
  background:#0f192a; color:#f5f8ff; font:15px/1.55 var(--sim-display); overflow-wrap:anywhere;
}
.guidance::placeholder { color:#b6c8d9; }
.editor { resize:vertical; }
.row { display:flex; flex-wrap:wrap; gap:8px; margin:8px 0; align-items:center; }
.regen { display:grid; gap:5px; justify-items:start; }
button { padding:7px 10px; border-radius:4px; font-size:13px; cursor:pointer; }
button:disabled { opacity:.55; cursor:not-allowed; }
.ghost { background:#16263b; border:1px solid #607a94; color:#f4f8ff; }
.generate { background:#b49aff; border-color:#b49aff; color:#24173c; }
.error,.generation { padding:8px 10px; border-radius:3px; overflow-wrap:anywhere; }
.error { background:#42282d; color:#ffd3c7; }
.generation { margin:22px 0 12px; background:#312449; color:#f0e6ff; }
.panel-foot { margin: auto 0 0; padding-top:9px; border-top:1px solid #40546d; }
.pending-record { padding:9px 0; border-bottom:1px solid #40546d; font-size:15px; overflow-wrap:anywhere; }
.pending-record p { margin:5px 0; }
.context { white-space:pre-line; }
@media(min-width:1001px) and (700px <= height <= 820px) {
  .panel { padding:10px; }
  header { margin:-10px -10px 5px; padding:6px 32px 6px 10px; }
  .hint,.action { margin:3px 0 5px; line-height:1.4; }
  .row { margin:4px 0; }
  .draft,.editor,.guidance { padding:5px 8px; line-height:1.45; }
  .ghost { padding-block:4px; }
  .panel-foot { padding-top:5px; line-height:1.4; }
}
@media(max-width:1000px) {
  .orb.expanded { position:fixed; z-index:19; inset:auto 12px 76px auto; width:min(402px,calc(100vw - 24px)); }
  .panel { height:auto; max-height:min(70dvh,600px); box-shadow:0 8px 30px #040811b5; }
}
</style>
