<script setup lang="ts">
import type { FieldError } from '../api/types'
defineProps<{ field: string; errors: FieldError[] }>()
</script>

<template><p v-if="errors.some(e => e.field === field)" :id="`${field}-error`" class="field-error" role="alert">{{ errors.find(e => e.field === field)?.message }}</p></template>
