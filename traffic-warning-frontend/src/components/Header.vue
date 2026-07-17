<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useTheme } from '../composables/useTheme.js'
import { useLocale } from '../composables/useLocale.js'

const { t } = useLocale()
const todoCount = ref(0)
const ALERT_STORAGE_KEY = 'traffic-warning-major-alert'
const ALERT_LIST_STORAGE_KEY = 'traffic-warning-major-alerts'
const router = useRouter()

const { theme, toggleTheme } = useTheme()

const showSettings = ref(false)
let hideTimer = null

const onSettingsEnter = () => {
  clearTimeout(hideTimer)
  showSettings.value = true
}
const onSettingsLeave = () => {
  hideTimer = setTimeout(() => { showSettings.value = false }, 200)
}

const syncMajorAlert = () => {
  try {
    const alerts = JSON.parse(localStorage.getItem(ALERT_LIST_STORAGE_KEY) || '[]')
    todoCount.value = Array.isArray(alerts) ? alerts.length : 0
  } catch {
    todoCount.value = localStorage.getItem(ALERT_STORAGE_KEY) === '1' ? 1 : 0
  }
}

const handleMajorAlert = () => {
  syncMajorAlert()
}

const clearMajorAlert = () => {
  localStorage.removeItem(ALERT_STORAGE_KEY)
  syncMajorAlert()
}

const goTodoMessages = () => {
  router.push({ name: 'TodoMessages' })
}
const goSettings = () => {
  showSettings.value = false
  router.push({ name: 'Settings' })
}

onMounted(() => {
  syncMajorAlert()
  window.addEventListener('major-traffic-alert', handleMajorAlert)
  window.addEventListener('major-traffic-alert-cleared', clearMajorAlert)
  window.addEventListener('storage', syncMajorAlert)
})

onUnmounted(() => {
  window.removeEventListener('major-traffic-alert', handleMajorAlert)
  window.removeEventListener('major-traffic-alert-cleared', clearMajorAlert)
  window.removeEventListener('storage', syncMajorAlert)
})
</script>

<template>
  <header class="header">
    <div class="header-left">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 48 48">
            <circle class="logo-ring" cx="24" cy="24" r="16" />
            <path class="logo-road" d="M24 5v38M5 24h38" />
            <circle class="logo-node" cx="24" cy="24" r="5" />
            <circle class="logo-dot" cx="24" cy="5" r="2.5" />
            <circle class="logo-dot" cx="43" cy="24" r="2.5" />
            <circle class="logo-dot" cx="24" cy="43" r="2.5" />
            <circle class="logo-dot" cx="5" cy="24" r="2.5" />
          </svg>
        </span>
        <span class="logo">RoadSense</span>
      </div>
      <label class="top-search">
        <input type="search" :placeholder="t('header.search')" />
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="11" cy="11" r="7" />
          <path d="m16.5 16.5 4 4" />
        </svg>
      </label>
    </div>

    <div class="header-right">
      <span class="service-state">
        <span class="state-dot"></span>
        {{ t('header.serviceStatus') }}
      </span>

      <button class="icon-button notify-button" type="button" :aria-label="t('header.notifications')" :title="t('header.notifications')" @click="goTodoMessages">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 7-3 7h18s-3 0-3-7" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>
        <span v-if="todoCount" class="notify-count" aria-hidden="true">{{ todoCount > 99 ? '99+' : todoCount }}</span>
      </button>

      <!-- 设置下拉 -->
      <div class="settings-wrapper" @mouseenter="onSettingsEnter" @mouseleave="onSettingsLeave">
        <button class="icon-button" type="button" aria-label="系统设置" title="系统设置">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" aria-hidden="true">
            <circle cx="12" cy="12" r="3" />
            <path d="M19.43 12.98c.04-.32.07-.65.07-.98s-.02-.66-.07-.98l2.11-1.65a.5.5 0 0 0 .12-.64l-2-3.46a.5.5 0 0 0-.6-.22l-2.49 1a7.25 7.25 0 0 0-1.69-.98L14.5 2.42A.5.5 0 0 0 14 2h-4a.5.5 0 0 0-.5.42L9.12 5.07c-.6.24-1.17.57-1.69.98l-2.49-1a.5.5 0 0 0-.6.22l-2 3.46a.5.5 0 0 0 .12.64l2.11 1.65c-.04.32-.07.65-.07.98s.02.66.07.98l-2.11 1.65a.5.5 0 0 0-.12.64l2 3.46c.14.24.43.34.68.22l2.4-.96c.52.4 1.08.73 1.69.98l.38 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.38-2.65c.6-.24 1.17-.57 1.69-.98l2.4.96c.25.1.54 0 .68-.22l2-3.46a.5.5 0 0 0-.12-.64l-2.1-1.65z" />
          </svg>
        </button>
        <transition name="drop">
          <div v-if="showSettings" class="settings-dropdown" @mouseenter="onSettingsEnter" @mouseleave="onSettingsLeave">
            <!-- 主题切换 -->
            <div class="dropdown-section">
              <span class="dropdown-label">{{ t('settings.appearance') }}</span>
              <div class="theme-toggle-row">
                <button
                  :class="['theme-opt', theme === 'light' && 'active']"
                  @click="toggleTheme"
                >
                  <span class="theme-opt-icon">☀️</span>
                  <span>{{ t('settings.lightName') }}</span>
                </button>
                <button
                  :class="['theme-opt', theme === 'dark' && 'active']"
                  @click="toggleTheme"
                >
                  <span class="theme-opt-icon">🌙</span>
                  <span>{{ t('settings.darkName') }}</span>
                </button>
              </div>
            </div>
            <!-- 更多设置 -->
            <div class="dropdown-section bottom">
              <button class="dropdown-link" @click="goSettings">
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.43 12.98c.04-.32.07-.65.07-.98s-.02-.66-.07-.98l2.11-1.65-2-3.46-2.49 1a7.25 7.25 0 0 0-1.69-.98L14.5 2.42A.5.5 0 0 0 14 2h-4a.5.5 0 0 0-.5.42L9.12 5.07c-.6.24-1.17.57-1.69.98l-2.49-1-2 3.46 2.11 1.65c-.04.32-.07.65-.07.98s.02.66.07.98l-2.11 1.65 2 3.46c.14.24.43.34.68.22l2.4-.96c.52.4 1.08.73 1.69.98l.38 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.38-2.65c.6-.24 1.17-.57 1.69-.98l2.4.96c.25.1.54 0 .68-.22l2-3.46-2.1-1.65z"/></svg>
                {{ t('sidebar.settings') }}
                <span class="arrow">→</span>
              </button>
            </div>
          </div>
        </transition>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid rgba(203, 213, 225, 0.78);
  box-shadow: 0 14px 34px rgba(29, 36, 51, 0.08);
  backdrop-filter: blur(14px);
  z-index: 20;
}

