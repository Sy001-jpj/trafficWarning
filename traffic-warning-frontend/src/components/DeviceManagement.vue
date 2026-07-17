<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useLocale } from '../composables/useLocale.js'

defineOptions({ name: 'DeviceManagement' })

const { locale } = useLocale()
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const devices = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
// 点击查询后才生效的过滤条件
const appliedKeyword = ref('')
const appliedStatusFilter = ref('')

const applyFilter = () => {
  appliedKeyword.value = keyword.value
  appliedStatusFilter.value = statusFilter.value
  currentPage.value = 1
}
const resetFilter = () => {
  keyword.value = ''
  statusFilter.value = ''
  appliedKeyword.value = ''
  appliedStatusFilter.value = ''
  currentPage.value = 1
}

const formatDateTime = (v: string) => v ? v.replace('T',' ').slice(0,19) : ''

const requestApi = async (path: string, opts: any = {}) => {
  const r = await fetch(`${API_BASE_URL}${path}`, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) } })
  if (!r.ok) throw new Error(locale.value === 'zh' ? '服务器连接失败' : 'Server error')
  const j = await r.json()
  if (j.code !== 200) throw new Error(j.message || '操作失败')
  return j.data
}

const fetchDevices = async () => {
  try { loading.value = true; const d = await requestApi('/devices'); devices.value = Array.isArray(d) ? d.map((x: any) => ({ ...x, createdAt: formatDateTime(x.createdAt || '') })) : [] } catch(e) {} finally { loading.value = false }
}
onMounted(fetchDevices)

const filteredDevices = computed(() => {
  return devices.value.filter((d: any) => {
    const k = appliedKeyword.value.trim().toLowerCase()
    if (k && ![d.id, d.name, d.location, d.status].join(' ').toLowerCase().includes(k)) return false
    if (appliedStatusFilter.value && d.status !== appliedStatusFilter.value) return false
    return true
  })
})

