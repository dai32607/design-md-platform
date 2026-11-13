<template>
  <div class="brand-detail" v-loading="loading">
    <!-- 面包屑 -->
    <div class="breadcrumb-bar">
      <el-breadcrumb>
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item>{{ brand?.name || '品牌详情' }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <!-- 品牌信息 -->
    <div class="brand-info" v-if="brand">
      <el-card>
        <div class="brand-header">
          <div
            class="brand-logo-wrap"
            :class="{ 'logo-editable': canChangeLogo }"
            :title="canChangeLogo ? '点击更换 Logo' : ''"
            @click="canChangeLogo && triggerLogoUpload()"
          >
            <img
              v-if="brand.logoUrl && !logoBroken"
              :src="brand.logoUrl"
              class="brand-logo"
              :alt="brand.name"
              @error="logoBroken = true"
            />
            <div v-else class="avatar-fallback brand-avatar" :style="avatarStyle(brand.name)">{{ avatarLetter(brand.name) }}</div>
            <div v-if="canChangeLogo" class="logo-upload-mask">
              <el-icon v-if="!uploadingLogo"><Upload /></el-icon>
              <span>{{ uploadingLogo ? '上传中...' : '更换 Logo' }}</span>
            </div>
          </div>
          <input
            ref="logoInput"
            type="file"
            accept="image/png,image/jpeg,image/svg+xml,image/webp"
            class="logo-file-input"
            @change="handleLogoChange"
          />
          <div class="brand-title-area">
            <h2>{{ brand.name }}</h2>
            <a :href="brand.website" target="_blank" class="website-link">{{ brand.website }}</a>
          </div>
          <div class="brand-actions">
            <el-button v-if="canEditBrand" type="primary" plain @click="openEditBrandDialog">
              <el-icon><EditPen /></el-icon> 编辑品牌信息
            </el-button>
            <el-button v-if="canEditBrand" type="danger" plain @click="handleDeleteBrand">
              <el-icon><Delete /></el-icon> 删除品牌
            </el-button>
            <el-button type="warning" @click="showAiPreview = true">
              <el-icon><MagicStick /></el-icon> Token 效果预览
            </el-button>
            <el-button v-if="canChangeLogo" type="primary" @click="goUpload">
              <el-icon><Upload /></el-icon> 上传 DESIGN.md
            </el-button>
          </div>
        </div>
        <p class="brand-desc">{{ brand.description }}</p>
      </el-card>
    </div>

    <!-- 主体区域：左侧分类导航 + 右侧 Token 列表 -->
    <div class="main-area" v-if="brand">
      <div class="sidebar">
        <el-card>
          <template #header>
            <span class="sidebar-title">Token 分类</span>
          </template>
          <el-menu
            :default-active="activeCategory"
            @select="handleCategorySelect"
          >
            <el-menu-item index="">
              <el-icon><List /></el-icon>
              <span>全部 Token</span>
            </el-menu-item>
            <el-menu-item
              v-for="cat in categories"
              :key="cat"
              :index="cat"
            >
              <el-icon>
                <PriceTag v-if="cat === 'COLORS'" />
                <EditPen v-else-if="cat === 'TYPOGRAPHY'" />
                <Operation v-else-if="cat === 'SPACING'" />
                <Grid v-else />
              </el-icon>
              <span>{{ categoryLabel(cat) }}</span>
            </el-menu-item>
          </el-menu>

          <!-- DESIGN.md 文件入口 -->
          <div class="design-files-section" v-if="designFiles.length > 0">
            <el-divider />
            <span class="sidebar-title" style="margin-bottom:12px;display:block;">DESIGN.md 文件</span>
            <div
              v-for="file in designFiles"
              :key="file.id"
              class="file-link"
              @click="goFile(file.id)"
            >
              <el-icon><Document /></el-icon>
              <span>{{ file.title }}</span>
            </div>
          </div>
        </el-card>
      </div>

      <div class="content">
        <el-card>
          <template #header>
            <div class="content-header">
              <span class="content-title">
                {{ activeCategory ? categoryLabel(activeCategory) : '全部设计 Token' }}
              </span>
              <div class="content-actions">
                <el-tag size="small">{{ tokens.length }} 条</el-tag>
                <el-button size="small" type="primary" plain @click="handleExportCss">
                  <el-icon><CopyDocument /></el-icon>
                  导出 CSS 变量
                </el-button>
              </div>
            </div>
          </template>

          <div class="search-bar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索 Token 名称或值"
              clearable
              :prefix-icon="Search"
            />
          </div>

          <div v-if="filteredTokens.length === 0" class="empty-state">
            <el-empty :description="tokens.length === 0 ? '该分类暂无 Token 数据' : '未找到匹配的 Token'" />
          </div>

          <div v-else class="token-list">
            <div
              v-for="token in filteredTokens"
              :key="token.id"
              class="token-item"
            >
              <!-- 颜色 Token 色块预览：点击复制色值 -->
              <div
                v-if="token.category === 'COLORS' && isColorValue(token.tokenValue)"
                class="color-swatch"
                :style="{ backgroundColor: token.tokenValue }"
                :title="`点击复制 ${token.tokenValue}`"
                @click="handleColorClick(token)"
              ></div>

              <div class="token-info">
                <div class="token-name">{{ token.tokenName }}</div>
                <div class="token-meta">
                  <!-- 字号预览 -->
                  <span
                    v-if="token.category === 'TYPOGRAPHY'"
                    class="type-preview"
                    :style="getTypePreviewStyle(token)"
                  >
                    {{ token.tokenValue }}
                  </span>
                  <code v-else class="token-value">{{ token.tokenValue }}</code>
                  <span v-if="token.description" class="token-desc">{{ token.description }}</span>
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="token-actions">
                <el-button size="small" text type="primary" @click="openEditDialog(token)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button size="small" text type="danger" @click="handleDeleteToken(token)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- Token 编辑弹窗 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑 Token"
      width="480px"
      destroy-on-close
    >
      <el-form :model="editForm" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editForm.tokenName" placeholder="Token 名称" />
        </el-form-item>
        <el-form-item label="值" required>
          <el-input v-model="editForm.tokenValue" placeholder="Token 值" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="描述说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingToken" @click="saveToken">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑品牌信息弹窗 -->
    <el-dialog
      v-model="editBrandDialogVisible"
      title="编辑品牌信息"
      width="520px"
      destroy-on-close
    >
      <el-form :model="editBrandForm" label-width="90px">
        <el-form-item label="品牌名称" required>
          <el-input v-model="editBrandForm.name" placeholder="品牌名称" />
        </el-form-item>
        <el-form-item label="Logo URL">
          <el-input v-model="editBrandForm.logoUrl" placeholder="Logo 图片地址（留空保持当前 Logo 不变；更换请点上方头像上传）" />
        </el-form-item>
        <el-form-item label="官网">
          <el-input v-model="editBrandForm.website" placeholder="官网地址" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="editBrandForm.description" type="textarea" :rows="3" placeholder="品牌简介" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editBrandDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingBrand" @click="saveBrandInfo">保存</el-button>
      </template>
    </el-dialog>

    <!-- Token 效果预览弹窗 -->
    <el-dialog
      v-model="showAiPreview"
      :title="'Token 效果预览 — ' + (brand?.name || '')"
      width="90%"
      top="3vh"
      destroy-on-close
    >
      <div class="ai-preview-wrapper" v-if="brand">
        <!-- 品牌完整色板：遍历该品牌全部颜色 Token -->
        <section class="ai-palette-section" v-if="previewColorList.length">
          <h4 class="ai-palette-title">
            品牌完整色板
            <span class="ai-palette-count">共 {{ previewColorList.length }} 个颜色</span>
          </h4>
          <div class="ai-palette-grid">
            <div
              v-for="(c, i) in previewColorList"
              :key="c.hex + '-' + i"
              class="ai-palette-item"
              :title="'点击复制 ' + c.hex"
              @click="handleCopyColor(c.hex)"
            >
              <span class="ai-palette-swatch" :style="{ backgroundColor: c.hex }"></span>
              <span class="ai-palette-meta">
                <span class="ai-palette-hex">{{ c.hex }}</span>
                <span class="ai-palette-name">{{ c.name }}</span>
              </span>
            </div>
          </div>
        </section>

        <!-- 模拟页面：导航栏 -->
        <nav class="ai-nav" :style="aiNavStyle">
          <div class="ai-nav-brand">{{ brand.name }}</div>
          <div class="ai-nav-links">
            <span v-for="i in 4" :key="i" class="ai-nav-link">导航 {{ i }}</span>
          </div>
          <div class="ai-nav-cta">
            <button class="ai-btn-primary" :style="aiPrimaryBtnStyle">开始使用</button>
          </div>
        </nav>

        <!-- 模拟页面：Hero 区域 -->
        <section class="ai-hero" :style="aiHeroStyle">
          <h2 class="ai-hero-title" :style="aiTitleStyle">欢迎使用 {{ brand.name }} 设计系统</h2>
          <p class="ai-hero-desc" :style="aiBodyStyle">这套设计系统提供了统一的配色、字体和间距规范，确保产品体验一致。</p>
          <button class="ai-btn-primary ai-btn-lg" :style="aiPrimaryBtnStyle">立即体验</button>
        </section>

        <!-- 模拟页面：卡片列表 -->
        <section class="ai-cards" :style="aiSectionStyle">
          <div v-for="i in 3" :key="i" class="ai-card" :style="aiCardStyle">
            <div class="ai-card-img" :style="aiCardImgStyle"></div>
            <div class="ai-card-body">
              <h4 :style="aiHeadingStyle">功能卡片 {{ i }}</h4>
              <p :style="aiBodyStyle">基于设计 Token 自动渲染的卡片组件，包含排版间距等细节。</p>
              <button class="ai-btn-secondary" :style="aiSecondaryBtnStyle">了解更多</button>
            </div>
          </div>
        </section>

        <!-- 模拟页面：表单区域 -->
        <section class="ai-form-section" :style="aiSectionStyle">
          <h3 :style="aiHeadingStyle">联系我们</h3>
          <div class="ai-form" :style="aiFormStyle">
            <input type="text" placeholder="您的姓名" class="ai-input" :style="aiInputStyle" />
            <input type="email" placeholder="电子邮箱" class="ai-input" :style="aiInputStyle" />
            <textarea placeholder="留言内容..." class="ai-textarea" :style="aiTextareaStyle"></textarea>
            <button class="ai-btn-primary" :style="aiPrimaryBtnStyle">提交</button>
          </div>
        </section>

        <!-- 模拟页面：页脚 -->
        <footer class="ai-footer" :style="aiFooterStyle">
          <p :style="aiBodyStyle">{{ brand.name }} Design System — Powered by DESIGN.md Platform</p>
        </footer>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getBrandById, getTokensByBrand, getTokenCategories, getDesignFiles, updateToken, deleteToken, uploadBrandLogo, updateUserBrand, deleteUserBrand } from '../api'
