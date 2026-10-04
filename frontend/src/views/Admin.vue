<template>
  <div class="admin-page">
    <div class="admin-topbar">
      <div class="topbar-title">管理后台</div>
      <el-button type="primary" :icon="HomeFilled" @click="goHome">返回主页</el-button>
    </div>
    <el-tabs v-model="activeTab" class="admin-tabs">
      <el-tab-pane label="品牌管理" name="brands">
        <div class="admin-header">
          <div>
            <h2>品牌管理</h2>
            <p>管理品牌列表：新增、编辑、删除（删除将同时移除该品牌的 Token 与 DESIGN.md 文件）</p>
          </div>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增品牌</el-button>
        </div>

        <el-card v-loading="loading">
          <el-table :data="brands" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column label="Logo" width="80">
              <template #default="{ row }">
                <img
                  v-if="row.logoUrl && !brokenLogos.has(row.id)"
                  :src="row.logoUrl"
                  class="brand-logo"
                  :alt="row.name"
                  @error="onLogoError(row.id)"
                />
                <el-icon v-else class="logo-placeholder"><Picture /></el-icon>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="品牌名称" min-width="140">
              <template #default="{ row }">
                <span>{{ row.name }}</span>
                <el-tag v-if="row.source === 'system'" size="small" type="info" class="source-tag">预置</el-tag>
                <el-tag v-else size="small" type="success" class="source-tag">自定义</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="website" label="官网地址" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <a v-if="row.website" :href="row.website" target="_blank" rel="noopener" class="website-link">{{ row.website }}</a>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                <span>{{ row.description || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <div class="op-col">
                  <el-button size="small" :icon="Edit" @click="openEdit(row)">编辑</el-button>
                  <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="users">
        <div class="admin-header">
          <div>
            <h2>用户管理</h2>
            <p>管理平台注册用户：重置密码、删除用户（内置 admin 与自身账号不可删除）</p>
          </div>
        </div>

        <el-card v-loading="userLoading">
          <el-table :data="users" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="username" label="用户名" min-width="140" />
            <el-table-column prop="nickname" label="昵称" min-width="140" />
            <el-table-column label="角色" width="110">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" min-width="170">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <div class="op-col">
                  <el-button size="small" :icon="Edit" @click="handleResetPassword(row)">重置密码</el-button>
                  <el-button size="small" type="danger" :icon="Delete" @click="handleDeleteUser(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="userTotal > 0" class="pagination-wrapper">
            <span class="pagination-total">共 {{ userTotal }} 条 · 共 {{ userPageCount }} 页</span>
            <el-pagination
              background
              layout="prev, pager, next"
              :total="userTotal"
              :page-size="userPageSize"
              :current-page="userCurrentPage"
              @current-change="handleUserPageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="数据统计" name="stats">
        <!-- 懒挂载：切到该 Tab 再渲染，保证图表容器可见、尺寸稳定 -->
        <DashboardView v-if="activeTab === 'stats'" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑品牌' : '新增品牌'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="品牌名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入品牌名称" />
        </el-form-item>
        <el-form-item label="Logo URL" prop="logoUrl">
          <el-input v-model="form.logoUrl" placeholder="Logo 图片地址（可选，留空保持当前 Logo 不变）" />
        </el-form-item>
        <el-form-item label="官网地址" prop="website">
          <el-input v-model="form.website" placeholder="请输入官网地址（可选）" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入品牌描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Picture, HomeFilled } from '@element-plus/icons-vue'
import { getBrands, createBrand, updateBrand, deleteBrand, getUsers, resetUserPassword, deleteUser } from '../api'
import DashboardView from './Dashboard.vue'

const activeTab = ref('brands')
const router = useRouter()

function goHome() {
  router.push('/')
}
const brands = ref([])
// 头像加载失败的品牌 id 集合：失败后回退图标占位，避免出现破图
const brokenLogos = reactive(new Set())

function onLogoError(id) {
  brokenLogos.add(id)
}
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

// 用户管理
const users = ref([])
const userLoading = ref(false)
const userTotal = ref(0)
const userCurrentPage = ref(1)
const userPageSize = ref(10)
// 总页数 = ceil(总条数 / 每页条数)
const userPageCount = computed(() => Math.ceil(userTotal.value / userPageSize.value))

function formatTime(v) {
  if (!v) return '—'
  return String(v).replace('T', ' ')
}

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await getUsers({ page: userCurrentPage.value, pageSize: userPageSize.value })
    users.value = res.records || []
    userTotal.value = res.total || 0
  } catch (e) {
    console.error('加载用户列表失败', e)
  } finally {
    userLoading.value = false
  }
}

