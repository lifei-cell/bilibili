<script setup lang="ts">
import { Play, MessageSquare } from 'lucide-vue-next'
import type { VideoListItem } from '@/types/api'
import { formatCount, formatDuration, relativeTime } from '@/utils/format'

defineProps<{ video: VideoListItem }>()
</script>

<template>
  <RouterLink class="video-card" :to="`/video/${video.id}`">
    <div class="video-card__cover">
      <img :src="video.coverUrl" :alt="video.title" loading="lazy" />
      <div class="video-card__stats">
        <span><Play :size="14" fill="currentColor" />{{ formatCount(video.viewCount) }}</span>
        <span><MessageSquare :size="14" />{{ formatCount(video.danmuCount) }}</span>
        <b>{{ formatDuration(video.duration) }}</b>
      </div>
    </div>
    <div class="video-card__body">
      <h3>{{ video.title }}</h3>
      <p>{{ video.authorName }} · {{ relativeTime(video.createTime) }}</p>
    </div>
  </RouterLink>
</template>