import { List, PriceTag, EditPen, Operation, Grid, Document, MagicStick, Upload, Edit, Delete, CopyDocument, Search } from '@element-plus/icons-vue'
import { avatarStyle, avatarLetter } from '../utils/brandAvatar'
import { categoryLabel, isColorValue, getTypePreviewStyle, buildCssVariables } from '../utils/tokenStyle'

const route = useRoute()
const router = useRouter()
const brand = ref(null)
const tokens = ref([])
const allTokens = ref([])
const categories = ref([])
const designFiles = ref([])
const activeCategory = ref('')
const loading = ref(true)
const showAiPreview = ref(false)
const editDialogVisible = ref(false)
const savingToken = ref(false)
const editForm = ref({ id: null, tokenName: '', tokenValue: '', description: '' })
const searchKeyword = ref('')
const logoInput = ref(null)
const uploadingLogo = ref(false)
// Logo 加载失败标记：失败即回退首字母占位，避免离线/异常时出现破图
const logoBroken = ref(false)

// 预置品牌（source=system）不允许更换 Logo
const canChangeLogo = computed(() => brand.value && brand.value.source !== 'system')

// ===== 当前用户与品牌编辑权限 =====
const currentUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    return null
  }
})
const isAdmin = computed(() => currentUser.value?.role === 'ADMIN')
const editBrandDialogVisible = ref(false)
const savingBrand = ref(false)
const editBrandForm = ref({ name: '', logoUrl: '', website: '', description: '' })

