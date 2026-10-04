<template>
  <div class="dashboard-page">
    <div class="admin-header">
      <div>
        <h2>数据统计</h2>
        <p>平台数据总览：品牌 Token 分布、分类占比与增长趋势</p>
      </div>
    </div>

    <!-- 指标卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.key" :xs="12" :sm="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ overviewData[card.key] ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区：上排两卡等高并排，下排折线图整行 -->
    <div class="chart-grid">
      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">品牌 Token 数量</span></template>
        <div v-loading="loading" class="chart-body">
          <div v-if="!loading && brandChartEmpty" class="chart-empty">
            <el-icon class="empty-icon"><DataAnalysis /></el-icon>
            <span>暂无 Token 数据</span>
          </div>
          <div v-else ref="brandChartRef" class="chart"></div>
        </div>
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header><span class="chart-title">Token 分类分布</span></template>
        <div v-loading="loading" class="chart-body">
          <div v-if="!loading && categoryChartEmpty" class="chart-empty">
            <el-icon class="empty-icon"><PieChart /></el-icon>
            <span>暂无分类数据</span>
          </div>
          <div v-else ref="categoryChartRef" class="chart"></div>
        </div>
      </el-card>

      <el-card shadow="never" class="chart-card trend-card">
        <template #header><span class="chart-title">品牌与用户增长趋势</span></template>
        <div v-loading="loading" class="chart-body">
          <div v-if="!loading && trendChartEmpty" class="chart-empty">
            <el-icon class="empty-icon"><TrendCharts /></el-icon>
            <span>暂无趋势数据</span>
          </div>
          <div v-else ref="trendChartRef" class="chart"></div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { DataAnalysis, PieChart as PieIcon, TrendCharts } from '@element-plus/icons-vue'
import {
  getStatsOverview,
  getStatsBrandTokens,
  getStatsCategoryDist,
  getStatsTrend
} from '../api'

echarts.use([
  BarChart,
  PieChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer
])

const CHART_HEIGHT = 320 // 每个图表容器固定高度

const loading = ref(false)
const overviewData = reactive({ brandCount: 0, tokenCount: 0, userCount: 0, fileCount: 0 })
const brandChartEmpty = ref(false)
const categoryChartEmpty = ref(false)
const trendChartEmpty = ref(false)

const brandChartRef = ref(null)
const categoryChartRef = ref(null)
const trendChartRef = ref(null)

let brandChart = null
let categoryChart = null
let trendChart = null

const statCards = [
  { key: 'brandCount', label: '品牌总数' },
  { key: 'tokenCount', label: 'Token 总数' },
  { key: 'userCount', label: '用户总数' },
  { key: 'fileCount', label: 'DESIGN.md 文件数' }
]

// 超长分类名/品牌名截断，避免标签拥挤
function truncate(text, max = 10) {
  const s = String(text || '')
  return s.length > max ? `${s.slice(0, max)}…` : s
}

// 等待容器尺寸稳定后再 init（Tab 懒挂载/动画场景兜底）
function waitForSize(maxTries = 10) {
  return new Promise(resolve => {
    let tries = 0
    const check = () => {
      const el =
        brandChartRef.value || categoryChartRef.value || trendChartRef.value
      const ready =
        el && el.clientWidth > 0 && el.clientHeight > 0
      if (ready || tries >= maxTries) {
        resolve()
        return
      }
      tries += 1
      setTimeout(check, 100)
    }
    check()
  })
}

async function loadData() {
  loading.value = true
  try {
    const [overview, brandTokens, categoryDist, trend] = await Promise.all([
      getStatsOverview().catch(() => null),
      getStatsBrandTokens().catch(() => null),
      getStatsCategoryDist().catch(() => null),
      getStatsTrend().catch(() => null)
    ])
    if (overview) {
      Object.assign(overviewData, overview)
    }

    brandChartEmpty.value = !brandTokens || brandTokens.length === 0
    categoryChartEmpty.value = !categoryDist || categoryDist.length === 0
    trendChartEmpty.value = !trend || trend.length === 0

    await nextTick()
    await waitForSize()
    renderBrandChart(brandTokens || [])
    renderCategoryChart(categoryDist || [])
    renderTrendChart(trend || [])
  } catch (e) {
    console.error('加载统计数据失败', e)
  } finally {
    loading.value = false
  }
}

