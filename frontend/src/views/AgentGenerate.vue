<template>
  <div class="agent-page">
    <div class="agent-header">
      <el-button link :icon="ArrowLeft" @click="router.push('/')">返回首页</el-button>
      <h2>AI 生成设计规范</h2>
      <p>用一句话描述你的品牌，AI 自动生成完整 DESIGN.md 设计规范并创建品牌、解析 Token 入库</p>
    </div>

    <el-card class="agent-card" shadow="never">
      <el-form label-position="top">
        <el-form-item label="品牌需求描述" required>
          <el-input
            v-model="form.prompt"
            type="textarea"
            :rows="5"
            maxlength="500"
            show-word-limit
            placeholder="现代极简风格的咖啡品牌"
          />
        </el-form-item>
        <el-form-item label="品牌名称（可选，默认从需求描述截取）">
          <el-input v-model="form.brandName" placeholder="如：山屿咖啡" />
        </el-form-item>
        <el-form-item label="品牌色板（可选，生成的规范 Colors 板块将只使用这些颜色）">
          <div class="palette-input">
            <el-color-picker v-model="pickerColor" :predefine="PRESET_COLORS" @change="addColor" />
            <el-button :disabled="!pickerColor" @click="addColor">添加颜色</el-button>
            <el-button link type="primary" @click="fillExample">填入示例</el-button>
            <el-button v-if="colors.length" link type="danger" @click="clearColors">清空</el-button>
          </div>
          <div v-if="colors.length" class="palette-tags">
            <el-tag v-for="c in colors" :key="c" closable @close="colors.splice(colors.indexOf(c), 1)">
              <span class="color-dot" :style="{ background: c }"></span>{{ c }}
            </el-tag>
          </div>
          <div v-else class="palette-hint">未指定时由 AI 根据品牌描述自动选色</div>
        </el-form-item>
      </el-form>
      <div class="form-actions">
        <el-button
          type="primary"
          :loading="generating"
          :disabled="generating"
          :icon="MagicStick"
          @click="handleGenerate"
        >
          {{ generating ? 'AI 生成中...' : '开始生成' }}
        </el-button>
      </div>

      <div v-if="generating" class="generating-panel">
        <el-progress :percentage="progressPercent" :stroke-width="8" :show-text="false" />
        <div class="generating-title">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>{{ phaseText }}</span>
        </div>
        <div class="generating-sub">{{ waitText }}</div>
      </div>
    </el-card>

    <el-alert
      v-if="errorMsg && !generating"
      class="error-alert"
      type="error"
      show-icon
      title="生成失败"
      :description="errorMsg"
      @close="errorMsg = ''"
    />

    <el-card v-if="result" class="result-card" shadow="never">
      <template #header>
        <div class="result-header">
          <span class="result-title">生成结果</span>
          <el-tag v-if="result.mock" type="warning" size="small">MOCK 演示模式</el-tag>
          <el-tag v-else type="success" size="small">AI 真实生成</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="品牌名称">
          <b>{{ result.brandName }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="Token 数">
          {{ result.tokenCount }}
        </el-descriptions-item>
        <el-descriptions-item label="品牌 ID">
          {{ result.brandId }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="result.colorsUsed && result.colorsUsed.length" class="used-colors">
        <span class="used-colors-label">本次使用颜色：</span>
        <el-tag v-for="c in result.colorsUsed" :key="c" size="small" class="used-color-tag">
          <span class="color-dot" :style="{ background: c }"></span>{{ c }}
        </el-tag>
      </div>

      <el-collapse class="spec-collapse">
        <el-collapse-item title="查看生成的规范内容（DESIGN.md）" name="spec">
          <pre class="spec-pre">{{ result.specText }}</pre>
        </el-collapse-item>
      </el-collapse>

      <div class="result-actions">
        <el-button type="primary" @click="router.push(`/brand/${result.brandId}`)">
          查看品牌详情
        </el-button>
        <el-button @click="router.push(`/brand/${result.brandId}`)">查看 Token 列表</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, MagicStick, Loading } from '@element-plus/icons-vue'
import { generateSpec } from '../api'
import { PRESET_COLORS, normalizeColor } from '../utils/palette'
import { MIN_FEEDBACK_MS, progressPercentOf, phaseTextOf, waitTextOf } from '../utils/progress'

const router = useRouter()
const form = reactive({ prompt: '', brandName: '' })
const generating = ref(false)
const result = ref(null)

// 生成中反馈：已等待时长 / 阶段文案 / 进度条 / 失败提示
// 计时粒度 300ms：mock（百毫秒级）与真实调用（秒级）场景都能看到过程态
const elapsedMs = ref(0)
const elapsedSec = computed(() => Math.floor(elapsedMs.value / 1000))
const errorMsg = ref('')
let timer = null

function startTimer() {
  clearTimer()
  elapsedMs.value = 0
  timer = setInterval(() => {
    elapsedMs.value += 300
  }, 300)
}

function clearTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

const phaseText = computed(() => phaseTextOf(elapsedMs.value))
const progressPercent = computed(() => progressPercentOf(elapsedMs.value))
const waitText = computed(() => waitTextOf(elapsedMs.value))

onBeforeUnmount(clearTimer)

// 品牌色板
const pickerColor = ref('#FF6B35')
const colors = ref([])

function addColor() {
  const c = normalizeColor(pickerColor.value)
  if (!c) return
  if (colors.value.includes(c)) {
    ElMessage.info('该颜色已添加')
    return
  }
  colors.value.push(c)
}

function fillExample() {
  colors.value = ['#FF6B35', '#1A1A2E', '#E94560']
}

function clearColors() {
  colors.value = []
}

async function handleGenerate() {
  const prompt = (form.prompt || '').trim()
  if (!prompt) {
    ElMessage.warning('请先输入品牌需求描述')
    return
  }
  const startedAt = Date.now()
  generating.value = true
  result.value = null
  errorMsg.value = ''
  startTimer()
  try {
    const res = await generateSpec({
      prompt,
      brandName: (form.brandName || '').trim(),
      colors: colors.value.length ? [...colors.value] : undefined
    })
    result.value = res
    if (res.degraded) {
      ElMessage.warning('真实模型调用失败，已自动降级为模板生成，结果仅供参考')
    } else {
      ElMessage.success(`品牌「${res.brandName}」已生成，解析出 ${res.tokenCount} 个设计 Token`)
    }
  } catch (e) {
    errorMsg.value =
      (e && (e.message || e.msg)) || '生成失败，请稍后重试；若持续失败请检查后端服务与网络后重试'
    console.error('AI 生成失败', e)
  } finally {
    // 最小展示时长：mock 分支返回过快（百毫秒级）时，补足到 MIN_FEEDBACK_MS 再复位，保证过程态可见
    const elapsed = Date.now() - startedAt
    if (elapsed < MIN_FEEDBACK_MS) {
      await new Promise(resolve => setTimeout(resolve, MIN_FEEDBACK_MS - elapsed))
    }
    clearTimer()
    generating.value = false
  }
}
</script>

<style scoped>
.agent-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 32px 20px 48px;
}

.agent-header {
  margin-bottom: 24px;
}

.agent-header h2 {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  margin: 8px 0;
}

.agent-header p {
  color: #909399;
  font-size: 14px;
  margin: 0;
}

.agent-card {
  margin-bottom: 24px;
}

.form-actions {
  margin-top: 8px;
}

.generating-panel {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fafafa;
}

.generating-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.generating-sub {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.error-alert {
  margin-bottom: 24px;
}

.palette-input {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.palette-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.palette-hint {
  color: #909399;
  font-size: 13px;
}

.color-dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 3px;
  margin-right: 6px;
  vertical-align: -2px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.used-colors {
  margin-top: 16px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.used-colors-label {
  font-size: 13px;
  color: #606266;
}

.used-color-tag {
  font-family: 'Consolas', monospace;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
}

.spec-collapse {
  margin-top: 16px;
}

.spec-pre {
  background: #f6f7f8;
  border-radius: 8px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 480px;
  overflow: auto;
  color: #303133;
}

.result-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}
</style>
