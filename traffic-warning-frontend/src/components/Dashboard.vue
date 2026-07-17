<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useLocale } from '../composables/useLocale.js'
import trafficHeroIllustration from '@/assets/traffic-hero-illustration.svg'

defineOptions({ name: 'Dashboard' })

const { t } = useLocale()

const router = useRouter()
const CURRENT_USER_KEY = 'traffic_current_user'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const users = ref([])
const devices = ref([])
const events = ref([])
const analyses = ref([])

const currentUser = computed(() => {
  try {
    return JSON.parse(localStorage.getItem(CURRENT_USER_KEY) || '{}')
  } catch {
    return {}
  }
})

const displayName = computed(() => currentUser.value.displayName || currentUser.value.username || '系统管理员')

const formatToday = () => {
  const date = new Date()
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

const trendRange = ref('month')
const selectedTrendIndex = ref(0)
const selectedCalendarDay = ref(new Date().getDate())

const trendRangeOptions = ['year', 'month', 'week']
const trendRangeLabels = {
  week: '本周',
  month: '本月',
  year: '本年'
}
const trendRangeTitles = {
  week: '本周交通流量趋势',
  month: '本月交通流量趋势',
  year: '本年交通流量趋势'
}

const getAnalysisFlow = (analysis) => (analysis.motorCount || 0) + (analysis.nonMotorCount || 0)
const getCongestionValue = (analysis) => {
  const levelScore = {
    I: 20,
    II: 40,
    III: 60,
    IV: 80,
    V: 100
  }
  return levelScore[analysis.congestionLevel] || Math.max(0, Math.round(100 - (analysis.avgSpeed || 0)))
}

const buildTrendFromBuckets = (key, buckets) => ({
  label: trendRangeLabels[key],
  title: trendRangeTitles[key],
  labels: buckets.map((bucket) => bucket.label),
  flow: buckets.map((bucket) => bucket.items.reduce((sum, item) => sum + getAnalysisFlow(item), 0)),
  congestion: buckets.map((bucket) => {
    if (!bucket.items.length) return 0
    return Math.round(bucket.items.reduce((sum, item) => sum + getCongestionValue(item), 0) / bucket.items.length)
  })
})

const trendRanges = computed(() => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const day = now.getDay() || 7
  const weekStart = new Date(year, month, now.getDate() - day + 1)

  const weekLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const weekBuckets = weekLabels.map((label, index) => {
    const bucketDate = new Date(weekStart)
    bucketDate.setDate(weekStart.getDate() + index)
    return {
      label,
      items: analyses.value.filter((item) => item.analysisDate.toDateString() === bucketDate.toDateString())
    }
  })

  const monthMarks = [1, 5, 10, 15, 20, 25, getDaysInMonth(year, month + 1)]
  const monthBuckets = monthMarks.map((mark, index) => {
    const startDay = index === 0 ? 1 : monthMarks[index - 1] + 1
    return {
      label: `${mark}日`,
      items: analyses.value.filter((item) =>
        item.analysisDate.getFullYear() === year &&
        item.analysisDate.getMonth() === month &&
        item.analysisDate.getDate() >= startDay &&
        item.analysisDate.getDate() <= mark
      )
    }
  })

  const yearBuckets = Array.from({ length: 12 }, (_, index) => ({
    label: `${index + 1}月`,
    items: analyses.value.filter((item) => item.analysisDate.getFullYear() === year && item.analysisDate.getMonth() === index)
  }))

  return {
    week: buildTrendFromBuckets('week', weekBuckets),
    month: buildTrendFromBuckets('month', monthBuckets),
    year: buildTrendFromBuckets('year', yearBuckets)
  }
})
const activeTrend = computed(() => trendRanges.value[trendRange.value])

const chart = {
  width: 680,
  height: 240,
  left: 34,
  right: 24,
  top: 24,
  bottom: 38
}

const buildPoints = (values) => {
  const max = Math.max(...values, 1)
  const usableWidth = chart.width - chart.left - chart.right
  const usableHeight = chart.height - chart.top - chart.bottom
  const step = usableWidth / Math.max(values.length - 1, 1)

  return values
    .map((value, index) => {
      const x = chart.left + index * step
      const y = chart.top + usableHeight - (value / max) * usableHeight
      return `${x},${y}`
    })
    .join(' ')
}

const getPoint = (values, index) => {
  const max = Math.max(...values, 1)
  const usableWidth = chart.width - chart.left - chart.right
  const usableHeight = chart.height - chart.top - chart.bottom
  const step = usableWidth / Math.max(values.length - 1, 1)
  return {
    x: chart.left + index * step,
    y: chart.top + usableHeight - (values[index] / max) * usableHeight
  }
}

const changeTrendRange = (key) => {
  trendRange.value = key
  selectedTrendIndex.value = 0
}

const selectedTrendPoint = computed(() => ({
  label: activeTrend.value.labels[selectedTrendIndex.value],
  flow: activeTrend.value.flow[selectedTrendIndex.value],
  congestion: activeTrend.value.congestion[selectedTrendIndex.value],
  point: getPoint(activeTrend.value.flow, selectedTrendIndex.value)
}))

const normalizeDate = (value) => String(value || '').replace('T', ' ').slice(0, 19)

const deviceStats = computed(() => {
  const total = devices.value.length
  const online = devices.value.filter((device) => device.status === '在线').length
  const fault = devices.value.filter((device) => device.status === '故障').length
  const offline = Math.max(0, total - online - fault)
  return { total, online, offline, fault }
})

