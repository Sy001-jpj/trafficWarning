<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

import { useLocale } from '../composables/useLocale.js'

defineOptions({ name: 'CongestionRecognition' })

const { locale } = useLocale()

// ========== Leaflet 默认图标修复 (Vite 打包路径问题) ==========
delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl:
    'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNSA0MSIgd2lkdGg9IjI1IiBoZWlnaHQ9IjQxIj48cGF0aCBmaWxsPSIjMjU2M2ViIiBkPSJNMTIuNSAwQzUuNiAwIDAgNS42IDAgMTIuNUMwIDIxLjkgMTIuNSA0MSAxMi41IDQxUzI1IDIxLjkgMjUgMTIuNUMyNSA1LjYgMTkuNCAwIDEyLjUgMHoiLz48Y2lyY2xlIGZpbGw9IiNmZmYiIGN4PSIxMi41IiBjeT0iMTIuNSIgcj0iNSIvPjwvc3ZnPg==',
  iconUrl:
    'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNSA0MSIgd2lkdGg9IjI1IiBoZWlnaHQ9IjQxIj48cGF0aCBmaWxsPSIjMjU2M2ViIiBkPSJNMTIuNSAwQzUuNiAwIDAgNS42IDAgMTIuNUMwIDIxLjkgMTIuNSA0MSAxMi41IDQxUzI1IDIxLjkgMjUgMTIuNUMyNSA1LjYgMTkuNCAwIDEyLjUgMHoiLz48Y2lyY2xlIGZpbGw9IiNmZmYiIGN4PSIxMi41IiBjeT0iMTIuNSIgcj0iNSIvPjwvc3ZnPg==',
  shadowUrl:
    'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCA0MSA0MSIgd2lkdGg9IjQxIiBoZWlnaHQ9IjQxIj48cGF0aCBmaWxsPSJyZ2JhKDAsMCwwLDAuMTUpIiBkPSJNMjAuNSAzNy41Yy0xLjcgMC0zLjMtLjMtNC44LS45bC04LjctOC43QzIuMyAyMy4zIDIuMyAxNy43IDcgMTN2MGM0LjctNC43IDEyLjMtNC43IDE3IDBsLjUuNS42LS42YzQuNy00LjcgMTIuMy00LjcgMTcgMHYwYzQuNyA0LjcgNC43IDEyLjMgMCAxN2wtOC43IDguN2MtMS41LjYtMy4xLjktNC44Ljl6Ii8+PC9zdmc+',
})

// ========== 类型定义 ==========
interface TrafficPoint {
  lng: number
  lat: number
}

interface TrafficRoad {
  name: string
  level?: string
  value?: number
  tone: string
  points: TrafficPoint[]
}

interface NetworkData {
  source: string
  updatedAt: string
  roads: TrafficRoad[]
}

interface CitySuggestion {
  displayName: string
  lat: number
  lng: number
}

interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
}

// ========== 常量 ==========
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const DEEPSEEK_API_KEY = import.meta.env.VITE_DEEPSEEK_API_KEY || ''
const DEEPSEEK_URL = 'https://api.deepseek.com/v1/chat/completions'

const levelLegend = [
  { label: '畅通', tone: 'green', range: '0 — 30', desc: '车速稳定，通行正常' },
  { label: '轻微拥堵', tone: 'yellow', range: '31 — 50', desc: '局部缓行，影响较小' },
  { label: '拥堵', tone: 'orange', range: '51 — 75', desc: '排队明显，建议关注' },
  { label: '严重拥堵', tone: 'red', range: '76 — 100', desc: '持续拥堵，需优先处置' },
]

const TONE_COLORS: Record<string, string> = {
  green: '#7fc56d',
  yellow: '#f3d34a',
  orange: '#f59e0b',
  red: '#7f002b',
}

const CHINA_LNG_RANGE = { min: 73, max: 135 }
const CHINA_LAT_RANGE = { min: 18, max: 54 }

const PREDEFINED_CITIES: CitySuggestion[] = [
  { displayName: '南京市', lat: 32.0603, lng: 118.7969 },
  { displayName: '北京市', lat: 39.9042, lng: 116.4074 },
  { displayName: '上海市', lat: 31.2304, lng: 121.4737 },
  { displayName: '广州市', lat: 23.1291, lng: 113.2644 },
  { displayName: '深圳市', lat: 22.5431, lng: 114.0579 },
  { displayName: '杭州市', lat: 30.2741, lng: 120.1551 },
  { displayName: '成都市', lat: 30.5728, lng: 104.0668 },
  { displayName: '南京市', lat: 32.0603, lng: 118.7969 },
  { displayName: '武汉市', lat: 30.5928, lng: 114.3055 },
  { displayName: '西安市', lat: 34.3416, lng: 108.9398 },
]

