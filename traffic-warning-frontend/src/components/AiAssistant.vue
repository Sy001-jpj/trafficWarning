<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'

import { useLocale } from '../composables/useLocale.js'

defineOptions({ name: 'AiAssistant' })

const { t, locale } = useLocale()
const DEEPSEEK_API_KEY = import.meta.env.VITE_DEEPSEEK_API_KEY || ''
const DEEPSEEK_URL = 'https://api.deepseek.com/v1/chat/completions'
const HISTORY_KEY = 'ai_chat_history_v2'
const CURRENT_KEY = 'ai_chat_current_v2'

interface ChatMessage { role: 'user' | 'assistant' | 'system'; content: string; timestamp: string }
interface Conversation { id: string; title: string; messages: ChatMessage[]; createdAt: string }
interface QuickPrompt { label: string; icon: string; text: string }

const quickPrompts = computed<QuickPrompt[]>(() => [
  { icon: '📊', label: t('aiAssistant.prompts.overview'), text: t('aiAssistant.prompts.overviewText') },
  { icon: '🔴', label: t('aiAssistant.prompts.top3'), text: t('aiAssistant.prompts.top3Text') },
  { icon: '🗺️', label: t('aiAssistant.prompts.route'), text: t('aiAssistant.prompts.routeText') },
  { icon: '⚠️', label: t('aiAssistant.prompts.alerts'), text: t('aiAssistant.prompts.alertsText') },
  { icon: '📡', label: t('aiAssistant.prompts.device'), text: t('aiAssistant.prompts.deviceText') },
  { icon: '💡', label: t('aiAssistant.prompts.optimize'), text: t('aiAssistant.prompts.optimizeText') },
])

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const aiLoading = ref(false)
const aiError = ref('')
const showPrompts = ref(true)
const chatBody = ref<HTMLElement | null>(null)
const currentConvId = ref('')
const showHistory = ref(false)
const conversations = ref<Conversation[]>([])