function handleUserPageChange(page) {
  userCurrentPage.value = page
  loadUsers()
}

async function handleResetPassword(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `为「${row.username}」设置新密码（至少 6 位）`,
      '重置密码',
      {
        confirmButtonText: '确认重置',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入新密码',
        inputValidator: v => (v && v.length >= 6 ? true : '新密码至少 6 位')
      }
    )
    await resetUserPassword(row.id, { newPassword: value })
    ElMessage.success(`已重置「${row.username}」的密码`)
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    console.error('重置密码失败', e)
  }
}

async function handleDeleteUser(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${row.username}」吗？内置 admin 与自身账号不可删除，删除后该账号将无法登录。`,
      '删除用户',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    loadUsers()
  } catch (e) {
    console.error('删除用户失败', e)
  }
}

const form = reactive({
  id: null,
  name: '',
  logoUrl: '',
  website: '',
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入品牌名称', trigger: 'blur' }]
}

async function loadBrands() {
  loading.value = true
  try {
    // 管理端展示全量品牌，使用大 pageSize 拉取
    const res = await getBrands({ page: 1, pageSize: 1000 })
    brands.value = res.records || []
  } catch (e) {
    console.error('加载品牌列表失败', e)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = null
  form.name = ''
  form.logoUrl = ''
  form.website = ''
  form.description = ''
}

function openCreate() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.id = row.id
  form.name = row.name
  // Logo 已统一存库由本地接口提供，不回填内部接口地址；留空表示保持当前 Logo 不变
  form.logoUrl = ''
  form.website = row.website || ''
  form.description = row.description || ''
  dialogVisible.value = true
}

async function handleSave() {
  await formRef.value.validate()
  saving.value = true
  try {
    const payload = {
      name: form.name,
      logoUrl: form.logoUrl || null,
      website: form.website || null,
      description: form.description || null
    }
    if (isEdit.value) {
      await updateBrand(form.id, payload)
      ElMessage.success('品牌已更新')
    } else {
      await createBrand(payload)
      ElMessage.success('品牌已创建')
    }
    dialogVisible.value = false
    loadBrands()
  } catch (e) {
    console.error('保存品牌失败', e)
  } finally {
    saving.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除品牌「${row.name}」吗？其下所有 Token 与 DESIGN.md 文件将一并删除，此操作不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteBrand(row.id)
    ElMessage.success('品牌已删除')
    loadBrands()
  } catch (e) {
    console.error('删除品牌失败', e)
  }
}

onMounted(() => {
  loadBrands()
  loadUsers()
})
</script>

<style scoped>
.admin-page {
  padding-bottom: 40px;
}

.admin-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 0 8px;
}

.topbar-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.admin-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 0;
}

.admin-header h2 {
  font-size: 26px;
  color: #303133;
}

.admin-header p {
  margin-top: 6px;
  font-size: 13px;
  color: #909399;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  object-fit: contain;
}

.logo-placeholder {
  width: 32px;
  height: 32px;
  color: #c0c4cc;
}

.source-tag {
  margin-left: 6px;
}

.website-link {
  color: #667eea;
  text-decoration: none;
}

.text-muted {
  color: #c0c4cc;
}

.op-col {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.op-col .el-button {
  margin-left: 0;
  margin-right: 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.pagination-total {
  margin-right: 12px;
  color: #606266;
  font-size: 14px;
  line-height: 32px;
}

@media (max-width: 768px) {
  .admin-header {
    flex-direction: column;
  }
}
</style>