// ========== 响应式状态 ==========
const network = ref<NetworkData>({ source: 'loading', updatedAt: '', roads: [] })
const loading = ref(false)
const errorMessage = ref('')
const selectedRoadName = ref('')
const dataLoaded = ref(false)

// 地图
const mapContainer = ref<HTMLElement | null>(null)
let mapInstance: L.Map | null = null
let roadLayerGroup: L.LayerGroup | null = null
const mapReady = ref(false)
const usingFallbackCoords = ref(false)

// 城市搜索
const citySearchQuery = ref('')
const citySuggestions = ref<CitySuggestion[]>([])
const citySearching = ref(false)
const citySearchError = ref('')
const cityDropdownVisible = ref(false)
const selectedCityName = ref('玄武区, 南京市, 江苏省')
const defaultCity: CitySuggestion = {
  displayName: '玄武区, 南京市, 江苏省',
  lat: 32.065,
  lng: 118.81,
}

// AI 面板
const aiPanelOpen = ref(false)
const aiPanelPosition = ref({ x: 0, y: 0 })
const aiDragging = ref(false)
const aiDragOffset = ref({ x: 0, y: 0 })
const aiMessages = ref<ChatMessage[]>([])
const aiInputText = ref('')
const aiLoading = ref(false)
const aiError = ref('')

// ========== 工具函数 ==========
const roadTone = (tone: string) =>
  ['green', 'yellow', 'orange', 'red'].includes(tone) ? tone : 'green'

const hasRealCoordinates = (roads: TrafficRoad[]): boolean => {
  const allPoints = roads.flatMap((r) => r.points)
  if (!allPoints.length) return false
  const avgLng = allPoints.reduce((s, p) => s + p.lng, 0) / allPoints.length
  const avgLat = allPoints.reduce((s, p) => s + p.lat, 0) / allPoints.length
  return (
    avgLng >= CHINA_LNG_RANGE.min &&
    avgLng <= CHINA_LNG_RANGE.max &&
    avgLat >= CHINA_LAT_RANGE.min &&
    avgLat <= CHINA_LAT_RANGE.max
  )
}

const formatDateTime = (value: string): string => {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 19)
}

// ========== API 请求 ==========
const requestApi = async <T>(path: string): Promise<T> => {
  const response = await fetch(`${API_BASE_URL}${path}`)
  if (!response.ok) throw new Error('操作失败')
  const result = await response.json()
  if (result.code !== 200) throw new Error(result.message || '操作失败')
  return result.data as T
}

const fetchNetwork = async () => {
  try {
    loading.value = true
    const data = await requestApi<NetworkData>('/congestion/road-network')
    network.value = {
      source: data.source || 'fallback',
      updatedAt: formatDateTime(String(data.updatedAt || '')),
      roads: Array.isArray(data.roads) ? data.roads : [],
    }
    if (!selectedRoadName.value || !network.value.roads.find((r) => r.name === selectedRoadName.value)) {
      selectedRoadName.value = network.value.roads[0]?.name || ''
    }
    errorMessage.value = ''
    dataLoaded.value = true
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error ? error.message : '请求失败'
  } finally {
    loading.value = false
  }
}

// ========== 地图初始化 ==========
const initMap = () => {
  if (!mapContainer.value) return

  mapInstance = L.map(mapContainer.value, {
    center: [32.065, 118.81],
    zoom: 14,
    zoomControl: true,
    attributionControl: true,
  })

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    maxZoom: 19,
  }).addTo(mapInstance)

  roadLayerGroup = L.layerGroup().addTo(mapInstance)
  mapReady.value = true
}

// ========== 路况折线渲染 ==========
const renderRoads = () => {
  if (!roadLayerGroup || !mapReady.value) return

  roadLayerGroup.clearLayers()

  const selectedName = selectedRoadName.value

  for (const road of network.value.roads) {
    if (!road.points || road.points.length < 2) continue

    const latLngs: L.LatLngTuple[] = road.points.map((p) => [p.lat, p.lng])
    const tone = roadTone(road.tone)
    const isActive = road.name === selectedName
    const color = TONE_COLORS[tone] || TONE_COLORS.green

    const polyline = L.polyline(latLngs, {
      color,
      weight: isActive ? 7 : 4,
      opacity: isActive ? 1 : 0.72,
      lineCap: 'round',
      lineJoin: 'round',
    })

    polyline.bindTooltip(`${road.name} · ${road.level || '未知'}`, {
      direction: 'top',
      offset: [0, -8],
    })

    polyline.on('click', () => {
      selectedRoadName.value = road.name
    })

    polyline.addTo(roadLayerGroup!)
  }

  // 真实坐标时自适应视图
  if (!usingFallbackCoords.value && network.value.roads.length > 0) {
    const allPoints = network.value.roads.flatMap((r) => r.points)
    if (allPoints.length > 0) {
      const latLngs = allPoints.map((p) => [p.lat, p.lng] as L.LatLngTuple)
      mapInstance?.fitBounds(L.latLngBounds(latLngs), { padding: [40, 40] })
    }
  }
}