// ========== localStorage ==========
const loadConversations = () => { try { conversations.value = JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]') } catch { conversations.value = [] } }
const saveConversations = () => localStorage.setItem(HISTORY_KEY, JSON.stringify(conversations.value))
const saveCurrentConv = () => {
  const msgs = messages.value.filter(m => m.role !== 'system')
  if (msgs.length <= 1) return
  localStorage.setItem(CURRENT_KEY, JSON.stringify({ id: currentConvId.value, messages: msgs }))
}
const loadCurrentConv = () => {
  try {
    const raw = localStorage.getItem(CURRENT_KEY)
    if (raw) { const d = JSON.parse(raw); currentConvId.value = d.id || ''; messages.value = d.messages || []; showPrompts.value = messages.value.length <= 1; return true }
  } catch { /* */ }
  return false
}
const archiveCurrentConv = () => {
  const msgs = messages.value.filter(m => m.role !== 'system')
  if (msgs.length <= 1) return
  const userMsgs = msgs.filter(m => m.role === 'user')
  const title = userMsgs[0]?.content.substring(0, 30) + (userMsgs[0]?.content.length > 30 ? '…' : '') || t('aiAssistant.emptyHistory')
  const conv: Conversation = { id: currentConvId.value || Date.now().toString(36), title, messages: msgs, createdAt: new Date().toLocaleString(locale.value === 'zh' ? 'zh-CN' : 'en-US') }
  const idx = conversations.value.findIndex(c => c.id === conv.id)
  if (idx >= 0) conversations.value[idx] = conv; else conversations.value.unshift(conv)
  if (conversations.value.length > 30) conversations.value = conversations.value.slice(0, 30)
  saveConversations()
}
const newConversation = () => {
  if (messages.value.length >= 1) archiveCurrentConv()
  currentConvId.value = Date.now().toString(36)
  messages.value = []
  aiError.value = ''; showPrompts.value = true; showHistory.value = false
  localStorage.removeItem(CURRENT_KEY)
}
const deleteConversation = (id: string) => {
  conversations.value = conversations.value.filter(c => c.id !== id); saveConversations()
  if (currentConvId.value === id) newConversation()
}
const loadConversation = (conv: Conversation) => {
  if (messages.value.length > 1) archiveCurrentConv()
  currentConvId.value = conv.id; messages.value = conv.messages; showPrompts.value = false; showHistory.value = false; aiError.value = ''
}

// =======Context Data=======
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'
const trafficData = ref(''); const deviceData = ref(''); const eventData = ref('')
const requestApi = async <T>(path: string): Promise<T> => {
  const r = await fetch(`${API_BASE_URL}${path}`)
  if (!r.ok) throw new Error(t('common.serverError'))
  const j = await r.json()
  if (j.code !== 200) throw new Error(j.message || t('common.serverError'))
  return j.data as T
}
const collectContextData = async () => {
  const [t, d, e] = await Promise.allSettled([requestApi<unknown>('/congestion/road-network'), requestApi<unknown>('/devices'), requestApi<unknown>('/events')])
  if (t.status === 'fulfilled') trafficData.value = JSON.stringify(t.value)
  if (d.status === 'fulfilled') deviceData.value = JSON.stringify(d.value)
  if (e.status === 'fulfilled') eventData.value = JSON.stringify(e.value)
}

// ========== DeepSeek ==========
const buildSystemPrompt = () => {
  const p = [t('aiAssistant.systemPrompt')]
  if (trafficData.value) p.push(`[Traffic] ${trafficData.value}`)
  if (deviceData.value) p.push(`[Device] ${deviceData.value}`)
  if (eventData.value) p.push(`[Alert] ${eventData.value}`)
  return p.join('\n')
}
const sendMessage = async () => {
  const text = inputText.value.trim(); if (!text || aiLoading.value) return
  showPrompts.value = false; messages.value.push({ role: 'user', content: text, timestamp: now() }); inputText.value = ''; aiLoading.value = true; aiError.value = ''
  await scrollToBottom()
  try {
    if (!DEEPSEEK_API_KEY) { messages.value.push({ role: 'assistant', content: t('aiAssistant.noKey'), timestamp: now() }); return }
    const r = await fetch(DEEPSEEK_URL, { method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${DEEPSEEK_API_KEY}` }, body: JSON.stringify({ model: 'deepseek-chat', messages: [{ role: 'system', content: buildSystemPrompt() }, ...messages.value.map(m => ({ role: m.role, content: m.content }))], temperature: 0.7, max_tokens: 1500 }) })
    if (!r.ok) throw new Error(`API ${r.status}`)
    const d = await r.json(); messages.value.push({ role: 'assistant', content: d.choices?.[0]?.message?.content || 'No reply', timestamp: now() })
  } catch (e: unknown) { aiError.value = e instanceof Error ? e.message : t('common.serverError') } finally { aiLoading.value = false; await scrollToBottom() }
}
const askPreset = (p: QuickPrompt) => { inputText.value = p.text; sendMessage() }

// ========== 工具 ==========
const now = () => `${String(new Date().getHours()).padStart(2, '0')}:${String(new Date().getMinutes()).padStart(2, '0')}`
const scrollToBottom = async () => { await nextTick(); chatBody.value && (chatBody.value.scrollTop = chatBody.value.scrollHeight) }
watch(() => messages.value.length, () => { scrollToBottom(); saveCurrentConv() })
onMounted(() => { loadConversations(); collectContextData(); if (!loadCurrentConv()) { currentConvId.value = Date.now().toString(36); messages.value = []; showPrompts.value = true } })
</script>

<template>
  <div class="ai-page">
    <!-- 顶栏 -->
    <header class="ai-topbar">
      <div class="ai-brand">
        <div class="ai-logo">
          <svg width="22" height="22" viewBox="0 0 34 32" fill="none">
            <path d="M8 11 L2 7 L4 16 L2 25 L8 21Z" fill="#fff"/>
            <ellipse cx="18" cy="16" rx="10" ry="8.5" fill="#fff"/>
            <circle cx="23" cy="13" r="1.4" fill="#4f46e5"/>
          </svg>
        </div>
        <div class="ai-brand-text">
          <span class="ai-brand-name">{{ t('aiAssistant.title') }}</span>
          <span class="ai-brand-sub">{{ t('aiAssistant.subtitle') }}</span>
        </div>
        <span v-if="DEEPSEEK_API_KEY" class="ai-status">{{ t('aiAssistant.online') }}</span>
        <span v-else class="ai-status off">{{ t('aiAssistant.offline') }}</span>
      </div>
      <div class="ai-actions">
        <button class="ai-btn-outline" @click="showHistory = !showHistory">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          {{ t('aiAssistant.history') }}
        </button>
        <button class="ai-btn-primary" @click="newConversation">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
          {{ t('aiAssistant.newChat') }}
        </button>
      </div>
    </header>

    <!-- 历史面板 -->
    <transition name="drawer">
      <aside v-if="showHistory" class="ai-drawer">
        <div class="ai-drawer-head">
          <h3>{{ t('aiAssistant.historyTitle') }}</h3>
          <button class="ai-drawer-close" @click="showHistory = false">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
          </button>
        </div>
        <div v-if="!conversations.length" class="ai-drawer-empty">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <span>{{ t('aiAssistant.emptyHistory') }}</span>
        </div>
        <div class="ai-drawer-list">
          <button v-for="c in conversations" :key="c.id" :class="['ai-drawer-item', currentConvId === c.id && 'active']" @click="loadConversation(c)">
            <span class="ai-drawer-icon">💬</span>
            <span class="ai-drawer-title">{{ c.title }}</span>
            <span class="ai-drawer-time">{{ c.createdAt }}</span>
            <button class="ai-drawer-del" @click.stop="deleteConversation(c.id)">
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
            </button>
          </button>
        </div>
      </aside>
    </transition>

    <!-- 聊天区 -->
    <div class="ai-chat">
      <div ref="chatBody" class="ai-chat-body">
        <!-- 欢迎页 -->
        <div v-if="showPrompts && messages.length <= 1" class="ai-welcome">
          <h2 class="ai-welcome-title">{{ t('aiAssistant.welcome') }}</h2>
          <p class="ai-welcome-desc">{{ t('aiAssistant.welcomeDesc') }}</p>
          <div class="ai-prompts">
            <button v-for="p in quickPrompts" :key="p.label" class="ai-prompt-card" :disabled="aiLoading" @click="askPreset(p)">
              <span class="ai-prompt-icon">{{ p.icon }}</span>
              <span class="ai-prompt-label">{{ p.label }}</span>
              <span class="ai-prompt-arrow">→</span>
            </button>
          </div>
        </div>

        <!-- 消息列表 -->
        <div v-for="(m, i) in messages" :key="i" :class="['ai-msg', m.role]">
          <div v-if="m.role === 'assistant'" class="ai-msg-avatar bot">
            <svg width="18" height="18" viewBox="0 0 34 32" fill="none"><path d="M8 11 L2 7 L4 16 L2 25 L8 21Z" fill="#4f46e5"/><ellipse cx="18" cy="16" rx="10" ry="8.5" fill="#4f46e5"/><circle cx="23" cy="13" r="1.4" fill="#fff"/></svg>
          </div>
          <div class="ai-msg-body">
            <div class="ai-bubble" v-html="fmt(m.content)"/>
            <span class="ai-msg-time">{{ m.timestamp }}</span>
          </div>
          <div v-if="m.role === 'user'" class="ai-msg-avatar you">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="aiLoading" class="ai-msg assistant">
          <div class="ai-msg-avatar bot">
            <svg width="18" height="18" viewBox="0 0 34 32" fill="none"><path d="M8 11 L2 7 L4 16 L2 25 L8 21Z" fill="#4f46e5"/><ellipse cx="18" cy="16" rx="10" ry="8.5" fill="#4f46e5"/></svg>
          </div>
          <div class="ai-msg-body">
            <div class="ai-typing"><span/><span/><span/></div>
          </div>
        </div>
      </div>

      <!-- 错误提示 -->
      <transition name="fade">
        <div v-if="aiError" class="ai-error">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
          <span>{{ aiError }}</span>
          <button @click="aiError = ''">✕</button>
        </div>
      </transition>

      <!-- 输入区 -->
      <div class="ai-input-bar">
        <div class="ai-input-wrap">
          <textarea
            ref="taRef"
            v-model="inputText"
            :placeholder="t('aiAssistant.inputPlaceholder')"
            :disabled="aiLoading"
            rows="1"
            @keydown.enter.exact.prevent="sendMessage"
            @input="autoGrow"
          />
          <button class="ai-send" :disabled="aiLoading || !inputText.trim()" @click="sendMessage" :title="t('aiAssistant.send')">
            <svg v-if="!aiLoading" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
            <span v-else class="ai-spinner"/>
          </button>
        </div>
        <p class="ai-input-hint">{{ t('aiAssistant.inputHint') }}</p>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
function fmt(t: string) { return t.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/\*\*(.+?)\*\*/g,'<b>$1</b>').replace(/\n/g,'<br>').replace(/`([^`]+)`/g,'<code>$1</code>') }
function autoGrow(e: Event) { const el = e.target as HTMLTextAreaElement; el.style.height = 'auto'; el.style.height = Math.min(el.scrollHeight, 120) + 'px' }
</script>

<style scoped>
.ai-page {
  display: flex; flex-direction: column; gap: 0;
  height: calc(100vh - 100px); min-height: 600px;
  position: relative;
  background: #ffffff;
  border-radius: 20px;
  overflow: hidden;
  border: 1px solid rgba(226,232,240,0.8);
  box-shadow:
    0 2px 12px rgba(15,23,42,0.04),
    0 12px 40px rgba(15,23,42,0.08),
    0 24px 72px rgba(15,23,42,0.1);
}

/* ===== 顶栏 ===== */
.ai-topbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 24px;
  background: #ffffff;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}
.ai-brand { display: flex; align-items: center; gap: 12px; }
.ai-logo {
  width: 42px; height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, #4BA3ED, #7c3aed);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 14px rgba(75,163,237,.3);
}
.ai-brand-text { display: flex; flex-direction: column; gap: 1px; }
.ai-brand-name { font-size: 15px; font-weight: 800; color: #0f172a; letter-spacing: -0.2px; }
.ai-brand-sub { font-size: 11px; color: #94a3b8; font-weight: 500; }
.ai-status {
  font-size: 11px; font-weight: 700;
  padding: 3px 10px; border-radius: 99px;
  background: #ecfdf5; color: #059669;
}
.ai-status.off { background: #fef2f2; color: #dc2626; }

.ai-actions { display: flex; gap: 8px; }
.ai-btn-outline {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px;
  border: 1px solid #e2e8f0; border-radius: 12px;
  background: #fff; color: #475569;
  font-size: 12.5px; font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
}
.ai-btn-outline:hover {
  border-color: #93c5fd; color: #2563eb; background: #eff6ff;
}
.ai-btn-primary {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 16px;
  border: none; border-radius: 12px;
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff; font-size: 12.5px; font-weight: 700;
  cursor: pointer;
  transition: all 0.18s ease;
  box-shadow: 0 2px 10px rgba(75,163,237,.25);
}
.ai-btn-primary:hover {
  box-shadow: 0 6px 20px rgba(75,163,237,.4);
  transform: translateY(-1px);
}

/* ===== 历史抽屉 ===== */
.ai-drawer {
  position: absolute; top: 0; right: 0; bottom: 0; z-index: 200;
  width: 320px;
  background: #ffffff;
  border-left: 1px solid #f1f5f9;
  display: flex; flex-direction: column;
  box-shadow: -16px 0 48px rgba(15,23,42,.1);
  border-radius: 0 20px 20px 0;
}
.ai-drawer-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18px 20px;
  border-bottom: 1px solid #f1f5f9;
}
.ai-drawer-head h3 { margin: 0; font-size: 15px; font-weight: 800; color: #0f172a; }
.ai-drawer-close {
  border: none; background: none; color: #94a3b8; cursor: pointer;
  padding: 6px; border-radius: 8px; transition: all .15s;
}
.ai-drawer-close:hover { color: #0f172a; background: #f1f5f9; }
.ai-drawer-empty {
  flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 12px; color: #94a3b8; font-size: 13px;
}
.ai-drawer-list {
  flex: 1; overflow-y: auto; padding: 10px;
  display: flex; flex-direction: column; gap: 2px;
}
.ai-drawer-item {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px;
  border: none; border-radius: 12px;
  background: transparent; text-align: left;
  cursor: pointer; width: 100%;
  transition: all .15s;
}
.ai-drawer-item:hover, .ai-drawer-item.active { background: #f8fafc; }
.ai-drawer-icon { font-size: 16px; flex-shrink: 0; }
.ai-drawer-title {
  flex: 1; font-size: 13px; font-weight: 600; color: #1e293b;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.ai-drawer-time { font-size: 11px; color: #94a3b8; flex-shrink: 0; }
.ai-drawer-del {
  border: none; background: none; color: #cbd5e1; cursor: pointer;
  padding: 4px; border-radius: 6px; opacity: 0; transition: .12s; flex-shrink: 0;
}
.ai-drawer-item:hover .ai-drawer-del { opacity: 1; }
.ai-drawer-del:hover { color: #ef4444; background: #fef2f2; }

/* ===== 聊天区 ===== */
.ai-chat { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.ai-chat-body {
  flex: 1; overflow-y: auto;
  padding: 28px 28px 8px;
  display: flex; flex-direction: column; gap: 18px;
  scroll-behavior: smooth;
  background: linear-gradient(180deg, #fafbfc 0%, #ffffff 60%);
}
.ai-chat-body::-webkit-scrollbar { width: 5px; }
.ai-chat-body::-webkit-scrollbar-thumb { background: #e2e8f0; border-radius: 99px; }

/* ===== 欢迎页 ===== */
.ai-welcome {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: flex-start;
  text-align: center; padding: 60px 20px 20px; gap: 16px;
}
.ai-welcome-title { margin: 0; font-size: 26px; font-weight: 800; color: #0f172a; letter-spacing: -0.5px; }
.ai-welcome-desc { margin: 0; font-size: 14px; color: #64748b; max-width: 440px; line-height: 1.7; }

.ai-prompts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  max-width: 600px;
  width: 100%;
  margin-top: 12px;
}
.ai-prompt-card {
  display: flex; align-items: center; gap: 8px;
  padding: 13px 16px;
  border: 1px solid #e2e8f0; border-radius: 14px;
  background: #ffffff;
  font-size: 13px; font-weight: 600; color: #334155;
  cursor: pointer;
  transition: all 0.2s ease;
  text-align: left;
}
.ai-prompt-card:hover {
  border-color: #93c5fd;
  background: #f8faff;
  color: #1e40af;
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(75,163,237,.12);
}
.ai-prompt-icon { font-size: 20px; flex-shrink: 0; }
.ai-prompt-label { flex: 1; }
.ai-prompt-arrow { color: #cbd5e1; font-size: 14px; transition: all .2s; }
.ai-prompt-card:hover .ai-prompt-arrow { color: #4BA3ED; transform: translateX(2px); }

/* ===== 消息 ===== */
.ai-msg {
  display: flex; gap: 12px; max-width: 720px;
  animation: msgIn 0.3s ease;
}
.ai-msg.user { align-self: flex-end; flex-direction: row-reverse; }
.ai-msg.assistant { align-self: flex-start; }

.ai-msg-avatar {
  width: 36px; height: 36px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.ai-msg-avatar.bot {
  background: linear-gradient(135deg, #eff6ff, #eef2ff);
  box-shadow: 0 2px 8px rgba(75,163,237,.1);
}
.ai-msg-avatar.you {
  background: linear-gradient(135deg, #ecfdf5, #d1fae5);
  color: #059669;
}

.ai-msg-body { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.ai-bubble {
  padding: 12px 18px; border-radius: 18px;
  font-size: 14px; line-height: 1.7;
  word-break: break-word;
}
.ai-msg.assistant .ai-bubble {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-top-left-radius: 6px;
  color: #1e293b;
}
.ai-msg.user .ai-bubble {
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff;
  border-top-right-radius: 6px;
  box-shadow: 0 6px 18px rgba(75,163,237,.25);
}
.ai-bubble :deep(b) { font-weight: 800; color: inherit; }
.ai-bubble :deep(code) {
  padding: 2px 7px; border-radius: 5px;
  background: rgba(0,0,0,.06); font-size: 12.5px;
}
.ai-msg-time { font-size: 11px; color: #94a3b8; padding: 0 4px; }

/* ===== 打字动画 ===== */
.ai-typing {
  display: flex; align-items: center; gap: 5px;
  padding: 14px 18px;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 18px; border-top-left-radius: 6px;
}
.ai-typing span {
  width: 7px; height: 7px; border-radius: 50%;
  background: #93c5fd;
  display: block;
  animation: bounce 1.4s infinite ease-in-out;
}
.ai-typing span:nth-child(2) { animation-delay: .2s; }
.ai-typing span:nth-child(3) { animation-delay: .4s; }

/* ===== 错误 ===== */
.ai-error {
  display: flex; align-items: center; gap: 8px;
  margin: 0 12px 8px; padding: 10px 14px;
  border-radius: 10px;
  background: #fef2f2; border: 1px solid #fecaca;
  color: #b91c1c; font-size: 12.5px; font-weight: 600;
}
.ai-error button { border: none; background: none; color: #b91c1c; cursor: pointer; margin-left: auto; }

/* ===== 输入区 ===== */
.ai-input-bar { padding: 12px 24px 20px; flex-shrink: 0; }
.ai-input-wrap {
  display: flex; align-items: flex-end; gap: 10px;
  padding: 14px 16px 14px 22px;
  background: #ffffff;
  border: 2px solid #e2e8f0;
  border-radius: 20px;
  transition: border-color .18s, box-shadow .18s;
}
.ai-input-wrap:focus-within {
  border-color: #4BA3ED;
  box-shadow: 0 0 0 5px rgba(75,163,237,.1);
}
.ai-input-wrap textarea {
  flex: 1; border: none; outline: none; resize: none;
  padding: 10px 0; font-size: 15px; font-family: inherit;
  line-height: 1.6; max-height: 140px;
  background: transparent; color: #0f172a;
}
.ai-input-wrap textarea::placeholder { color: #94a3b8; }
.ai-send {
  flex-shrink: 0; width: 44px; height: 44px;
  border: none; border-radius: 14px;
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  cursor: pointer; transition: all .18s ease;
  box-shadow: 0 2px 10px rgba(75,163,237,.2);
}
.ai-send:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(75,163,237,.35);
  transform: scale(1.05);
}
.ai-send:disabled { opacity: .35; cursor: not-allowed; }
.ai-input-hint { margin: 8px 0 0 6px; font-size: 11px; color: #94a3b8; font-weight: 500; }

.ai-spinner {
  width: 18px; height: 18px;
  border: 2px solid rgba(255,255,255,.3);
  border-top-color: #fff; border-radius: 50%;
  animation: spin .6s linear infinite;
}

/* ===== 动画 ===== */
@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .3; }
  30% { transform: translateY(-8px); opacity: 1; }
}
@keyframes spin { to { transform: rotate(360deg); } }
@keyframes msgIn { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

.drawer-enter-active, .drawer-leave-active { transition: all .25s ease; }
.drawer-enter-from, .drawer-leave-to { opacity: 0; transform: translateX(20px); }
.fade-enter-active, .fade-leave-active { transition: all .2s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-4px); }
</style>