function renderBrandChart(data) {
  if (!brandChartRef.value) return
  if (!brandChart) {
    brandChart = echarts.init(brandChartRef.value)
  }
  brandChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 48, right: 24, top: 24, bottom: 64 },
    xAxis: {
      type: 'category',
      data: data.map(d => truncate(d.brandName)),
      // 品牌名倾斜 30 度 + 固定展示全部，避免重叠
      axisLabel: { interval: 0, rotate: 30, fontSize: 12, color: '#606266' },
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisTick: { alignWithLabel: true }
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f0f2f5' } } },
    series: [
      {
        name: 'Token 数',
        type: 'bar',
        data: data.map(d => d.count),
        barMaxWidth: 48,
        itemStyle: { color: '#667eea', borderRadius: [4, 4, 0, 0] }
      }
    ]
  })
}

function renderCategoryChart(data) {
  if (!categoryChartRef.value) return
  if (!categoryChart) {
    categoryChart = echarts.init(categoryChartRef.value)
  }
  categoryChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: {
      bottom: 4,
      type: 'scroll',
      itemWidth: 12,
      itemHeight: 12,
      textStyle: { fontSize: 12, color: '#606266' }
    },
    series: [
      {
        name: '分类占比',
        type: 'pie',
        radius: ['42%', '66%'],
        center: ['50%', '44%'],
        minAngle: 5,
        avoidLabelOverlap: true,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        // 分类名与数值分行，避免标签拥挤
        label: { show: true, fontSize: 12, color: '#606266', formatter: '{b}\n{c}' },
        labelLine: { length: 12, length2: 8 },
        data: data.map(d => ({ name: truncate(d.category, 12), value: d.count }))
      }
    ]
  })
}

function renderTrendChart(data) {
  if (!trendChartRef.value) return
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value)
  }
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['新增品牌', '新增用户'],
      top: 0,
      itemWidth: 14,
      itemHeight: 10,
      textStyle: { fontSize: 12, color: '#606266' }
    },
    grid: { left: 48, right: 24, top: 44, bottom: 40 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data.map(d => d.date),
      axisLabel: { fontSize: 12, color: '#606266' },
      axisLine: { lineStyle: { color: '#dcdfe6' } }
    },
    yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: '#f0f2f5' } } },
    series: [
      {
        name: '新增品牌',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: data.map(d => d.brandCount),
        itemStyle: { color: '#667eea' },
        lineStyle: { width: 3, color: '#667eea' },
        areaStyle: { opacity: 0.08 }
      },
      {
        name: '新增用户',
        type: 'line',
        smooth: true,
        symbol: 'diamond',
        symbolSize: 8,
        data: data.map(d => d.userCount),
        itemStyle: { color: '#f093fb' },
        lineStyle: { width: 3, color: '#f093fb' },
        areaStyle: { opacity: 0.08 }
      }
    ]
  })
}

function handleResize() {
  brandChart && brandChart.resize()
  categoryChart && categoryChart.resize()
  trendChart && trendChart.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  brandChart && brandChart.dispose()
  categoryChart && categoryChart.dispose()
  trendChart && trendChart.dispose()
})
</script>

<style scoped>
.dashboard-page {
  padding-bottom: 40px;
}

.admin-header {
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

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  margin-bottom: 8px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.stat-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

/* 统一栅格：上排 3:2 等高并排，下排整行，间距 16px */
.chart-grid {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: 16px;
  align-items: stretch;
}

.trend-card {
  grid-column: 1 / -1;
}

.chart-card {
  margin: 0;
  overflow: hidden;
}

.chart-card :deep(.el-card__body) {
  padding: 20px;
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.chart-body {
  position: relative;
  height: v-bind('CHART_HEIGHT + "px"');
  min-height: v-bind('CHART_HEIGHT + "px"');
}

.chart {
  width: 100%;
  height: 100%;
}

/* 空数据 / 加载中占位 */
.chart-empty {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  font-size: 14px;
  color: #909399;
  background: #fafafa;
  border-radius: 8px;
}

.empty-icon {
  font-size: 30px;
  color: #c0c4cc;
}

/* 窄屏：图表上下排列 */
@media (max-width: 992px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .trend-card {
    grid-column: auto;
  }
}
</style>