// 只更新选中道路样式（不重建所有折线）
const updateRoadStyles = () => {
  if (!roadLayerGroup) return
  const selectedName = selectedRoadName.value
  roadLayerGroup.eachLayer((layer) => {
    if (layer instanceof L.Polyline) {
      const tooltip = layer.getTooltip()
      const roadName = tooltip?.getContent()?.toString().split(' · ')[0]
      const isActive = roadName === selectedName
      const tone = (layer.options.color as string) || TONE_COLORS.green
      const colorKey = Object.entries(TONE_COLORS).find(([, v]) => v === tone)?.[0] || 'green'
      layer.setStyle({
        weight: isActive ? 7 : 4,
        opacity: isActive ? 1 : 0.72,
      })
    }
  })
}

// ========== 坐标检测与回退处理 ==========
const handleRoadData = () => {
  usingFallbackCoords.value =
    network.value.roads.length > 0 && !hasRealCoordinates(network.value.roads)
  if (mapReady.value) {
    renderRoads()
  }
}

watch(
  () => network.value.roads,
  () => {
    handleRoadData()
  },
  { deep: true },
)

watch(selectedRoadName, () => {
  if (mapReady.value) {
    updateRoadStyles()
  }
})

// ========== 城市搜索 ==========
let searchTimer: ReturnType<typeof setTimeout> | null = null

const doCitySearch = (query: string) => {
  if (searchTimer) clearTimeout(searchTimer)

  const trimmed = query.trim()
  if (!trimmed) {
    citySuggestions.value = []
    cityDropdownVisible.value = false
    citySearchError.value = ''
    return
  }

  searchTimer = setTimeout(async () => {
    citySearching.value = true
    citySearchError.value = ''

    try {
      const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(trimmed)}&limit=5&countrycodes=cn`
      const resp = await fetch(url, {
        headers: { 'Accept-Language': 'zh' },
      })
      if (!resp.ok) throw new Error('Nominatim 不可用')
      const data: Array<{ display_name: string; lat: string; lon: string }> = await resp.json()
      if (data.length === 0) {
        // 回退预设列表
        const q = trimmed.toLowerCase()
        citySuggestions.value = PREDEFINED_CITIES.filter(
          (c) =>
            c.displayName.includes(trimmed) ||
            c.displayName.toLowerCase().includes(q),
        )
        citySearchError.value = citySuggestions.value.length === 0 ? '未找到匹配城市' : ''
      } else {
        citySuggestions.value = data.map((item) => ({
          displayName: item.display_name,
          lat: parseFloat(item.lat),
          lng: parseFloat(item.lon),
        }))
      }
      cityDropdownVisible.value = citySuggestions.value.length > 0
    } catch {
      // 网络错误时回退预设列表
      const q = trimmed.toLowerCase()
      citySuggestions.value = PREDEFINED_CITIES.filter(
        (c) =>
          c.displayName.includes(trimmed) ||
          c.displayName.toLowerCase().includes(q),
      )
      citySearchError.value = ''
      cityDropdownVisible.value = citySuggestions.value.length > 0
    } finally {
      citySearching.value = false
    }
  }, 400)
}

const selectCity = (city: CitySuggestion) => {
  selectedCityName.value = city.displayName
  citySearchQuery.value = city.displayName
  cityDropdownVisible.value = false
  citySuggestions.value = []
  mapInstance?.setView([city.lat, city.lng], 14, { animate: true })
}

const hideCityDropdown = () => {
  // 延迟隐藏以保证 click 事件能触发
  setTimeout(() => {
    cityDropdownVisible.value = false
  }, 200)
}

const clearCitySearch = () => {
  citySearchQuery.value = ''
  citySuggestions.value = []
  cityDropdownVisible.value = false
}

// ========== AI 面板 ==========
const AI_PANEL_WIDTH = 400

const initAiPanelPosition = () => {
  aiPanelPosition.value = {
    x: Math.max(0, window.innerWidth - AI_PANEL_WIDTH - 20),
    y: 140,
  }
}

const toggleAiPanel = () => {
  aiPanelOpen.value = !aiPanelOpen.value
  if (aiPanelOpen.value) {
    if (aiPanelPosition.value.x === 0 && aiPanelPosition.value.y === 0) {
      initAiPanelPosition()
    }
    // 首次打开时初始化消息
    if (aiMessages.value.length === 0) {
      aiMessages.value.push({
        role: 'assistant',
        content: 'Hello, I am the AI assistant. How can I help you?',
      })
    }
  }
}

// 拖拽处理
const onAiPanelPointerDown = (e: PointerEvent) => {
  const target = e.target as HTMLElement
  if (!target.closest('.ai-panel-header')) return

  aiDragging.value = true
  aiDragOffset.value = {
    x: e.clientX - aiPanelPosition.value.x,
    y: e.clientY - aiPanelPosition.value.y,
  }
  ;(target.closest('.ai-panel-header') as HTMLElement)?.setPointerCapture(e.pointerId)
}

const onAiPanelPointerMove = (e: PointerEvent) => {
  if (!aiDragging.value) return

  const newX = e.clientX - aiDragOffset.value.x
  const newY = e.clientY - aiDragOffset.value.y

  aiPanelPosition.value = {
    x: Math.max(0, Math.min(newX, window.innerWidth - AI_PANEL_WIDTH)),
    y: Math.max(60, Math.min(newY, window.innerHeight - 100)),
  }
}

const onAiPanelPointerUp = () => {
  aiDragging.value = false
}

// DeepSeek API 调用
const sendAiMessage = async () => {
  const text = aiInputText.value.trim()
  if (!text || aiLoading.value) return

  aiMessages.value.push({ role: 'user', content: text })
  aiInputText.value = ''
  aiLoading.value = true
  aiError.value = ''

  try {
    if (!DEEPSEEK_API_KEY) {
      aiMessages.value.push({
        role: 'assistant',
        content:
          '⚠️ 未配置 DeepSeek API Key。请在项目根目录创建 `.env` 文件并设置 `VITE_DEEPSEEK_API_KEY=your-api-key` 后重启应用。',
      })
      return
    }

    const trafficSummary = network.value.roads
      .map((r) => `${r.name}：${r.level || '未知'}（拥堵指数 ${r.value ?? 0}%）`)
      .join('；')

    const systemMsg = trafficSummary
      ? `You are a traffic congestion analysis assistant. Current congestion data:：${trafficSummary}。Note: Do not proactively display this data. Only analyze when the user asks about a specific road. Answer concisely and professionally.`
      : 'You are a traffic congestion analysis assistant. Currently no road network data. Answer concisely and professionally.'

    const response = await fetch(DEEPSEEK_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${DEEPSEEK_API_KEY}`,
      },
      body: JSON.stringify({
        model: 'deepseek-chat',
        messages: [
          { role: 'system', content: systemMsg },
          ...aiMessages.value.map((m) => ({ role: m.role, content: m.content })),
        ],
        temperature: 0.7,
        max_tokens: 1024,
      }),
    })

    if (!response.ok) {
      const errText = await response.text().catch(() => '')
      throw new Error(`API 请求失败 (${response.status})${errText ? ': ' + errText : ''}`)
    }

    const data = await response.json()
    const reply =
      data.choices?.[0]?.message?.content || 'Failed to get AI response. Please try again later.'
    aiMessages.value.push({ role: 'assistant', content: reply })
  } catch (error: unknown) {
    const msg = error instanceof Error ? error.message : 'AI 请求失败'
    aiError.value = msg
    aiMessages.value.push({
      role: 'assistant',
      content: `❌ ${msg}`,
    })
  } finally {
    aiLoading.value = false
  }
}

