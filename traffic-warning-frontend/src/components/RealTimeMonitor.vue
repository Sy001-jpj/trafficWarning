<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'

defineOptions({ name: 'RealTimeMonitor' })

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const monitors = ref([])
const loading = ref(false)
const errorMessage = ref('')
const selectedId = ref(null)
const activeCameraId = ref(null)  // Camera actually streaming; null = none
const videoRef = ref(null)        // <video> element reference
const showAiOverlay = ref(false)  // Toggle YOLO detection overlay

const selectedMonitor = computed(() => monitors.value.find((m) => m.id === selectedId.value) || null)
const activeMonitor = computed(() => monitors.value.find((m) => m.id === activeCameraId.value) || null)

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

const normalizeMonitor = (monitor) => ({
  ...monitor,
  motorCount: monitor.motorCount ?? 0,
  nonMotorCount: monitor.nonMotorCount ?? 0,
  avgSpeed: monitor.avgSpeed ?? 0,
  events: Array.isArray(monitor.events) ? monitor.events : [],
  lastUpdate: formatDateTime(monitor.lastUpdate || monitor.analysisTime),
  videoPath: monitor.videoPath || ''
})

const requestApi = async (path, options = {}) => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  if (!response.ok) throw new Error('服务器连接失败')
  const result = await response.json()
  if (result.code !== 200) throw new Error(result.message || '操作失败')
  return result.data
}