// 仅登录且为品牌创建者（或 ADMIN）可编辑；预置品牌一律不可编辑
const canEditBrand = computed(() => {
  const b = brand.value
  if (!b || b.source === 'system') return false
  if (isAdmin.value) return true
  const uid = currentUser.value?.id
  return !!uid && b.creatorId === uid
})

async function handleDeleteBrand() {
  const b = brand.value
  if (!b) return
  try {
    await ElMessageBox.confirm(
      `确定删除品牌「${b.name}」吗？该品牌下的全部 Token 与 DESIGN.md 规范文件将一并删除，且不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await deleteUserBrand(b.id)
    ElMessage.success('品牌已删除')
    router.push('/')
  } catch (e) {
    // 错误信息已由 api 拦截器统一 toast
    console.error('删除品牌失败', e)
  }
}

function openEditBrandDialog() {
  const b = brand.value
  if (!b) return
  editBrandForm.value = {
    name: b.name || '',
    // Logo 已统一存库并由本地接口提供，此处不再回填内部接口地址，留空表示保持当前 Logo 不变
    logoUrl: '',
    website: b.website || '',
    description: b.description || ''
  }
  editBrandDialogVisible.value = true
}

async function saveBrandInfo() {
  if (!editBrandForm.value.name || !editBrandForm.value.name.trim()) {
    ElMessage.warning('请填写品牌名称')
    return
  }
  savingBrand.value = true
  try {
    const payload = {
      name: editBrandForm.value.name.trim(),
      website: editBrandForm.value.website,
      description: editBrandForm.value.description
    }
    // 仅在填写了地址时才提交 logoUrl：留空表示保持当前 Logo（已存库的二进制头像不会被覆盖）
    if (editBrandForm.value.logoUrl && editBrandForm.value.logoUrl.trim()) {
      payload.logoUrl = editBrandForm.value.logoUrl.trim()
    }
    const res = await updateUserBrand(brand.value.id, payload)
    brand.value = res
    logoBroken.value = false
    ElMessage.success('品牌信息已更新')
    editBrandDialogVisible.value = false
  } catch (e) {
    // 错误信息已由 api 拦截器统一 toast
    console.error('更新品牌信息失败', e)
  } finally {
    savingBrand.value = false
  }
}

// 按名称/值即时过滤当前分类下的 Token 列表（保留分类切换）
const filteredTokens = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return tokens.value
  return tokens.value.filter(t =>
    (t.tokenName || '').toLowerCase().includes(keyword) ||
    (t.tokenValue || '').toLowerCase().includes(keyword)
  )
})

async function loadTokens(category) {
  try {
    const brandId = route.params.id
    // pageSize=0 表示全量拉取，保持现有展示形态
    const res = await getTokensByBrand(brandId, category || undefined, 1, 0)
    tokens.value = res.records || []
  } catch (e) {
    console.error('加载 Token 失败', e)
  }
}

async function refreshTokenLists() {
  await loadTokens(activeCategory.value)
  try {
    const allRes = await getTokensByBrand(route.params.id, undefined, 1, 0)
    allTokens.value = allRes.records || []
  } catch (e) {
    console.error('加载全部 Token 失败', e)
  }
}

async function handleColorClick(token) {
  const color = token.tokenValue.trim()
  try {
    await navigator.clipboard.writeText(color)
    ElMessage.success(`已复制 ${color}`)
  } catch (e) {
    console.error('复制色值失败', e)
    ElMessage.error('复制失败，请手动复制')
  }
}

// ===== 一键复制 CSS 变量 =====

async function handleExportCss() {
  if (!allTokens.value.length) {
    ElMessage.warning('暂无可导出的 Token')
    return
  }
  const cssText = buildCssVariables(allTokens.value)
  try {
    await navigator.clipboard.writeText(cssText)
    ElMessage.success('已复制 CSS 变量')
  } catch (e) {
    console.error('复制 CSS 变量失败', e)
    ElMessage.error('复制失败，请手动复制')
  }
}

function openEditDialog(token) {
  editForm.value = {
    id: token.id,
    tokenName: token.tokenName,
    tokenValue: token.tokenValue,
    description: token.description || ''
  }
  editDialogVisible.value = true
}

async function saveToken() {
  if (!editForm.value.tokenName || !editForm.value.tokenValue) {
    ElMessage.warning('请填写 Token 名称和值')
    return
  }
  savingToken.value = true
  try {
    await updateToken(editForm.value.id, {
      tokenName: editForm.value.tokenName,
      tokenValue: editForm.value.tokenValue,
      description: editForm.value.description
    })
    ElMessage.success('Token 已更新')
    editDialogVisible.value = false
    await refreshTokenLists()
  } catch (e) {
    // 错误信息已由 api 拦截器统一 toast
    console.error('更新 Token 失败', e)
  } finally {
    savingToken.value = false
  }
}

async function handleDeleteToken(token) {
  try {
    await ElMessageBox.confirm(
      `确定删除 Token「${token.tokenName}」吗？此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteToken(token.id)
    ElMessage.success('Token 已删除')
    await refreshTokenLists()
  } catch (e) {
    // 错误信息已由 api 拦截器统一 toast
    console.error('删除 Token 失败', e)
  }
}

function handleCategorySelect(cat) {
  activeCategory.value = cat
  loadTokens(cat)
}

function goFile(fileId) {
  router.push(`/file/${fileId}`)
}

function goUpload() {
  router.push(`/brand/${route.params.id}/upload`)
}

// ===== 品牌 Logo 上传 =====

const LOGO_TYPE_MAP = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/svg+xml': 'svg',
  'image/webp': 'webp'
}

