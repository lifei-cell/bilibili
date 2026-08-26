<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { Check, CloudUpload, FileVideo, LoaderCircle, Sparkles, X } from 'lucide-vue-next'
import SparkMD5 from 'spark-md5'
import { uploadApi, videoApi } from '@/api'
import { categories } from '@/utils/format'

const file = ref<File>()
const fileInput = ref<HTMLInputElement>()
const dragging = ref(false)
const phase = ref<'idle' | 'hashing' | 'uploading' | 'transcoding' | 'ready' | 'publishing' | 'done'>('idle')
const progress = ref(0)
const error = ref('')
const sourceUrl = ref('')
const fileMd5 = ref('')
const duration = ref(0)
const resolution = ref('1080P')
let pageActive = true
const form = ref({ title: '', description: '', coverUrl: '', categoryId: categories[0]?.id || 0, tags: '' })
const statusText = computed(() => ({ idle: '等待选择', hashing: '正在校验文件', uploading: '正在上传', transcoding: '正在转码', ready: '文件已就绪', publishing: '正在提交', done: '已提交审核' })[phase.value])

onBeforeUnmount(() => { pageActive = false })

function chooseFile(selected?: File) {
  if (!selected) return
  if (!selected.type.startsWith('video/')) { error.value = '请选择视频文件'; return }
  file.value = selected
  form.value.title ||= selected.name.replace(/\.[^.]+$/, '')
  error.value = ''
  phase.value = 'idle'
  progress.value = 0
  readVideoMeta(selected)
}

function readVideoMeta(value: File) {
  const el = document.createElement('video')
  const url = URL.createObjectURL(value)
  el.preload = 'metadata'
  el.onloadedmetadata = () => {
    duration.value = Math.floor(el.duration || 0)
    const height = el.videoHeight
    resolution.value = height >= 2160 ? '4K' : height >= 1080 ? '1080P' : height >= 720 ? '720P' : '480P'
    URL.revokeObjectURL(url)
  }
  el.src = url
}

async function hashBlob(blob: Blob): Promise<string> {
  return SparkMD5.ArrayBuffer.hash(await blob.arrayBuffer())
}

async function startUpload() {
  if (!file.value) return
  const currentFile = file.value
  const chunkSize = 5 * 1024 * 1024
  const totalChunks = Math.ceil(currentFile.size / chunkSize)
  error.value = ''
  try {
    phase.value = 'hashing'
    const hasher = new SparkMD5.ArrayBuffer()
    for (let index = 0; index < totalChunks; index++) {
      hasher.append(await currentFile.slice(index * chunkSize, Math.min((index + 1) * chunkSize, currentFile.size)).arrayBuffer())
      progress.value = Math.round(((index + 1) / totalChunks) * 15)
    }
    fileMd5.value = hasher.end()
    if (currentFile.size >= 20 * 1024 * 1024) {
      phase.value = 'uploading'
      const direct = (await uploadApi.directInit({ fileName: currentFile.name, contentType: currentFile.type || 'video/mp4', fileSize: currentFile.size, fileMd5: fileMd5.value })).data
      await putPresigned(direct.uploadUrl, currentFile, direct.contentType)
      const completed = (await uploadApi.directComplete(direct.uploadId)).data
      sourceUrl.value = completed.sourceUrl
      progress.value = 96
      phase.value = 'transcoding'
      const transcode = await waitForTranscode(completed.transcodeTaskId)
      if (transcode.coverUrl && !form.value.coverUrl) form.value.coverUrl = transcode.coverUrl
      progress.value = 100
      phase.value = 'ready'
      return
    }
    const checked = (await uploadApi.check({ fileMd5: fileMd5.value, fileName: currentFile.name, fileSize: currentFile.size, totalChunks })).data
    if (checked.instant && checked.sourceUrl) {
      sourceUrl.value = checked.sourceUrl
      progress.value = 100
      phase.value = 'ready'
      return
    }
    phase.value = 'uploading'
    const uploaded = new Set(checked.uploadedChunks || [])
    for (let index = 0; index < totalChunks; index++) {
      if (!uploaded.has(index)) {
        const blob = currentFile.slice(index * chunkSize, Math.min((index + 1) * chunkSize, currentFile.size))
        const data = new FormData()
        data.append('uploadId', checked.uploadId)
        data.append('fileMd5', fileMd5.value)
        data.append('chunkMd5', await hashBlob(blob))
        data.append('chunkIndex', String(index))
        data.append('chunkSize', String(blob.size))
        data.append('totalChunks', String(totalChunks))
        data.append('file', blob, `${currentFile.name}.part${index}`)
        await uploadApi.chunk(data)
      }
      progress.value = 15 + Math.round(((index + 1) / totalChunks) * 80)
    }
    const merged = (await uploadApi.merge({ uploadId: checked.uploadId, fileMd5: fileMd5.value, fileName: currentFile.name, totalChunks })).data
    sourceUrl.value = merged.sourceUrl
    progress.value = 96
    phase.value = 'transcoding'
    const transcode = await waitForTranscode(merged.transcodeTaskId)
    if (transcode.coverUrl && !form.value.coverUrl) form.value.coverUrl = transcode.coverUrl
    progress.value = 100
    phase.value = 'ready'
  } catch (e) { error.value = e instanceof Error ? e.message : '上传失败'; phase.value = 'idle'; progress.value = 0 }
}

