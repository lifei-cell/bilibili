<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'
defineProps<{ title?: string; description?: string }>()
</script>

<template>
  <div class="empty-state">
    <div class="empty-state__icon"><Inbox :size="28" /></div>
    <h3>{{ title || '这里还没有内容' }}</h3>
    <p>{{ description || '换个条件再试试看吧' }}</p>
  </div>
</template>
