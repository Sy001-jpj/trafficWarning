<script setup>
import { ref, computed, onMounted } from 'vue'
import { useLocale } from '../composables/useLocale.js'

defineOptions({ name: 'RegisterReview' })

const { locale } = useLocale()
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const registrations = ref([])
const loading = ref(false)

const requestApi = async (path, opts = {}) => {
  const r = await fetch(`${API_BASE_URL}${path}`, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) } })
  if (!r.ok) throw new Error(locale.value === 'zh' ? '服务器连接失败' : 'Server error')
  const j = await r.json()
  if (j.code !== 200) throw new Error(j.message || '操作失败')
  return j.data
}

const fetchRegistrations = async () => {
  try {
    loading.value = true
    // Filter users that need review (status = '禁用' means pending review in demo)
    const users = await requestApi('/users')
    registrations.value = users.filter(u => u.status === '禁用').map(u => ({
      ...u,
      createdAt: u.createdAt ? String(u.createdAt).replace('T',' ').slice(0, 19) : '-'
    }))
  } catch (e) { registrations.value = [] }
  finally { loading.value = false }
}

onMounted(fetchRegistrations)

const approve = async (user) => {
  try {
    await requestApi(`/users/${user.id}/status`, { method: 'PATCH', body: JSON.stringify({ status: '正常' }) })
    registrations.value = registrations.value.filter(r => r.id !== user.id)
    alert(locale.value === 'zh' ? `已通过 ${user.username} 的注册申请` : `Approved registration for ${user.username}`)
  } catch (e) { alert(e.message) }
}

const reject = async (user) => {
  if (!confirm(locale.value === 'zh' ? `确定拒绝 ${user.username} 的注册申请吗？` : `Reject registration for ${user.username}?`)) return
  try {
    await requestApi(`/users/${user.id}`, { method: 'DELETE' })
    registrations.value = registrations.value.filter(r => r.id !== user.id)
    alert(locale.value === 'zh' ? '已拒绝并删除该注册申请' : 'Registration rejected and removed')
  } catch (e) { alert(e.message) }
}

const showDetail = ref(false)
const detailUser = ref(null)
const viewDetail = (user) => { detailUser.value = user; showDetail.value = true }
const closeDetail = () => { showDetail.value = false; detailUser.value = null }
</script>

<template>
  <div class="page">
    <div class="head">
      <div>
        <h2>{{ locale === 'zh' ? '用户注册审核' : 'Registration Review' }}</h2>
        <p class="sub">{{ locale === 'zh' ? '审核新用户注册申请，通过或拒绝账号开通' : 'Review new user registration requests, approve or reject account activation' }}</p>
      </div>
      <button class="btn" @click="fetchRegistrations">{{ locale === 'zh' ? '刷新' : 'Refresh' }}</button>
    </div>

    <section class="card">
      <table>
        <thead><tr><th>{{ locale === 'zh' ? '用户名' : 'Username' }}</th><th>{{ locale === 'zh' ? '姓名' : 'Name' }}</th><th>{{ locale === 'zh' ? '角色' : 'Role' }}</th><th>{{ locale === 'zh' ? '注册时间' : 'Registered' }}</th><th>{{ locale === 'zh' ? '操作' : 'Actions' }}</th></tr></thead>
        <tbody>
          <tr v-if="!registrations.length"><td colspan="5" class="emp">{{ loading ? (locale === 'zh' ? '加载中...' : 'Loading...') : (locale === 'zh' ? '暂无待审核的注册申请' : 'No pending registration requests') }}</td></tr>
          <tr v-for="r in registrations" :key="r.id">
            <td>{{ r.username }}</td><td>{{ r.displayName || '-' }}</td>
            <td><span class="tag tn">{{ r.role || (locale === 'zh' ? '普通用户' : 'User') }}</span></td>
            <td>{{ r.createdAt }}</td>
            <td class="ops">
              <button class="lk" @click="viewDetail(r)">{{ locale === 'zh' ? '查看' : 'View' }}</button>
              <button class="lk ok" @click="approve(r)">{{ locale === 'zh' ? '通过' : 'Approve' }}</button>
              <button class="lk danger" @click="reject(r)">{{ locale === 'zh' ? '拒绝' : 'Reject' }}</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <div v-if="showDetail && detailUser" class="mask" @click.self="closeDetail">
      <div class="dlg">
        <div class="dlg-head"><strong>{{ locale === 'zh' ? '用户详情' : 'User Detail' }}</strong><button @click="closeDetail">&times;</button></div>
        <div class="dlg-body">
          <div class="dlg-row"><span>{{ locale === 'zh' ? '用户名' : 'Username' }}</span><b>{{ detailUser.username }}</b></div>
          <div class="dlg-row"><span>{{ locale === 'zh' ? '姓名' : 'Name' }}</span><b>{{ detailUser.displayName || '-' }}</b></div>
          <div class="dlg-row"><span>{{ locale === 'zh' ? '手机号' : 'Phone' }}</span><b>{{ detailUser.phone || '-' }}</b></div>
          <div class="dlg-row"><span>{{ locale === 'zh' ? '邮箱' : 'Email' }}</span><b>{{ detailUser.email || '-' }}</b></div>
          <div class="dlg-row"><span>{{ locale === 'zh' ? '角色' : 'Role' }}</span><b>{{ detailUser.role || (locale === 'zh' ? '普通用户' : 'User') }}</b></div>
          <div class="dlg-row"><span>{{ locale === 'zh' ? '状态' : 'Status' }}</span><b>{{ detailUser.status || '-' }}</b></div>
          <div class="dlg-row"><span>{{ locale === 'zh' ? '注册时间' : 'Registered' }}</span><b>{{ detailUser.createdAt }}</b></div>
        </div>
      </div>
    </div>
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
.tag { padding: 2px 10px; border-radius: 99px; font-size: 11px; font-weight: 700; }
.tn { background: #dbeafe; color: #1e40af; }
.ops { white-space: nowrap; }
.lk { border: none; background: none; font-size: 12px; cursor: pointer; margin-right: 8px; }
.lk.ok { color: #059669; }
.lk.danger { color: #ef4444; }
.mask { position:fixed;inset:0;background:rgba(15,23,42,.4);display:flex;align-items:center;justify-content:center;z-index:60 }
.dlg { width:440px;max-width:94%;background:#fff;border-radius:18px;overflow:hidden;box-shadow:0 20px 40px rgba(0,0,0,.2) }
.dlg-head { padding:16px 20px;display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #e2e8f0 }
.dlg-head strong { font-size:16px;font-weight:700;color:#0f172a }
.dlg-head button { border:none;background:none;font-size:20px;color:#94a3b8;cursor:pointer }
.dlg-body { padding:20px;display:flex;flex-direction:column;gap:12px }
.dlg-row { display:flex;justify-content:space-between;align-items:center;padding:10px 14px;background:#f8fafc;border-radius:10px }
.dlg-row span { font-size:13px;color:#64748b }
.dlg-row b { font-size:14px;color:#0f172a;font-weight:600 }
</style>