function triggerLogoUpload() {
  if (uploadingLogo.value) return
  logoInput.value?.click()
}

async function handleLogoChange(e) {
  const file = e.target.files && e.target.files[0]
  e.target.value = '' // 允许重复选择同一文件
  if (!file) return
  if (!LOGO_TYPE_MAP[file.type]) {
    ElMessage.error('仅支持 png/jpg/jpeg/svg/webp 格式的图片')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 2MB')
    return
  }
  uploadingLogo.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await uploadBrandLogo(route.params.id, formData)
    brand.value = res
    logoBroken.value = false
    ElMessage.success('Logo 已更新')
  } catch (err) {
    // 错误信息已由 api 拦截器统一 toast
    console.error('上传 Logo 失败', err)
  } finally {
    uploadingLogo.value = false
  }
}

// ===== Token 效果预览：从 Token 提取样式 =====

function findColor(name) {
  const t = allTokens.value.find(t => t.tokenName.toLowerCase().includes(name.toLowerCase()) && t.category === 'COLORS')
  return t ? t.tokenValue : null
}

function findColorByIndex(i) {
  const colors = allTokens.value.filter(t => t.category === 'COLORS')
  return colors[i % colors.length]?.tokenValue || '#409eff'
}

function findFontSize() {
  const t = allTokens.value.find(t => t.tokenName.toLowerCase().includes('font-size'))
  return t ? t.tokenValue : '16px'
}

