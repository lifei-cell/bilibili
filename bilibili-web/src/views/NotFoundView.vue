<script setup lang="ts">
import { Compass } from 'lucide-vue-next'
</script>
<template><div class="not-found"><div><Compass :size="38" /></div><span>404</span><h1>这片内容还在探索中</h1><p>你访问的页面不存在或已经离开。</p><RouterLink class="primary-button" to="/">回到首页</RouterLink></div></template>
<style scoped>.not-found{min-height:70vh;display:flex;flex-direction:column;align-items:center;justify-content:center}.not-found>div{width:78px;height:78px;display:grid;place-items:center;border-radius:25px;color:var(--pink);background:var(--pink-soft)}.not-found>span{margin-top:20px;color:#dddfe5;font-size:60px;font-weight:800}.not-found h1{margin:0;font-size:23px}.not-found p{margin:9px 0 20px;color:var(--muted)}</style>
