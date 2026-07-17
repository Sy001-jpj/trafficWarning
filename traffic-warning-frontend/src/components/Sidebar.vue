<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { useLocale } from '../composables/useLocale.js'
import { useAuth } from '../composables/useAuth.js'

const { t } = useLocale()
const { isAdmin } = useAuth()
const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const CURRENT_USER_KEY = 'traffic_current_user'
const AVATAR_KEY = 'traffic_user_avatar'

const loadUser = () => {
  try { return JSON.parse(localStorage.getItem(CURRENT_USER_KEY) || '{}') } catch { return {} }
}

const userData = ref(loadUser())
const avatarDataUrl = ref(localStorage.getItem(AVATAR_KEY) || '')

const displayName = computed(() => userData.value.displayName || userData.value.username || t('sidebar.role'))
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase())

const onProfileUpdate = (e) => { userData.value = { ...userData.value, ...e.detail } }
const onAvatarUpdate = (e) => { avatarDataUrl.value = e.detail || '' }

onMounted(() => {
  window.addEventListener('profile-updated', onProfileUpdate)
  window.addEventListener('avatar-updated', onAvatarUpdate)
})
onUnmounted(() => {
  window.removeEventListener('profile-updated', onProfileUpdate)
  window.removeEventListener('avatar-updated', onAvatarUpdate)
})

const isActive = (name) => route.name === name

const goProfile = () => {
  router.push({ name: 'ProfileCenter' })
}

const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem(CURRENT_USER_KEY)
  router.push({ name: 'Login' })
}

