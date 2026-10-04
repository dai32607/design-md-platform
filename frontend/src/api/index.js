import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动附加 Authorization: Bearer <token>
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

api.interceptors.response.use(
  response => {
    const res = response.data
    if (res.code !== 0) {
      console.error('API Error:', res.message)
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  error => {
    // 401：token 缺失/失效，清除本地登录态并跳转登录页
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (window.location.pathname !== '/login') {
        window.location.href = `/login?redirect=${encodeURIComponent(window.location.pathname)}`
      }
    }
    const msg =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络错误'
    console.error('Network Error:', error)
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export function getBrands(params) {
  return api.get('/brands', { params })
}

export function getBrandById(id) {
  return api.get(`/brands/${id}`)
}

// 登录用户创建品牌（普通用户，非管理后台）
export function createUserBrand(data) {
  return api.post('/brands', data)
}

// 登录用户修改品牌信息（仅创建者本人；ADMIN 可改任意 custom 品牌）
export function updateUserBrand(id, data) {
  return api.put(`/brands/${id}`, data)
}

// 登录用户删除自己创建的品牌（仅创建者本人；ADMIN 可删任意 custom 品牌）
export function deleteUserBrand(id) {
  return api.delete(`/brands/${id}`)
}

// AI 生成设计规范 + 自动入库（Agent 闭环）
export function generateSpec(data) {
  return api.post('/agent/generate-spec', data)
}

export function getTokensByBrand(brandId, category, page, pageSize) {
  return api.get(`/brands/${brandId}/tokens`, {
    params: { category, page, pageSize }
  })
}

export function getTokenCategories(brandId) {
  return api.get(`/brands/${brandId}/token-categories`)
}

export function updateToken(id, data) {
  return api.put(`/tokens/${id}`, data)
}

export function deleteToken(id) {
  return api.delete(`/tokens/${id}`)
}

export function getDesignFiles(brandId) {
  return api.get(`/brands/${brandId}/files`)
}

export function getDesignFileById(id) {
  return api.get(`/files/${id}`)
}

export function uploadDesignMd(formData) {
  return api.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function uploadBrandLogo(brandId, formData) {
  return api.post(`/brands/${brandId}/logo`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ===== 用户认证 =====
export function register(data) {
  return api.post('/auth/register', data)
}

export function login(data) {
  return api.post('/auth/login', data)
}

export function getCurrentUser() {
  return api.get('/auth/me')
}

// 用户自助注销（需携带 token + body.password 二次校验）
export function deleteAccount(data) {
  return api.delete('/auth/account', { data })
}

// ===== 用户管理（仅 ADMIN） =====
export function getUsers(params) {
  return api.get('/admin/users', { params })
}

export function resetUserPassword(id, data) {
  return api.put(`/admin/users/${id}/password`, data)
}

export function deleteUser(id) {
  return api.delete(`/admin/users/${id}`)
}

// ===== 品牌管理（仅 ADMIN） =====
export function createBrand(data) {
  return api.post('/admin/brands', data)
}

export function updateBrand(id, data) {
  return api.put(`/admin/brands/${id}`, data)
}

export function deleteBrand(id) {
  return api.delete(`/admin/brands/${id}`)
}

// ===== 数据统计（仅 ADMIN） =====
export function getStatsOverview() {
  return api.get('/stats/overview')
}

export function getStatsBrandTokens() {
  return api.get('/stats/brand-tokens')
}

export function getStatsCategoryDist() {
  return api.get('/stats/category-dist')
}

export function getStatsTrend() {
  return api.get('/stats/trend')
}

export default api