const clearAiChat = () => {
  aiMessages.value = []
  aiError.value = ''
  aiMessages.value.push({
    role: 'assistant',
    content: 'Hello, I am the AI assistant. How can I help you?',
  })
}

// ========== 计算属性 ==========
const rankedRoads = computed(() =>
  [...network.value.roads].sort((a, b) => (b.value || 0) - (a.value || 0)),
)

const selectedRoad = computed(
  () =>
    network.value.roads.find((r) => r.name === selectedRoadName.value) ||
    network.value.roads[0],
)

// ========== 生命周期 ==========
let resizeObserver: ResizeObserver | null = null

onMounted(async () => {
  initMap()
  if (mapReady.value) mapInstance?.setView([32.065, 118.81], 14)
  resizeObserver = new ResizeObserver(() => { mapInstance?.invalidateSize() })
  if (mapContainer.value) resizeObserver.observe(mapContainer.value)
  window.addEventListener('resize', () => mapInstance?.invalidateSize())
})

const loadMapData = async () => {
  await fetchNetwork()
  if (mapReady.value && network.value.roads.length > 0) {
    renderRoads()
    const allPoints = network.value.roads.flatMap((r) => r.points)
    if (allPoints.length > 0) {
      const latLngs = allPoints.map((p) => [p.lat, p.lng] as L.LatLngTuple)
      mapInstance?.fitBounds(L.latLngBounds(latLngs), { padding: [50, 50] })
    }
  }
}

const cancelLoad = () => {
  network.value = { source: '', updatedAt: '', roads: [] }
  selectedRoadName.value = ''
  dataLoaded.value = false
  errorMessage.value = ''
  if (roadLayerGroup) roadLayerGroup.clearLayers()
  if (mapInstance) mapInstance.setView([32.065, 118.81], 14)
}

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  window.removeEventListener('resize', () => mapInstance?.invalidateSize())
  if (mapInstance) { mapInstance.remove(); mapInstance = null }
})
</script>