function findFontFamily() {
  const t = allTokens.value.find(t => t.tokenName.toLowerCase().includes('font-family'))
  return t ? t.tokenValue : '-apple-system, sans-serif'
}

function findSpacing(name) {
  const t = allTokens.value.find(t => t.tokenName.toLowerCase().includes(name.toLowerCase()) && t.category === 'SPACING')
  return t ? t.tokenValue : '16px'
}

// ===== 智能语义取色：把品牌全部 COLORS Token 分配到预览的各语义槽位 =====
// 规则：Token 名含语义（primary / background / text…）时优先按名称匹配；
// 否则按亮度、饱和度、色相计算——最亮→页面背景、最暗→正文主色、最饱和彩色→主色、
// 色相错开的次高饱和色→次色与强调色、浅色→卡片底色、中低饱和灰→次要文字、浅灰→边框分割线。

const COLOR_SLOT_KEYWORDS = {
  background: ['background', 'bg', 'surface', 'canvas', '底色', '背景'],
  border: ['border', 'divider', 'outline', 'stroke', '边框', '分割'],
  muted: ['muted', 'subtle', 'placeholder', 'disabled', 'weak', '弱化', '次要', '占位', '禁用'],
  accent: ['accent', 'highlight', '强调', '点缀'],
  secondary: ['secondary', 'support', '辅助', '次色', '副色'],
  text: ['text', 'foreground', 'body', '正文', '文字'],
  primary: ['primary', 'brand', 'main', '主色', '品牌']
}

function previewHexToRgb(value) {
  if (!value || typeof value !== 'string') return null
  let h = value.trim().replace('#', '')
  if (h.length === 3) h = h[0] + h[0] + h[1] + h[1] + h[2] + h[2]
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return null
  return {
    r: parseInt(h.slice(0, 2), 16),
    g: parseInt(h.slice(2, 4), 16),
    b: parseInt(h.slice(4, 6), 16)
  }
}

function previewRgbToHsl(rgb) {
  const r = rgb.r / 255
  const g = rgb.g / 255
  const b = rgb.b / 255
  const max = Math.max(r, g, b)
  const min = Math.min(r, g, b)
  const d = max - min
  const l = (max + min) / 2
  let h = 0
  let s = 0
  if (d !== 0) {
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
    if (max === r) h = (g - b) / d + (g < b ? 6 : 0)
    else if (max === g) h = (b - r) / d + 2
    else h = (r - g) / d + 4
    h *= 60
  }
  return { h, s, l }
}

function previewRelativeLuminance(rgb) {
  const channel = c => {
    const v = c / 255
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4)
  }
  return 0.2126 * channel(rgb.r) + 0.7152 * channel(rgb.g) + 0.0722 * channel(rgb.b)
}

function previewHueDistance(a, b) {
  const d = Math.abs(a - b) % 360
  return d > 180 ? 360 - d : d
}

// 品牌全部颜色 Token（保持库内顺序），预计算亮度 / 饱和度 / 色相
const previewColorList = computed(() => allTokens.value
  .filter(t => t.category === 'COLORS')
  .map(t => {
    const rgb = previewHexToRgb(t.tokenValue)
    if (!rgb) return null
    const hsl = previewRgbToHsl(rgb)
    return {
      name: t.tokenName || '',
      hex: t.tokenValue.trim().toUpperCase(),
      h: hsl.h,
      s: hsl.s,
      l: hsl.l,
      lum: previewRelativeLuminance(rgb)
    }
  })
  .filter(Boolean)
)

