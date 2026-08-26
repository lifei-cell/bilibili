<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { CheckCircle2, Flag, RefreshCw, ShieldCheck, XCircle } from 'lucide-vue-next'
import { governanceApi } from '@/api'
import type { AdminVideo, ContentReport } from '@/types/api'

const tab = ref<'videos' | 'reports'>('videos')
const videos = ref<AdminVideo[]>([])
const reports = ref<ContentReport[]>([])
const videoStatus = ref<number | undefined>(0)
const reportStatus = ref<number | undefined>(0)
const loading = ref(false)
const error = ref('')

const statusText: Record<number, string> = { 0: '待审核', 1: '已发布', 2: '已拒绝', 3: '已下架' }
const reportStatusText: Record<number, string> = { 0: '待处理', 1: '处理中', 2: '举报成立', 3: '已驳回' }

async function loadVideos() {
  loading.value = true; error.value = ''
  try { videos.value = (await governanceApi.videos({ status: videoStatus.value, size: 100 })).data.records }
  catch (e) { error.value = e instanceof Error ? e.message : '加载失败' }
  finally { loading.value = false }
}

async function loadReports() {
  loading.value = true; error.value = ''
  try { reports.value = (await governanceApi.reports({ status: reportStatus.value, size: 100 })).data.records }
  catch (e) { error.value = e instanceof Error ? e.message : '加载失败' }
  finally { loading.value = false }
}

async function audit(video: AdminVideo, action: string) {
  const remark = action === 'APPROVE' ? '人工复核通过' : window.prompt('请输入审核备注', '')
  if (action !== 'APPROVE' && remark === null) return
  await governanceApi.auditVideo(video.id, action, remark || undefined)
  await loadVideos()
}

async function resolve(report: ContentReport, action: 'UPHOLD' | 'DISMISS') {
  const remark = window.prompt(action === 'UPHOLD' ? '请输入处置说明' : '请输入驳回说明', '')
  if (remark === null) return
  await governanceApi.resolveReport(report.id, action, remark)
  await loadReports()
}

function selectTab(value: 'videos' | 'reports') {
  tab.value = value
  if (value === 'videos') void loadVideos(); else void loadReports()
}

onMounted(loadVideos)
</script>

<template>
  <div class="page-shell admin-page">
    <header class="admin-head">
      <div><span><ShieldCheck :size="16" /> CONTENT GOVERNANCE</span><h1>内容治理后台</h1><p>审核、举报和风险分级统一闭环，所有处置写入不可变审计日志。</p></div>
      <button class="refresh-button" :disabled="loading" @click="tab === 'videos' ? loadVideos() : loadReports()"><RefreshCw :size="16" />刷新</button>
    </header>

    <nav class="admin-tabs">
      <button :class="{ active: tab === 'videos' }" @click="selectTab('videos')">视频审核</button>
      <button :class="{ active: tab === 'reports' }" @click="selectTab('reports')">举报处置</button>
    </nav>
    <div v-if="error" class="error-banner">{{ error }}</div>

    <section v-if="tab === 'videos'" class="admin-panel">
      <div class="panel-tools"><strong>视频队列</strong><select v-model="videoStatus" @change="loadVideos"><option :value="undefined">全部状态</option><option :value="0">待审核</option><option :value="1">已发布</option><option :value="2">已拒绝</option><option :value="3">已下架</option></select></div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>视频</th><th>作者</th><th>风险</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="item in videos" :key="item.id"><td><div class="video-cell"><img :src="item.coverUrl || '/favicon.ico'" alt="" /><div><strong>{{ item.title }}</strong><small>#{{ item.id }}</small></div></div></td><td>{{ item.authorName || item.userId }}</td><td><span class="risk" :class="item.riskLevel.toLowerCase()">{{ item.riskLevel }}</span></td><td>{{ statusText[item.status] }}</td><td>{{ new Date(item.createTime).toLocaleString() }}</td><td><div class="row-actions"><button v-if="item.status !== 1" class="approve" @click="audit(item, item.status === 3 ? 'RESTORE' : 'APPROVE')"><CheckCircle2 :size="14" />通过</button><button v-if="item.status === 0" @click="audit(item, 'REJECT')"><XCircle :size="14" />拒绝</button><button v-if="item.status === 1" @click="audit(item, 'OFFLINE')"><XCircle :size="14" />下架</button></div></td></tr></tbody>
        </table><p v-if="!videos.length && !loading" class="empty">当前队列暂无内容</p>
      </div>
    </section>

    <section v-else class="admin-panel">
      <div class="panel-tools"><strong>举报队列</strong><select v-model="reportStatus" @change="loadReports"><option :value="undefined">全部状态</option><option :value="0">待处理</option><option :value="2">成立</option><option :value="3">驳回</option></select></div>
      <div class="report-list"><article v-for="item in reports" :key="item.id"><div class="report-icon"><Flag :size="20" /></div><div><header><strong>{{ item.reasonCode }} · {{ item.targetType }} #{{ item.targetId }}</strong><span>{{ reportStatusText[item.status] }}</span></header><p>{{ item.description || '举报人未补充说明' }}</p><small>举报人 {{ item.reporterName || item.reporterId }} · {{ new Date(item.createTime).toLocaleString() }}</small></div><div v-if="item.status < 2" class="row-actions"><button class="approve" @click="resolve(item, 'UPHOLD')">成立并处置</button><button @click="resolve(item, 'DISMISS')">驳回</button></div></article><p v-if="!reports.length && !loading" class="empty">当前队列暂无举报</p></div>
    </section>
  </div>