const allNavItems = computed(() => [
  { name: 'Dashboard', path: '/', label: t('sidebar.dashboard'), adminOnly: false, icon: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>' },
  { name: 'UserManagement', path: '/users', label: t('sidebar.userManagement'), adminOnly: true, icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="9.5" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>' },
  { name: 'DeviceManagement', path: '/devices', label: t('sidebar.deviceManagement'), adminOnly: true, icon: '<rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8"/><path d="M12 17v4"/>' },
  { name: 'RealTimeMonitor', path: '/realtime', label: t('sidebar.realtimeMonitor'), adminOnly: false, icon: '<path d="M15 10l5-3v10l-5-3z"/><rect x="3" y="6" width="12" height="12" rx="2"/>' },
  { name: 'RoadStatus', path: '/road-status', label: t('sidebar.roadStatus'), adminOnly: false, icon: '<path d="M10.3 3.9L2.6 17.4A2 2 0 0 0 4.3 20h15.4a2 2 0 0 0 1.7-2.6L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4"/><path d="M12 17h.01"/>' },
  { name: 'CongestionRecognition', path: '/congestion', label: t('sidebar.congestion'), adminOnly: false, icon: '<path d="M3 6.5l6-3 6 3 6-3v14l-6 3-6-3-6 3z"/><path d="M9 3.5v14"/><path d="M15 6.5v14"/>' },
  { name: 'AiAssistant', path: '/assistant', label: t('sidebar.aiAssistant'), adminOnly: false, icon: '<path d="M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4z"/>' },
  { name: 'AdminPanel', path: '/admin', label: t('sidebar.adminPanel'), adminOnly: true, icon: '<rect x="3" y="3" width="18" height="18" rx="3"/><path d="M9 12h6"/><path d="M12 9v6"/>' },
  { name: 'Settings', path: '/settings', label: t('sidebar.settings'), adminOnly: false, icon: '<circle cx="12" cy="12" r="3"/><path d="M19.43 12.98c.04-.32.07-.65.07-.98s-.02-.66-.07-.98l2.11-1.65a.5.5 0 0 0 .12-.64l-2-3.46a.5.5 0 0 0-.6-.22l-2.49 1a7.25 7.25 0 0 0-1.69-.98L14.5 2.42A.5.5 0 0 0 14 2h-4a.5.5 0 0 0-.5.42L9.12 5.07c-.6.24-1.17.57-1.69.98l-2.49-1a.5.5 0 0 0-.6.22l-2 3.46a.5.5 0 0 0 .12.64l2.11 1.65c-.04.32-.07.65-.07.98s.02.66.07.98l-2.11 1.65a.5.5 0 0 0-.12.64l2 3.46c.14.24.43.34.68.22l2.4-.96c.52.4 1.08.73 1.69.98l.38 2.65c.04.24.25.42.5.42h4c.25 0 .46-.18.5-.42l.38-2.65c.6-.24 1.17-.57 1.69-.98l2.4.96c.25.1.54 0 .68-.22l2-3.46a.5.5 0 0 0-.12-.64l-2.1-1.65z"/>' },
])

const navItems = computed(() => allNavItems.value.filter(item => !item.adminOnly || isAdmin.value))
</script>

<template>
  <aside :class="['sidebar', collapsed && 'collapsed']">
    <div class="sidebar-head">
      <div class="brand-lockup">
        <span class="admin-avatar" aria-hidden="true">
          <img v-if="avatarDataUrl" :src="avatarDataUrl" class="avatar-img" alt="" />
          <span v-else>{{ userInitial }}</span>
        </span>
        <span class="brand-copy">
          <strong>{{ displayName }}</strong>
          <small>{{ t('sidebar.role', { role: displayName.value }) }}</small>
        </span>
      </div>
      <button
        class="collapse-btn"
        type="button"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        :title="collapsed ? '展开侧栏' : '收起侧栏'"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      >
        <span v-if="!collapsed" class="close-icon" aria-hidden="true"></span>
        <span v-else class="menu-icon" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </span>
      </button>
    </div>

    <nav class="nav-list" aria-label="业务导航">
      <RouterLink
        v-for="item in navItems"
        :key="item.name"
        :class="['nav-item', isActive(item.name) && 'active']"
        :to="item.path"
        :title="item.label"
      >
        <svg class="nav-icon" viewBox="0 0 24 24" aria-hidden="true" v-html="item.icon"></svg>
        <span class="item-label">{{ item.label }}</span>
      </RouterLink>
    </nav>

    <div class="sidebar-footer">
      <button class="footer-action" type="button" title="个人信息" @click="goProfile">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M20 21a8 8 0 0 0-16 0" />
          <circle cx="12" cy="7" r="4" />
        </svg>
        <span>{{ t('sidebar.profile') }}</span>
      </button>
      <button class="footer-action danger" type="button" title="退出" @click="logout">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="M16 17l5-5-5-5" />
          <path d="M21 12H9" />
        </svg>
        <span>{{ t('sidebar.logout') }}</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 208px;
  flex-shrink: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, rgba(238, 243, 251, 0.96) 100%);
  color: var(--text-main);
  border-right: 1px solid rgba(203, 213, 225, 0.78);
  box-shadow: 8px 0 34px rgba(29, 36, 51, 0.08);
  transition: width 0.22s ease;
}

.sidebar-head {
  min-height: 86px;
  padding: 16px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid rgba(203, 213, 225, 0.78);
}

.brand-lockup {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.admin-avatar {
  width: 38px;
  height: 38px;
  border-radius: 999px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary), var(--accent));
  color: #ffffff;
  font-size: 16px;
  font-weight: 900;
  box-shadow: 0 8px 20px rgba(47, 124, 246, 0.22);
  overflow: hidden;
}
.admin-avatar .avatar-img {
  width: 100%; height: 100%; object-fit: cover;
}

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.16;
}

.brand-copy strong {
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 0;
  white-space: nowrap;
  color: var(--text-main);
}

.brand-copy small {
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}

.collapse-btn:hover {
  background: var(--surface-soft);
  color: var(--text-main);
}

.close-icon {
  position: relative;
  width: 20px;
  height: 20px;
}

.close-icon::before,
.close-icon::after {
  content: '';
  position: absolute;
  top: 11px;
  left: 2px;
  width: 16px;
  height: 2px;
  border-radius: 999px;
  background: currentColor;
}

.close-icon::before {
  transform: rotate(45deg);
}

.close-icon::after {
  transform: rotate(-45deg);
}

.menu-icon {
  width: 24px;
  display: inline-flex;
  flex-direction: column;
  gap: 5px;
}