const pageSize = ref(10); const currentPage = ref(1)
const total = computed(() => filteredDevices.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pagedDevices = computed(() => filteredDevices.value.slice((currentPage.value-1)*pageSize.value, currentPage.value*pageSize.value))

const online = computed(() => devices.value.filter((d: any) => d.status === '在线').length)
const offline = computed(() => devices.value.filter((d: any) => d.status === '离线').length)
const fault = computed(() => devices.value.filter((d: any) => d.status === '故障').length)

// ── 新增/编辑弹窗 ──
const showEdit = ref(false)
const editMode = ref<'create' | 'edit'>('edit')
const editForm = ref({ id: '', name: '', location: '', status: '在线', rtsp: '', videoPath: '', creator: '' })
const editSubmitting = ref(false)

const generateDeviceId = () => {
  const max = devices.value
    .map((d: any) => d.id)
    .filter((id: string) => /^C\d{3}$/.test(id))
    .map((id: string) => parseInt(id.slice(1), 10))
  const next = max.length ? Math.max(...max) + 1 : 1
  return 'C' + String(next).padStart(3, '0')
}

const openEdit = (d: any) => {
  if (d) {
    editMode.value = 'edit'
    editForm.value = { id: d.id, name: d.name || '', location: d.location || '', status: d.status || '在线', rtsp: d.rtsp || '', videoPath: d.videoPath || '', creator: d.creator || '' }
  } else {
    editMode.value = 'create'
    editForm.value = { id: generateDeviceId(), name: '', location: '', status: '在线', rtsp: '', videoPath: '', creator: '系统管理员' }
  }
  showEdit.value = true
}
const closeEdit = () => { showEdit.value = false }
const submitEdit = async () => {
  const f = editForm.value
  if (!f.id.trim() || !f.name.trim() || !f.location.trim()) { alert(locale.value === 'zh' ? '设备编号、名称和位置不能为空' : 'ID, name and location required'); return }
  editSubmitting.value = true
  try {
    if (editMode.value === 'create') {
      const u = await requestApi('/devices', { method: 'POST', body: JSON.stringify({ id: f.id.trim(), name: f.name.trim(), location: f.location.trim(), status: f.status, rtsp: f.rtsp.trim(), videoPath: f.videoPath.trim(), creator: f.creator.trim() }) })
      devices.value = [...devices.value, { ...u, createdAt: formatDateTime(u.createdAt || '') }]
    } else {
      const u = await requestApi(`/devices/${f.id}`, { method: 'PUT', body: JSON.stringify({ name: f.name.trim(), location: f.location.trim(), status: f.status, rtsp: f.rtsp.trim(), videoPath: f.videoPath.trim(), creator: f.creator.trim() }) })
      devices.value = devices.value.map(x => x.id === u.id ? { ...x, ...u, createdAt: formatDateTime(u.createdAt || '') } : x)
    }
    showEdit.value = false
  } catch(e: any) { alert(e.message) }
  finally { editSubmitting.value = false }
}

const handleDelete = async (id: string) => {
  if (!confirm(locale.value === 'zh' ? '确定删除该设备吗？' : 'Confirm delete this device?')) return
  try { await requestApi(`/devices/${id}`, { method: 'DELETE' }); devices.value = devices.value.filter(d => d.id !== id) } catch(e: any) { alert(e.message) }
}
</script>

<template>
  <div class="page">
    <h2 class="tt">{{ locale === 'zh' ? '监控设备管理' : 'Monitoring Device Management' }}</h2>
          <p>{{ locale === 'zh' ? '设备信息管理与运行状态。' : 'Real-time traffic display with OpenStreetMap. Supports city search and AI analysis.' }}</p>
    <div class="stats">
      <div class="sc total"><span>{{ locale === 'zh' ? '总设备数' : 'Total Devices' }}</span><strong>{{ devices.length }}</strong></div>
      <div class="sc on"><span>{{ locale === 'zh' ? '在线设备' : 'Online Devices' }}</span><strong>{{ online }}</strong></div>
      <div class="sc off"><span>{{ locale === 'zh' ? '离线设备' : 'Offline Devices' }}</span><strong>{{ offline }}</strong></div>
      <div class="sc fl"><span>{{ locale === 'zh' ? '故障设备' : 'Faulty Devices' }}</span><strong>{{ fault }}</strong></div>
    </div>
    <div class="bar">
      <button class="btn pri" @click="openEdit(null)">{{ locale === 'zh' ? '新增监控设备' : 'Add Monitoring Device' }}</button>
      <button class="btn" @click="fetchDevices">{{ locale === 'zh' ? '刷新数据' : 'Refresh Data' }}</button>
    </div>
    <section class="card">
      <div class="row">
        <div class="f"><label>{{ locale === 'zh' ? '搜索' : 'Search' }}</label><input v-model="keyword" type="search" placeholder="搜索设备名称、位置、编号" /></div>
        <div class="f"><label>{{ locale === 'zh' ? '状态' : 'Status' }}</label><select v-model="statusFilter"><option value="">全部状态</option><option value="在线">{{ locale === 'zh' ? '在线' : 'Online' }}</option><option value="离线">{{ locale === 'zh' ? '离线' : 'Offline' }}</option><option value="故障">{{ locale === 'zh' ? '故障' : 'Faulty' }}</option></select></div>
        <div class="f acts"><button class="btn pri" @click="applyFilter">{{ locale === 'zh' ? '查询' : 'Search' }}</button><button class="btn" @click="resetFilter">{{ locale === 'zh' ? '重置' : 'Reset' }}</button></div>
      </div>
      <table>
        <thead><tr><th>序号</th><th>设备名称</th><th>安装位置</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-if="!pagedDevices.length"><td colspan="6" class="emp">{{ loading ? '加载中...' : '暂无设备数据' }}</td></tr>
          <tr v-for="(d,i) in pagedDevices" :key="d.id">
            <td>{{ (currentPage-1)*pageSize + i + 1 }}</td>
            <td>{{ d.name }}</td><td>{{ d.location }}</td>
            <td><span :class="['tag', d.status==='在线'?'ton':d.status==='故障'?'tfl':'tof']">{{ d.status }}</span></td>
            <td>{{ d.createdAt }}</td>
            <td class="ops"><button class="lk" @click="openEdit(d)">编辑</button><button class="lk danger" @click="handleDelete(d.id)">删除</button></td>
          </tr>
        </tbody>
      </table>
      <div class="pager"><span>共 {{ total }} 条</span><select v-model.number="pageSize"><option v-for="s in [5,10,20]" :key="s" :value="s">{{ s }}/页</option></select><button :disabled="currentPage===1" @click="currentPage--">&lsaquo;</button><span>{{ currentPage }}</span><button :disabled="currentPage===totalPages" @click="currentPage++">&rsaquo;</button></div>
    </section>

    <div v-if="showEdit" class="mask" @click.self="closeEdit">
      <div class="info-card">
        <div class="info-card-head">
          <div class="info-avatar">{{ editMode === 'create' ? '➕' : '📹' }}</div>
          <div>
            <strong>{{ editMode === 'create' ? (locale === 'zh' ? '新增设备' : 'New Device') : editForm.name }}</strong>
            <span>{{ editMode === 'create' ? (locale === 'zh' ? '创建新的监控设备' : 'Create a new monitoring device') : editForm.id }}</span>
          </div>
          <button class="info-close" @click="closeEdit">&times;</button>
        </div>
        <div class="info-card-body">
          <label class="edit-field"><span>{{ locale === 'zh' ? '设备编号' : 'Device ID' }}</span><input v-model="editForm.id" :readonly="editMode === 'edit'" :class="{ readonly: editMode === 'edit' }" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '设备名称' : 'Device Name' }}</span><input v-model="editForm.name" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '安装位置' : 'Location' }}</span><input v-model="editForm.location" /></label>
          <label class="edit-field"><span>RTSP</span><input v-model="editForm.rtsp" placeholder="rtsp://..." /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '视频文件' : 'Video File' }}</span><input v-model="editForm.videoPath" placeholder="C011.webm" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '创建者' : 'Creator' }}</span><input v-model="editForm.creator" /></label>
          <label class="edit-field"><span>{{ locale === 'zh' ? '状态' : 'Status' }}</span><select v-model="editForm.status"><option value="在线">{{ locale === 'zh' ? '在线' : 'Online' }}</option><option value="离线">{{ locale === 'zh' ? '离线' : 'Offline' }}</option><option value="故障">{{ locale === 'zh' ? '故障' : 'Faulty' }}</option></select></label>
          <div class="edit-actions">
            <button class="btn pri" :disabled="editSubmitting" @click="submitEdit">{{ editSubmitting ? (locale === 'zh' ? '保存中...' : 'Saving...') : (editMode === 'create' ? (locale === 'zh' ? '创建设备' : 'Create') : (locale === 'zh' ? '保存' : 'Save')) }}</button>
            <button class="btn" @click="closeEdit">{{ locale === 'zh' ? '取消' : 'Cancel' }}</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.page{display:flex;flex-direction:column;gap:16px}.tt{margin:0;font-size:22px;font-weight:900;color:var(--text-main)}
