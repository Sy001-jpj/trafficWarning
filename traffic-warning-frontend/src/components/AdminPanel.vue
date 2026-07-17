<script setup>
import { useRouter } from 'vue-router'
import { useLocale } from '../composables/useLocale.js'

defineOptions({ name: 'AdminPanel' })

const router = useRouter()
const { locale } = useLocale()

const goTo = (name) => router.push({ name })

const adminFeatures = [
  {
    key: 'register-review',
    icon: '📋',
    title: locale.value === 'zh' ? '用户注册审核' : 'Registration Review',
    desc: locale.value === 'zh' ? '审核新用户的注册申请，通过或拒绝账号开通' : 'Review and approve or reject new user registration requests',
    route: 'RegisterReview'
  },
  {
    key: 'password-reset',
    icon: '🔑',
    title: locale.value === 'zh' ? '密码重置审核' : 'Password Reset Review',
    desc: locale.value === 'zh' ? '审核用户提交的密码重置请求，批准或拒绝新密码' : 'Review password reset requests from users, approve or reject',
    route: 'PasswordResetReview'
  },
  {
    key: 'user-management',
    icon: '👥',
    title: locale.value === 'zh' ? '用户管理' : 'User Management',
    desc: locale.value === 'zh' ? '管理系统用户账号与权限，支持增删改查' : 'Manage user accounts and permissions',
    route: 'UserManagement'
  },
  {
    key: 'device-management',
    icon: '📹',
    title: locale.value === 'zh' ? '设备信息管理' : 'Device Management',
    desc: locale.value === 'zh' ? '管理监控设备信息与运行状态' : 'Manage monitoring devices and operational status',
    route: 'DeviceManagement'
  },
]
</script>

<template>
  <div class="page">
    <div class="head">
      <div>
        <h2>{{ locale === 'zh' ? '管理功能' : 'Management' }}</h2>
        <p class="sub">{{ locale === 'zh' ? '系统管理员专用功能入口，包含用户审核、账号管理、设备管理等' : 'Administrator-only features including registration review, account management, and device management' }}</p>
      </div>
    </div>

    <div class="feature-grid">
      <button v-for="f in adminFeatures" :key="f.key" class="feature-card" @click="goTo(f.route)">
        <span class="feature-icon">{{ f.icon }}</span>
        <div class="feature-info">
          <strong>{{ f.title }}</strong>
          <p>{{ f.desc }}</p>
        </div>
        <span class="feature-arrow">→</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 20px; max-width: 720px; margin: 0 auto; }
.head h2 { margin: 0; font-size: 22px; font-weight: 900; color: var(--text-main); }
.sub { margin: 6px 0 0; font-size: 13px; color: var(--text-secondary); }

.feature-grid { display: flex; flex-direction: column; gap: 12px; }
.feature-card {
  display: flex; align-items: center; gap: 16px; width: 100%;
  padding: 20px 22px; border: 1px solid #d2dceb; border-radius: 18px;
  background: linear-gradient(180deg, #fff 0%, #f7fbff 100%);
  box-shadow: var(--shadow-float); cursor: pointer; text-align: left;
  transition: all .22s ease;
}
.feature-card:hover { transform: translateY(-2px); box-shadow: var(--shadow-float-hover); border-color: rgba(47,124,246,.38); }
.feature-icon { font-size: 34px; flex-shrink: 0; }
.feature-info { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.feature-info strong { font-size: 16px; font-weight: 800; color: #0f172a; }
.feature-info p { margin: 0; font-size: 12px; color: #64748b; line-height: 1.5; }
.feature-arrow { font-size: 20px; color: #94a3b8; flex-shrink: 0; }
</style>
