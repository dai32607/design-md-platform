<template>
  <div class="design-file-view" v-loading="loading">
    <div class="breadcrumb-bar">
      <el-breadcrumb>
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: `/brand/${file?.brandId}` }">
          {{ file?.brandId ? '品牌详情' : '' }}
        </el-breadcrumb-item>
        <el-breadcrumb-item>{{ file?.title || 'DESIGN.md' }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <el-card v-if="file">
      <template #header>
        <div class="file-header">
          <h2>{{ file.title }}</h2>
          <a v-if="file.sourceUrl" :href="file.sourceUrl" target="_blank" class="source-link">
            查看官方来源
          </a>
        </div>
      </template>

      <div class="markdown-body" v-html="renderedContent"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getDesignFileById } from '../api'
import { marked } from 'marked'

const route = useRoute()
const file = ref(null)
const loading = ref(true)

const renderedContent = computed(() => {
  if (!file.value?.content) return ''
  return marked(file.value.content)
})

onMounted(async () => {
  try {
    const res = await getDesignFileById(route.params.id)
    file.value = res
  } catch (e) {
    console.error('加载文件失败', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.breadcrumb-bar {
  margin-bottom: 16px;
}

.file-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-header h2 {
  font-size: 22px;
  font-weight: 700;
}

.source-link {
  color: #409eff;
  font-size: 14px;
  text-decoration: none;
}

.source-link:hover {
  text-decoration: underline;
}

.markdown-body {
  padding: 8px 0;
  line-height: 1.8;
  color: #303133;
  font-size: 15px;
}

.markdown-body :deep(h1) {
  font-size: 28px;
  font-weight: 700;
  margin: 24px 0 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #ebeef5;
}

.markdown-body :deep(h2) {
  font-size: 22px;
  font-weight: 600;
  margin: 20px 0 12px;
}

.markdown-body :deep(h3) {
  font-size: 18px;
  font-weight: 600;
  margin: 16px 0 8px;
}

.markdown-body :deep(p) {
  margin: 8px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 8px 0;
}

.markdown-body :deep(li) {
  margin: 4px 0;
}

.markdown-body :deep(code) {
  background: #f0f2f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #e74c3c;
}

.markdown-body :deep(pre) {
  background: #f8f9fa;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 12px 0;
}

.markdown-body :deep(pre code) {
  background: none;
  color: #303133;
  padding: 0;
}

.markdown-body :deep(strong) {
  font-weight: 600;
  color: #303133;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #667eea;
  padding: 8px 16px;
  margin: 12px 0;
  background: #f5f7fa;
  border-radius: 0 4px 4px 0;
}
</style>
