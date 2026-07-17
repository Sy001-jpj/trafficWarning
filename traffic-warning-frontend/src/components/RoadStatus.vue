<script setup>
import { ref, computed, onMounted } from 'vue'

defineOptions({ name: 'RoadStatus' })

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const records = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('all')
const showDialog = ref(false)
const activeRecord = ref(null)
const sharedSnapshot = 'https://picsum.photos/seed/rec1/260/150'

const formatDateTime = (v) => v ? String(v).replace('T',' ').slice(0,19) : ''

const requestApi = async (path, opts = {}) => {
  const r = await fetch(`${API_BASE_URL}${path}`, { ...opts, headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) } })
  if (!r.ok) throw new Error('服务器连接失败')
  const j = await r.json()
  if (j.code !== 200) throw new Error(j.message || '操作失败')
  return j.data
}

const fetchRecords = async () => {
  try { loading.value = true; const d = await requestApi('/events'); records.value = d.map((r) => ({ ...r, time: formatDateTime(r.time), status: r.status || '未处理', snapshotUrl: r.snapshotUrl || sharedSnapshot, congestionLevel: r.congestionLevel || '-', vehicles: r.vehicles ?? 0, avgSpeed: r.avgSpeed ?? 0, description: r.description || '' })) } catch(e) {} finally { loading.value = false }
}
onMounted(fetchRecords)

const appliedKeyword = ref('')
const appliedStatusFilter = ref('all')

const filteredRecords = computed(() => records.value.filter(r => {
  if (appliedKeyword.value.trim() && !(r.cameraName + r.roadName + r.eventType).toLowerCase().includes(appliedKeyword.value.trim().toLowerCase())) return false
  if (appliedStatusFilter.value === 'unprocessed' && r.status !== '未处理') return false
  if (appliedStatusFilter.value === 'processed' && r.status !== '已处理') return false
  return true
}))

const applyFilter = () => {
  appliedKeyword.value = keyword.value
  appliedStatusFilter.value = statusFilter.value
  currentPage.value = 1
}
const resetFilter = () => {
  keyword.value = ''
  statusFilter.value = 'all'
  appliedKeyword.value = ''
  appliedStatusFilter.value = 'all'
  currentPage.value = 1
}

