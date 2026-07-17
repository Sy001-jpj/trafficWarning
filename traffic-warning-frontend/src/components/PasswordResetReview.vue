<script setup>
import { ref, onMounted } from 'vue'
import { useLocale } from '../composables/useLocale.js'

defineOptions({ name: 'PasswordResetReview' })

const { locale } = useLocale()
const PASSWORD_REQUESTS_KEY = 'traffic_password_requests'
const requests = ref([])

const loadRequests = () => {
  try { requests.value = JSON.parse(localStorage.getItem(PASSWORD_REQUESTS_KEY) || '[]') }
  catch { requests.value = [] }
}

onMounted(loadRequests)

const approveRequest = (req) => {
  if (!confirm(locale.value === 'zh' ? `确定批准 ${req.username} 的密码重置请求吗？密码将变更为：${req.newPassword}` : `Approve password reset for ${req.username}? New password: ${req.newPassword}`)) return
  const CURRENT_USER_KEY = 'traffic_current_user'
  try {
    const user = JSON.parse(localStorage.getItem(CURRENT_USER_KEY) || '{}')
    if (user.username === req.username) {
      user.password = req.newPassword
      localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user))
    }
  } catch {}
  requests.value = requests.value.filter(r => r.id !== req.id)
  localStorage.setItem(PASSWORD_REQUESTS_KEY, JSON.stringify(requests.value))
  alert(locale.value === 'zh' ? `已批准 ${req.username} 的密码重置` : `Password reset approved for ${req.username}`)
}

const rejectRequest = (req) => {
  if (!confirm(locale.value === 'zh' ? `确定拒绝 ${req.username} 的密码重置请求吗？` : `Reject password reset for ${req.username}?`)) return
  requests.value = requests.value.filter(r => r.id !== req.id)
  localStorage.setItem(PASSWORD_REQUESTS_KEY, JSON.stringify(requests.value))
}
</script>

<template>
  <div class="page">
    <div class="head">
      <div>
        <h2>{{ locale === 'zh' ? '密码重置审核' : 'Password Reset Review' }}</h2>
        <p class="sub">{{ locale === 'zh' ? '审核用户提交的密码重置请求' : 'Review password reset requests submitted by users' }}</p>
      </div>
      <button class="btn" @click="loadRequests">{{ locale === 'zh' ? '刷新' : 'Refresh' }}</button>
    </div>

    <section class="card">
      <table>
        <thead><tr><th>{{ locale === 'zh' ? '用户名' : 'Username' }}</th><th>{{ locale === 'zh' ? '手机号' : 'Phone' }}</th><th>{{ locale === 'zh' ? '新密码' : 'New Password' }}</th><th>{{ locale === 'zh' ? '提交时间' : 'Submitted' }}</th><th>{{ locale === 'zh' ? '操作' : 'Actions' }}</th></tr></thead>
        <tbody>
          <tr v-if="!requests.length"><td colspan="5" class="emp">{{ locale === 'zh' ? '暂无密码重置请求' : 'No password reset requests' }}</td></tr>
          <tr v-for="r in requests" :key="r.id">
            <td>{{ r.username }}</td><td>{{ r.phone }}</td><td><code>{{ r.newPassword }}</code></td><td>{{ r.createdAt }}</td>
            <td class="ops">
              <button class="lk ok" @click="approveRequest(r)">{{ locale === 'zh' ? '批准' : 'Approve' }}</button>
              <button class="lk danger" @click="rejectRequest(r)">{{ locale === 'zh' ? '拒绝' : 'Reject' }}</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; }
.head h2 { margin: 0; font-size: 22px; font-weight: 900; color: var(--text-main); }
.sub { margin: 4px 0 0; font-size: 13px; color: var(--text-secondary); }
.btn { height: 36px; padding: 0 14px; border: 1px solid #bfdbfe; border-radius: 10px; background: #eff6ff; color: #2563eb; font-size: 13px; font-weight: 600; cursor: pointer; }
.card { background: linear-gradient(180deg, #fff 0%, #f7fbff 100%); border: 1px solid #d2dceb; border-radius: 18px; padding: 16px 18px; box-shadow: var(--shadow-float); overflow-x: auto; }
table { width: 100%; min-width: 600px; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px 6px; border-bottom: 1px solid #f1f5f9; text-align: left; }
th { background: #f8fafc; color: #64748b; font-weight: 700; }
tbody tr:hover { background: #fafcfd; }
.emp { text-align: center; color: #94a3b8; }
.ops { white-space: nowrap; }
.lk { border: none; background: none; font-size: 12px; cursor: pointer; margin-right: 8px; }
.lk.ok { color: #059669; }
.lk.danger { color: #ef4444; }
code { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 12px; }
</style>
