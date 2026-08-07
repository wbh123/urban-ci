<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { trackPublicFeedback } from '@/shared/api'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  reportCode: localStorage.getItem('urban-safe-last-feedback-code') || '',
  trackingSecret: '',
})

if (form.reportCode) {
  form.trackingSecret = localStorage.getItem(`urban-safe-feedback-secret:${form.reportCode}`) || ''
}

const rules: FormRules = {
  reportCode: [{ required: true, message: '请输入工单编号', trigger: 'blur' }],
  trackingSecret: [{ required: true, message: '请输入查询凭证', trigger: 'blur' }],
}

async function query(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await trackPublicFeedback(form.reportCode.trim(), form.trackingSecret.trim())
    localStorage.setItem(`urban-safe-feedback-secret:${form.reportCode.trim()}`, form.trackingSecret.trim())
    localStorage.setItem('urban-safe-last-feedback-code', form.reportCode.trim())
    await router.push(`/citizen/reports/${encodeURIComponent(form.reportCode.trim())}`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '未查询到反馈工单')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="track-page">
    <el-button text @click="router.push('/citizen')">返回首页</el-button>
    <h1>查询处理进度</h1>
    <p>为保护反馈信息，需要同时提供工单编号和提交时获得的查询凭证。</p>
    <el-card shadow="never" class="track-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="工单编号" prop="reportCode">
          <el-input v-model="form.reportCode" placeholder="例如：FB-20260724-XXXXXXXX" />
        </el-form-item>
        <el-form-item label="查询凭证" prop="trackingSecret">
          <el-input v-model="form.trackingSecret" show-password placeholder="提交成功时返回的一次性凭证" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" class="query-button" @click="query">
          查询进度
        </el-button>
      </el-form>
    </el-card>
    <el-alert
      title="查询凭证遗失"
      type="info"
      :closable="false"
      description="当前版本不会通过公开页面找回查询凭证。请通过已配置的电话或短信渠道联系工作人员核实。"
    />
  </section>
</template>

<style scoped lang="scss">
.track-page { max-width: 620px; margin: 0 auto; }
h1 { margin: 8px 0; color: #173f37; }
p { margin: 0 0 20px; color: #667085; line-height: 1.7; }
.track-card { margin-bottom: 18px; border-radius: 18px; }
.query-button { width: 100%; min-height: 48px; }
</style>
