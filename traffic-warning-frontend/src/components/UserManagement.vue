<script setup>
import { ref, computed, onMounted } from 'vue'

defineOptions({ name: 'UserManagement' })

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const users = ref([])
const loading = ref(false)
const filters = ref({ username: '', displayName: '', role: '', status: '' })

const formatDateTime = (v) => v ? String(v).replace('T',' ').slice(0,19) : ''
const normalizeUser = (u) => ({ ...u, displayName: u.displayName || '', role: u.role || '普通用户', status: u.status || '正常', createdAt: formatDateTime(u.createdAt) })

const requestApi = async (path, opts = {}) => {
  const r = await fetch(`${API_BASE_URL}${path}`, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) } })
  if (!r.ok) throw new Error('服务器连接失败')
  const j = await r.json()
  if (j.code !== 200) throw new Error(j.message || '操作失败')
  return j.data
}

const fetchUsers = async () => { try { loading.value = true; users.value = (await requestApi('/users')).map(normalizeUser) } catch(e) {} finally { loading.value = false } }
onMounted(fetchUsers)

const appliedFilters = ref({ username: '', displayName: '', role: '', status: '' })

const filteredUsers = computed(() => users.value.filter(u => {
  const f = appliedFilters.value
  if (f.username && !String(u.username).includes(f.username.trim())) return false
  if (f.displayName && !String(u.displayName).includes(f.displayName.trim())) return false
  if (f.role && u.role !== f.role) return false
  if (f.status && u.status !== f.status) return false
  return true
}))

