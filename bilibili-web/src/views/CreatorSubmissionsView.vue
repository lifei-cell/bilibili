<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Film, Plus } from 'lucide-vue-next'
import { creatorApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import SubmissionResubmitForm from '@/components/SubmissionResubmitForm.vue'
import type { CreatorSubmission, SubmissionStatus, VideoResubmission } from '@/types/api'
import { relativeTime } from '@/utils/format'

const pageSize = 12
const statusLabels: Record<SubmissionStatus, string> = { 0: '待审核', 1: '已发布', 2: '已驳回', 3: '已下架' }
const statusOptions: SubmissionStatus[] = [0, 1, 2, 3]
const status = ref<SubmissionStatus>()
const page = ref(1)
const total = ref(0)
const records = ref<CreatorSubmission[]>([])
const editing = ref<number>()
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const saveError = ref('')
const notice = ref('')

async function load(targetPage = 1) {
  loading.value = true
  loadError.value = ''
  editing.value = undefined
  try {
    const { data } = await creatorApi.submissions({ status: status.value, page: targetPage, size: pageSize })
    records.value = data.records
    total.value = data.total
    page.value = targetPage
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '投稿加载失败'
  } finally {
    loading.value = false
  }
}

function edit(id: number) {
  editing.value = id
  saveError.value = ''
  notice.value = ''
}

async function resubmit(id: number, data: VideoResubmission) {
  saving.value = true
  saveError.value = ''
  try {
    const response = await creatorApi.resubmit(id, data)
    status.value = response.data.status
    notice.value = status.value === 0 ? '已重新提交审核' : '修改已保存，投稿仍未通过风控'
    await load()
  } catch (error) {
    saveError.value = error instanceof Error ? error.message : '重新提交失败'
  } finally {
    saving.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <div class="page-shell submissions-page">
    <header class="submissions-header">
      <h1>投稿管理</h1>
      <RouterLink class="primary-button" to="/upload"><Plus :size="17" />发布新作品</RouterLink>
    </header>
    <section class="submissions-panel" aria-label="我的投稿" :aria-busy="loading">
      <div class="panel-toolbar">
        <label class="field" for="submission-status">审核状态</label>
        <select id="submission-status" v-model="status" :disabled="loading || saving" @change="load()">
          <option :value="undefined">全部状态</option>
          <option v-for="key in statusOptions" :key="key" :value="key">{{ statusLabels[key] }}</option>
        </select>
        <span v-if="!loading && !loadError">{{ total }} 个作品</span>
      </div>
      <p v-if="notice" class="submission-notice" role="status">{{ notice }}</p>
      <p v-if="loading" class="panel-message" role="status">正在加载投稿…</p>
      <div v-else-if="loadError" class="panel-message" role="alert">{{ loadError }} <button class="soft-button" @click="load()">重试</button></div>
      <EmptyState v-else-if="!records.length" title="没有符合条件的投稿" />
      <template v-else>
        <article v-for="item in records" :key="item.id" class="submission">
          <div class="submission-summary">
            <img v-if="item.coverUrl" class="submission-cover" :src="item.coverUrl" alt="" loading="lazy" />
            <div v-else class="submission-cover cover-placeholder" aria-hidden="true"><Film :size="20" /></div>
            <div class="submission-meta">
              <h2>{{ item.title }}</h2>
              <span class="submission-status" :class="`status-${item.status}`">{{ statusLabels[item.status] }}</span>
              <small>{{ relativeTime(item.updateTime) }}更新</small>
            </div>
            <RouterLink v-if="item.status === 1" class="ghost-button" :to="`/video/${item.id}`">查看作品</RouterLink>
            <button v-else-if="item.status === 2 && editing !== item.id" class="soft-button" :disabled="saving" @click="edit(item.id)">修改并重提</button>
          </div>
          <p v-if="item.auditRemark" class="audit-remark">审核说明 · {{ item.auditRemark }}</p>
          <template v-if="editing === item.id">
            <SubmissionResubmitForm :key="item.id" :submission="item" :saving="saving" @cancel="editing = undefined" @submit="resubmit(item.id, $event)" />
            <p v-if="saveError" class="error-banner" role="alert">{{ saveError }}</p>
          </template>
        </article>
        <nav v-if="total > pageSize" class="submission-pagination" aria-label="投稿分页">
          <button class="ghost-button" :disabled="page === 1 || saving" @click="load(page - 1)">上一页</button>
          <span>{{ page }} / {{ Math.ceil(total / pageSize) }}</span>
          <button class="ghost-button" :disabled="page * pageSize >= total || saving" @click="load(page + 1)">下一页</button>
        </nav>
      </template>
    </section>
  </div>
</template>

<style scoped>
.submissions-page { padding-top: 34px; padding-bottom: 40px; max-width: 1000px; }
.submissions-header, .panel-toolbar, .submission-summary, .submission-pagination { display: flex; align-items: center; gap: 12px; }
.submissions-header { justify-content: space-between; margin-bottom: 20px; }
.submissions-header h1 { margin: 0; }
.submissions-panel { background: #fff; border: 1px solid var(--line); border-radius: 16px; }
.panel-toolbar, .submission { padding: 16px 20px; border-bottom: 1px solid var(--line); }
.panel-toolbar select { border: 1px solid var(--line); border-radius: 8px; padding: 7px 10px; background: white; }
.panel-toolbar span { margin-left: auto; color: var(--muted); font-size: 13px; }
.submission-cover { width: 88px; aspect-ratio: 16 / 9; flex-shrink: 0; object-fit: cover; border-radius: 7px; background: #f2f3f5; }
.cover-placeholder { display: grid; place-items: center; color: var(--muted); }
.submission-meta { flex: 1; min-width: 0; }
.submission-meta h2 { margin: 0 0 7px; font-size: 15px; overflow-wrap: anywhere; }
.submission-meta small { margin-left: 8px; color: var(--muted); }
.submission-status { padding: 3px 7px; border-radius: 6px; font-size: 12px; background: #eceef2; }
.status-0 { color: #805200; background: #fff2cf; }
.status-1 { color: #13734f; background: #e5f7ec; }
.status-2 { color: #ad2b46; background: #ffebef; }
.panel-message, .submission-notice, .audit-remark { padding: 10px 14px; margin: 12px 16px; border-radius: 8px; background: #f5f6f8; overflow-wrap: anywhere; }
.audit-remark { margin-inline: 0; margin-bottom: 0; }
.submission-notice { background: #e5f7ec; color: #13734f; }
.submission-pagination { justify-content: center; padding: 14px; }
@media (max-width: 540px) {
  .submissions-header h1 { font-size: 23px; }
  .submission-summary { flex-wrap: wrap; }
  .submission-meta { flex-basis: calc(100% - 100px); }
  .submission-summary > button, .submission-summary > a { margin-left: auto; }
}
</style>