const fetchMonitors = async () => {
  try {
    loading.value = true
    const data = await requestApi('/monitor/devices')
    monitors.value = data.map(normalizeMonitor)
    if (!selectedId.value || !monitors.value.some((m) => m.id === selectedId.value)) {
      selectedId.value = monitors.value[0]?.id || null
    }
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}

const getLevelInfo = (avgSpeed) => {
  if (avgSpeed >= 35) return { level: 'I', label: '畅通', color: '#16a34a' }
  else if (avgSpeed >= 25) return { level: 'II', label: '基本通畅', color: '#22c55e' }
  else if (avgSpeed >= 15) return { level: 'III', label: '轻度拥堵', color: '#facc15' }
  else if (avgSpeed >= 10) return { level: 'IV', label: '中度拥堵', color: '#f97316' }
  return { level: 'V', label: '严重拥堵', color: '#ef4444' }
}

const selectedEvents = computed(() => {
  const monitor = activeMonitor.value || selectedMonitor.value
  if (!monitor?.events?.length) return []
  return monitor.events.map((event, index) => ({
    id: `${monitor.id}-${index}`,
    type: event,
    time: monitor.lastUpdate,
    level: index === 0 ? '高' : '中'
  }))
})

const selectMonitor = (m) => {
  selectedId.value = m.id
  // If another camera is active, switch to the newly selected device
  if (activeCameraId.value && activeCameraId.value !== m.id) {
    stopCamera()
    // Auto-start after DOM updates
    nextTick().then(() => startCamera())
  }
}

const startCamera = async () => {
  if (!selectedMonitor.value) return
  activeCameraId.value = selectedMonitor.value.id
  // Wait for <video> element to render, then explicitly play
  await nextTick()
  if (videoRef.value) {
    videoRef.value.load()
    videoRef.value.play().catch((e) => console.warn('Video autoplay failed:', e.message))
  }
}

const stopCamera = () => {
  if (videoRef.value) {
    videoRef.value.pause()
  }
  activeCameraId.value = null
  showAiOverlay.value = false
}

const aiVideoPath = computed(() => {
  if (!activeMonitor.value?.videoPath) return ''
  return activeMonitor.value.videoPath.replace(/\.\w+$/, '_ai.webm')
})

const videoSrc = computed(() => {
  if (!activeMonitor.value?.videoPath) return ''
  if (showAiOverlay.value && aiVideoPath.value) {
    return '/videos/' + aiVideoPath.value
  }
  return '/videos/' + activeMonitor.value.videoPath
})

const toggleAiOverlay = () => {
  const video = videoRef.value
  const currentTime = video ? video.currentTime : 0
  const wasPaused = video ? video.paused : true
  showAiOverlay.value = !showAiOverlay.value
  nextTick().then(() => {
    if (videoRef.value) {
      videoRef.value.currentTime = currentTime
      videoRef.value.play().catch(() => {})
    }
  })
}

// ── Trend Chart ──
const trendData = ref([])
const showTrend = ref(false)
let trendCanvas = null

const fetchHistory = async () => {
  if (!selectedId.value) return
  try {
    const data = await requestApi(`/monitor/devices/${selectedId.value}/history?minutes=60`)
    trendData.value = (data || []).reverse()
    await nextTick()
    drawTrendChart()
  } catch { trendData.value = [] }
}

const toggleTrend = async () => {
  showTrend.value = !showTrend.value
  if (showTrend.value) {
    await fetchHistory()
    await nextTick()
    const canvas = document.getElementById('trend-canvas')
    if (canvas) { trendCanvas = canvas; drawTrendChart() }
  }
}

const drawTrendChart = () => {
  if (!trendCanvas || !trendData.value.length) return
  const canvas = trendCanvas
  const ctx = canvas.getContext('2d')
  const W = canvas.width
  const H = canvas.height
  ctx.clearRect(0, 0, W, H)

  const data = trendData.value
  const speeds = data.map((d) => d.avgSpeed || 0)
  const maxSpeed = Math.max(...speeds, 5)
  const minSpeed = Math.min(...speeds, 0)
  const range = maxSpeed - minSpeed || 1
  const pad = { top: 20, right: 16, bottom: 28, left: 40 }
  const pw = W - pad.left - pad.right
  const ph = H - pad.top - pad.bottom

  // Grid lines
  ctx.strokeStyle = '#d1d5db'; ctx.lineWidth = 1
  ctx.beginPath(); ctx.moveTo(pad.left, pad.top); ctx.lineTo(pad.left, H - pad.bottom); ctx.lineTo(W - pad.right, H - pad.bottom); ctx.stroke()
  ctx.strokeStyle = '#f0f0f0'
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + (ph / 4) * i
    const speed = maxSpeed - (range / 4) * i
    ctx.beginPath(); ctx.moveTo(pad.left, y); ctx.lineTo(W - pad.right, y); ctx.stroke()
    ctx.fillStyle = '#6b7280'; ctx.font = '10px sans-serif'; ctx.textAlign = 'right'
    ctx.fillText(Math.round(speed) + '', pad.left - 6, y + 4)
  }
  // Speed line
  if (data.length > 1) {
    ctx.strokeStyle = '#4BA3ED'; ctx.lineWidth = 2; ctx.beginPath()
    data.forEach((d, i) => {
      const x = pad.left + (pw / (data.length - 1)) * i
      const y = pad.top + ph - ((d.avgSpeed - minSpeed) / range) * ph
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
    })
    ctx.stroke()
    data.forEach((d, i) => {
      const x = pad.left + (pw / (data.length - 1)) * i
      const y = pad.top + ph - ((d.avgSpeed - minSpeed) / range) * ph
      ctx.fillStyle = '#4BA3ED'; ctx.beginPath(); ctx.arc(x, y, 3, 0, Math.PI * 2); ctx.fill()
    })
  }
  // X-axis labels
  [0, Math.floor((data.length - 1) / 2), data.length - 1].forEach((i) => {
    if (i < data.length && data[i]) {
      const x = pad.left + (pw / Math.max(data.length - 1, 1)) * i
      ctx.fillStyle = '#6b7280'; ctx.font = '9px sans-serif'; ctx.textAlign = 'center'
      ctx.fillText(formatDateTime(data[i].analysisTime || data[i].lastUpdate).slice(11, 16), x, H - pad.bottom + 16)
    }
  })
}

let pollTimer = null
onMounted(() => { fetchMonitors(); pollTimer = setInterval(fetchMonitors, 15000) })
onUnmounted(() => { if (pollTimer) clearInterval(pollTimer) })
</script>

