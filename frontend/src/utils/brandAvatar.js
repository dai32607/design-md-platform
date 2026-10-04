// 品牌头像工具：logo 为空时按品牌名生成彩色圆形首字母头像兜底
const AVATAR_COLORS = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9C27B0', '#00BCD4', '#FF9800']

export function avatarStyle(name) {
  const text = (name || '').trim()
  let hash = 0
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) >>> 0
  }
  return { backgroundColor: AVATAR_COLORS[hash % AVATAR_COLORS.length] }
}

export function avatarLetter(name) {
  const text = (name || '').trim()
  return text ? text.charAt(0).toUpperCase() : '?'
}
