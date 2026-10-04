<template>
  <div class="home">
    <div class="hero">
      <h2>探索全球设计规范</h2>
      <p>收录 Apple、Google、Ant Design 等知名品牌的设计系统规范，结构化展示设计 Token，可供 AI 代码助手直接引用</p>
      <div v-if="isLoggedIn" class="hero-actions">
        <el-button type="primary" :icon="MagicStick" @click="goAgent">AI 生成规范</el-button>
        <el-button type="primary" plain :icon="Plus" @click="showCreateDialog">新建品牌</el-button>
      </div>
    </div>

    <!-- 搜索 + 来源筛选栏 -->
    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        class="filter-search"
        placeholder="搜索品牌名称或描述"
        clearable
        :prefix-icon="Search"
        @input="onSearchInput"
        @clear="onSearchInput"
      />
      <el-select v-model="sourceFilter" class="filter-source" @change="onFilterChange">
        <el-option label="全部来源" value="" />
        <el-option label="官方预置" value="system" />
        <el-option label="自定义" value="custom" />
        <el-option v-if="isLoggedIn" label="我的" value="mine" />
      </el-select>
      <el-button v-if="hasFilter" :icon="RefreshLeft" @click="resetFilters">重置</el-button>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="3" animated />
    </div>

    <template v-else>
    <div v-if="brands.length > 0" class="brand-grid">
      <el-card
        v-for="brand in brands"
        :key="brand.id"
        class="brand-card"
        shadow="hover"
        @click="goDetail(brand.id)"
      >
        <div class="card-header">
          <img
            v-if="brand.logoUrl && !brokenLogos.has(brand.id)"
            :src="brand.logoUrl"
            class="brand-logo"
            :alt="brand.name"
            @error="onLogoError(brand.id)"
          />
          <div v-else class="avatar-fallback" :style="avatarStyle(brand.name)">{{ avatarLetter(brand.name) }}</div>
          <h3>{{ brand.name }}</h3>
        </div>
        <p class="brand-desc">{{ brand.description }}</p>
        <div class="card-footer">
          <el-button type="primary" size="small" @click.stop="goDetail(brand.id)">
            查看规范
          </el-button>
        </div>
      </el-card>
    </div>
    <el-empty v-else description="未找到匹配的品牌，试试调整关键词或筛选条件" />
    </template>

    <div v-if="!loading && total > 0" class="pagination-wrapper">
      <span class="pagination-total">共 {{ total }} 条 · 共 {{ pageCount }} 页</span>
      <el-pagination
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="currentPage"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog v-model="createDialogVisible" title="新建品牌" width="460px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="品牌名称" required>
          <el-input v-model="createForm.name" placeholder="请输入品牌名称" />
        </el-form-item>
        <el-form-item label="Logo URL">
          <el-input v-model="createForm.logoUrl" placeholder="可选，品牌 Logo 图片地址" />
        </el-form-item>
        <el-form-item label="官网地址">
          <el-input v-model="createForm.website" placeholder="可选，品牌官网链接" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="createForm.description" type="textarea" :rows="3" placeholder="可选，品牌描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateBrand">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, MagicStick, Search, RefreshLeft } from '@element-plus/icons-vue'
import { getBrands, createUserBrand } from '../api'
import { avatarStyle, avatarLetter } from '../utils/brandAvatar'

const router = useRouter()
const brands = ref([])
// 头像加载失败的品牌 id 集合：失败后回退首字母占位，任何情况下都不出现破图
const brokenLogos = reactive(new Set())

function onLogoError(id) {
  brokenLogos.add(id)
}
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(9)
// 总页数 = ceil(总条数 / 每页条数)
const pageCount = computed(() => Math.ceil(total.value / pageSize.value))
const loading = ref(true)

// 搜索 + 来源筛选状态
const searchKeyword = ref('')
const sourceFilter = ref('')
const hasFilter = computed(() => searchKeyword.value.trim() !== '' || sourceFilter.value !== '')
let searchTimer = null

// 输入防抖 300ms 后自动查询，并重置页码
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadBrands()
  }, 300)
}

// 来源筛选变化立即查询（含"我的"）
function onFilterChange() {
  currentPage.value = 1
  loadBrands()
}

// 清空关键词与来源，恢复全量列表
function resetFilters() {
  clearTimeout(searchTimer)
  searchKeyword.value = ''
  sourceFilter.value = ''
  currentPage.value = 1
  loadBrands()
}

// 是否已登录：有 token 才显示"新建品牌"入口
const isLoggedIn = computed(() => !!localStorage.getItem('token'))

// 新建品牌弹窗
const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = ref({
  name: '',
  logoUrl: '',
  website: '',
  description: ''
})

function showCreateDialog() {
  createForm.value = { name: '', logoUrl: '', website: '', description: '' }
  createDialogVisible.value = true
}

async function handleCreateBrand() {
  const name = (createForm.value.name || '').trim()
  if (!name) {
    ElMessage.warning('品牌名称不能为空')
    return
  }
  creating.value = true
  try {
    const res = await createUserBrand({
      name,
      logoUrl: createForm.value.logoUrl || '',
      website: createForm.value.website || '',
      description: createForm.value.description || ''
    })
    createDialogVisible.value = false
    ElMessage.success(`品牌「${res.name}」创建成功，可进入详情页上传 DESIGN.md 设计规范`)
    // 回到第一页并刷新列表，让新品牌可见
    currentPage.value = 1
    loadBrands()
  } catch (e) {
    console.error('创建品牌失败', e)
  } finally {
    creating.value = false
  }
}

async function loadBrands() {
  loading.value = true
  try {
    const params = { page: currentPage.value, pageSize: pageSize.value }
    const kw = searchKeyword.value.trim()
    if (kw) params.keyword = kw
    if (sourceFilter.value === 'mine') {
      params.mine = true
    } else if (sourceFilter.value) {
      params.source = sourceFilter.value
    }
    const res = await getBrands(params)
    brands.value = res.records || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载品牌列表失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(loadBrands)

function handlePageChange(page) {
  currentPage.value = page
  loadBrands()
}

function goDetail(id) {
  router.push(`/brand/${id}`)
}

function goAgent() {
  router.push('/agent')
}
</script>

<style scoped>
.hero {
  text-align: center;
  padding: 48px 0 40px;
}

.hero h2 {
  font-size: 32px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 12px;
}

.hero p {
  font-size: 16px;
  color: #909399;
  max-width: 600px;
  margin: 0 auto;
  line-height: 1.6;
}

.hero-actions {
  margin-top: 24px;
}

.filter-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 0 16px 28px;
}

.filter-search {
  width: 340px;
}

.filter-source {
  width: 150px;
}

.loading-wrapper {
  padding: 40px 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  padding-bottom: 40px;
}
.pagination-total {
  margin-right: 12px;
  color: #606266;
  font-size: 14px;
  line-height: 32px;
}

.brand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
  padding-bottom: 40px;
}

.brand-card {
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  will-change: transform;
}

.brand-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.12);
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.brand-logo {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  object-fit: contain;
}

.avatar-fallback {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
  user-select: none;
}

.card-header h3 {
  font-size: 20px;
  font-weight: 600;
}

.brand-desc {
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  text-align: right;
}

/* ===== 移动端适配（≤768px） ===== */
@media (max-width: 768px) {
  .hero {
    padding: 32px 16px 24px;
  }

  .hero h2 {
    font-size: 24px;
  }

  .hero p {
    font-size: 14px;
  }

  .brand-grid {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 0 12px 24px;
  }

  .card-header h3 {
    font-size: 18px;
  }
}
</style>
