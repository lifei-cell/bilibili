<script setup lang="ts">
import { ref } from 'vue'
import type { CreatorSubmission, VideoResubmission } from '@/types/api'

const props = defineProps<{ submission: CreatorSubmission; saving: boolean }>()
const emit = defineEmits<{ submit: [data: VideoResubmission]; cancel: [] }>()
const title = ref(props.submission.title)
const description = ref(props.submission.description ?? '')
const tags = ref(props.submission.tags.join('，'))
const error = ref('')

function submit() {
  if (props.saving) return
  const normalizedTags = tags.value.split(/[,，]/).map(tag => tag.trim()).filter(Boolean)
  if (normalizedTags.some(tag => tag.length > 50)) {
    error.value = '每个标签最多 50 字'
    return
  }
  error.value = ''
  emit('submit', { title: title.value.trim(), description: description.value, tags: normalizedTags })
}
</script>

<template>
  <form class="resubmit-form" @submit.prevent="submit">
    <label class="field">作品标题<input v-model.trim="title" maxlength="200" required :disabled="saving" /></label>
    <label class="field">作品简介<textarea v-model="description" maxlength="65535" :disabled="saving"></textarea></label>
    <label class="field">标签<input v-model="tags" placeholder="多个标签用逗号分隔" :disabled="saving" /></label>
    <p v-if="error" class="error-banner" role="alert">{{ error }}</p>
    <div class="form-actions">
      <button type="button" class="ghost-button" :disabled="saving" @click="emit('cancel')">取消</button>
      <button class="primary-button" :disabled="saving || !title">{{ saving ? '正在提交…' : '重新提交审核' }}</button>
    </div>
  </form>
</template>

<style scoped>
.resubmit-form { display: grid; gap: 14px; border-top: 1px solid var(--line); margin-top: 16px; padding-top: 16px; }
.form-actions { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
</style>