// 智能语义分配结果：每个语义槽位都从全部颜色 Token 中挑选
const previewPalette = computed(() => {
  const list = previewColorList.value
  const palette = {
    background: '#FFFFFF',
    text: '#303133',
    primary: '#409EFF',
    secondary: '#79BBFF',
    accent: '#409EFF',
    cardBg: '#FFFFFF',
    muted: '#909399',
    border: '#DCDFE6'
  }
  if (!list.length) return palette

  const used = new Set()
  const take = i => {
    used.add(i)
    return list[i].hex
  }
  const free = () => list.map((_, i) => i).filter(i => !used.has(i))
  const indexOfHex = hex => list.findIndex(c => c.hex === hex)
  const assigned = new Set()

  // 第一步：Token 名含语义关键词时，优先按名称匹配
  Object.keys(COLOR_SLOT_KEYWORDS).forEach(slot => {
    const hit = free().find(i => COLOR_SLOT_KEYWORDS[slot].some(k => list[i].name.toLowerCase().includes(k)))
    if (hit !== undefined) {
      palette[slot] = take(hit)
      assigned.add(slot)
    }
  })

  // 第二步：最亮 → 页面背景；最暗 → 正文主色
  if (!assigned.has('background')) {
    const byLum = free().sort((a, b) => list[b].lum - list[a].lum)
    if (byLum.length) {
      palette.background = take(byLum[0])
      assigned.add('background')
    }
  }
  if (!assigned.has('text')) {
    const byLum = free().sort((a, b) => list[a].lum - list[b].lum)
    if (byLum.length) {
      palette.text = take(byLum[0])
      assigned.add('text')
    }
  }

  // 第三步：高饱和彩色 → 主色 / 次色 / 强调色（要求彼出色相错开，避免同色系扎堆）
  const pickColorful = (minSat, excludes) => {
    for (const i of free()) {
      const c = list[i]
      if (c.s < minSat || c.l <= 0.12 || c.l >= 0.92) continue
      if (excludes.some(j => j >= 0 && previewHueDistance(c.h, list[j].h) < 30)) continue
      return i
    }
    return -1
  }
  const anchors = () => ['primary', 'secondary', 'accent']
    .filter(s => assigned.has(s))
    .map(s => indexOfHex(palette[s]))
    .filter(i => i >= 0)

  if (!assigned.has('primary')) {
    const i = pickColorful(0.35, [])
    if (i >= 0) {
      palette.primary = take(i)
      assigned.add('primary')
    }
  }
  if (!assigned.has('secondary')) {
    const i = pickColorful(0.3, anchors())
    if (i >= 0) {
      palette.secondary = take(i)
      assigned.add('secondary')
    }
  }
  if (!assigned.has('accent')) {
    const i = pickColorful(0.25, anchors())
    if (i >= 0) {
      palette.accent = take(i)
      assigned.add('accent')
    }
  }

  // 第四步：剩余最亮的浅色 → 卡片底色
  if (!assigned.has('cardBg')) {
    const rest = free().sort((a, b) => list[b].l - list[a].l)
    const light = rest.filter(i => list[i].l >= 0.85)
    const pickIdx = light.length ? light[0] : rest[0]
    if (pickIdx !== undefined) {
      palette.cardBg = take(pickIdx)
      assigned.add('cardBg')
    }
  }

  // 第五步：剩余中性色中亮度适中的 → 次要文字
  if (!assigned.has('muted')) {
    const neutral = free().filter(i => list[i].s < 0.25)
    if (neutral.length) {
      neutral.sort((a, b) => Math.abs(list[a].l - 0.45) - Math.abs(list[b].l - 0.45))
      palette.muted = take(neutral[0])
      assigned.add('muted')
    } else {
      const rest = free().sort((a, b) => list[a].l - list[b].l)
      if (rest.length) {
        palette.muted = take(rest[0])
        assigned.add('muted')
      }
    }
  }

  // 第六步：剩余浅色 / 低饱和色 → 边框、分割线
  if (!assigned.has('border')) {
    const lightNeutral = free().filter(i => list[i].s < 0.2 && list[i].l >= 0.75)
    const rest = free().sort((a, b) => list[b].l - list[a].l)
    const pickIdx = lightNeutral.length
      ? lightNeutral.sort((a, b) => list[b].l - list[a].l)[0]
      : rest[0]
    if (pickIdx !== undefined) {
      palette.border = take(pickIdx)
    }
  }

  return palette
})

const primaryColor = computed(() => previewPalette.value.primary)
const secondaryColor = computed(() => previewPalette.value.secondary)
const accentColor = computed(() => previewPalette.value.accent)
const bgColor = computed(() => previewPalette.value.background)
const cardBg = computed(() => previewPalette.value.cardBg)
const textColor = computed(() => previewPalette.value.text)
const mutedColor = computed(() => previewPalette.value.muted)
const borderColor = computed(() => previewPalette.value.border)

// 点击色板复制色值
async function handleCopyColor(hex) {
  try {
    await navigator.clipboard.writeText(hex)
    ElMessage.success('已复制 ' + hex)
  } catch (err) {
    ElMessage.warning('复制失败，请手动选取色值 ' + hex)
  }
}
const fontSizeVal = computed(() => findFontSize())
const fontFamilyVal = computed(() => findFontFamily())
const spacingVal = computed(() => findSpacing('padding') || findSpacing('gap') || '24px')

const aiNavStyle = computed(() => ({
  backgroundColor: primaryColor.value,
  color: '#fff',
  padding: findSpacing('padding') || '12px 24px',
  fontFamily: fontFamilyVal.value
}))