// ── Donut hover tooltips ───────────────────────────────────────────────────
const deviceDonutHover = ref({ show: false, label: '', value: '', color: '', x: 0, y: 0 })
const aiDonutHover = ref({ show: false, label: '', value: '', color: '', x: 0, y: 0 })

const handleDeviceDonutMove = (e) => {
  const rect = e.currentTarget.getBoundingClientRect()
  const cx = rect.width / 2; const cy = rect.height / 2
  const x = e.clientX - rect.left - cx; const y = e.clientY - rect.top - cy
  let angle = (Math.atan2(y, x) * 180) / Math.PI + 90
  if (angle < 0) angle += 360
  const { online, offline, fault, total } = deviceStats.value
  if (!total) return
  const onlineDeg = Math.round((online / total) * 360)
  const offlineDeg = Math.round((offline / total) * 360)
  if (angle < onlineDeg) {
    deviceDonutHover.value = { show: true, label: '在线设备', value: online + ' 台', color: '#ef4444', x: e.clientX, y: e.clientY }
  } else if (angle < onlineDeg + offlineDeg) {
    deviceDonutHover.value = { show: true, label: '离线设备', value: offline + ' 台', color: '#c7ccd8', x: e.clientX, y: e.clientY }
  } else {
    deviceDonutHover.value = { show: true, label: '故障设备', value: fault + ' 台', color: '#8b93a3', x: e.clientX, y: e.clientY }
  }
}
const hideDeviceDonut = () => { deviceDonutHover.value.show = false }

const handleAiDonutMove = (e) => {
  const rect = e.currentTarget.getBoundingClientRect()
  const cx = rect.width / 2; const cy = rect.height / 2
  const x = e.clientX - rect.left - cx; const y = e.clientY - rect.top - cy
  let angle = (Math.atan2(y, x) * 180) / Math.PI + 90
  if (angle < 0) angle += 360
  const { aiCount, total } = analysisStats.value
  if (!total) return
  const aiDeg = Math.round((aiCount / total) * 360)
  if (angle < aiDeg) {
    aiDonutHover.value = { show: true, label: 'AI分析事件', value: aiCount + ' 条', color: '#ef4444', x: e.clientX, y: e.clientY }
  } else {
    aiDonutHover.value = { show: true, label: '其他事件', value: (total - aiCount) + ' 条', color: '#c7ccd8', x: e.clientX, y: e.clientY }
  }
}
const hideAiDonut = () => { aiDonutHover.value.show = false }

const deviceOnlinePercent = computed(() => {
  if (!deviceStats.value.total) return 0
  return Math.round((deviceStats.value.online / deviceStats.value.total) * 100)
})
const aiAnalysisPercent = computed(() => {
  if (!analysisStats.value.total) return 0
  return Math.round((analysisStats.value.aiCount / analysisStats.value.total) * 100)
})

const deviceOnlineDegrees = computed(() => {
  if (!deviceStats.value.total) return 0
  return Math.round((deviceStats.value.online / deviceStats.value.total) * 360)
})

const deviceFaultDegrees = computed(() => {
  if (!deviceStats.value.total) return 0
  return Math.round((deviceStats.value.fault / deviceStats.value.total) * 360)
})

const deviceOfflineDegrees = computed(() => {
  if (!deviceStats.value.total) return 0
  return Math.round((deviceStats.value.offline / deviceStats.value.total) * 360)
})

const deviceDonutStyle = computed(() => {
  const onlineEnd = deviceOnlineDegrees.value
  const offlineEnd = onlineEnd + deviceOfflineDegrees.value
  const faultEnd = offlineEnd + deviceFaultDegrees.value
  return {
    '--online-end': `${onlineEnd}deg`,
    '--offline-end': `${offlineEnd}deg`,
    '--fault-end': `${faultEnd}deg`
  }
})

const analysisStats = ref({
  aiCount: 0,
  total: 0
})

const aiAnalysisDegrees = computed(() => {
  if (!analysisStats.value.total) return 0
  return Math.round((analysisStats.value.aiCount / analysisStats.value.total) * 360)
})

const aiDonutStyle = computed(() => ({
  '--ai-deg': `${aiAnalysisDegrees.value}deg`
}))

const requestApi = async (path) => {
  const response = await fetch(`${API_BASE_URL}${path}`)
  if (!response.ok) {
    throw new Error('服务器连接失败')
  }
  const result = await response.json()
  if (result.code !== 200) {
    throw new Error(result.message || '操作失败')
  }
  return result.data
}

const fetchDashboardData = async () => {
  try {
    const [userData, deviceData, eventData, analysisData] = await Promise.all([
      requestApi('/users'),
      requestApi('/devices'),
      requestApi('/events'),
      requestApi('/analysis')
    ])
    users.value = userData
    devices.value = deviceData
    events.value = eventData.map((event) => ({
      ...event,
      time: normalizeDate(event.time),
      status: event.status || '未处理',
      analysisType: event.analysisType || 'AI分析',
      roadName: event.roadName || '未知路段',
      congestionLevel: event.congestionLevel || ''
    }))
    analyses.value = analysisData.map((analysis) => ({
      ...analysis,
      motorCount: analysis.motorCount || 0,
      nonMotorCount: analysis.nonMotorCount || 0,
      avgSpeed: analysis.avgSpeed || 0,
      congestionLevel: analysis.congestionLevel || '',
      analysisTime: normalizeDate(analysis.analysisTime),
      analysisDate: new Date(analysis.analysisTime)
    })).filter((analysis) => !Number.isNaN(analysis.analysisDate.getTime()))
    analysisStats.value = {
      aiCount: events.value.filter((event) => event.analysisType === 'AI分析').length,
      total: events.value.length
    }
  } catch {
    users.value = []
    devices.value = []
    events.value = []
    analyses.value = []
    analysisStats.value = { aiCount: 0, total: 0 }
  }
}