.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px}
.sc{min-height:130px;padding:26px 28px;border-radius:18px;border:1px solid #d2dceb;display:flex;flex-direction:column;justify-content:space-between;box-shadow:var(--shadow-float);transition:transform .26s cubic-bezier(0.34,1.56,0.64,1),box-shadow .26s ease,border-color .26s ease}
.sc:hover{transform:translateY(-3px);box-shadow:var(--shadow-float-hover);border-color:rgba(47,124,246,.38)}
.sc span{color:#5f6676;font-size:13px;font-weight:900}
.sc strong{font-size:34px;font-weight:900;line-height:1}
.sc{background:linear-gradient(180deg,#ffffff 0%,#f7fbff 100%)}
.sc.on{background:linear-gradient(135deg,#dfeaff,#ffffff)}.sc.on strong{color:#16a34a}
.sc.off{background:linear-gradient(135deg,#fff0c7,#ffffff)}.sc.off strong{color:#d88a00}
.sc.fl{background:linear-gradient(135deg,#ffe1ef,#ffffff)}.sc.fl strong{color:#e60012}
.sc.total{border:2px solid var(--primary);box-shadow:0 0 0 1px rgba(75,163,237,.1);background:var(--card-white)}.sc.total strong{color:var(--primary)}
.bar{display:flex;gap:10px}.btn{height:36px;padding:0 14px;border:1px solid #e2e8f0;border-radius:10px;background:#fff;color:var(--primary-strong);font-size:13px;font-weight:700;cursor:pointer}.btn.pri{background:linear-gradient(135deg,var(--primary),var(--accent));color:#fff;border:none}.btn.pri:hover{transform:translateY(-1px);box-shadow:0 6px 18px rgba(75,163,237,.3)}.btn:hover{background:#f8fafc}
.card{background:linear-gradient(180deg,#ffffff 0%,#f7fbff 100%);border:1px solid #d2dceb;border-radius:18px;padding:16px 18px;box-shadow:var(--shadow-float);transition:transform .26s cubic-bezier(0.34,1.56,0.64,1),box-shadow .26s ease,border-color .26s ease;overflow-x:auto}.card:hover{transform:translateY(-3px);box-shadow:var(--shadow-float-hover);border-color:rgba(47,124,246,.38)}
.row{display:grid;grid-template-columns:2fr 1fr auto;gap:10px;align-items:end}.f{display:flex;flex-direction:column;gap:4px;font-size:12px}.f label{color:var(--text-secondary)}.f input,.f select{border-radius:12px;border:1px solid #cfd7e6;min-height:36px;padding:6px 10px;font-size:13px;background:#fff}.f input:focus,.f select:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px rgba(75,163,237,.16)}.f.acts{display:flex;gap:8px}.srch{width:260px;height:34px;padding:0 10px;border:1px solid #e2e8f0;border-radius:8px;display:flex;align-items:center;gap:8px;background:#fff;margin-bottom:12px}.srch input{border:none;outline:none;flex:1;font-size:13px}.srch span{color:#94a3b8}
table{width:100%;min-width:700px;border-collapse:collapse;font-size:13px}th,td{padding:8px 6px;border-bottom:1px solid #f1f5f9;text-align:left}th{background:#f8fafc;color:#64748b;font-weight:700}tbody tr:hover{background:#fafcfd}.emp{text-align:center;color:#94a3b8}
.tag{padding:2px 10px;border-radius:99px;font-size:11px;font-weight:700}.ton{background:#dcfce7;color:#15803d}.tof{background:#fee2e2;color:#dc2626}.tfl{background:#fef3c7;color:#92400e}
.pager{display:flex;justify-content:flex-end;align-items:center;gap:10px;margin-top:10px;font-size:12px;color:#64748b}.pager select{border:1px solid #e2e8f0;border-radius:99px;padding:4px 8px}.pager button{border:1px solid #e2e8f0;border-radius:99px;padding:2px 8px;background:#fff;cursor:pointer}.pager button:disabled{opacity:.4;cursor:default}
.ops{white-space:nowrap}.lk{border:none;background:none;font-size:12px;color:#4BA3ED;cursor:pointer;margin-right:6px}.lk.danger{color:#ef4444}.mask{position:fixed;inset:0;background:rgba(15,23,42,.4);display:flex;align-items:center;justify-content:center;z-index:60}.info-card{width:460px;max-width:94%;background:#fff;border-radius:20px;overflow:hidden;box-shadow:0 20px 50px rgba(0,0,0,.2)}.info-card-head{display:flex;align-items:center;gap:14px;padding:24px 24px 18px;background:linear-gradient(135deg,#eff6ff,#f0f9ff)}.info-avatar{width:48px;height:48px;border-radius:12px;display:flex;align-items:center;justify-content:center;background:rgba(75,163,237,.12);font-size:24px}.info-card-head strong{font-size:16px;font-weight:800;color:#0f172a}.info-card-head span{font-size:12px;color:#64748b}.info-close{margin-left:auto;border:none;background:none;font-size:22px;color:#94a3b8;cursor:pointer}.info-card-body{padding:8px 24px 24px}.edit-field{display:flex;flex-direction:column;gap:4px;padding:10px 0;border-bottom:1px solid #f1f5f9}.edit-field span{font-size:12px;color:#64748b;font-weight:600}.edit-field input,.edit-field select{height:38px;border:1px solid #e2e8f0;border-radius:10px;padding:0 12px;font-size:14px;background:#f8fafc}.edit-field input:focus,.edit-field select:focus{outline:none;border-color:#4BA3ED;box-shadow:0 0 0 3px rgba(75,163,237,.1)}.edit-field input.readonly{background:#f1f5f9;color:#94a3b8;cursor:not-allowed}.edit-actions{display:flex;gap:8px;margin-top:16px;justify-content:flex-end}.edit-actions .btn{height:38px;padding:0 18px;border-radius:10px;font-size:13px;font-weight:700;cursor:pointer;border:1px solid #e2e8f0;background:#fff;color:#475569}.edit-actions .btn.pri{background:linear-gradient(135deg,#4BA3ED,#6366f1);color:#fff;border:none}
</style>