function wait(ms: number) { return new Promise(resolve => window.setTimeout(resolve, ms)) }

function putPresigned(url: string, value: File, contentType: string) {
  return new Promise<void>((resolve, reject) => {
    const request = new XMLHttpRequest()
    request.open('PUT', url)
    request.setRequestHeader('Content-Type', contentType)
    request.upload.onprogress = event => {
      if (event.lengthComputable) progress.value = 15 + Math.round((event.loaded / event.total) * 80)
    }
    request.onload = () => request.status >= 200 && request.status < 300 ? resolve() : reject(new Error(`对象存储直传失败 (${request.status})`))
    request.onerror = () => reject(new Error('对象存储直传网络异常'))
    request.send(value)
  })
}

async function waitForTranscode(taskId: string) {
  while (pageActive) {
    const task = (await uploadApi.transcodeStatus(taskId)).data
    if (task.status === 'completed') return task
    if (task.status === 'failed') throw new Error(task.errorMessage || '视频转码失败，请重新上传')
    await wait(3000)
  }
  throw new Error('页面已关闭')
}

async function publish() {
  if (!file.value || !sourceUrl.value) { error.value = '请先完成视频上传'; return }
  const tags = form.value.tags.split(/[,，\s]+/).map(item => item.trim()).filter(Boolean)
  if (!tags.length) { error.value = '请至少填写一个标签'; return }
  phase.value = 'publishing'
  error.value = ''
  try {
    await videoApi.publish({ title: form.value.title, description: form.value.description, coverUrl: form.value.coverUrl || undefined, sourceUrl: sourceUrl.value, fileMd5: fileMd5.value, fileSize: file.value.size, duration: duration.value, resolution: resolution.value, categoryId: form.value.categoryId, tags })
    phase.value = 'done'
  } catch (e) { error.value = e instanceof Error ? e.message : '发布失败'; phase.value = 'ready' }
}

function drop(event: DragEvent) { dragging.value = false; chooseFile(event.dataTransfer?.files[0]) }
function clearFile() { file.value = undefined; sourceUrl.value = ''; fileMd5.value = ''; phase.value = 'idle'; progress.value = 0 }
</script>

