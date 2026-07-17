import { computed } from 'vue'

const CURRENT_USER_KEY = 'traffic_current_user'

const readUser = () => {
  try { return JSON.parse(localStorage.getItem(CURRENT_USER_KEY) || '{}') }
  catch { return {} }
}

export function useAuth() {
  const user = computed(() => readUser())
  const isAdmin = computed(() => user.value.role === '管理员' || user.value.role === 'admin')
  const isLoggedIn = computed(() => !!localStorage.getItem('token'))
  const canManageUsers = computed(() => isAdmin.value)
  const canManageDevices = computed(() => isAdmin.value)
  const canReviewRegistrations = computed(() => isAdmin.value)

  return { user, isAdmin, isLoggedIn, canManageUsers, canManageDevices, canReviewRegistrations }
}
