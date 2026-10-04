import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import AgentGenerate from '../views/AgentGenerate.vue'
import BrandDetail from '../views/BrandDetail.vue'
import DesignFileView from '../views/DesignFileView.vue'
import UploadDesignMd from '../views/UploadDesignMd.vue'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import Admin from '../views/Admin.vue'

const routes = [
  { path: '/', name: 'Home', component: Home },
  { path: '/agent', name: 'AgentGenerate', component: AgentGenerate, meta: { requiresAuth: true } },
  { path: '/brand/:id', name: 'BrandDetail', component: BrandDetail },
  { path: '/file/:id', name: 'DesignFileView', component: DesignFileView },
  { path: '/brand/:id/upload', name: 'UploadDesignMd', component: UploadDesignMd, meta: { requiresAuth: true } },
  { path: '/login', name: 'Login', component: Login },
  { path: '/register', name: 'Register', component: Register },
  { path: '/admin', name: 'Admin', component: Admin, meta: { requiresAuth: true, requiresAdmin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：未登录访问需鉴权页面跳转登录页；非 ADMIN 无法访问管理后台
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  let user = null
  try {
    user = JSON.parse(localStorage.getItem('user') || 'null')
  } catch (e) {
    user = null
  }

  if (to.meta.requiresAuth && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.meta.requiresAdmin && (!user || user.role !== 'ADMIN')) {
    next('/')
    return
  }
  next()
})

export default router