<template>
  <div class="page-shell upload-page">
    <header class="upload-head"><div><span><Sparkles :size="15" /> CREATOR STUDIO</span><h1>发布新作品</h1><p>让你的灵感，被更多人看见。</p></div><div class="step-track"><span class="active"><b>1</b>上传文件</span><i></i><span :class="{ active: phase === 'ready' || phase === 'publishing' || phase === 'done' }"><b>2</b>填写信息</span><i></i><span :class="{ active: phase === 'done' }"><b>3</b>发布完成</span></div></header>

    <div class="upload-layout">
      <section class="upload-card">
        <div v-if="!file" class="drop-zone" :class="{ dragging }" @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="drop" @click="fileInput?.click()">
          <div class="upload-icon"><CloudUpload :size="34" /></div><h2>拖拽视频到这里上传</h2><p>或者点击选择本地文件</p><button class="primary-button">选择视频</button><small>支持 MP4、WebM 等常见格式，分片大小为 5MB</small>
          <input ref="fileInput" type="file" accept="video/*" hidden @change="chooseFile(($event.target as HTMLInputElement).files?.[0])" />
        </div>
        <div v-else class="file-progress">
          <div class="file-row"><div class="file-icon"><FileVideo :size="25" /></div><div><strong>{{ file.name }}</strong><p>{{ (file.size / 1024 / 1024).toFixed(1) }} MB · {{ resolution }} · {{ Math.floor(duration / 60) }}:{{ String(duration % 60).padStart(2,'0') }}</p></div><button v-if="phase === 'idle'" @click="clearFile"><X :size="18" /></button><span v-else :class="{ success: phase === 'ready' || phase === 'done' }"><Check v-if="phase === 'ready' || phase === 'done'" :size="16" /><LoaderCircle v-else :size="16" class="spin" />{{ statusText }}</span></div>
          <div class="progress-track"><i :style="{ width: `${progress}%` }"></i></div><div class="progress-meta"><span>{{ statusText }}</span><b>{{ progress }}%</b></div>
          <button v-if="phase === 'idle'" class="primary-button" @click="startUpload"><CloudUpload :size="18" />开始上传</button>
          <p v-if="phase === 'hashing'" class="notice">正在计算文件 MD5，用于秒传检测和分片完整性校验。</p>
          <p v-else-if="phase === 'transcoding'" class="notice">源文件已进入媒体流水线，正在生成 HLS 多码率清晰度和自动封面。你可以继续填写作品信息。</p>
        </div>

        <form class="publish-form" @submit.prevent="publish">
          <div class="field"><label>作品标题 <span>*</span></label><input v-model.trim="form.title" maxlength="200" required placeholder="用一个好标题吸引观众" /><small>{{ form.title.length }}/200</small></div>
          <div class="field"><label>作品简介</label><textarea v-model="form.description" maxlength="2000" placeholder="介绍一下你的作品内容和创作故事"></textarea></div>
          <div class="form-grid"><div class="field"><label>内容分区 <span>*</span></label><select v-model="form.categoryId"><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select></div><div class="field"><label>视频标签 <span>*</span></label><input v-model.trim="form.tags" required placeholder="多个标签用逗号分隔" /></div></div>
          <div class="field"><label>封面地址</label><input v-model.trim="form.coverUrl" maxlength="500" type="url" placeholder="转码完成后自动截帧，也可手动替换" /><img v-if="form.coverUrl" class="cover-preview" :src="form.coverUrl" alt="自动截帧封面" /></div>
          <div v-if="error" class="error-banner">{{ error }}</div>
          <div v-if="phase === 'done'" class="notice">作品已提交审核；管理员通过后会自动出现在首页和搜索结果中。</div><button class="primary-button publish-button" :disabled="phase !== 'ready'"><LoaderCircle v-if="phase === 'publishing'" :size="17" class="spin" /><Check v-else-if="phase === 'done'" :size="17" />{{ phase === 'publishing' ? '正在提交…' : phase === 'done' ? '已提交审核' : phase === 'transcoding' ? '等待转码完成' : '提交作品' }}</button>
        </form>
      </section>

      <aside class="upload-tips"><h3>发布小贴士</h3><ol><li><b>清晰的标题</b><p>准确表达主题，避免堆砌无关词汇。</p></li><li><b>选择合适分区</b><p>能帮助感兴趣的观众更快发现作品。</p></li><li><b>添加有效标签</b><p>推荐填写 3—5 个和内容相关的标签。</p></li></ol><div><strong>生产媒体流程</strong><code>presign → object storage → HLS → cover → audit</code><p>大文件使用短期预签名直传，小文件保留断点续传；发布后进入风险识别和人工审核。</p></div></aside>
    </div>
  </div>
</template>