const quickActions = computed(() => [
  { name: t('dashboard.realtimeMonitor'), route: '/realtime', tone: 'blue', icon: '<path d="M15 10l5-3v10l-5-3z"/><rect x="3" y="6" width="12" height="12" rx="2"/>' },
  { name: t('sidebar.userManagement'), route: '/users', tone: 'indigo', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
  { name: t('sidebar.deviceManagement'), route: '/devices', tone: 'teal', icon: '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/>' },
  { name: t('dashboard.abnormalRecords'), route: '/road-status', tone: 'orange', icon: '<path d="M10.3 3.9L2.6 17.4A2 2 0 0 0 4.3 20h15.4a2 2 0 0 0 1.7-2.6L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/>' },
  { name: t('dashboard.congestionRecognition'), route: '/congestion', tone: 'red', icon: '<path d="M3 6.5l6-3 6 3 6-3v14l-6 3-6-3-6 3z"/><path d="M9 3.5v14"/><path d="M15 6.5v14"/>' },
  { name: t('dashboard.aiCustomer'), route: '/assistant', tone: 'green', icon: '<path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"/>' },
])

const calendarWeekdays = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']
const today = new Date()
const selectedCalendarYear = ref(today.getFullYear())
const selectedCalendarMonth = ref(today.getMonth() + 1)
const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December']
const calendarTitle = computed(() => `${monthNames[selectedCalendarMonth.value - 1]} ${selectedCalendarYear.value}`)
const getDaysInMonth = (year, month) => new Date(year, month, 0).getDate()
const clampSelectedCalendarDay = () => {
  selectedCalendarDay.value = Math.min(selectedCalendarDay.value, getDaysInMonth(selectedCalendarYear.value, selectedCalendarMonth.value))
}
const changeCalendarMonth = (offset) => {
  const date = new Date(selectedCalendarYear.value, selectedCalendarMonth.value - 1 + offset, 1)
  selectedCalendarYear.value = date.getFullYear()
  selectedCalendarMonth.value = date.getMonth() + 1
  clampSelectedCalendarDay()
}
const changeCalendarYear = (offset) => {
  selectedCalendarYear.value += offset
  clampSelectedCalendarDay()
}
const calendarDays = computed(() => {
  const daysInMonth = getDaysInMonth(selectedCalendarYear.value, selectedCalendarMonth.value)
  const daysInPreviousMonth = getDaysInMonth(selectedCalendarYear.value, selectedCalendarMonth.value - 1 || 12)
  const leadingCount = new Date(selectedCalendarYear.value, selectedCalendarMonth.value - 1, 1).getDay()
  const trailingCount = (7 - ((leadingCount + daysInMonth) % 7)) % 7
  const leadingDays = Array.from({ length: leadingCount }, (_, index) => ({
    day: daysInPreviousMonth - leadingCount + index + 1,
    muted: true
  }))
  const monthDays = Array.from({ length: daysInMonth }, (_, index) => ({
    day: index + 1,
    active: index + 1 === selectedCalendarDay.value
  }))
  const trailingDays = Array.from({ length: trailingCount }, (_, index) => ({ day: index + 1, muted: true }))
  return [...leadingDays, ...monthDays, ...trailingDays]
})
const selectedCalendarDetail = computed(() => {
  const month = String(selectedCalendarMonth.value).padStart(2, '0')
  const day = String(selectedCalendarDay.value).padStart(2, '0')
  const datePrefix = `${selectedCalendarYear.value}-${month}-${day}`
  const dayEvents = events.value.filter((event) => event.time.startsWith(datePrefix))

  const countByRoad = (list) => {
    const counts = new Map()
    list.forEach((event) => {
      counts.set(event.roadName, (counts.get(event.roadName) || 0) + 1)
    })
    return [...counts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '暂无数据'
  }

  const congestionEvents = dayEvents.filter((event) =>
    `${event.eventType}${event.congestionLevel}${event.description || ''}`.includes('拥堵')
  )

  return {
    congestedRoad: countByRoad(congestionEvents.length ? congestionEvents : dayEvents),
    abnormalRoad: countByRoad(dayEvents)
  }
})

onMounted(() => {
  fetchDashboardData()
})

const goTo = (path) => {
  router.push(path)
}
</script>

<template>
  <div class="dashboard-page">
    <section class="welcome-panel scroll-reveal">
      <div class="welcome-copy">
        <h1>Welcome back <span>{{ displayName }}</span></h1>
        <p>{{ formatToday() }} · {{ t('dashboard.today') }}</p>
        <p class="system-summary">{{ t('dashboard.summary') }}</p>
        <div class="welcome-metrics" :aria-label="t('dashboard.today')">
          <span>
            <b>{{ deviceOnlinePercent }}%</b>
            {{ t('dashboard.deviceOnlineRate') }}
          </span>
          <span>
            <b>{{ events.filter((event) => event.status === '未处理').length }}</b>
            {{ t('dashboard.pendingAlerts') }}
          </span>
          <span>
            <b>{{ aiAnalysisPercent }}%</b>
            {{ t('dashboard.aiAnalysis') }}
          </span>
        </div>
      </div>
      <div class="welcome-visual" aria-hidden="true">
        <img :src="trafficHeroIllustration" alt="" />
      </div>
    </section>

    <section class="stat-grid">
      <article class="stat-card blue">
        <div class="stat-icon"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg></div>
        <span class="stat-label">{{ t('dashboard.userCount') }}</span><strong class="stat-value">{{ users.length }}</strong><small class="stat-sub">{{ t('dashboard.userCountSub') }}</small>
      </article>
      <article class="stat-card green">
        <div class="stat-icon"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/></svg></div>
        <span class="stat-label">{{ t('dashboard.deviceCount') }}</span><strong class="stat-value">{{ devices.length }}</strong><small class="stat-sub">{{ t('dashboard.deviceCountSub') }}</small>
      </article>
      <article class="stat-card pink">
        <div class="stat-icon"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M10.3 3.9L2.6 17.4A2 2 0 0 0 4.3 20h15.4a2 2 0 0 0 1.7-2.6L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></div>
        <span class="stat-label">{{ t('dashboard.eventCount') }}</span><strong class="stat-value">{{ events.length }}</strong><small class="stat-sub">{{ t('dashboard.eventCountSub') }}</small>
      </article>
      <article class="stat-card yellow">
        <div class="stat-icon"><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 7-3 7h18s-3 0-3-7"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg></div>
        <span class="stat-label">{{ t('dashboard.pendingWarnings') }}</span><strong class="stat-value">{{ events.filter(e => e.status === '未处理').length }}</strong><small class="stat-sub">{{ t('dashboard.pendingWarningsSub') }}</small>
      </article>
    </section>

    <section class="device-overview-grid">
      <article class="overview-card health-card scroll-reveal">
        <div class="overview-header">
          <h2>{{ t('dashboard.deviceHealth') }}</h2>
          <p>{{ t('dashboard.deviceHealthSub') }}</p>
        </div>
        <div class="health-status-list">
          <div class="health-status online">
            <span class="status-icon">✓</span>
            <strong>{{ t('dashboard.onlineDevice') }}</strong>
            <b>{{ deviceStats.online }}</b>
          </div>
          <div class="health-status offline">
            <span class="status-icon">!</span>
            <strong>{{ t('dashboard.offlineDevice') }}</strong>
            <b>{{ deviceStats.offline }}</b>
          </div>
          <div class="health-status fault">
            <span class="status-icon">×</span>
            <strong>{{ t('dashboard.faultDevice') }}</strong>
            <b>{{ deviceStats.fault }}</b>
          </div>
        </div>
        <button class="device-manage-btn" type="button" @click="goTo('/devices')">
          {{ t('dashboard.deviceManagement') }}
          <span>→</span>
        </button>
      </article>

      <article class="overview-card donut-card scroll-reveal">
        <div class="overview-header">
          <h2>{{ t('dashboard.deviceOnlineShare') }}</h2>
          <p>{{ t('dashboard.deviceOnlineShareSub') }}</p>
        </div>
        <div class="donut-wrap" style="position:relative">
          <div class="donut device-donut" :style="deviceDonutStyle"
            @mousemove="handleDeviceDonutMove"
            @mouseleave="hideDeviceDonut"
          >
            <span class="donut-center-label">{{ deviceOnlinePercent }}<em>%</em></span>
          </div>
          <div v-if="deviceDonutHover.show" class="donut-tooltip"
            :style="{ left: (deviceDonutHover.x - 60) + 'px', top: (deviceDonutHover.y - 45) + 'px' }"
          >
            <span class="dt-dot" :style="{ background: deviceDonutHover.color }"></span>
            <strong>{{ deviceDonutHover.label }}</strong>
            <b>{{ deviceDonutHover.value }}</b>
          </div>
        </div>
        <div class="legend-row">
          <span><i class="green"></i>在线 <b>{{ deviceStats.online }}</b></span>
          <span><i class="orange"></i>离线 <b>{{ deviceStats.offline }}</b></span>
          <span><i class="red"></i>故障 <b>{{ deviceStats.fault }}</b></span>
        </div>
      </article>

      <article class="overview-card donut-card scroll-reveal">
        <div class="overview-header">
          <h2>{{ t('dashboard.aiAnalysisShare') }}</h2>
          <p>{{ t('dashboard.aiAnalysisShareSub') }}</p>
        </div>
        <div class="donut-wrap" style="position:relative">
          <div class="donut ai-donut" :style="aiDonutStyle"
            @mousemove="handleAiDonutMove"
            @mouseleave="hideAiDonut"
          >
            <span class="donut-center-label">{{ aiAnalysisPercent }}<em>%</em></span>
          </div>
          <div v-if="aiDonutHover.show" class="donut-tooltip"
            :style="{ left: (aiDonutHover.x - 60) + 'px', top: (aiDonutHover.y - 45) + 'px' }"
          >
            <span class="dt-dot" :style="{ background: aiDonutHover.color }"></span>
            <strong>{{ aiDonutHover.label }}</strong>
            <b>{{ aiDonutHover.value }}</b>
          </div>
        </div>
        <div class="legend-row">
          <span><i class="blue"></i>AI分析 <b>{{ analysisStats.aiCount }}</b></span>
          <span><i class="gray"></i>事件总数 <b>{{ analysisStats.total }}</b></span>
        </div>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="panel trend-panel scroll-reveal">
        <div class="panel-header">
          <div>
            <h2>{{ t('dashboard.trafficTrend') }}</h2>
            <p>{{ activeTrend.title }} </p>
          </div>
          <div class="range-tabs">
            <button
              v-for="key in trendRangeOptions"
              :key="key"
              type="button"
              :class="['range-tab', trendRange === key && 'active']"
              @click="changeTrendRange(key)"
            >
              {{ trendRanges[key].label }}
            </button>
          </div>
        </div>
        <svg class="trend-svg" viewBox="0 0 680 240" aria-label="交通流量趋势图">
          <line v-for="lineY in [52, 96, 140, 184]" :key="lineY" x1="34" x2="656" :y1="lineY" :y2="lineY" class="grid-line" />
          <polyline :points="buildPoints(activeTrend.flow)" class="trend-line flow-line" />
          <polyline :points="buildPoints(activeTrend.congestion)" class="trend-line congestion-line" />
          <circle
            v-for="(value, index) in activeTrend.flow"
            :key="'flow' + index"
            :cx="getPoint(activeTrend.flow, index).x"
            :cy="getPoint(activeTrend.flow, index).y"
            :r="selectedTrendIndex === index ? 7 : 4.5"
            :class="['trend-point', 'flow-point', selectedTrendIndex === index && 'active']"
            @mouseenter="selectedTrendIndex = index"
            @focus="selectedTrendIndex = index"
          />
          <circle
            v-for="(value, index) in activeTrend.congestion"
            :key="'cong' + index"
            :cx="getPoint(activeTrend.congestion, index).x"
            :cy="getPoint(activeTrend.congestion, index).y"
            :r="selectedTrendIndex === index ? 7 : 4.5"
            :class="['trend-point', 'congestion-point', selectedTrendIndex === index && 'active']"
            @mouseenter="selectedTrendIndex = index"
            @focus="selectedTrendIndex = index"
          />
          <g
            class="trend-tooltip"
            :transform="`translate(${Math.min(selectedTrendPoint.point.x, 538)}, ${Math.max(selectedTrendPoint.point.y - 78, 20)})`"
          >
            <rect width="126" height="62" rx="10" />
            <text x="14" y="20" class="tooltip-label">{{ selectedTrendPoint.label }}</text>
            <text x="14" y="37" class="tooltip-flow">🚗 车流 <tspan font-weight="900">{{ selectedTrendPoint.flow }}</tspan> 辆</text>
            <text x="14" y="54" class="tooltip-cong">🚦 拥堵 <tspan font-weight="900">{{ selectedTrendPoint.congestion }}</tspan>%</text>
          </g>
          <g v-for="(label, index) in activeTrend.labels" :key="label" class="axis-label">
            <text :x="34 + index * ((680 - 34 - 24) / Math.max(activeTrend.labels.length - 1, 1))" y="224" text-anchor="middle">
              {{ label }}
            </text>
          </g>
        </svg>
      </article>

      <article class="panel calendar-panel scroll-reveal">
        <div class="calendar-heading">
          <h2>{{ t('dashboard.calendar') }}</h2>
          <p>{{ t('dashboard.calendarSub') }}</p>
        </div>
        <div class="calendar-picker">
          <div class="calendar-toolbar">
            <button type="button" aria-label="上一年" @click="changeCalendarYear(-1)">&lt;&lt;</button>
            <button type="button" aria-label="上一月" @click="changeCalendarMonth(-1)">&lt;</button>
            <strong>{{ calendarTitle }}</strong>
            <button type="button" aria-label="下一月" @click="changeCalendarMonth(1)">&gt;</button>
            <button type="button" aria-label="下一年" @click="changeCalendarYear(1)">&gt;&gt;</button>
          </div>
          <div class="weekday-row">
            <span v-for="day in calendarWeekdays" :key="day">{{ day }}</span>
          </div>
          <div class="calendar-grid">
            <button
              v-for="day in calendarDays"
              :key="`${day.muted ? 'muted' : 'current'}-${day.day}`"
              :class="['calendar-day', day.active && 'active', day.muted && 'muted']"
              type="button"
              :disabled="day.muted"
              @click="!day.muted && (selectedCalendarDay = day.day)"
            >
              {{ day.day }}
            </button>
          </div>
        </div>
        <div class="calendar-detail-card">
          <strong>{{ selectedCalendarYear }}/{{ selectedCalendarMonth }}/{{ selectedCalendarDay }}</strong>
          <p><span class="detail-dot red"></span>{{ t('最拥堵路段') }}：{{ selectedCalendarDetail.congestedRoad }}</p>
          <p><span class="detail-dot amber"></span>{{ t('异常事件最多路段') }}：{{ selectedCalendarDetail.abnormalRoad }}</p>
        </div>
      </article>

      <section class="quick-panel scroll-reveal">
        <div class="quick-heading">
          <h2>{{ t('快捷入口') }}</h2>
          <p>{{ t('快速访问各功能模块') }}</p>
        </div>
        <div class="quick-grid">
          <button
            v-for="item in quickActions"
            :key="item.name"
            :class="['quick-card', item.tone]"
            type="button"
            @click="goTo(item.route)"
          >
            <svg viewBox="0 0 24 24" aria-hidden="true" v-html="item.icon"></svg>
            <strong>{{ item.name }}</strong>
          </button>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
/* ================================================================
   Dashboard — Clean Modern Theme
   ================================================================ */
.dashboard-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  grid-template-areas:
    "welcome welcome"
    "stats calendar"
    "overview calendar"
    "trend quick";
  gap: 18px;
  padding: 2px;
  color: #1e293b;
}
.dashboard-grid { display: contents; }

/* ===== Welcome Banner ===== */
.welcome-panel {
  grid-area: welcome;
  display: flex; align-items: center; justify-content: space-between;
  gap: 32px; min-height: 240px;
  padding: 32px 44px;
  border-radius: 24px; overflow: hidden; position: relative;
  background:
    linear-gradient(135deg, #fce4ec 0%, #fce4ec 28%, #e3f2fd 72%, #e3f2fd 100%);
  border: 1px solid #e8ecf1;
  box-shadow:
    0 1px 4px rgba(15,23,42,.03),
    0 8px 24px rgba(15,23,42,.06),
    0 20px 56px rgba(15,23,42,.08);
  transition: transform .28s ease, box-shadow .28s ease;
}
.welcome-panel::before { display: none; }
.welcome-panel::after  { display: none; }

.welcome-copy {
  position: relative; z-index: 2; flex: 1; min-width: 0;
  display: flex; flex-direction: column; align-items: flex-start; text-align: left;
}
.welcome-copy h1 {
  margin: 0; font-size: 34px; font-weight: 800; color: #0f172a; line-height: 1.15;
}
.welcome-copy h1 span {
  padding: 2px 12px 5px; border-radius: 8px;
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff; box-decoration-break: clone;
}
.welcome-copy p {
  margin: 12px 0 0; font-size: 14px; font-weight: 600; color: #64748b; line-height: 1.6;
}
.welcome-copy .system-summary {
  display: block; margin-top: 6px; color: #94a3b8; font-size: 12px; font-weight: 500; max-width: 520px;
}

.welcome-metrics { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 20px; }
.welcome-metrics span {
  min-width: 130px; padding: 12px 16px; border-radius: 16px;
  display: flex; flex-direction: column; gap: 2px;
  background: #fff; border: 1px solid #e8ecf1;
  color: #64748b; font-size: 11px; font-weight: 700;
  box-shadow: 0 4px 14px rgba(15,23,42,.04);
}
.welcome-metrics b {
  color: #4BA3ED; font-size: 22px; font-weight: 800; line-height: 1;
}

.welcome-visual {
  position: relative; z-index: 2;
  width: min(34%, 320px); min-width: 240px;
  display: flex; align-items: center; justify-content: center;
}
.welcome-visual img {
  width: 100%; max-height: 200px; object-fit: contain;
  filter: drop-shadow(0 16px 24px rgba(41,72,120,.12));
}

/* ===== Stat Cards ===== */
.stat-grid { grid-area: stats; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; }

.stat-card {
  min-height: 140px; padding: 22px 24px;
  border-radius: 20px; background: #fff;
  border: 1px solid #e8ecf1;
  box-shadow:
    0 1px 3px rgba(15,23,42,.03),
    0 6px 18px rgba(15,23,42,.05);
  display: flex; flex-direction: column; gap: 4px;
  position: relative; overflow: hidden;
  transition: transform .24s ease, box-shadow .24s ease, border-color .24s ease;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow:
    0 2px 6px rgba(15,23,42,.05),
    0 12px 32px rgba(15,23,42,.1);
  border-color: #cbd5e1;
}
.stat-card::after { display: none; }

.stat-card .stat-icon {
  width: 42px; height: 42px; border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; margin-bottom: 6px; color: #fff;
}
.stat-card.blue  .stat-icon { background: #eff6ff; color: #2563eb; }
.stat-card.green .stat-icon { background: #ecfdf5; color: #059669; }
.stat-card.pink  .stat-icon { background: #fef2f2; color: #dc2626; }
.stat-card.yellow .stat-icon { background: #fffbeb; color: #d97706; }

.stat-card > * { position: relative; z-index: 1; }
.stat-card .stat-label { font-size: 13px; font-weight: 700; color: #475569; }
.stat-card .stat-value { margin-top: auto; font-size: 40px; font-weight: 900; color: #0f172a; line-height: 1; }
.stat-card .stat-sub { font-size: 11px; font-weight: 600; color: #94a3b8; }

/* ===== Device Overview ===== */
.device-overview-grid {
  grid-area: overview;
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px;
}
.overview-card {
  min-height: 130px; padding: 20px 22px; border-radius: 20px;
  background: #fff; border: 1px solid #e8ecf1;
  box-shadow:
    0 1px 3px rgba(15,23,42,.03),
    0 6px 18px rgba(15,23,42,.05);
  display: flex; flex-direction: column;
  transition: transform .24s ease, box-shadow .24s ease;
}
.overview-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 6px rgba(15,23,42,.05), 0 12px 32px rgba(15,23,42,.1);
}

.overview-header h2 { margin: 0; font-size: 15px; font-weight: 800; color: #0f172a; }
.overview-header p  { margin: 2px 0 0; font-size: 11px; font-weight: 500; color: #94a3b8; }

/* Health list */
.health-card .health-status-list { display: flex; flex-direction: column; gap: 6px; margin: 12px 0; flex: 1; }
.health-status {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 12px; border-radius: 12px;
  font-size: 12px; font-weight: 700;
}
.health-status.online  { background: #ecfdf5; color: #059669; }
.health-status.offline { background: #fff7ed; color: #c2410c; }
.health-status.fault   { background: #fef2f2; color: #dc2626; }
.status-icon {
  width: 24px; height: 24px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 800; background: rgba(255,255,255,.6);
}
.health-status strong { flex: 1; font-weight: 700; }
.health-status b { font-size: 18px; font-weight: 900; }

.device-manage-btn {
  width: 100%; height: 36px;
  border: 1px solid #e2e8f0; border-radius: 12px;
  background: #f8fafc; color: #475569;
  font-size: 12px; font-weight: 700; cursor: pointer;
  display: flex; align-items: center; justify-content: center; gap: 6px;
  transition: all .15s;
}
.device-manage-btn:hover { background: #4BA3ED; border-color: #4BA3ED; color: #fff; }

/* Donut */
.donut-card { justify-content: space-between; }
.donut-wrap { flex: 1; display: flex; align-items: center; justify-content: center; position: relative; min-height: 80px; }
.donut {
  width: 88px; height: 88px; border-radius: 50%; position: relative;
  box-shadow: 0 6px 18px rgba(15,23,42,.06);
}
.donut::after {
  content: ''; position: absolute; inset: 22px; border-radius: inherit;
  background: #fff; z-index: 1;
}
.donut-center-label {
  position: absolute; inset: 0; z-index: 2;
  display: flex; align-items: center; justify-content: center;
  font-size: 15px; font-weight: 900; color: #0f172a; pointer-events: none;
}
.donut-center-label em { font-size: 11px; font-weight: 700; color: #94a3b8; margin-left: 1px; }

.device-donut {
  background: conic-gradient(
    #4BA3ED 0deg var(--online-end),
    #e2e8f0 var(--online-end) var(--offline-end),
    #94a3b8 var(--offline-end) var(--fault-end),
    #f1f5f9 var(--fault-end) 360deg
  );
}
.ai-donut {
  background: conic-gradient(#6366f1 0deg var(--ai-deg), #e2e8f0 var(--ai-deg) 360deg);
}

.legend-row {
  display: flex; justify-content: center; gap: 16px;
  font-size: 11px; font-weight: 600; color: #64748b; margin-top: 4px;
}
.legend-row span { display: flex; align-items: center; gap: 5px; }
.legend-row i { width: 8px; height: 8px; border-radius: 50%; }
.legend-row b { font-weight: 800; color: #334155; }
.legend-row .green  { background: #4BA3ED; }
.legend-row .orange { background: #e2e8f0; }
.legend-row .red    { background: #94a3b8; }
.legend-row .blue   { background: #6366f1; }
.legend-row .gray   { background: #e2e8f0; }

.donut-tooltip {
  position: fixed; z-index: 100;
  display: flex; align-items: center; gap: 8px;
  padding: 8px 14px; background: #fff;
  border: 1px solid #e2e8f0; border-radius: 12px;
  box-shadow: 0 8px 24px rgba(15,23,42,.1);
  font-size: 12px; pointer-events: none;
}
.dt-dot { width: 8px; height: 8px; border-radius: 50%; }
.donut-tooltip strong { color: #64748b; font-weight: 600; }
.donut-tooltip b { color: #0f172a; font-weight: 800; }

/* ===== Trend Chart ===== */
.trend-panel {
  grid-area: trend;
  padding: 26px 30px; border-radius: 20px;
  background: #fff; border: 1px solid #e8ecf1;
  box-shadow:
    0 1px 3px rgba(15,23,42,.03),
    0 6px 18px rgba(15,23,42,.05);
  display: flex; flex-direction: column; min-height: 380px;
  transition: transform .24s ease, box-shadow .24s ease;
}
.trend-panel:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 6px rgba(15,23,42,.05), 0 12px 32px rgba(15,23,42,.1);
}

.panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 4px; }
.panel-header h2 { margin: 0; font-size: 17px; font-weight: 800; color: #0f172a; }
.panel-header p  { margin: 2px 0 0; font-size: 12px; font-weight: 500; color: #94a3b8; }

.range-tabs { display: flex; gap: 2px; padding: 3px; border-radius: 999px; background: #f1f5f9; }
.range-tab {
  height: 30px; min-width: 46px; border: none; border-radius: 999px;
  background: transparent; color: #94a3b8; font-size: 12px; font-weight: 700;
  cursor: pointer; padding: 0 12px; transition: all .15s;
}
.range-tab.active { background: #fff; color: #0f172a; box-shadow: 0 4px 12px rgba(15,23,42,.06); }

.trend-svg { width: 100%; flex: 1; min-height: 280px; margin-top: 12px; }
.grid-line { stroke: #f1f5f9; stroke-width: 1; }
.trend-line { fill: none; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
.flow-line { stroke: #4BA3ED; }
.congestion-line { stroke: #6366f1; }
.trend-point { cursor: pointer; transition: r .15s ease; }
.flow-point { fill: #4BA3ED; stroke: #fff; stroke-width: 2; }
.congestion-point { fill: #6366f1; stroke: #fff; stroke-width: 2; }
.trend-point.active { filter: drop-shadow(0 4px 10px rgba(75,163,237,.35)); }

.trend-tooltip { pointer-events: none; }
.trend-tooltip rect {
  fill: #fff; stroke: #e2e8f0; rx: 8;
  filter: drop-shadow(0 4px 12px rgba(15,23,42,.08));
}
.trend-tooltip .tooltip-label { fill: #475569; font-size: 11px; font-weight: 700; }
.trend-tooltip .tooltip-flow  { fill: #4BA3ED; font-size: 10px; font-weight: 600; }
.trend-tooltip .tooltip-cong  { fill: #6366f1; font-size: 10px; font-weight: 600; }
.axis-label text { fill: #94a3b8; font-size: 10px; font-weight: 600; }

/* ===== Calendar ===== */
.calendar-panel {
  grid-area: calendar;
  display: flex; flex-direction: column; gap: 14px;
  padding: 22px; border-radius: 20px;
  background: #fff; border: 1px solid #e8ecf1;
  box-shadow:
    0 1px 3px rgba(15,23,42,.03),
    0 6px 18px rgba(15,23,42,.05);
}
.calendar-heading h2 { margin: 0; font-size: 17px; font-weight: 800; color: #0f172a; }
.calendar-heading p  { margin: 2px 0 0; font-size: 12px; font-weight: 500; color: #94a3b8; }

.calendar-picker {
  padding: 16px; border-radius: 18px;
  background: #f8fafc; border: 1px solid #f1f5f9;
}
.calendar-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 4px; }
.calendar-toolbar button {
  width: 30px; height: 30px; border: 1px solid #e2e8f0; border-radius: 10px;
  background: #fff; color: #94a3b8; font-size: 12px; font-weight: 700;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  transition: all .15s;
}
.calendar-toolbar button:hover { border-color: #4BA3ED; color: #4BA3ED; background: #eff6ff; }
.calendar-toolbar strong { font-size: 14px; font-weight: 800; color: #0f172a; min-width: 110px; text-align: center; }

.weekday-row {
  display: grid; grid-template-columns: repeat(7, 1fr);
  margin-top: 12px; text-align: center;
  font-size: 11px; font-weight: 700; color: #94a3b8;
}
.calendar-grid { display: grid; grid-template-columns: repeat(7, 1fr); gap: 3px; margin-top: 4px; }
.calendar-day {
  width: 100%; aspect-ratio: 1; border: none; border-radius: 10px;
  background: transparent; color: #475569; font-size: 12px; font-weight: 600;
  cursor: pointer; transition: all .12s;
}
.calendar-day:hover { background: #eff6ff; color: #4BA3ED; }
.calendar-day.active {
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff; box-shadow: 0 6px 16px rgba(75,163,237,.3);
}
.calendar-day.muted { color: #cbd5e1; cursor: default; pointer-events: none; }

.calendar-detail-card {
  margin-top: 0; padding: 16px; border-radius: 16px;
  background: #f8fafc; border: 1px solid #e8ecf1;
}
.calendar-detail-card strong { display: block; font-size: 13px; font-weight: 800; color: #0f172a; margin-bottom: 6px; }
.calendar-detail-card p { margin: 4px 0 0; display: flex; align-items: center; gap: 6px; font-size: 11px; font-weight: 600; color: #64748b; }
.detail-dot { width: 6px; height: 6px; border-radius: 50%; flex: none; }
.detail-dot.red   { background: #ef4444; }
.detail-dot.amber { background: #f59e0b; }

/* ===== Quick Actions ===== */
.quick-panel {
  grid-area: quick;
  padding: 22px; border-radius: 20px;
  background: #fff; border: 1px solid #e8ecf1;
  box-shadow:
    0 1px 3px rgba(15,23,42,.03),
    0 6px 18px rgba(15,23,42,.05);
}
.quick-heading h2 { margin: 0; font-size: 17px; font-weight: 800; color: #0f172a; }
.quick-heading p  { margin: 2px 0 0; font-size: 12px; font-weight: 500; color: #94a3b8; }

.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 16px; }
.quick-card {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 8px; min-height: 80px; padding: 14px 10px;
  border-radius: 16px; border: 1px solid #e8ecf1;
  background: #fafcfd; cursor: pointer;
  transition: all .18s ease;
}
.quick-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(15,23,42,.08);
}
.quick-card svg {
  width: 22px; height: 22px;
  fill: none; stroke: currentColor;
  stroke-linecap: round; stroke-linejoin: round; stroke-width: 2;
}
.quick-card strong { font-size: 12px; font-weight: 700; }

.quick-card.blue   { background: #eff6ff; border-color: #bfdbfe; color: #3b82f6; }
.quick-card.orange { background: #fff7ed; border-color: #fed7aa; color: #ea580c; }
.quick-card.red    { background: #fef2f2; border-color: #fecaca; color: #ef4444; }
.quick-card.green  { background: #ecfdf5; border-color: #a7f3d0; color: #10b981; }
.quick-card.indigo { background: #eef2ff; border-color: #c7d2fe; color: #4f46e5; }
.quick-card.teal   { background: #f0fdfa; border-color: #99f6e4; color: #0d9488; }

/* ===== Responsive ===== */
@media (max-width: 1180px) {
  .dashboard-page {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: "welcome" "stats" "overview" "trend" "calendar" "quick";
  }
  .device-overview-grid { grid-template-columns: 1fr 1fr 1fr; }
}
@media (max-width: 1380px) and (min-width: 1181px) {
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 820px) {
  .stat-grid { grid-template-columns: repeat(2, 1fr); }
  .device-overview-grid { grid-template-columns: 1fr; }
}
@media (max-width: 540px) {
  .welcome-panel { flex-direction: column; align-items: flex-start; }
  .stat-grid, .quick-grid { grid-template-columns: 1fr; }
}
</style>