</template>

<style scoped>
.admin-page{padding-top:34px}.admin-head{display:flex;align-items:flex-end;justify-content:space-between;padding:26px;border-radius:18px;color:#fff;background:linear-gradient(120deg,#292d3a,#5b4057)}.admin-head span{display:flex;gap:7px;align-items:center;color:#ff9aae;font-size:11px;font-weight:700;letter-spacing:.12em}.admin-head h1{margin:8px 0 4px}.admin-head p{margin:0;color:#c9cbd2;font-size:12px}.refresh-button{display:flex;align-items:center;gap:6px;padding:9px 14px;border:1px solid #ffffff38;border-radius:9px;color:#fff;background:#ffffff12}.admin-tabs{display:flex;gap:8px;margin:24px 0 12px}.admin-tabs button{padding:10px 18px;border:0;border-radius:9px;background:#eceef2}.admin-tabs button.active{color:#fff;background:var(--pink)}.admin-panel{border:1px solid var(--line);border-radius:15px;background:#fff}.panel-tools{display:flex;justify-content:space-between;align-items:center;padding:16px 19px;border-bottom:1px solid var(--line)}.panel-tools select{padding:7px 10px;border:1px solid var(--line);border-radius:8px}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:12px}th,td{padding:13px 16px;text-align:left;border-bottom:1px solid #eff0f2;white-space:nowrap}th{color:var(--muted);font-weight:600}.video-cell{display:flex;align-items:center;gap:10px}.video-cell img{width:72px;height:42px;object-fit:cover;border-radius:6px;background:#eee}.video-cell small{display:block;margin-top:4px;color:var(--muted)}.risk{padding:4px 7px;border-radius:6px;font-size:10px;font-weight:700}.risk.low{color:#16845c;background:#e6f7f0}.risk.medium{color:#a66c08;background:#fff4d8}.risk.high{color:#c9364f;background:#ffe8ec}.row-actions{display:flex;gap:7px}.row-actions button{display:flex;align-items:center;gap:4px;padding:6px 9px;border:1px solid #e1e3e8;border-radius:7px;background:#fff}.row-actions .approve{color:#14845c;border-color:#bce8d7;background:#f0fbf7}.report-list article{display:grid;grid-template-columns:40px 1fr auto;gap:13px;align-items:center;padding:18px;border-bottom:1px solid var(--line)}.report-icon{width:40px;height:40px;display:grid;place-items:center;border-radius:10px;color:var(--pink);background:var(--pink-soft)}.report-list header{display:flex;align-items:center;gap:9px}.report-list header span{padding:3px 7px;border-radius:6px;color:#7a5962;background:#fff0f3;font-size:10px}.report-list p{margin:6px 0;color:#555c68}.report-list small,.empty{color:var(--muted)}.empty{text-align:center;padding:34px}@media(max-width:700px){.admin-head{align-items:start}.admin-head p,.refresh-button{display:none}.report-list article{grid-template-columns:40px 1fr}.report-list .row-actions{grid-column:2}.video-cell img{display:none}}
</style>