<template>
  <div class="page">
    <h2 class="page-title">实时监控</h2>
    <p class="page-desc">统一管理所有监控路口的实时画面、 AI 检测结果、交通事件记录与设备状态。</p>

    <section class="layout">
      <!-- ═══ LEFT: Video / Snapshot ═══ -->
      <div class="left scroll-reveal">
        <!-- Camera active: show video panel -->
        <div v-if="activeMonitor" class="video-panel">
          <div class="video-header">
            <div class="title">
              <span class="text">智慧交通监控预警</span>
              <span class="device-name">{{ activeMonitor.name }}</span>
              <span class="live-dot"></span>
            </div>
            <div class="video-actions">
              <button
                class="action-btn ai-btn" :class="{ active: showAiOverlay }"
                type="button" @click="toggleAiOverlay"
                :title="showAiOverlay ? '关闭AI检测标注' : '开启AI检测标注'"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="9" cy="9" r="2"/><path d="M21 15l-5-5L5 21"/></svg>
                {{ showAiOverlay ? '关闭AI' : 'AI检测' }}
              </button>
              <button class="action-btn stop-btn" type="button" @click="stopCamera">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><rect x="4" y="4" width="16" height="16" rx="2"/></svg>
                关闭摄像头
              </button>
              <button class="action-btn ghost-btn" type="button" :disabled="loading" @click="fetchMonitors">
                刷新
              </button>
            </div>
          </div>

          <div class="video-area">
            <video
              v-if="activeMonitor.videoPath"
              ref="videoRef"
              :key="activeMonitor.id + (showAiOverlay ? '-ai' : '')"
              class="real-video"
              :src="videoSrc"
              autoplay
              loop
              muted
              playsinline
            ></video>
            <div v-else class="video-placeholder">
              <div class="road-scene">
                <span class="lane"></span>
                <span class="lane second"></span>
                <span class="car car-a"></span>
                <span class="car car-b"></span>
                <span class="car car-c"></span>
                <div class="detect-box"><strong>AI 检测中...</strong></div>
              </div>
            </div>
            <div class="overlay-top">
              汽车：{{ activeMonitor.motorCount }} 辆，平均速度 = {{ activeMonitor.avgSpeed }} km/h
              <br />
              非机动车：{{ activeMonitor.nonMotorCount }} 辆：正常
            </div>
          </div>

          <div class="video-footer">
            <div class="metric">
              <div class="label">拥堵等级</div>
              <div class="value">
                <span class="level-tag" :style="{ background: getLevelInfo(activeMonitor.avgSpeed).color + '22', color: getLevelInfo(activeMonitor.avgSpeed).color }">
                  等级 {{ getLevelInfo(activeMonitor.avgSpeed).level }} · {{ getLevelInfo(activeMonitor.avgSpeed).label }}
                </span>
              </div>
            </div>
            <div class="metric">
              <div class="label">当前车辆数</div>
              <div class="value">{{ activeMonitor.motorCount + activeMonitor.nonMotorCount }} 辆</div>
            </div>
            <div class="metric">
              <div class="label">平均速度</div>
              <div class="value">{{ activeMonitor.avgSpeed }} km/h</div>
            </div>
            <div class="metric full trend-metric">
              <button class="trend-toggle" type="button" @click="toggleTrend">
                历史趋势{{ showTrend ? '▲' : '▼' }}
              </button>
              <div v-if="showTrend" class="trend-chart-wrap">
                <canvas id="trend-canvas" class="trend-canvas" width="640" height="240"></canvas>
                <div v-if="!trendData.length" class="trend-empty">暂无历史数据</div>
              </div>
            </div>
          </div>
        </div>

        <!-- Camera not active: idle state -->
        <div v-else class="camera-idle">
          <div class="idle-icon">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M15 10l5-3v10l-5-3z"/><rect x="3" y="6" width="12" height="12" rx="2"/>
            </svg>
          </div>
          <template v-if="selectedMonitor">
            <p class="idle-device-name">{{ selectedMonitor.name }}</p>
            <template v-if="selectedMonitor.status === '在线'">
              <p class="idle-hint">设备已选中，点击右侧「启动摄像头」查看实时画面</p>
            </template>
            <template v-else>
              <p class="idle-offline">设备已离线</p>
              <p class="idle-hint">该设备当前处于离线状态，无法启动摄像头</p>
            </template>
          </template>
          <template v-else>
            <p class="idle-hint">请从右侧列表选择一个监控路口，然后启动摄像头查看实时画面</p>
          </template>
        </div>
      </div>

      <!-- ═══ RIGHT: Events + Device List ═══ -->
      <div class="right">
        <section class="side-card event-card scroll-reveal">
          <div class="side-card-header">
            <h3>交通事件记录</h3>
            <span v-if="activeMonitor">{{ activeMonitor.lastUpdate }}</span>
          </div>
          <div v-if="errorMessage" class="error-tip">{{ errorMessage }}</div>
          <div class="event-list">
            <article v-for="event in selectedEvents" :key="event.id" class="event-record">
              <div class="record-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M10.3 3.9L2.6 17.4A2 2 0 0 0 4.3 20h15.4a2 2 0 0 0 1.7-2.6L13.7 3.9a2 2 0 0 0-3.4 0z" />
                  <path d="M12 9v4" /><path d="M12 17h.01" />
                </svg>
              </div>
              <div class="record-copy">
                <strong>{{ event.type }}</strong>
                <span>{{ event.time }}</span>
              </div>
              <span :class="['record-level', event.level === '高' && 'high']">{{ event.level }}</span>
            </article>
            <div v-if="!selectedEvents.length && selectedMonitor" class="event-empty">{{ locale === 'zh' ? '暂无交通事件记录：' : 'No traffic event records available:' }}</div>
          </div>
        </section>

        <section class="side-card device-card scroll-reveal">
          <div class="side-card-header">
            <h3>监控设备列表</h3>
            <button
              v-if="selectedMonitor && selectedMonitor.status === '在线' && activeCameraId !== selectedMonitor.id"
              class="action-btn start-btn-compact" type="button" @click="startCamera"
            >
              <svg width="13" height="13" viewBox="0 0 24 24" fill="currentColor"><polygon points="5,3 19,12 5,21"/></svg>
              启动摄像头
            </button>
            <span v-else-if="selectedMonitor && selectedMonitor.status !== '在线'" class="offline-tag">设备已离线</span>
          </div>
          <div class="device-list">
            <div v-if="!monitors.length" class="event-empty">
              {{ loading ? '加载中...' : '暂无监控设备' }}
            </div>
            <div class="device-scroll">
              <button v-for="m in monitors" :key="m.id" class="device-item" :class="{ active: selectedId === m.id, streaming: activeCameraId === m.id }" type="button" @click="selectMonitor(m)">
                <svg class="device-cam" viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M15 10l5-3v10l-5-3z"/><rect x="3" y="6" width="12" height="12" rx="2"/>
                </svg>
                <span class="device-copy">
                  <strong>{{ m.name }}</strong>
                  <small>{{ locale === 'zh' ? m.location : 'Unknown Location' }}</small>
                  <em :class="{ offline: m.status !== '在线' ? 'offline' : '' }">{{ m.status }}</em>
                </span>
                <span class="device-level" :style="{ background: getLevelInfo(m.avgSpeed).color + '20', color: getLevelInfo(m.avgSpeed).color }">
                  {{ getLevelInfo(m.avgSpeed).level }}
                </span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.page-title { margin: 0; color: var(--text-main); font-size: 22px; font-weight: 900; }
