<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

const router = useRouter()
const hotline = computed(() => String(import.meta.env.VITE_CITIZEN_HOTLINE || '').trim())
const smsNumber = computed(() => String(import.meta.env.VITE_CITIZEN_SMS_NUMBER || '').trim())

function callHotline(): void {
  if (!hotline.value) {
    ElMessage.info('服务热线尚未配置，请先使用网页反馈。')
    return
  }
  window.location.href = `tel:${hotline.value}`
}

function sendSms(): void {
  if (!smsNumber.value) {
    ElMessage.info('短信接收号码尚未配置，请先使用网页反馈。')
    return
  }
  window.location.href = `sms:${smsNumber.value}`
}
</script>

<template>
  <section class="hero">
    <el-tag effect="plain" type="success">移动优先 · 匿名可追踪</el-tag>
    <h1>发现房屋或公共区域问题？</h1>
    <p>提交后会获得工单编号和查询凭证，可随时查看受理与处理进度。</p>
  </section>

  <section class="action-grid" aria-label="反馈入口">
    <button class="action-card primary" type="button" @click="router.push('/citizen/report')">
      <strong>一键问题反馈</strong>
      <span>填写位置、问题描述和联系方式</span>
    </button>
    <button class="action-card" type="button" @click="callHotline">
      <strong>电话反馈</strong>
      <span>{{ hotline || '服务热线尚未配置' }}</span>
    </button>
    <button class="action-card" type="button" @click="sendSms">
      <strong>短信反馈</strong>
      <span>{{ smsNumber || '短信号码尚未配置' }}</span>
    </button>
    <button class="action-card" type="button" @click="router.push('/citizen/track')">
      <strong>查询处理进度</strong>
      <span>使用工单编号和查询凭证</span>
    </button>
  </section>

  <el-alert
    class="notice"
    title="重要说明"
    type="warning"
    :closable="false"
    show-icon
    description="反馈内容将作为巡检和治理线索，不代表正式房屋安全鉴定结论。紧急危险情况请优先联系当地应急、消防或政务服务热线。"
  />
</template>

<style scoped lang="scss">
.hero { padding: 22px 4px 18px; }
.hero h1 { margin: 14px 0 10px; color: #173f37; font-size: clamp(28px, 8vw, 42px); line-height: 1.15; }
.hero p { margin: 0; color: #667085; line-height: 1.8; }
.action-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin: 18px 0 24px; }
.action-card { min-height: 128px; padding: 20px; border: 1px solid #d9e2df; border-radius: 18px; background: #fff; color: #173f37; text-align: left; cursor: pointer; box-shadow: 0 10px 30px rgb(16 52 44 / 6%); }
.action-card:hover { transform: translateY(-2px); border-color: #8bb7aa; }
.action-card strong, .action-card span { display: block; }
.action-card strong { margin-bottom: 10px; font-size: 18px; }
.action-card span { color: #667085; line-height: 1.5; }
.action-card.primary { background: #176b59; color: #fff; }
.action-card.primary span { color: rgb(255 255 255 / 78%); }
.notice { margin-top: 8px; }
@media (max-width: 560px) { .action-grid { grid-template-columns: 1fr; } .action-card { min-height: 108px; } }
</style>