const pageSize = ref(10); const currentPage = ref(1)
const total = computed(() => filteredUsers.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pagedUsers = computed(() => filteredUsers.value.slice((currentPage.value-1)*pageSize.value, currentPage.value*pageSize.value))
const resetFilters = () => { filters.value = { username:'', displayName:'', role:'', status:'' }; appliedFilters.value = { username:'', displayName:'', role:'', status:'' }; currentPage.value = 1 }
const search = () => { appliedFilters.value = { ...filters.value }; currentPage.value = 1 }
// ── 编辑弹窗 ──
const showEdit = ref(false)
const editForm = ref({ id: '', username: '', displayName: '', phone: '', email: '', role: '普通用户', status: '正常' })
const editSubmitting = ref(false)

const openEditForm = (user) => {
  editForm.value = { id: user.id, username: user.username, displayName: user.displayName || '', phone: user.phone || '', email: user.email || '', role: user.role || '普通用户', status: user.status || '正常' }
  showEdit.value = true
}
const closeEdit = () => { showEdit.value = false }
const submitEdit = async () => {
  const f = editForm.value
  if (!f.displayName.trim()) { alert('姓名不能为空'); return }
  editSubmitting.value = true
  try {
    const u = await requestApi(`/users/${f.id}`, { method: 'PUT', body: JSON.stringify({ displayName: f.displayName.trim(), phone: f.phone.trim(), email: f.email.trim(), role: f.role, status: f.status }) })
    users.value = users.value.map(x => x.id === u.id ? normalizeUser(u) : x)
    showEdit.value = false
  } catch(e) { alert(e.message) }
  finally { editSubmitting.value = false }
}

const handleDelete = async (id) => {
  if (!confirm('确定删除该用户吗？')) return
  try { await requestApi(`/users/${id}`, { method: 'DELETE' }); users.value = users.value.filter(u => u.id !== id) } catch(e) { alert(e.message) }
}

const showDetail = ref(false)
const detailUser = ref(null)
const viewDetail = (user) => { detailUser.value = user; showDetail.value = true }
const closeDetail = () => { showDetail.value = false; detailUser.value = null }
</script>
<template>
  <div class="page">
    <div class="head"><h2> 用户管理</h2><button class="btn" @click="fetchUsers">刷新数据</button></div>
    <p class="page-desc">统一管理所有用户的账号信息，包括用户名、姓名、角色、状态等。</p>
    <section class="card"><div class="row">
      <div class="f"><label>用户名</label><input v-model="filters.username" :placeholder="locale === 'zh' ? '搜索用户名' : 'Search Username'" /></div>
      <div class="f"><label>姓名</label><input v-model="filters.displayName" :placeholder="locale === 'zh' ? '搜索姓名' : 'Search Name'" /></div>
      <div class="f"><label>角色</label><select v-model="filters.role"><option value="">全部</option><option value="管理员">管理员</option><option value="普通用户">普通用户</option></select></div>
      <div class="f"><label>状态</label><select v-model="filters.status"><option value="">全部</option><option value="正常">正常</option><option value="禁用">禁用</option></select></div>
      <div class="f acts"><button class="btn pri" @click="search">查询</button><button class="btn" @click="resetFilters">重置</button></div>
    </div></section>
    <section class="card tbl"><table>
      <thead><tr><th>{{ locale === 'zh' ? '用户名' : 'Username' }}</th><th>{{ locale === 'zh' ? '姓名' : 'Name' }}</th><th>{{ locale === 'zh' ? '角色' : 'Role' }}</th><th>{{ locale === 'zh' ? '状态' : 'Status' }}</th><th>{{ locale === 'zh' ? '创建时间' : 'Created At' }}</th><th>{{ locale === 'zh' ? '操作' : 'Actions' }}</th></tr></thead>
      <tbody>
        <tr v-if="!pagedUsers.length"><td colspan="6" class="emp">{{ loading ? '加载中...' : '暂无数据' }}</td></tr>
        <tr v-for="u in pagedUsers" :key="u.id">
          <td>{{ u.username }}</td><td>{{ u.displayName }}</td>
          <td><span :class="['tag', u.role==='管理员'?'ta':'tn']">{{ u.role }}</span></td>
          <td><span :class="['tag', u.status!=='禁用'?'so':'sb']">{{ u.status }}</span></td>
          <td>{{ u.createdAt }}</td>
          <td class="ops"><button class="lk" @click="viewDetail(u)">{{ locale === 'zh' ? '查看' : 'View' }}</button><button class="lk" @click="openEditForm(u)">{{ locale === 'zh' ? '编辑' : 'Edit' }}</button><button class="lk danger" @click="handleDelete(u.id)">{{ locale === 'zh' ? '删除' : 'Delete' }}</button></td>
        </tr>
      </tbody>
    </table>
    <div class="pager"><span>{{ locale === 'zh' ? '共 ' : 'Total: ' }}{{ total }}{{ locale === 'zh' ? ' 条' : ' items' }}</span><select v-model.number="pageSize"><option v-for="s in [5,10,20]" :key="s" :value="s">{{ s }}/页</option></select><button :disabled="currentPage===1" @click="currentPage--">&lsaquo;</button><span>{{ currentPage }}</span><button :disabled="currentPage===totalPages" @click="currentPage++">&rsaquo;</button></div>
    </section>

    <div v-if="showDetail && detailUser" class="mask" @click.self="closeDetail">
      <div class="info-card">
        <div class="info-card-head">
          <div class="info-avatar">{{ (detailUser.displayName || detailUser.username).slice(0,1).toUpperCase() }}</div>
          <div>
            <strong>{{ detailUser.displayName || detailUser.username }}</strong>
            <span>@{{ detailUser.username }}</span>
          </div>
          <button class="info-close" @click="closeDetail">&times;</button>
        </div>
        <div class="info-card-body">
          <div class="info-item"><span class="info-label">{{ locale === 'zh' ? '手机号' : 'Phone' }}</span><span class="info-val">{{ detailUser.phone || '-' }}</span></div>
          <div class="info-item"><span class="info-label">{{ locale === 'zh' ? '邮箱' : 'Email' }}</span><span class="info-val">{{ detailUser.email || '-' }}</span></div>
          <div class="info-item"><span class="info-label">{{ locale === 'zh' ? '角色' : 'Role' }}</span><span :class="['info-tag', detailUser.role==='管理员'?'ta':'tn']">{{ detailUser.role || (locale === 'zh' ? '普通用户' : 'User') }}</span></div>
          <div class="info-item"><span class="info-label">{{ locale === 'zh' ? '状态' : 'Status' }}</span><span :class="['info-tag', detailUser.status!=='禁用'?'so':'sb']">{{ detailUser.status || '-' }}</span></div>
          <div class="info-item"><span class="info-label">{{ locale === 'zh' ? '注册时间' : 'Registered' }}</span><span class="info-val">{{ detailUser.createdAt }}</span></div>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗（卡片式） -->
    <div v-if="showEdit" class="mask" @click.self="closeEdit">
      <div class="info-card">
        <div class="info-card-head">
          <div class="info-avatar">{{ (editForm.displayName || editForm.username).slice(0,1).toUpperCase() }}</div>
          <div>
            <strong>{{ editForm.displayName || editForm.username }}</strong>
            <span>@{{ editForm.username }}</span>
          </div>
          <button class="info-close" @click="closeEdit">&times;</button>
        </div>
        <div class="info-card-body">
          <label class="edit-field"><span>{{ locale === 'zh' ? '姓名' : 'Name' }}</span><input v-model="editForm.displayName" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '手机号' : 'Phone' }}</span><input v-model="editForm.phone" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '邮箱' : 'Email' }}</span><input v-model="editForm.email" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '角色' : 'Role' }}</span><select v-model="editForm.role"><option value="管理员">{{ locale === 'zh' ? '管理员' : 'Admin' }}</option><option value="普通用户">{{ locale === 'zh' ? '普通用户' : 'User' }}</option></select></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '状态' : 'Status' }}</span><select v-model="editForm.status"><option value="正常">{{ locale === 'zh' ? '正常' : 'Active' }}</option><option value="禁用">{{ locale === 'zh' ? '禁用' : 'Disabled' }}</option></select></label>
          <div class="edit-actions">
            <button class="btn pri" :disabled="editSubmitting" @click="submitEdit">{{ editSubmitting ? (locale === 'zh' ? '保存中...' : 'Saving...') : (locale === 'zh' ? '保存' : 'Save') }}</button>
            <button class="btn" @click="closeEdit">{{ locale === 'zh' ? '取消' : 'Cancel' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.head h2 { margin: 0; font-size: 22px; font-weight: 900; color: var(--text-main); }

.btn { height: 36px; padding: 0 14px; border: 1px solid #bfdbfe; border-radius: 10px; background: #eff6ff; color: #2563eb; font-size: 13px; font-weight: 600; cursor: pointer; transition: background .16s,border-color .16s; }
.btn:hover { background: var(--surface-soft); border-color: var(--line-soft); }
.btn.pri { background: linear-gradient(135deg, var(--primary), var(--accent)); color: #fff; border: none; }
.btn.pri:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(75,163,237,.3); }

.card {
  background: linear-gradient(180deg, #ffffff 0%, #f7fbff 100%);
  border: 1px solid #d2dceb; border-radius: 18px;
  padding: 14px 18px 16px;
  box-shadow: var(--shadow-float);
  transition: transform .26s cubic-bezier(0.34,1.56,0.64,1), box-shadow .26s ease, border-color .26s ease;
}
.card:hover { transform: translateY(-3px); box-shadow: var(--shadow-float-hover); border-color: rgba(47,124,246,.38); }

.row { display: grid; grid-template-columns: repeat(4, 1.5fr) auto; gap: 10px; align-items: end; }
.f { display: flex; flex-direction: column; gap: 4px; font-size: 12px; }
.f label { color: var(--text-secondary); }
.f input, .f select {
  border-radius: 12px; border: 1px solid #cfd7e6; min-height: 36px;
  padding: 6px 10px; font-size: 13px; background: #fff;
  transition: border-color .18s, box-shadow .18s;
}
.f input:focus, .f select:focus { outline: none; border-color: var(--primary); box-shadow: 0 0 0 3px rgba(75,163,237,.16); }
.f.acts { display: flex; gap: 8px; }

.tbl { overflow-x: auto; margin-top: 16px; }
table { width: 100%; min-width: 980px; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px 6px; border-bottom: 1px solid var(--line-soft); text-align: left; }
th { background: #eaf1fb; color: var(--text-secondary); font-weight: 700; }
tbody tr:hover { background: #eef5ff; }
.emp { text-align: center; color: var(--text-muted); }

.tag { display: inline-flex; align-items: center; padding: 2px 8px; border-radius: 999px; font-size: 11px; }
.ta { background: rgba(247,206,116,.18); color: #92400e; }
.tn { background: rgba(75,163,237,.16); color: #2470b8; }
.so { background: rgba(113,224,180,.16); color: #15803d; }
.sb { background: rgba(244,126,139,.14); color: #be123c; }

.ops { white-space: nowrap; }
.lk { border: none; background: transparent; font-size: 12px; color: var(--primary); cursor: pointer; margin-right: 6px; }
.lk.danger { color: #ef4444; }

.pager { margin-top: 10px; display: flex; justify-content: flex-end; align-items: center; gap: 12px; font-size: 12px; color: #6b7280; }
.pager select { border-radius: 999px; border: 1px solid #e5e7eb; padding: 4px 8px; font-size: 12px; }
.pager button { border-radius: 999px; border: 1px solid #e5e7eb; background: #fff; padding: 2px 8px; cursor: pointer; }
.pager button:disabled { opacity: .4; cursor: default; }
.dlg-field{display:flex;flex-direction:column;gap:4px}.dlg-field span{font-size:12px;color:#64748b;font-weight:600}.dlg-field input,.dlg-field select{height:38px;border:1px solid #e2e8f0;border-radius:10px;padding:0 12px;font-size:13px;background:#fff}.dlg-field input:focus,.dlg-field select:focus{outline:none;border-color:#4BA3ED;box-shadow:0 0 0 3px rgba(75,163,237,.1)}.dlg-actions{display:flex;gap:8px;margin-top:8px;justify-content:flex-end}.dlg-actions .btn{height:36px;padding:0 16px;border-radius:10px;font-size:13px;font-weight:700;cursor:pointer;border:1px solid #e2e8f0;background:#fff;color:#475569}.dlg-actions .btn.pri{background:linear-gradient(135deg,#4BA3ED,#6366f1);color:#fff;border:none}
.info-card{width:420px;max-width:94%;background:#fff;border-radius:20px;overflow:hidden;box-shadow:0 20px 50px rgba(0,0,0,.2)}.info-card-head{display:flex;align-items:center;gap:14px;padding:24px 24px 18px;background:linear-gradient(135deg,#eff6ff,#f0f9ff)}.info-avatar{width:56px;height:56px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:linear-gradient(135deg,#4BA3ED,#6366f1);color:#fff;font-size:24px;font-weight:800;flex-shrink:0}.info-card-head strong{display:block;font-size:18px;font-weight:800;color:#0f172a}.info-card-head span{font-size:13px;color:#64748b}.info-close{margin-left:auto;border:none;background:none;font-size:22px;color:#94a3b8;cursor:pointer}.info-card-body{display:flex;flex-direction:column;gap:0;padding:8px 24px 24px}.info-item{display:flex;justify-content:space-between;align-items:center;padding:14px 0;border-bottom:1px solid #f1f5f9}.info-item:last-child{border-bottom:none}.info-label{font-size:13px;color:#64748b;font-weight:500}.info-val{font-size:14px;color:#0f172a;font-weight:600}.info-tag{padding:3px 12px;border-radius:99px;font-size:12px;font-weight:700}
.edit-field{display:flex;flex-direction:column;gap:4px;padding:10px 0;border-bottom:1px solid #f1f5f9}.edit-field:last-of-type{border-bottom:none}.edit-field span{font-size:12px;color:#64748b;font-weight:600}.edit-field input,.edit-field select{height:38px;border:1px solid #e2e8f0;border-radius:10px;padding:0 12px;font-size:14px;background:#f8fafc;color:#0f172a}.edit-field input:focus,.edit-field select:focus{outline:none;border-color:#4BA3ED;box-shadow:0 0 0 3px rgba(75,163,237,.1)}.edit-actions{display:flex;gap:8px;margin-top:16px;justify-content:flex-end}.edit-actions .btn{height:38px;padding:0 18px;border-radius:10px;font-size:13px;font-weight:700;cursor:pointer;border:1px solid #e2e8f0;background:#fff;color:#475569}.edit-actions .btn.pri{background:linear-gradient(135deg,#4BA3ED,#6366f1);color:#fff;border:none}
</style>