.page-desc { margin: 0; color: var(--text-secondary); font-size: 13px; font-weight: 700; }

.layout { display: grid; grid-template-columns: minmax(0, 2.25fr) minmax(330px, 0.95fr); gap: 18px; align-items: stretch; }

.left, .side-card {
  border: 1px solid #d2dceb; border-radius: 18px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  box-shadow: var(--shadow-float);
  transition: transform 0.26s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.26s ease, border-color 0.26s ease;
}
.side-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-float-hover); border-color: rgba(47, 124, 246, 0.38); }

.left { min-height: 620px; padding: 18px; display: flex; flex-direction: column; }
.video-panel { flex: 1; min-height: 0; display: flex; flex-direction: column; gap: 14px; }

.video-header, .side-card-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.video-header .title { min-width: 0; display: flex; align-items: center; gap: 12px; }
.video-header .text, .side-card-header h3 { margin: 0; color: var(--text-main); font-size: 17px; font-weight: 900; }

.device-name { padding: 4px 10px; border-radius: 999px; background: rgba(75, 163, 237, 0.16); color: var(--primary-strong); font-size: 12px; font-weight: 800; }
.video-actions { display: flex; align-items: center; gap: 8px; }

.action-btn { height: 30px; border: 1px solid var(--line-soft); border-radius: 8px; padding: 0 12px; font-size: 12px; font-weight: 700; cursor: pointer; white-space: nowrap; }
.ghost-btn { background: #ffffff; color: var(--text-secondary); }
.ghost-btn:disabled { cursor: not-allowed; opacity: 0.58; }

.video-area { position: relative; flex: 1; min-height: 430px; border: 1px solid #0f172a; border-radius: 16px; overflow: hidden; background: #000000; }
.real-video { width: 100%; height: 100%; object-fit: cover; display: block; }
.video-placeholder { width: 100%; height: 100%; padding: 0 11%; background: #000000; }

.road-scene {
  position: relative; width: 100%; height: 100%; overflow: hidden;
  background:
    linear-gradient(90deg, rgba(0,0,0,0.45) 0 10%, transparent 10% 90%, rgba(0,0,0,0.5) 90%),
    linear-gradient(180deg, rgba(255,255,255,0.24), transparent 42%),
    linear-gradient(90deg, #5d646d 0 42%, #7f8790 42% 58%, #59616b 58% 100%);
}
.lane, .lane.second { position: absolute; top: 0; bottom: 0; width: 2px; background: repeating-linear-gradient(180deg, rgba(255,255,255,0.7) 0 28px, transparent 28px 58px); }
.lane { left: 43%; } .lane.second { left: 58%; }
.car, .car::before, .car::after { position: absolute; border-radius: 8px; }
.car { width: 54px; height: 88px; background: #f8fafc; box-shadow: 0 12px 22px rgba(0,0,0,0.32); }
.car::before, .car::after { content: ''; left: 8px; right: 8px; height: 16px; background: rgba(14,165,233,0.28); }
.car::before { top: 10px; } .car::after { bottom: 10px; }
.car-a { left: 50%; top: 30%; } .car-b { left: 34%; top: 58%; transform: scale(0.86); } .car-c { right: 18%; top: 25%; transform: scale(0.78); }

.detect-box { position: absolute; left: 47%; top: 28%; width: 92px; height: 116px; border: 3px solid #243cff; color: #ffffff; }
.detect-box strong { position: absolute; top: -30px; left: 0; padding: 3px 7px; background: rgba(36,60,255,0.82); font-size: 13px; white-space: nowrap; }

.overlay-top {
  position: absolute; left: 50%; bottom: 18px; z-index: 3; transform: translateX(-50%);
  max-width: calc(100% - 32px); padding: 7px 12px; border-radius: 6px;
  background: rgba(0,0,0,0.68); color: #f9fafb; font-size: 11px; line-height: 1.5; text-align: center; white-space: nowrap;
}

.video-footer { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.video-footer .metric { min-height: 66px; padding: 12px; border: 1px solid #d2dceb; border-radius: 14px; background: #eaf2ff; }
.metric .label { margin-bottom: 6px; color: var(--text-secondary); font-size: 12px; font-weight: 700; }
.metric .value { color: var(--text-main); font-size: 13px; font-weight: 800; }
.level-tag { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 12px; }
.video-empty, .event-empty { color: var(--text-muted); font-size: 13px; padding: 16px; }

.right { min-height: 620px; display: grid; grid-template-rows: minmax(180px, 0.78fr) minmax(300px, 1.22fr); gap: 14px; }
.side-card { min-height: 0; padding: 16px; display: flex; flex-direction: column; }
.side-card-header span { color: var(--text-secondary); font-size: 12px; font-weight: 800; }

.error-tip { margin: 8px 0 0; color: #b91c1c; font-size: 12px; }
.event-list { min-height: 0; margin-top: 14px; display: flex; flex-direction: column; gap: 10px; overflow-y: auto; }
.device-list { min-height: 0; margin-top: 14px; display: flex; flex-direction: column; }
.device-scroll { display: flex; flex-direction: column; gap: 10px; max-height: 440px; overflow-y: auto; padding-right: 4px; }
.device-scroll::-webkit-scrollbar { width: 5px; }
.device-scroll::-webkit-scrollbar-thumb { border-radius: 999px; background: #cbd5e1; }

.event-record { min-height: 58px; padding: 10px; border: 1px solid #eef1f5; border-radius: 8px; display: grid; grid-template-columns: 34px minmax(0,1fr) auto; align-items: center; gap: 10px; background: #fffafa; }
.record-icon { width: 34px; height: 34px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; background: #fff1f2; color: #f97316; }
.record-icon svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 2.1; }
.record-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.record-copy strong { overflow: hidden; color: var(--text-main); font-size: 13px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.record-copy span { color: var(--text-muted); font-size: 11px; }
.record-level { padding: 3px 7px; border-radius: 999px; background: #fff7ed; color: #f97316; font-size: 11px; font-weight: 800; }
.record-level.high { background: #fee2e2; color: #dc2626; }

.device-item { width: 100%; min-height: 78px; border: 1px solid transparent; border-radius: 8px; padding: 12px; display: grid; grid-template-columns: 20px minmax(0,1fr) auto; align-items: center; gap: 10px; background: #ffffff; color: inherit; text-align: left; cursor: pointer; }
.device-item:hover { background: #f8fbff; }
.device-item.active { border-color: rgba(75,163,237,0.5); background: rgba(75,163,237,0.06); box-shadow: inset 0 0 0 1px rgba(75,163,237,0.12); }
.device-cam { width: 20px; height: 20px; flex-shrink: 0; fill: none; stroke: var(--text-muted); stroke-linecap: round; stroke-linejoin: round; stroke-width: 2; }
.device-item.active .device-cam { stroke: var(--primary-strong); }
.device-copy { min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.device-copy strong { overflow: hidden; color: var(--text-main); font-size: 13px; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.device-copy small { overflow: hidden; color: var(--text-muted); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.device-copy em { position: relative; padding-left: 14px; color: #22c55e; font-size: 12px; font-style: normal; font-weight: 800; }
.device-copy em::before { content: ''; position: absolute; left: 0; top: 5px; width: 8px; height: 8px; border-radius: 999px; background: currentColor; }
.device-copy em.offline { color: #ef4444; }
.device-level { min-width: 30px; height: 30px; border-radius: 999px; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 900; }

/* ── Trend chart ── */
.trend-metric { display: flex; flex-direction: column; }
.trend-toggle {
  align-self: flex-start; height: 30px; border: 1px solid rgba(75,163,237,0.4);
  border-radius: 10px; padding: 0 14px; background: rgba(75,163,237,0.08);
  color: var(--primary-strong); font-size: 12px; font-weight: 700; cursor: pointer;
  transition: background 0.16s ease;
}
.trend-toggle:hover { background: rgba(75,163,237,0.18); }
.trend-chart-wrap { margin-top: 10px; width: 100%; }
.trend-canvas { width: 100%; height: auto; border: 1px solid #eef1f5; border-radius: 6px; }
.trend-empty { color: var(--text-muted); font-size: 12px; text-align: center; padding: 12px; }
.video-footer .metric.full { grid-column: 1 / -1; }

/* ── Live indicator ── */
.live-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: #ef4444; box-shadow: 0 0 6px rgba(239,68,68,0.6);
  animation: live-pulse 1.4s ease-in-out infinite;
}
@keyframes live-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.75); }
}

/* ── AI detection overlay ── */
.ai-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  object-fit: cover; opacity: 0.55; z-index: 2; pointer-events: none;
  animation: overlay-in 0.35s ease;
}
@keyframes overlay-in { from { opacity: 0; } to { opacity: 0.55; } }

/* ── AI toggle button ── */
.ai-btn {
  display: inline-flex; align-items: center; gap: 5px;
  background: #f0f9ff; color: #4BA3ED; border-color: #bae6fd;
}
.ai-btn.active { background: #4BA3ED; color: #fff; border-color: #4BA3ED; }
.ai-btn:hover { background: #e0f2fe; }
.ai-btn.active:hover { background: #3b93dd; }

/* ── Stop camera button ── */
.stop-btn {
  background: #fef2f2; color: #dc2626; border-color: #fecaca;
  display: inline-flex; align-items: center; gap: 5px;
}
.stop-btn:hover { background: #fee2e2; }

/* ── Idle / camera-not-started state ── */
.camera-idle {
  flex: 1; min-height: 580px; display: flex;
  flex-direction: column; align-items: center; justify-content: center;
  gap: 18px; text-align: center; padding: 40px 20px;
}
.idle-icon {
  width: 80px; height: 80px; border-radius: 50%;
  background: rgba(75,163,237,0.08); color: var(--primary-strong);
  display: flex; align-items: center; justify-content: center;
}
.idle-icon svg { width: 40px; height: 40px; fill: none; stroke: currentColor; stroke-width: 1.8; stroke-linecap: round; stroke-linejoin: round; }
.idle-device-name { margin: 0; color: var(--text-main); font-size: 18px; font-weight: 900; }
.idle-hint { margin: 0; color: var(--text-secondary); font-size: 13px; max-width: 300px; line-height: 1.6; }

/* ── Compact start button (in device card header) ── */
.start-btn-compact {
  display: inline-flex; align-items: center; gap: 5px;
  height: 28px; padding: 0 12px; border: none; border-radius: 8px;
  background: linear-gradient(135deg, #4BA3ED, #2F7CF6);
  color: #ffffff; font-size: 12px; font-weight: 700;
  cursor: pointer; white-space: nowrap;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.start-btn-compact:hover { transform: translateY(-1px); box-shadow: 0 3px 10px rgba(47,124,246,0.35); }

/* ── Offline tag (in device card header) ── */
.offline-tag {
  padding: 4px 10px; border-radius: 999px;
  background: #fef2f2; color: #dc2626;
  font-size: 12px; font-weight: 800; white-space: nowrap;
}

/* ── Offline message (idle state) ── */
.idle-offline {
  margin: 0; color: #dc2626; font-size: 16px; font-weight: 900;
}

/* ── Big start camera button (idle state, kept for reference) ── */
.start-camera-btn {
  display: inline-flex; align-items: center; gap: 8px;
  height: 44px; padding: 0 28px; border: none; border-radius: 12px;
  background: linear-gradient(135deg, #4BA3ED, #2F7CF6);
  color: #ffffff; font-size: 15px; font-weight: 800;
  cursor: pointer; box-shadow: 0 4px 14px rgba(47,124,246,0.35);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.start-camera-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(47,124,246,0.45); }
.start-camera-btn:active { transform: translateY(0); }

/* ── Device item: streaming (camera active) state ── */
.device-item.streaming {
  border-color: rgba(34,197,94,0.5);
  background: rgba(34,197,94,0.04);
  box-shadow: inset 0 0 0 1px rgba(34,197,94,0.15);
}
.device-item.streaming .device-cam { stroke: #22c55e; }

@media (max-width: 1120px) { .layout { grid-template-columns: minmax(0, 1fr); } .left, .right { min-height: auto; } .right { grid-template-rows: auto auto; } }
@media (max-width: 720px) { .left, .side-card { padding: 14px; } .video-header { align-items: flex-start; flex-direction: column; } .video-area { min-height: 300px; } .overlay-top { white-space: normal; } .video-footer { grid-template-columns: minmax(0, 1fr); } }
</style>