<template>
  <div class="congestion-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <div>
        <h1>{{ locale === 'zh' ? '拥堵识别' : 'Real-time traffic recognition' }}</h1>
        <p>{{ locale === 'zh' ? '基于开源地图实时展示路况信息，支持城市搜索与 AI 智能分析。' : 'Real-time traffic display with OpenStreetMap. Supports city search and AI analysis.' }}</p>
      </div>
      <div class="header-btns">
        <button class="refresh-btn" type="button" :disabled="loading" @click="loadMapData">
          {{ loading ? '加载中...' : dataLoaded ? '刷新路网' : '调用地图API' }}
        </button>
        <button v-if="dataLoaded" class="cancel-btn" type="button" @click="cancelLoad">
          {{ locale === 'zh' ? '取消调用' : 'Cancel' }}
        </button>
      </div>
    </div>

    <p v-if="errorMessage" class="error-tip">{{ errorMessage }}</p>

    <section class="content-grid">
      <!-- 地图区域 -->
      <article class="map-panel scroll-reveal">
        <div class="panel-header">
          <div>
            <h2>{{ locale === 'zh' ? '地图定位' : 'Map location' }}</h2>
            <span class="city-badge">📍 {{ selectedCityName }}</span>
            <span class="source-tag">{{ network.source === 'amap' ? locale === 'zh' ? '高德实时交通' : '本地路网' : 'Local road network' }} · {{ network.updatedAt || '等待刷新' }}</span>
          </div>
          <div v-if="selectedRoad" :class="['selected-pill', roadTone(selectedRoad.tone)]">
            {{ selectedRoad.name }} · {{ selectedRoad.level || '未知' }}
          </div>
        </div>

        <!-- 模拟数据横幅 -->
        <div v-if="usingFallbackCoords" class="fallback-banner">
          {{ locale === 'zh' ? '暂无分析数据，正在使用模拟坐标显示。请刷新页面或稍后再试。' : 'No analysis data available. Using fallback coordinates. Please refresh the page or try again later.' }}
        </div>

        <!-- 地图容器 -->
        <div class="map-wrapper">
          <!-- 城市搜索悬浮层（左侧） -->
          <div class="city-search-overlay">
            <div class="city-search-box">
              <svg
                class="search-icon"
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="#98a2b3"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="11" cy="11" r="8" />
                <path d="M21 21l-4.35-4.35" />
              </svg>
              <input
                v-model="citySearchQuery"
                type="text"
                :placeholder="'查询' + '...'"
                @input="doCitySearch(citySearchQuery)"
                @focus="cityDropdownVisible = citySuggestions.length > 0"
                @blur="hideCityDropdown"
              />
              <span v-if="citySearching" class="search-spinner"></span>
              <button
                v-if="citySearchQuery"
                class="search-clear"
                type="button"
                @click="clearCitySearch"
              >
                ✕
              </button>
            </div>

            <!-- 下拉结果 -->
            <ul v-if="cityDropdownVisible && citySuggestions.length" class="city-dropdown">
              <li
                v-for="city in citySuggestions"
                :key="city.displayName"
                @mousedown.prevent="selectCity(city)"
              >
                <svg
                  width="14"
                  height="14"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="#667085"
                  stroke-width="2"
                >
                  <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
                  <circle cx="12" cy="10" r="3" />
                </svg>
                <span>{{ city.displayName }}</span>
              </li>
            </ul>
            <p v-if="citySearchError" class="search-error">{{ citySearchError }}</p>
          </div>

          <!-- AI 切换按钮（右侧悬浮） -->
          <button
            :class="['ai-toggle-btn', { active: aiPanelOpen }]"
            type="button"
            @click="toggleAiPanel"
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            >
              <path d="M12 2a4 4 0 0 1 4 4c0 1.1-.4 2.1-1.2 2.8l-.8.8c-.9.9-1.4 2.1-1.4 3.4v1" />
              <circle cx="12" cy="19" r="2" />
              <path d="M17.5 6.5c1.7 1 2.5 2.8 2.5 5.5a8 8 0 1 1-16 0c0-2.7.8-4.5 2.5-5.5" />
            </svg>
            AI 分析
          </button>

          <!-- Leaflet 地图 -->
          <div ref="mapContainer" class="leaflet-map-container"></div>

          <!-- 空数据提示 -->
          <div v-if="!network.roads.length && !loading" class="empty-map">
            <template v-if="!dataLoaded">
              {{ locale === 'zh' ? '点击上方「调用地图API」按钮加载路况数据' : 'Click the "Load Map API" button above to load traffic data' }}
            </template>
            <template v-else>
              {{ locale === 'zh' ? '暂无分析数据' : 'No analysis data available.' }}
            </template>
          </div>
        </div>
      </article>

      <!-- 侧边栏 -->
      <aside class="side-column">
        <!-- 拥堵等级图例 -->
        <article class="legend-panel scroll-reveal">
          <div class="panel-header compact">
            <h2>{{ locale === 'zh' ? '拥堵等级分类' : 'Congestion level classification' }}</h2>
          </div>
          <div class="legend-list">
            <div v-for="item in levelLegend" :key="item.label" class="legend-item">
              <i :class="item.tone"></i>
              <div>
                <strong>{{ item.label }} <em class="range">{{ item.range }}</em></strong>
                <span>{{ item.desc }}</span>
              </div>
            </div>
          </div>
        </article>

        <!-- 拥堵识别 -->
        <article class="ranking-panel scroll-reveal">
          <div class="panel-header compact">
            <h2>{{ locale === 'zh' ? '拥堵排行榜' : 'Congestion Ranking' }}</h2>
            <span>{{ rankedRoads.length }} {{ locale === 'zh' ? '条路段' : 'segments' }}</span>
          </div>
          <div class="segment-list">
            <button
              v-for="(item, index) in rankedRoads"
              :key="item.name"
              :class="['segment-item', selectedRoadName === item.name && 'active']"
              type="button"
              @click="selectedRoadName = item.name"
            >
              <b>{{ index + 1 }}</b>
              <div class="segment-copy">
                <strong>{{ item.name }}</strong>
                <span :class="['level-tag', roadTone(item.tone)]">{{ item.level || '未知' }}</span>
              </div>
              <div class="progress">
                <i
                  :class="roadTone(item.tone)"
                  :style="{ width: `${Math.min(item.value || 0, 100)}%` }"
                ></i>
              </div>
              <em>{{ item.value || 0 }}%</em>
            </button>
          </div>
        </article>
      </aside>
    </section>

    <!-- AI 分析面板（可拖拽） -->
    <div
      v-if="aiPanelOpen"
      :class="['ai-panel', { dragging: aiDragging }]"
      :style="{ left: aiPanelPosition.x + 'px', top: aiPanelPosition.y + 'px' }"
      @pointerdown="onAiPanelPointerDown"
      @pointermove="onAiPanelPointerMove"
      @pointerup="onAiPanelPointerUp"
      @pointerleave="onAiPanelPointerUp"
    >
      <div class="ai-panel-header">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
        >
          <path d="M12 2a4 4 0 0 1 4 4c0 1.1-.4 2.1-1.2 2.8l-.8.8c-.9.9-1.4 2.1-1.4 3.4v1" />
          <circle cx="12" cy="19" r="2" />
          <path d="M17.5 6.5c1.7 1 2.5 2.8 2.5 5.5a8 8 0 1 1-16 0c0-2.7.8-4.5 2.5-5.5" />
        </svg>
        <span>{{ locale === 'zh' ? '智能交通助手' : 'Traffic Warning System' }}</span>
        <div class="ai-panel-actions">
          <button class="ai-action-btn" type="button" title="清空对话" @click="clearAiChat">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="3 6 5 6 21 6" />
              <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
              <path d="M10 11v6" /><path d="M14 11v6" />
            </svg>
          </button>
          <button class="ai-action-btn" type="button" title="关闭" @click="aiPanelOpen = false">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M18 6L6 18M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>

      <div class="ai-chat-body">
        <div v-for="(msg, i) in aiMessages" :key="i" :class="['ai-msg', msg.role]">
          <div class="ai-bubble">{{ msg.content }}</div>
        </div>
        <div v-if="aiLoading" class="ai-typing">
          <span></span><span></span><span></span>
        </div>
        <div v-if="aiError && !aiLoading" class="ai-error-inline">{{ aiError }}</div>
      </div>

      <div class="ai-input-area">
        <textarea
          v-model="aiInputText"
          :placeholder="'输入问题...'"
          :disabled="aiLoading"
          @keyup.enter.exact.prevent="sendAiMessage"
        ></textarea>
        <button :disabled="aiLoading || !aiInputText.trim()" @click="sendAiMessage">
          发送
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========== 页面布局 ========== */
.congestion-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-header h1 {
  margin: 0;
  color: #111827;
  font-size: 22px;
  font-weight: 900;
}

