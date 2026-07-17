import { createRouter, createWebHistory } from 'vue-router'

import LoginForm from '../components/LoginForm.vue'
import Dashboard from '../components/Dashboard.vue'
import UserManagement from '../components/UserManagement.vue'
import DeviceManagement from '../components/DeviceManagement.vue'
import RealTimeMonitor from '../components/RealTimeMonitor.vue'
import RoadStatus from '../components/RoadStatus.vue'
import CongestionRecognition from '../components/CongestionRecognition.vue'
import AiAssistant from '../components/AiAssistant.vue'
import ProfileCenter from '../components/ProfileCenter.vue'
import TodoMessages from '../components/TodoMessages.vue'
import SettingsPage from '../components/SettingsPage.vue'
import RegisterReview from '../components/RegisterReview.vue'
import AdminPanel from '../components/AdminPanel.vue'
import PasswordResetReview from '../components/PasswordResetReview.vue'



const routes = [
  {
    path: '/login',
    name: 'Login',
    component: LoginForm,
  },
  {
    path: '/',
    name: 'Dashboard',
    component: Dashboard,
    meta: { requiresAuth: true }
  },
  {
    path: '/dashboard',
    redirect: '/'
  },
  {
    path: '/users',
    name: 'UserManagement',
    component: UserManagement,
    meta: { requiresAuth: true }
  },
  {
    path: '/devices',
    name: 'DeviceManagement',
    component: DeviceManagement,
    meta: { requiresAuth: true }
  },
  {
  path: '/realtime',
  name: 'RealTimeMonitor',
  component: RealTimeMonitor,
  meta: { requiresAuth: true }
  },
  {
    path: '/road-status',
    name: 'RoadStatus',
    component: RoadStatus,
    meta: { requiresAuth: true }
  },
  {
    path: '/todos',
    name: 'TodoMessages',
    component: TodoMessages,
    meta: { requiresAuth: true }
  },
  {
    path: '/congestion',
    name: 'CongestionRecognition',
    component: CongestionRecognition,
    meta: { requiresAuth: true }
  },
  {
  path: '/assistant',
  name: 'AiAssistant',
  component: AiAssistant,
  meta: { requiresAuth: true }
},
  {
    path: '/profile',
    name: 'ProfileCenter',
    component: ProfileCenter,
    meta: { requiresAuth: true }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: SettingsPage,
    meta: { requiresAuth: true }
  },
  {
    path: '/register-review',
    name: 'RegisterReview',
    component: RegisterReview,
    meta: { requiresAuth: true, adminOnly: true }
  },
  {
    path: '/admin',
    name: 'AdminPanel',
    component: AdminPanel,
    meta: { requiresAuth: true, adminOnly: true }
  },
  {
    path: '/password-reset-review',
    name: 'PasswordResetReview',
    component: PasswordResetReview,
    meta: { requiresAuth: true, adminOnly: true }
  },


]

// 下面的路由守卫保持不变
const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('token')) {
    next({ name: 'Login' })
  } else if (to.meta.adminOnly) {
    const user = JSON.parse(localStorage.getItem('traffic_current_user') || '{}')
    if (user.role !== '管理员' && user.role !== 'admin') {
      next({ name: 'Dashboard' })
    } else {
      next()
    }
  } else {
    next()
  }
})

export default router