const aiHeroStyle = computed(() => ({
  backgroundColor: bgColor.value,
  padding: `${spacingVal.value} ${spacingVal.value}`,
  textAlign: 'center',
  fontFamily: fontFamilyVal.value
}))

const aiSectionStyle = computed(() => ({
  padding: spacingVal.value,
  fontFamily: fontFamilyVal.value
}))

const aiCardStyle = computed(() => ({
  backgroundColor: cardBg.value,
  borderRadius: '12px',
  padding: spacingVal.value,
  border: `1px solid ${borderColor.value}`,
  boxShadow: '0 2px 12px rgba(0,0,0,0.08)'
}))

const aiCardImgStyle = computed(() => ({
  backgroundColor: accentColor.value,
  borderRadius: '8px'
}))

const aiFormStyle = computed(() => ({
  backgroundColor: cardBg.value,
  borderRadius: '12px',
  padding: spacingVal.value
}))

const aiInputStyle = computed(() => ({
  fontFamily: fontFamilyVal.value,
  fontSize: fontSizeVal.value,
  padding: findSpacing('padding') || '12px',
  borderRadius: '8px',
  border: `1px solid ${borderColor.value}`
}))

const aiTextareaStyle = computed(() => ({
  ...aiInputStyle.value,
  minHeight: '80px',
  resize: 'vertical'
}))

const aiFooterStyle = computed(() => ({
  backgroundColor: primaryColor.value,
  color: '#fff',
  padding: spacingVal.value,
  textAlign: 'center',
  fontFamily: fontFamilyVal.value
}))

const aiPrimaryBtnStyle = computed(() => ({
  backgroundColor: primaryColor.value,
  color: '#fff',
  border: 'none',
  borderRadius: '8px',
  padding: `${findSpacing('padding') || '10px 24px'}`,
  fontSize: fontSizeVal.value,
  fontFamily: fontFamilyVal.value,
  cursor: 'pointer'
}))

const aiSecondaryBtnStyle = computed(() => ({
  backgroundColor: 'transparent',
  color: secondaryColor.value,
  border: `2px solid ${secondaryColor.value}`,
  borderRadius: '8px',
  padding: `${findSpacing('padding') || '8px 20px'}`,
  fontSize: fontSizeVal.value,
  fontFamily: fontFamilyVal.value,
  cursor: 'pointer'
}))

const aiTitleStyle = computed(() => ({
  fontSize: '28px',
  fontWeight: 700,
  color: textColor.value,
  fontFamily: fontFamilyVal.value,
  marginBottom: '12px'
}))

const aiHeadingStyle = computed(() => ({
  fontSize: '20px',
  fontWeight: 600,
  color: textColor.value,
  fontFamily: fontFamilyVal.value,
  marginBottom: '8px'
}))

const aiBodyStyle = computed(() => ({
  fontSize: fontSizeVal.value,
  color: mutedColor.value,
  fontFamily: fontFamilyVal.value,
  lineHeight: 1.6
}))

onMounted(async () => {
  const brandId = route.params.id
  try {
    const [brandRes, catRes, fileRes] = await Promise.all([
      getBrandById(brandId),
      getTokenCategories(brandId),
      getDesignFiles(brandId)
    ])
    brand.value = brandRes
    logoBroken.value = false
    categories.value = catRes
    designFiles.value = fileRes
    await loadTokens('')
    // 加载全部 Token 用于 Token 效果预览
    const allRes = await getTokensByBrand(brandId, undefined, 1, 0)
    allTokens.value = allRes.records || []
  } catch (e) {
    console.error('加载数据失败', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.breadcrumb-bar {
  margin-bottom: 16px;
}

.brand-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 12px;
}

.brand-logo-wrap {
  position: relative;
  width: 56px;
  height: 56px;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
}

.brand-logo-wrap.logo-editable {
  cursor: pointer;
}

.brand-logo {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  object-fit: contain;
  display: block;
}

.logo-upload-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 11px;
  line-height: 1.2;
  opacity: 0;
  transition: opacity 0.2s;
}

.brand-logo-wrap:hover .logo-upload-mask {
  opacity: 1;
}

.logo-file-input {
  display: none;
}

.avatar-fallback {
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
  user-select: none;
}

.brand-avatar {
  width: 56px;
  height: 56px;
  font-size: 24px;
}

.brand-header h2 {
  font-size: 24px;
  font-weight: 700;
}

.website-link {
  color: #409eff;
  font-size: 13px;
  text-decoration: none;
}

.brand-desc {
  color: #606266;
  line-height: 1.6;
}

.main-area {
  display: flex;
  gap: 20px;
  margin-top: 20px;
  align-items: flex-start;
}

.sidebar {
  width: 240px;
  flex-shrink: 0;
}

.sidebar-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.content {
  flex: 1;
  min-width: 0;
}

.content-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.content-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content-title {
  font-weight: 600;
  font-size: 16px;
}

.search-bar {
  margin-bottom: 16px;
}

.token-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.token-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
  transition: background 0.2s;
}

.token-item:hover {
  background: #f0f2f5;
}

.token-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  opacity: 0.5;
  transition: opacity 0.2s;
}