.page-header p {
  margin: 8px 0 0;
  color: #667085;
  font-size: 13px;
  font-weight: 700;
}

.refresh-btn {
  min-height: 36px;
  border: 1px solid rgba(75, 163, 237, 0.4);
  border-radius: 10px;
  padding: 0 16px;
  background: rgba(75, 163, 237, 0.08);
  color: var(--primary-strong);
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.16s ease;
}

.refresh-btn:hover:not(:disabled) {
  background: rgba(75, 163, 237, 0.18);
}

.refresh-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.header-btns { display: flex; gap: 10px; align-items: center; flex-shrink: 0; }

.cancel-btn {
  min-height: 36px; border: 1px solid #e2e8f0; border-radius: 10px;
  padding: 0 16px; background: #fff; color: #64748b;
  font-size: 13px; font-weight: 700; cursor: pointer; white-space: nowrap;
  transition: all .16s;
}
.cancel-btn:hover { background: #fef2f2; border-color: #fecaca; color: #dc2626; }

.error-tip {
  margin: 0;
  color: #b91c1c;
  font-size: 12px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  gap: 18px;
  align-items: stretch;
}

/* ========== 地图面板 ========== */
.map-panel,
.legend-panel,
.ranking-panel {
  border: 1px solid #d2dceb;
  border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  box-shadow: var(--shadow-float);
  transition:
    transform 0.26s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.26s ease,
    border-color 0.26s ease;
}

.legend-panel:hover,
.ranking-panel:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-float-hover);
  border-color: rgba(47, 124, 246, 0.38);
}

