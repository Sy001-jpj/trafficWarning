<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useLocale } from '../composables/useLocale.js'

const { locale } = useLocale()
const router = useRouter()
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const ALERT_STORAGE_KEY = 'traffic-warning-major-alert'
const ALERT_DETAIL_STORAGE_KEY = 'traffic-warning-major-alert-detail'
const ALERT_LIST_STORAGE_KEY = 'traffic-warning-major-alerts'

const alertDetails = ref([])

const normalizeText = (value) => String(value || '').trim().toLowerCase()
const textMatched = (left, right) => {
  const a = normalizeText(left)
  const b = normalizeText(right)
  return Boolean(a && b && (a.includes(b) || b.includes(a)))
}

const getAlertId = (alert) =>
  alert.id || `${alert.deviceId || 'device'}-${alert.eventType || 'event'}-${alert.time || ''}`

const isAlertForRecord = (alert, record) => {
  const deviceMatched =
    alert.deviceId && record.deviceId && normalizeText(alert.deviceId) === normalizeText(record.deviceId)
  const eventMatched = textMatched(record.eventType, alert.eventType)
  const cameraMatched = textMatched(record.cameraName, alert.deviceName)
  const roadMatched = textMatched(record.roadName, alert.location)
  const timeMatched =
    alert.time && record.time && String(record.time).slice(0, 16) === String(alert.time).slice(0, 16)

  return deviceMatched || (eventMatched && (cameraMatched || roadMatched || timeMatched))
}

const normalizeRecord = (record) => ({
  ...record,
  time: String(record.time || '').replace('T', ' ').slice(0, 19)
})

const requestApi = async (path) => {
  const response = await fetch(`${API_BASE_URL}${path}`)
  if (!response.ok) return []

  const result = await response.json()
  return result.code === 200 && Array.isArray(result.data) ? result.data : []
}

const syncStoredAlerts = (alerts) => {
  alertDetails.value = alerts
  localStorage.setItem(ALERT_LIST_STORAGE_KEY, JSON.stringify(alerts))
  if (!alerts.length) {
    localStorage.removeItem(ALERT_STORAGE_KEY)
    localStorage.removeItem(ALERT_DETAIL_STORAGE_KEY)
  }
  window.dispatchEvent(new CustomEvent('major-traffic-alert-cleared'))
}

const removeProcessedAlerts = async (alerts) => {
  const records = (await requestApi('/events')).map(normalizeRecord)
  if (!records.length) return alerts

  return alerts.filter((alert) => {
    const matchedRecord = records.find((record) => isAlertForRecord(alert, record))
    return !matchedRecord || matchedRecord.status !== '已处理'
  })
}

const loadAlertDetail = async () => {
  try {
    const alerts = JSON.parse(localStorage.getItem(ALERT_LIST_STORAGE_KEY) || '[]')
    if (Array.isArray(alerts) && alerts.length) {
      syncStoredAlerts(await removeProcessedAlerts(alerts))
      return
    }

    const legacyAlert = JSON.parse(localStorage.getItem(ALERT_DETAIL_STORAGE_KEY) || 'null')
    syncStoredAlerts(legacyAlert ? await removeProcessedAlerts([legacyAlert]) : [])
  } catch {
    syncStoredAlerts([])
  }
}

const todoMessages = computed(() => {
  return alertDetails.value.map((alert) => ({
    id: getAlertId(alert),
    deviceId: alert.deviceId || '',
    title: alert.source === 'auto' ? '系统自动检测重大交通异常' : '重大交通异常事件待处理',
    eventType: alert.eventType || '交通异常事件',
    deviceName: alert.deviceName || '未知监控点',
    location: alert.location || '未知路段',
    level: alert.level || '高',
    time: alert.time || '-'
  }))
})

const openMessage = (message) => {
  router.push({
    name: 'RoadStatus',
    query: {
      from: 'todo',
      alertId: message.id,
      deviceId: message.deviceId,
      eventType: message.eventType,
      deviceName: message.deviceName,
      location: message.location,
      time: message.time
    }
  })
}

onMounted(loadAlertDetail)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">待办消息</h2>
        <p class="page-desc">{{ locale === 'zh' ? '集中查看需要立即处理的重大交通异常事件。' : 'View major traffic anomaly events that require immediate attention.' }}</p>
      </div>
    </div>

    <section class="todo-panel scroll-reveal">
      <article v-if="!todoMessages.length" class="empty-card">
        <div class="empty-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M20 6 9 17l-5-5" />
          </svg>
        </div>
        <strong>暂无待办消息</strong>
        <span>当前没有重大交通异常事件需要处理。</span>
      </article>

      <button
        v-for="message in todoMessages"
        :key="message.id"
        class="todo-card"
        type="button"
        @click="openMessage(message)"
      >
        <span class="severity-dot"></span>
        <span class="todo-copy">
          <strong>{{ message.title }}</strong>
          <span>{{ message.deviceName }} · {{ message.location }}</span>
          <em>{{ message.eventType }} / {{ message.time }}</em>
        </span>
        <span class="level-tag">{{ message.level }}</span>
      </button>
    </section>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.page-title {
  margin: 0;
  color: var(--text-main);
  font-size: 20px;
  font-weight: 800;
}

.page-desc {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.todo-panel {
  border: 1px solid #d2dceb;
  border-radius: 18px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  box-shadow: var(--shadow-float);
  transition:
    transform 0.26s cubic-bezier(0.34, 1.56, 0.64, 1),
    box-shadow 0.26s ease,
    border-color 0.26s ease;
}

.todo-panel:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-float-hover);
  border-color: rgba(47, 124, 246, 0.38);
}

.todo-card {
  width: 100%;
  min-height: 86px;
  border: 1px solid rgba(244, 126, 139, 0.3);
  border-radius: 16px;
  padding: 14px 16px;
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  background: linear-gradient(135deg, #ffffff, #fff2f5);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease, box-shadow 0.16s ease;
}

.todo-card:hover {
  border-color: rgba(244, 126, 139, 0.6);
  background: #fff0f3;
  box-shadow: 0 12px 28px rgba(244, 126, 139, 0.12);
}

.severity-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #F47E8B;
  box-shadow: 0 0 0 5px rgba(244, 126, 139, 0.14);
}

.todo-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.todo-copy strong {
  color: var(--text-main);
  font-size: 15px;
  font-weight: 800;
}

.todo-copy span,
.todo-copy em {
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 12px;
  font-style: normal;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.level-tag {
  border-radius: 999px;
  padding: 4px 10px;
  background: rgba(244, 126, 139, 0.14);
  color: #be123c;
  font-size: 12px;
  font-weight: 900;
}

.empty-card {
  min-height: 220px;
  border: 1px dashed #d9e2ef;
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8px;
  color: var(--text-secondary);
}

.empty-card strong {
  color: var(--text-main);
  font-size: 15px;
}

.empty-card span {
  font-size: 13px;
}

.empty-icon {
  width: 42px;
  height: 42px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(113, 224, 180, 0.18);
  color: #15803d;
}

.empty-icon svg {
  width: 22px;
  height: 22px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.4;
}
</style>
