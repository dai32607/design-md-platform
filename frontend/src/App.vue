<template>
  <div id="app" :class="{ 'dark-mode': isDark }">
    <el-container>
      <el-header class="app-header">
        <div class="header-content">
          <router-link to="/" class="logo">
            <h1>DESIGN.md</h1>
            <span class="subtitle">设计规范平台</span>
          </router-link>
          <div class="header-actions">
            <template v-if="currentUser">
              <router-link v-if="currentUser.role === 'ADMIN'" to="/admin" class="admin-link">管理后台</router-link>
              <el-dropdown trigger="click" @command="handleUserCommand">
                <span class="user-name user-dropdown">
                  {{ currentUser.nickname || currentUser.username }}
                  <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="deleteAccount">注销账号</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button size="small" class="logout-btn" @click="logout">退出</el-button>
            </template>
            <template v-else>
              <router-link to="/login" class="auth-link">登录</router-link>
              <router-link to="/register" class="auth-link register-link">注册</router-link>
            </template>
            <el-switch
              v-model="isDark"
              :active-icon="Moon"
              :inactive-icon="Sunny"
              inline-prompt
              @change="toggleDark"
            />
          </div>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
      <el-footer class="app-footer">
        <p>DESIGN.md Platform — 收集和展示各品牌设计系统规范，为 AI 代码助手提供结构化设计 Token</p>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Moon, Sunny, ArrowDown } from '@element-plus/icons-vue'
import { deleteAccount } from './api'

const router = useRouter()
const currentUser = ref(null)

function loadUser() {
  try {
    currentUser.value = JSON.parse(localStorage.getItem('user') || 'null')
  } catch (e) {
    currentUser.value = null
  }
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  currentUser.value = null
  router.push('/login')
}

function handleUserCommand(command) {
  if (command === 'deleteAccount') {
    handleDeleteAccount()
  }
}

async function handleDeleteAccount() {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入当前登录密码以确认注销账号，注销后账号将无法恢复。',
      '注销账号',
      {
        confirmButtonText: '确认注销',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入当前密码',
        inputValidator: v => (v && v.length >= 6 ? true : '请输入至少 6 位的密码'),
        type: 'warning'
      }
    )
    await deleteAccount({ password: value })
    ElMessage.success('账号已注销')
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    currentUser.value = null
    router.push('/login')
  } catch (e) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e.message || '注销失败')
  }
}

const isDark = ref(localStorage.getItem('theme') === 'dark')

function toggleDark(val) {
  localStorage.setItem('theme', val ? 'dark' : 'light')
  if (val) {
    document.documentElement.classList.add('dark')
  } else {
    document.documentElement.classList.remove('dark')
  }
}

// 初始化主题
if (isDark.value) {
  document.documentElement.classList.add('dark')
} else {
  document.documentElement.classList.remove('dark')
}

onMounted(loadUser)
router.afterEach(loadUser)
</script>

<style>
/* ===== CSS 变量：亮色模式默认值 ===== */
:root {
  --bg-primary: #f5f7fa;
  --bg-card: #ffffff;
  --bg-header: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  --text-primary: #303133;
  --text-secondary: #606266;
  --text-muted: #909399;
  --border-color: #ebeef5;
  --hover-bg: #f0f2f5;
  --token-bg: #fafafa;
  --token-value-bg: #e6efff;
  --token-value-color: #1677ff;
  --footer-bg: #ffffff;
  --footer-border: #ebeef5;
  --card-shadow: 0 2px 12px rgba(0,0,0,0.06);
}

/* ===== 暗色模式变量 ===== */
html.dark {
  --bg-primary: #1a1a2e;
  --bg-card: #252540;
  --bg-header: linear-gradient(135deg, #3a3a6e 0%, #4a2a6e 100%);
  --text-primary: #e0e0e0;
  --text-secondary: #b0b0c0;
  --text-muted: #808090;
  --border-color: #3a3a5c;
  --hover-bg: #2a2a40;
  --token-bg: #2a2a40;
  --token-value-bg: #1e3a5f;
  --token-value-color: #7eb8ff;
  --footer-bg: #252540;
  --footer-border: #3a3a5c;
  --card-shadow: 0 2px 12px rgba(0,0,0,0.3);
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
  transition: background 0.3s, color 0.3s;
}

.app-header {
  background: var(--bg-header);
  padding: 0;
  height: 64px !important;
}

.header-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 24px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.auth-link {
  color: #fff;
  font-size: 14px;
  text-decoration: none;
  opacity: 0.9;
  transition: opacity 0.2s;
}

.auth-link:hover {
  opacity: 1;
  text-decoration: underline;
}

.register-link {
  padding: 4px 12px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 6px;
}

.admin-link {
  color: #fff;
  font-size: 14px;
  text-decoration: none;
  padding: 4px 12px;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  transition: background 0.2s;
}

.admin-link:hover {
  background: rgba(255, 255, 255, 0.18);
}

.user-name {
  color: #fff;
  font-size: 14px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-dropdown {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  outline: none;
}

.user-dropdown .dropdown-icon {
  font-size: 12px;
}

.logout-btn {
  --el-button-bg-color: transparent;
  --el-button-border-color: rgba(255, 255, 255, 0.7);
  --el-button-text-color: #fff;
  --el-button-hover-bg-color: rgba(255, 255, 255, 0.18);
  --el-button-hover-border-color: #fff;
  --el-button-hover-text-color: #fff;
}

.logo {
  text-decoration: none;
  color: #fff;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.logo h1 {
  font-size: 24px;
  font-weight: 700;
  letter-spacing: 1px;
}

.logo .subtitle {
  font-size: 14px;
  opacity: 0.85;
}

.el-main {
  min-height: calc(100vh - 124px);
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
  width: 100%;
}

.app-footer {
  background: var(--footer-bg);
  border-top: 1px solid var(--footer-border);
  height: 60px !important;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 24px;
}

.app-footer p {
  color: var(--text-muted);
  font-size: 13px;
}

/* ===== Element Plus 暗色模式覆盖 ===== */
html.dark .el-card {
  background: var(--bg-card);
  border-color: var(--border-color);
  color: var(--text-primary);
}

html.dark .el-card__header {
  border-bottom-color: var(--border-color);
}

html.dark .el-menu {
  background: var(--bg-card);
  border-right-color: var(--border-color);
}

html.dark .el-menu-item {
  color: var(--text-secondary);
}

html.dark .el-menu-item:hover {
  background: var(--hover-bg);
}

html.dark .el-breadcrumb__inner {
  color: var(--text-muted);
}

html.dark .el-tag {
  background: var(--token-bg);
  border-color: var(--border-color);
  color: var(--text-secondary);
}

html.dark .el-divider {
  border-top-color: var(--border-color);
}

html.dark .el-empty__description p {
  color: var(--text-muted);
}

html.dark .token-item {
  background: var(--token-bg) !important;
}

html.dark .token-item:hover {
  background: var(--hover-bg) !important;
}

html.dark .token-value {
  background: var(--token-value-bg) !important;
  color: var(--token-value-color) !important;
}
</style>