.token-item:hover .token-actions {
  opacity: 1;
}

.color-swatch {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  flex-shrink: 0;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.color-swatch:hover {
  transform: scale(1.06);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.token-info {
  flex: 1;
  min-width: 0;
}

.token-name {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  margin-bottom: 4px;
}

.token-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.token-value {
  background: #e6efff;
  color: #1677ff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 13px;
}

.type-preview {
  display: inline-block;
  line-height: 1.3;
  color: #303133;
}

.token-desc {
  color: #909399;
  font-size: 13px;
}

.design-files-section {
  margin-top: 8px;
}

.file-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  color: #409eff;
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
  transition: background 0.2s;
}

.file-link:hover {
  background: #ecf5ff;
}

.empty-state {
  padding: 40px 0;
}

/* ===== 品牌头部操作区 ===== */
.brand-title-area {
  flex: 1;
}

.brand-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ===== Token 效果预览样式 ===== */
.ai-preview-wrapper {
  font-family: var(--ai-font, inherit);
}

.ai-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  color: #fff;
  border-radius: 8px 8px 0 0;
}

.ai-nav-brand {
  font-size: 20px;
  font-weight: 700;
}

.ai-nav-links {
  display: flex;
  gap: 20px;
}

.ai-nav-link {
  cursor: pointer;
  opacity: 0.85;
  font-size: 14px;
}

.ai-nav-cta {
  flex-shrink: 0;
}

.ai-hero {
  padding: 48px 24px;
  text-align: center;
}

.ai-hero-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
}

.ai-hero-desc {
  margin-bottom: 24px;
  max-width: 500px;
  margin-left: auto;
  margin-right: auto;
}

.ai-btn-primary {
  border: none;
  cursor: pointer;
}

.ai-btn-secondary {
  background: transparent;
  cursor: pointer;
}

.ai-btn-lg {
  font-size: 18px;
  padding: 14px 32px;
}

.ai-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  padding: 24px;
}

.ai-card {
  border-radius: 12px;
  overflow: hidden;
}

.ai-card-img {
  height: 120px;
  border-radius: 8px 8px 0 0;
}

.ai-card-body {
  padding: 16px;
}

.ai-card-body h4 {
  margin-bottom: 8px;
}

.ai-card-body p {
  margin-bottom: 12px;
}

.ai-form-section {
  padding: 24px;
}

.ai-form-section h3 {
  margin-bottom: 16px;
}

.ai-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-width: 500px;
  border-radius: 12px;
  padding: 24px;
}

.ai-input,
.ai-textarea {
  width: 100%;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
  outline: none;
  box-sizing: border-box;
}

.ai-input:focus,
.ai-textarea:focus {
  border-color: inherit;
}

.ai-textarea {
  min-height: 80px;
  resize: vertical;
}

.ai-footer {
  padding: 24px;
  text-align: center;
  border-radius: 0 0 8px 8px;
  color: #fff;
}

/* ===== Token 效果预览：品牌完整色板 ===== */
.ai-palette-section {
  padding: 16px 24px 20px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
}

.ai-palette-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.ai-palette-count {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.ai-palette-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 10px;
}

.ai-palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.ai-palette-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

.ai-palette-swatch {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.ai-palette-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.ai-palette-hex {
  font-size: 12px;
  font-family: monospace;
  color: #303133;
}

.ai-palette-name {
  font-size: 11px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .ai-cards {
    grid-template-columns: 1fr;
  }

  .ai-nav {
    flex-direction: column;
    gap: 12px;
  }
}

/* ===== 移动端适配（≤768px） ===== */
@media (max-width: 768px) {
  .brand-header {
    flex-wrap: wrap;
    gap: 12px;
  }

  .brand-logo-wrap,
  .brand-logo,
  .brand-avatar {
    width: 44px;
    height: 44px;
  }

  .brand-header h2 {
    font-size: 20px;
  }

  .brand-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .main-area {
    flex-direction: column;
    gap: 16px;
  }

  .sidebar {
    width: 100%;
  }

  .content-header {
    flex-wrap: wrap;
    gap: 8px;
  }

  .content-actions {
    flex-wrap: wrap;
  }

  .token-item {
    flex-wrap: wrap;
    gap: 10px;
  }

  .color-swatch {
    width: 40px;
    height: 40px;
  }
}
</style>