.map-panel {
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 0;
}

.legend-panel {
  padding: 14px;
  flex-shrink: 0;
}

.ranking-panel {
  padding: 14px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-header.compact {
  align-items: flex-start;
}

.panel-header h2 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  font-weight: 900;
}

.panel-header span {
  color: #98a2b3;
  font-size: 11px;
  font-weight: 700;
}

.selected-pill {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 900;
  white-space: nowrap;
}

.selected-pill.green,
.level-tag.green {
  background: #ecfdf3;
  color: #15803d;
}

.selected-pill.yellow,
.level-tag.yellow {
  background: #fffbeb;
  color: #b77900;
}

.selected-pill.orange,
.level-tag.orange {
  background: #fff7ed;
  color: #c2410c;
}

.selected-pill.red,
.level-tag.red {
  background: #fff1f2;
  color: #be123c;
}

/* ========== 模拟数据横幅 ========== */
.fallback-banner {
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
  font-size: 13px;
  font-weight: 700;
}

/* ========== 地图包装器 ========== */
.map-wrapper {
  position: relative;
  flex: 1;
  min-height: 640px;
  margin-top: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.leaflet-map-container {
  width: 100%;
  height: 100%;
}

.empty-map {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #98a2b3;
  font-size: 13px;
  font-weight: 700;
  background: #ffffff;
  pointer-events: none;
}

/* ========== 城市搜索悬浮层 ========== */
.city-search-overlay {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 1000;
  width: 320px;
}

.city-search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 10px;
  background: #ffffff;
  border: 1px solid #d7ddeb;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.city-search-box:focus-within {
  border-color: var(--primary);
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.15);
}

.search-icon {
  flex-shrink: 0;
}

.city-search-box input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-main);
  font-size: 14px;
}

.city-search-box input::placeholder {
  color: var(--text-muted);
}

.search-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid #e5e7eb;
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

.search-clear {
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  padding: 2px;
}

.search-clear:hover {
  color: var(--text-main);
}

.city-dropdown {
  margin: 6px 0 0;
  padding: 6px 0;
  list-style: none;
  background: #ffffff;
  border: 1px solid #e5e9f2;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  max-height: 240px;
  overflow-y: auto;
}

.city-dropdown li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text-main);
  transition: background 0.12s;
}

.city-dropdown li:hover {
  background: #f0f4ff;
}

.city-dropdown li span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-error {
  margin: 6px 0 0;
  padding: 0 4px;
  color: #98a2b3;
  font-size: 12px;
}

/* ========== AI 切换按钮 ========== */
.ai-toggle-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1000;
  display: flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: #ffffff;
  color: var(--primary);
  font-size: 13px;
  font-weight: 800;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  cursor: pointer;
  transition: all 0.2s;
}

.ai-toggle-btn:hover,
.ai-toggle-btn.active {
  background: linear-gradient(135deg, var(--primary), var(--accent));
  color: #ffffff;
}

/* ========== AI 分析面板 ========== */
.ai-panel {
  position: fixed;
  z-index: 5000;
  width: 400px;
  min-height: 320px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e5e9f2;
  border-radius: 14px;
  box-shadow: 0 18px 40px rgba(38, 47, 74, 0.16);
  user-select: none;
}

.ai-panel.dragging {
  box-shadow: 0 24px 48px rgba(38, 47, 74, 0.24);
  transition: box-shadow 0.1s;
}

.ai-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-soft);
  background: var(--surface-soft);
  border-radius: 14px 14px 0 0;
  cursor: move;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 900;
}

.ai-panel-header span {
  flex: 1;
}

.ai-panel-actions {
  display: flex;
  gap: 4px;
}

.ai-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}