const pageSize = ref(10); const currentPage = ref(1)
const total = computed(() => filteredRecords.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const pagedRecords = computed(() => filteredRecords.value.slice((currentPage.value-1)*pageSize.value, currentPage.value*pageSize.value))

const openDialog = (rec) => { activeRecord.value = rec; showDialog.value = true }
const closeDialog = () => { showDialog.value = false; activeRecord.value = null }

const updateStatus = async (rec, status) => {
  try { const u = await requestApi(`/events/${rec.id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }); records.value = records.value.map(r => r.id === u.id ? { ...r, status: u.status } : r); if (activeRecord.value?.id === rec.id) activeRecord.value.status = u.status } catch(e) { alert(e.message) }
}
const deleteRecord = async (rec) => {
  if (!confirm('确定删除该记录吗？')) return
  try { await requestApi(`/events/${rec.id}`, { method: 'DELETE' }); records.value = records.value.filter(r => r.id !== rec.id); if (activeRecord.value?.id === rec.id) closeDialog() } catch(e) { alert(e.message) }
}

// ── 一键处理：重大事故 → 待办消息，非重大 → 标记已处理 ──
const batchProcessing = ref(false)

// 重大事故判定：
//   人员伤亡 → 直接重大
//   碰撞 + 影响交通（拥堵IV/V级 / 速度<15km/h / 车辆>15）→ 重大
const isMajorEvent = (record) => {
  const type = record.eventType || ''
  if (type.includes('人员伤亡')) return true
  if (type.includes('碰撞')) {
    const level = record.congestionLevel || ''
    const speed = record.avgSpeed ?? 0
    const vehicles = record.vehicles ?? 0
    return level === 'IV' || level === 'V' || speed < 15 || vehicles > 15
  }
  return false
}

const batchProcessEvents = async () => {
  const unprocessed = records.value.filter(r => r.status === '未处理')
  if (!unprocessed.length) {
    alert('没有未处理的事件记录')
    return
  }

  const major = unprocessed.filter(r => isMajorEvent(r))
  const minor = unprocessed.filter(r => !isMajorEvent(r))

  if (!confirm(
    `共 ${unprocessed.length} 条未处理记录：\n\n` +
    `🔴 重大事故 ${major.length} 条 → 发送到待办消息\n` +
    `🟢 非重大事故 ${minor.length} 条 → 标记为已处理\n\n` +
    `确认执行？`
  )) return

  batchProcessing.value = true

  // 1. 重大事故发送到 localStorage → TodoMessages 读取
  if (major.length) {
    const alerts = major.map(r => ({
      id: r.id,
      deviceId: r.deviceId,
      deviceName: r.cameraName,
      location: r.roadName,
      eventType: r.eventType,
      time: r.time,
      level: '高',
      source: 'auto'
    }))
    const stored = JSON.parse(localStorage.getItem('traffic-warning-major-alerts') || '[]')
    // 去重：同 id 不重复添加
    const existingIds = new Set(stored.map(a => a.id))
    const newAlerts = alerts.filter(a => !existingIds.has(a.id))
    if (newAlerts.length) {
      localStorage.setItem('traffic-warning-major-alerts', JSON.stringify([...stored, ...newAlerts]))
      window.dispatchEvent(new CustomEvent('major-traffic-alert-cleared'))
    }
  }

  // 2. 非重大事故批量标记为已处理
  let doneCount = 0
  for (const r of minor) {
    try {
      await requestApi(`/events/${r.id}/status`, { method: 'PATCH', body: JSON.stringify({ status: '已处理' }) })
      records.value = records.value.map(item => item.id === r.id ? { ...item, status: '已处理' } : item)
      if (activeRecord.value?.id === r.id) activeRecord.value.status = '已处理'
      doneCount++
    } catch (e) {
      console.warn('标记失败:', r.id, e.message)
    }
  }

  batchProcessing.value = false
  alert(`✅ 完成：${major.length} 条发送到待办消息，${doneCount} 条标记为已处理`)
}
</script>

<template>
  <div class="page">
    <div class="head">
      <h2>交通异常事件记录</h2>
      <div style="display:flex;gap:8px;align-items:center">
        <button class="btn batch-btn" :disabled="batchProcessing" @click="batchProcessEvents">
          {{ batchProcessing ? '处理中...' : '⚡ 一键处理' }}
        </button>
        <button class="btn" @click="fetchRecords">{{ locale === 'zh' ? '刷新数据' : 'Refresh Data' }}</button>
      </div>
    </div>
    <p class="page-desc">统一管理各监控路口的交通事件记录，包括拥堵预警、异常停车、车流量异常等。「一键处理」将碰撞且影响交通/人员伤亡事件等重大事故发送到待办消息，其余标记为已处理。</p>
    <section class="card"><div class="row">
      <div class="f"><label>监控点/道路</label><input v-model="keyword" placeholder="输入搜索关键词" /></div>
      <div class="f"><label>处理状态</label><select v-model="statusFilter"><option value="all">全部</option><option value="unprocessed">未处理</option><option value="processed">已处理</option></select></div>
      <div class="f acts"><button class="btn pri" @click="applyFilter">查询</button><button class="btn" @click="resetFilter">重置</button></div>
    </div></section>
    <section class="card tbl"><table>
      <thead><tr><th>序号</th><th>监控点</th><th>道路名称</th><th>检测时间</th><th>事件类型</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-if="!pagedRecords.length"><td colspan="7" class="emp">{{ loading ? '加载中...' : '暂无数据' }}</td></tr>
        <tr v-for="(r,i) in pagedRecords" :key="r.id">
          <td>{{ (currentPage-1)*pageSize + i + 1 }}</td>
          <td>{{ r.cameraName }}</td><td>{{ r.roadName }}</td><td>{{ r.time }}</td>
          <td>
            {{ r.eventType }}
            <span v-if="isMajorEvent(r) && r.status==='未处理'" class="major-badge">重大</span>
          </td>
          <td><span :class="['tag', r.status==='已处理'?'tp':'tu']">{{ r.status }}</span></td>
          <td class="ops">
            <button class="lk" @click="openDialog(r)">查看</button>
            <button v-if="r.status==='未处理'" class="lk" @click="updateStatus(r,'已处理')">标记已处理</button>
            <button v-else class="lk" @click="updateStatus(r,'未处理')">设为未处理</button>
            <button class="lk danger" @click="deleteRecord(r)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="pager"><span>{{ locale === 'zh' ? '共 ' : 'Total: ' }}{{ total }} {{ locale === 'zh' ? '条' : 'records' }}</span><select v-model.number="pageSize"><option v-for="s in [5,10,20]" :key="s" :value="s">{{ s }}{{ locale === 'zh' ? '/页' : '/page' }}</option></select><button :disabled="currentPage===1" @click="currentPage--">&lsaquo;</button><span>{{ currentPage }}</span><button :disabled="currentPage===totalPages" @click="currentPage++">&rsaquo;</button></div>
    </section>

    <!-- 详情弹窗 -->
    <div v-if="showDialog && activeRecord" class="dialog-mask" @click.self="closeDialog">
      <div class="dialog">
        <div class="dialog-head">
          <div>
            <div class="dialog-title">{{ activeRecord.eventType }} - {{ activeRecord.cameraName }}</div>
            <div class="dialog-sub">{{ activeRecord.time }} ｜ {{ activeRecord.roadName }}</div>
          </div>
          <button class="dialog-close" @click="closeDialog">&times;</button>
        </div>
        <div class="dialog-body">
          <img class="dialog-img" :src="activeRecord.snapshotUrl" alt="检测截图" />
          <div class="dialog-info">
            <div class="info-row"><span class="k">{{ locale === 'zh' ? '拥堵等级：' : 'Congestion Level: ' }}</span><span class="v">{{ activeRecord.congestionLevel }}</span></div>
            <div class="info-row"><span class="k">{{ locale === 'zh' ? '车辆总数：' : 'Total Vehicles: ' }}</span><span class="v">{{ activeRecord.vehicles }} 辆</span></div>
            <div class="info-row"><span class="k">{{ locale === 'zh' ? '平均速度：' : 'Average Speed: ' }}</span><span class="v">{{ activeRecord.avgSpeed }} km/h</span></div>
            <div class="info-row"><span class="k">{{ locale === 'zh' ? '事件说明：' : 'Event Description: ' }}</span><span class="v">{{ activeRecord.description }}</span></div>
            <div class="info-row"><span class="k">{{ locale === 'zh' ? '处理状态：' : 'Processing Status: ' }}</span><span class="v">{{ activeRecord.status }}</span></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<style scoped>
.page{display:flex;flex-direction:column;gap:16px}.head{display:flex;justify-content:space-between;align-items:center}.head h2{margin:0;font-size:22px;font-weight:900;color:var(--text-main)}.btn{height:36px;padding:0 14px;border:1px solid #e2e8f0;border-radius:10px;background:#fff;color:var(--primary-strong);font-size:13px;font-weight:700;cursor:pointer}.btn:hover{background:#f8fafc}.btn.pri{background:linear-gradient(135deg,#4BA3ED,#6366f1);color:#fff;border:none}.card{background:linear-gradient(180deg,#ffffff 0%,#f7fbff 100%);border:1px solid #d2dceb;border-radius:18px;padding:16px 18px;box-shadow:var(--shadow-float);transition:transform .26s cubic-bezier(0.34,1.56,0.64,1),box-shadow .26s ease,border-color .26s ease}.card:hover{transform:translateY(-3px);box-shadow:var(--shadow-float-hover);border-color:rgba(47,124,246,.38)}.row{display:grid;grid-template-columns:2fr 1fr auto;gap:10px;align-items:end}.f{display:flex;flex-direction:column;gap:4px}.f label{font-size:12px;color:#64748b;font-weight:600}.f input,.f select{height:36px;border:1px solid #e2e8f0;border-radius:10px;padding:0 10px;font-size:13px}.f.acts{display:flex;gap:8px}.tbl{overflow-x:auto;margin-top:16px}table{width:100%;min-width:800px;border-collapse:collapse;font-size:13px}th,td{padding:8px 6px;border-bottom:1px solid #f1f5f9;text-align:left}th{background:#f8fafc;color:#64748b;font-weight:700}tbody tr:hover{background:#fafcfd}.emp{text-align:center;color:#94a3b8}.tag{padding:2px 10px;border-radius:99px;font-size:11px;font-weight:700}.tp{background:#dcfce7;color:#15803d}.tu{background:#fef3c7;color:#92400e}.ops{white-space:nowrap}.lk{border:none;background:none;color:#4BA3ED;font-size:12px;cursor:pointer;margin-right:8px}.lk.danger{color:#ef4444}.pager{display:flex;justify-content:flex-end;align-items:center;gap:10px;margin-top:10px;font-size:12px;color:#64748b}.pager select{border:1px solid #e2e8f0;border-radius:99px;padding:4px 8px}.pager button{border:1px solid #e2e8f0;border-radius:99px;padding:2px 8px;background:#fff;cursor:pointer}.pager button:disabled{opacity:.4;cursor:default}
.dialog-mask{position:fixed;inset:0;background:rgba(15,23,42,.45);display:flex;align-items:center;justify-content:center;z-index:60}.dialog{width:860px;max-width:96%;background:#fff;border-radius:16px;box-shadow:0 20px 40px rgba(0,0,0,.35);overflow:hidden}.dialog-head{padding:16px 20px;border-bottom:1px solid #e2e8f0;display:flex;justify-content:space-between;align-items:center}.dialog-title{font-size:16px;font-weight:700;color:#0f172a}.dialog-sub{font-size:12px;color:#64748b;margin-top:4px}.dialog-close{background:none;border:none;font-size:22px;cursor:pointer;color:#94a3b8;padding:4px 8px}.dialog-close:hover{color:#0f172a}.dialog-body{padding:20px;display:grid;grid-template-columns:280px 1fr;gap:20px;align-items:start}.dialog-img{width:280px;border-radius:10px;object-fit:cover}.dialog-info{display:flex;flex-direction:column;gap:12px}.info-row{font-size:14px}.info-row .k{color:#64748b;margin-right:4px}.info-row .v{color:#0f172a;font-weight:600}.batch-btn{background:linear-gradient(135deg,#f97316,#ef4444);color:#fff;border:none;font-weight:800}.batch-btn:hover:not(:disabled){background:linear-gradient(135deg,#ea580c,#dc2626)}.batch-btn:disabled{opacity:.6;cursor:not-allowed}.major-badge{margin-left:6px;padding:1px 7px;border-radius:99px;background:#fee2e2;color:#dc2626;font-size:11px;font-weight:800;vertical-align:middle}@media(max-width:720px){.dialog-body{grid-template-columns:1fr}.dialog-img{width:100%}}
</style>
