import { describe, it, expect, beforeEach } from 'vitest'
import router from '../src/router/index'

describe('路由鉴权守卫', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('未登录访问需鉴权页 /agent 重定向 /login 并携带 redirect', async () => {
    await router.push('/agent')
    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/agent')
  })

  it('登录后可访问需鉴权页 /agent', async () => {
    localStorage.setItem('token', 'x')
    await router.push('/agent')
    expect(router.currentRoute.value.path).toBe('/agent')
  })

  it('普通用户访问 /admin 重定向首页', async () => {
    localStorage.setItem('token', 'x')
    localStorage.setItem('user', JSON.stringify({ role: 'USER' }))
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('ADMIN 可访问 /admin', async () => {
    localStorage.setItem('token', 'x')
    localStorage.setItem('user', JSON.stringify({ role: 'ADMIN' }))
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/admin')
  })

  it('user 数据损坏时视为非 ADMIN，/admin 被拦回首页', async () => {
    localStorage.setItem('token', 'x')
    localStorage.setItem('user', 'not-json')
    await router.push('/login')
    await router.push('/admin')
    expect(router.currentRoute.value.path).toBe('/')
  })

  it('公开路由不受守卫影响', async () => {
    await router.push('/login')
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