.ai-action-btn:hover {
  background: #e5e7eb;
  color: var(--text-main);
}

.ai-chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 400px;
  user-select: text;
}

.ai-msg {
  display: flex;
}

.ai-msg.assistant {
  justify-content: flex-start;
}

.ai-msg.user {
  justify-content: flex-end;
}

.ai-bubble {
  max-width: 85%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-msg.assistant .ai-bubble {
  background: #eef9f7;
  color: var(--text-main);
  border: 1px solid #ccece6;
  border-bottom-left-radius: 3px;
}

.ai-msg.user .ai-bubble {
  background: var(--primary);
  color: #ffffff;
  border-bottom-right-radius: 3px;
}

.ai-typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0;
}

.ai-typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #98a2b3;
  animation: typing-bounce 1.2s ease-in-out infinite;
}

.ai-typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.ai-typing span:nth-child(3) {
  animation-delay: 0.4s;
}

.ai-error-inline {
  color: #b91c1c;
  font-size: 12px;
  font-weight: 700;
}

.ai-input-area {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  border-top: 1px solid var(--line-soft);
}

.ai-input-area textarea {
  flex: 1;
  min-height: 44px;
  max-height: 80px;
  resize: vertical;
  border-radius: 8px;
  border: 1px solid var(--line);
  padding: 6px 8px;
  font-size: 13px;
  font-family: inherit;
}

.ai-input-area textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.1);
}

.ai-input-area button {
  border: none;
  border-radius: 8px;
  padding: 6px 14px;
  background: var(--primary);
  color: #ffffff;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  white-space: nowrap;
}

.ai-input-area button:hover:not(:disabled) {
  background: var(--primary-strong);
}

.ai-input-area button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

/* ========== 图例 ========== */
.legend-list {
  margin-top: 10px;
  display: grid;
  gap: 8px;
}

.legend-item {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
}

.legend-item i {
  height: 8px;
  border-radius: 999px;
}

.legend-item i.green {
  background: #7fc56d;
}

.legend-item i.yellow {
  background: #f3d34a;
}

.legend-item i.orange {
  background: #f59e0b;
}

.legend-item i.red {
  background: #7f002b;
}

.legend-item strong {
  display: block;
  color: #111827;
  font-size: 13px;
  font-weight: 900;
}

.legend-item .range {
  margin-left: 4px;
  color: var(--text-muted);
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
}

.legend-item span {
  display: block;
  margin-top: 3px;
  color: #7a8190;
  font-size: 12px;
}

/* ========== 排行榜 ========== */
.segment-list {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  min-height: 0;
  max-height: 400px;
  overflow-y: auto;
  padding-right: 6px;
}

.segment-list::-webkit-scrollbar {
  width: 5px;
}

.segment-list::-webkit-scrollbar-track {
  background: transparent;
}

.segment-list::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 999px;
}

.segment-list::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}

.segment-item {
  width: 100%;
  border: 1px solid #d8e2f0;
  border-radius: 14px;
  padding: 10px;
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 44px;
  gap: 6px 10px;
  align-items: center;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  flex-shrink: 0;
}

.segment-item.active,
.segment-item:hover {
  border-color: #7fb0ff;
  background: #eaf2ff;
}

.segment-item > b {
  grid-row: span 2;
  color: #98a2b3;
  font-size: 18px;
  font-weight: 900;
}

.segment-copy {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.segment-copy strong {
  overflow: hidden;
  color: #111827;
  font-size: 13px;
  font-weight: 900;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.level-tag {
  flex-shrink: 0;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 900;
}

.progress {
  height: 8px;
  border-radius: 999px;
  background: #edf2f7;
  overflow: hidden;
}

.progress i {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.progress .green {
  background: #7fc56d;
}

.progress .yellow {
  background: #f3d34a;
}

.progress .orange {
  background: #f59e0b;
}

.progress .red {
  background: #7f002b;
}

.segment-item em {
  grid-column: 3;
  grid-row: 2;
  color: #111827;
  font-size: 16px;
  font-style: normal;
  font-weight: 900;
  text-align: right;
}

/* ========== 动画 ========== */
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes typing-bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  30% {
    transform: translateY(-6px);
    opacity: 1;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 1120px) {
  .content-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .side-column {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-rows: auto;
  }

  .city-search-overlay {
    width: 260px;
  }
}

@media (max-width: 760px) {
  .page-header,
  .panel-header,
  .side-column {
    align-items: flex-start;
    grid-template-columns: minmax(0, 1fr);
    flex-direction: column;
  }

  .map-wrapper {
    min-height: 180px;
  }

  .city-search-overlay {
    width: 220px;
  }

  .ai-panel {
    width: calc(100vw - 24px);
    left: 12px !important;
    max-height: 60vh;
  }

  .ai-toggle-btn {
    top: 56px;
  }
}
</style>