.header-left {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 40px;
}

.brand {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.brand-mark {
  position: relative;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.brand-mark svg {
  width: 100%;
  height: 100%;
  overflow: visible;
}

.logo-ring {
  fill: rgba(75, 163, 237, 0.12);
  stroke: rgba(75, 163, 237, 0.25);
  stroke-width: 2;
}

.logo-road {
  fill: none;
  stroke: var(--primary);
  stroke-linecap: round;
  stroke-width: 4;
}

.logo-node {
  fill: #ffffff;
  stroke: var(--primary);
  stroke-width: 2;
}

.logo-dot {
  fill: var(--accent);
}

.logo {
  overflow: hidden;
  color: var(--text-main);
  font-size: 24px;
  font-weight: 800;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-search {
  width: 280px;
  height: 40px;
  padding: 0 14px 0 18px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: #eef3fb;
  border: 1px solid #d2dced;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.top-search:focus-within {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(75, 163, 237, 0.14);
}

.top-search input {
  min-width: 0;
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-main);
  font-size: 14px;
  font-weight: 500;
}

.top-search input::placeholder {
  color: var(--text-muted);
}

.top-search svg {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  fill: none;
  stroke: var(--text-muted);
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.service-state {
  height: 30px;
  padding: 0 10px;
  border: 1px solid rgba(75, 163, 237, 0.3);
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: var(--primary-soft);
  color: var(--blue-600);
  font-size: 12px;
  font-weight: 700;
}

.state-dot {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.14);
}

.icon-button {
  position: relative;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.16s ease, color 0.16s ease;
}

.icon-button:hover {
  background: #e8f0ff;
  color: var(--primary-strong);
}

.icon-button svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
  overflow: visible;
  pointer-events: none;
}

.badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 24px;
  height: 24px;
  padding: 0 7px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #F47E8B;
  color: #ffffff;
  font-size: 15px;
  font-weight: 800;
  line-height: 1;
}

.notify-count {
  position: absolute;
  top: -4px;
  right: -3px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border: 2px solid #ffffff;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #F47E8B;
  color: #ffffff;
  font-size: 11px;
  font-weight: 900;
  line-height: 1;
  box-shadow: 0 0 0 3px rgba(244, 126, 139, 0.14);
}

/* ===== Settings Dropdown ===== */
.settings-wrapper {
  position: relative;
  z-index: 100;
}
.settings-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 240px;
  background: var(--surface, #fff);
  border: 1px solid var(--line-soft, #e2e8f0);
  border-radius: 16px;
  box-shadow:
    0 4px 16px rgba(15,23,42,.08),
    0 16px 40px rgba(15,23,42,.14);
  overflow: hidden;
}
.dropdown-section {
  padding: 16px;
}
.dropdown-section.bottom {
  border-top: 1px solid var(--line-soft, #f1f5f9);
  padding: 8px;
}
.dropdown-label {
  display: block;
  font-size: 11px;
  font-weight: 700;
  color: var(--text-muted, #94a3b8);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}
.theme-toggle-row {
  display: flex;
  gap: 8px;
}
.theme-opt {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 12px 8px;
  border: 2px solid var(--line-soft, #e2e8f0);
  border-radius: 14px;
  background: var(--surface-soft, #f8fafc);
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-secondary, #64748b);
  transition: all .18s ease;
}
.theme-opt:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}
.theme-opt.active {
  border-color: var(--primary, #4BA3ED);
  background: var(--primary-soft, #eff6ff);
  color: var(--primary-strong, #1e40af);
}
.theme-opt-icon {
  font-size: 22px;
}
.dropdown-link {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
}
.dropdown-link:hover {
  background: var(--surface-soft, #f1f5f9);
  color: var(--text-main, #0f172a);
}
.dropdown-link .arrow {
  margin-left: auto;
  color: var(--text-muted, #94a3b8);
}

/* Dropdown animation */
.drop-enter-active { transition: all .18s ease; }
.drop-leave-active { transition: all .12s ease; }
.drop-enter-from { opacity: 0; transform: translateY(-6px); }
.drop-leave-to   { opacity: 0; transform: translateY(-4px); }

@media (max-width: 760px) {
  .header {
    height: auto;
    padding: 12px 16px;
    gap: 12px;
  }

  .top-search,
  .service-state {
    display: none;
  }

  .header-right {
    gap: 8px;
  }
}
</style>