.menu-icon span {
  height: 2px;
  border-radius: 999px;
  background: currentColor;
}

.nav-list {
  flex: 1;
  min-height: 0;
  padding: 22px 10px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.nav-item {
  min-height: 42px;
  padding: 0 16px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-secondary);
  text-decoration: none;
  border: 1px solid transparent;
  transition: background 0.18s ease, color 0.18s ease, box-shadow 0.18s ease;
}

.nav-item:hover {
  background: #e9f1ff;
  color: var(--primary);
}

.nav-item.active {
  background: linear-gradient(135deg, #dce8ff, #f3e6ff);
  color: var(--primary-strong);
  font-weight: 700;
  box-shadow: inset 3px 0 0 var(--primary), 0 10px 22px rgba(47, 124, 246, 0.12);
}

.nav-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2.2;
}

.item-label {
  min-width: 0;
  overflow: hidden;
  color: inherit;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-footer {
  min-height: 116px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid rgba(203, 213, 225, 0.78);
  background: rgba(226, 233, 245, 0.82);
}

.footer-action {
  width: 100%;
  min-height: 40px;
  border: 1px solid transparent;
  border-radius: 14px;
  padding: 0 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.68);
  color: inherit;
  font-size: 13px;
  font-weight: 800;
  cursor: pointer;
  transition: background 0.18s ease, border-color 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

.footer-action:hover {
  border-color: rgba(47, 124, 246, 0.28);
  background: #e9f1ff;
  color: var(--primary-strong);
  transform: translateY(-1px);
}

.footer-action.danger:hover {
  border-color: #fecdd3;
  background: #fff1f2;
  color: #be123c;
}

.footer-action svg {
  width: 17px;
  height: 17px;
  flex-shrink: 0;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.sidebar.collapsed {
  width: 68px;
}

.collapsed .sidebar-head {
  min-height: 90px;
  padding: 16px 10px;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
}

.collapsed .brand-lockup {
  justify-content: center;
}

.collapsed .admin-avatar {
  width: 36px;
  height: 36px;
}

.collapsed .brand-copy {
  display: none;
}

.collapsed .nav-list {
  padding: 22px 10px 16px;
  align-items: center;
  gap: 12px;
}

.collapsed .nav-item {
  width: 42px;
  min-height: 42px;
  padding: 0;
  justify-content: center;
  border-radius: 12px;
}

.collapsed .nav-item.active {
  width: 48px;
  min-height: 48px;
}

.collapsed .nav-icon {
  width: 21px;
  height: 21px;
}

.collapsed .item-label,
.collapsed .footer-action span {
  display: none;
}

.collapsed .sidebar-footer {
  min-height: 108px;
  padding: 12px 10px;
  justify-content: center;
}

.collapsed .footer-action {
  min-height: 42px;
  padding: 0;
  justify-content: center;
}

@media (max-width: 860px) {
  .sidebar,
  .sidebar.collapsed {
    width: 100%;
    min-height: auto;
  }

  .sidebar-head,
  .collapsed .sidebar-head {
    min-height: 84px;
    padding: 14px 18px;
    flex-direction: row;
    justify-content: space-between;
  }

  .admin-avatar,
  .collapsed .admin-avatar {
    width: 48px;
    height: 48px;
  }

  .brand-copy,
  .collapsed .brand-copy {
    display: flex;
  }

  .brand-copy strong {
    font-size: 20px;
  }

  .brand-copy small {
    font-size: 14px;
  }

  .nav-list,
  .collapsed .nav-list {
    padding: 12px;
    flex-direction: row;
    align-items: stretch;
    gap: 10px;
    overflow-x: auto;
  }

  .nav-item,
  .collapsed .nav-item,
  .collapsed .nav-item.active {
    width: auto;
    min-height: 48px;
    padding: 0 14px;
    gap: 8px;
    border-radius: 10px;
    flex-shrink: 0;
  }

  .nav-icon,
  .collapsed .nav-icon {
    width: 22px;
    height: 22px;
  }

  .item-label,
  .collapsed .item-label {
    display: inline;
    font-size: 14px;
  }

  .sidebar-footer {
    display: none;
  }
}
</style>
