<template>
  <div class="upload-page">
    <el-breadcrumb>
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: `/brand/${brandId}` }">品牌详情</el-breadcrumb-item>
      <el-breadcrumb-item>上传 DESIGN.md</el-breadcrumb-item>
    </el-breadcrumb>

    <el-card class="upload-card">
      <template #header>
        <span class="card-title">上传 DESIGN.md 文件</span>
      </template>

      <div
        class="drop-zone"
        :class="{ 'is-dragover': dragOver }"
        @dragover.prevent="dragOver = true"
        @dragleave.prevent="dragOver = false"
        @drop.prevent="handleDrop"
        @click="triggerFileInput"
      >
        <el-icon :size="48" style="color: #909399"><UploadFilled /></el-icon>
        <p class="drop-hint">拖拽 .md 文件到此处，或点击选择文件</p>
        <p class="drop-sub">文件解析后将自动提取配色、字体、间距 Token</p>
        <input
          ref="fileInput"
          type="file"
          accept=".md"
          style="display:none"
          @change="handleFileChange"
        />
      </div>

      <div v-if="selectedFile" class="file-info">
        <el-tag type="info">{{ selectedFile.name }}</el-tag>
        <el-button type="primary" :loading="uploading" @click="doUpload">
          {{ uploading ? '解析中...' : '开始解析' }}
        </el-button>
      </div>

      <div v-if="uploadResult" class="result-panel">
        <el-alert :title="`解析完成，共提取 ${uploadResult.tokenCount} 条 Token`" type="success" :closable="false" />
        <div class="result-tokens">
          <div
            v-for="token in uploadResult.parsedTokens"
            :key="token.id"
            class="result-token-item"
          >
            <div
              v-if="token.category === 'COLORS'"
              class="mini-swatch"
              :style="{ backgroundColor: token.tokenValue }"
            ></div>
            <div class="result-token-info">
              <span class="result-token-name">{{ token.tokenName }}</span>
              <code class="result-token-value">{{ token.tokenValue }}</code>
              <span class="result-token-cat">{{ token.category }}</span>
            </div>
          </div>
        </div>
        <el-button type="primary" @click="goBrandDetail">查看品牌详情</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { uploadDesignMd } from '../api'
import { UploadFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const brandId = computed(() => route.params.id)
const dragOver = ref(false)
const selectedFile = ref(null)
const uploading = ref(false)
const uploadResult = ref(null)
const fileInput = ref(null)

function triggerFileInput() {
  fileInput.value.click()
}

function handleFileChange(e) {
  const file = e.target.files[0]
  if (file) {
    selectedFile.value = file
    uploadResult.value = null
  }
}

function handleDrop(e) {
  dragOver.value = false
  const file = e.dataTransfer.files[0]
  if (file && file.name.endsWith('.md')) {
    selectedFile.value = file
    uploadResult.value = null
  } else {
    ElMessage.warning('仅支持 .md 文件')
  }
}

async function doUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('brandId', brandId.value)
    const res = await uploadDesignMd(formData)
    uploadResult.value = res
    if (res.updated) {
      ElMessage.success('该文件已上传过，已更新内容')
    } else {
      ElMessage.success('解析成功')
    }
  } catch (e) {
    // 错误信息已由 api 拦截器统一 toast
    console.error('上传失败', e)
  } finally {
    uploading.value = false
  }
}

function goBrandDetail() {
  router.push(`/brand/${brandId.value}`)
}
</script>

<style scoped>
.upload-page {
  max-width: 700px;
  margin: 0 auto;
}

.upload-card {
  margin-top: 20px;
}

.card-title {
  font-weight: 600;
  font-size: 18px;
}

.drop-zone {
  border: 2px dashed #dcdfe6;
  border-radius: 12px;
  padding: 48px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.drop-zone:hover,
.drop-zone.is-dragover {
  border-color: #409eff;
  background: #ecf5ff;
}

.drop-hint {
  color: #606266;
  font-size: 16px;
  margin: 12px 0 8px;
}

.drop-sub {
  color: #909399;
  font-size: 13px;
  margin: 0;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
}

.result-panel {
  margin-top: 24px;
}

.result-tokens {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 16px 0;
  max-height: 300px;
  overflow-y: auto;
}

.result-token-item {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f5f7fa;
  padding: 6px 12px;
  border-radius: 6px;
}

.mini-swatch {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.result-token-name {
  font-weight: 600;
  font-size: 13px;
}

.result-token-value {
  background: #e6efff;
  color: #1677ff;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 12px;
}

.result-token-cat {
  font-size: 11px;
  color: #909399;
}
</style>