<style scoped>
.cover-preview { width: 220px; aspect-ratio: 16/9; margin-top: 10px; object-fit: cover; border-radius: 9px; border: 1px solid var(--line); }
.upload-page { padding-top: 38px; }.upload-head { display: flex; justify-content: space-between; align-items: flex-end; padding-bottom: 26px; border-bottom: 1px solid var(--line); }.upload-head > div > span { display: flex; align-items: center; gap: 6px; color: var(--pink); font-size: 11px; font-weight: 700; letter-spacing: .12em; }.upload-head h1 { margin: 8px 0 4px; font-size: 29px; }.upload-head p { margin: 0; color: var(--muted); font-size: 13px; }.step-track { display: flex; align-items: center; gap: 9px; color: #a1a6b0; font-size: 11px; }.step-track span { display: flex; align-items: center; gap: 5px; }.step-track b { width: 23px; height: 23px; display: grid; place-items: center; border-radius: 50%; background: #e8eaf0; }.step-track span.active { color: var(--pink); }.step-track span.active b { color: #fff; background: var(--pink); }.step-track i { width: 35px; height: 1px; background: #dde0e6; }.upload-layout { display: grid; grid-template-columns: minmax(0,1fr) 280px; gap: 26px; margin-top: 28px; }.upload-card { padding: 28px; border: 1px solid var(--line); border-radius: 17px; background: #fff; }.drop-zone { min-height: 300px; display: flex; flex-direction: column; align-items: center; justify-content: center; border: 1px dashed #ccd0d9; border-radius: 14px; background: #fafbfc; transition: .2s; cursor: pointer; }.drop-zone.dragging,.drop-zone:hover { border-color: var(--pink); background: #fff7f9; }.upload-icon { width: 68px; height: 68px; display: grid; place-items: center; border-radius: 20px; color: var(--pink); background: var(--pink-soft); }.drop-zone h2 { margin: 17px 0 5px; font-size: 18px; }.drop-zone p { margin: 0 0 17px; color: var(--muted); font-size: 12px; }.drop-zone small { margin-top: 13px; color: #a1a6af; }.file-progress { padding: 22px; border-radius: 13px; background: #f7f8fa; }.file-row { display: flex; align-items: center; gap: 12px; }.file-icon { width: 48px; height: 48px; display: grid; place-items: center; border-radius: 12px; color: var(--pink); background: var(--pink-soft); }.file-row > div:nth-child(2) { flex: 1; min-width: 0; }.file-row strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-row p { margin: 5px 0 0; color: var(--muted); font-size: 11px; }.file-row button { border: 0; background: none; color: #858a94; }.file-row > span { display: flex; align-items: center; gap: 5px; color: var(--pink); font-size: 11px; }.file-row > span.success { color: #23a86b; }.progress-track { height: 6px; margin-top: 20px; overflow: hidden; border-radius: 6px; background: #e2e4e8; }.progress-track i { display: block; height: 100%; border-radius: 6px; background: linear-gradient(90deg,#ff90a6,var(--pink)); transition: width .25s; }.progress-meta { display: flex; justify-content: space-between; margin: 7px 0 14px; color: var(--muted); font-size: 11px; }.progress-meta b { color: var(--pink); }.publish-form { display: flex; flex-direction: column; gap: 19px; margin-top: 28px; padding-top: 26px; border-top: 1px solid var(--line); }.field label span { color: var(--pink); }.field small { align-self: flex-end; margin-top: -25px; margin-right: 10px; pointer-events: none; }.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 17px; }.publish-button { align-self: flex-end; min-width: 160px; }.publish-button:disabled { opacity: .45; cursor: not-allowed; }.upload-tips { align-self: start; padding: 22px; border-radius: 15px; background: #fff7f9; }.upload-tips h3 { margin: 0 0 18px; font-size: 15px; }.upload-tips ol { padding-left: 20px; }.upload-tips li { margin-bottom: 16px; color: var(--pink); }.upload-tips li b { color: #454b57; font-size: 13px; }.upload-tips li p,.upload-tips > div p { margin: 4px 0; color: #858b96; font-size: 11px; line-height: 1.6; }.upload-tips > div { padding: 14px; border-radius: 10px; background: #fff; }.upload-tips > div strong { font-size: 12px; }.upload-tips code { display: block; margin-top: 8px; color: var(--pink); font-size: 10px; }.spin { animation: spin 1s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) { .upload-layout { grid-template-columns: 1fr; }.upload-tips { display: none; }.step-track { display: none; } }.notice { margin: 12px 0 0; }.error-banner { margin: 0; }
@media (max-width: 600px) { .upload-card { padding: 14px; }.drop-zone { min-height: 250px; }.form-grid { grid-template-columns: 1fr; }.upload-head { align-items: start; }.publish-button { width: 100%; }.file-row > span { display: none; } }
</style>
