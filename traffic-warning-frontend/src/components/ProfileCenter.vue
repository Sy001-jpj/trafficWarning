<script setup>
import { computed, ref } from 'vue'
import { useLocale } from '../composables/useLocale.js'

const { t } = useLocale()

const CURRENT_USER_KEY = 'traffic_current_user'
const AVATAR_KEY = 'traffic_user_avatar'

const readUser = () => {
  try {
    return JSON.parse(localStorage.getItem(CURRENT_USER_KEY) || '{}')
  } catch {
    return {}
  }
}

const user = ref(readUser())
const nickname = ref(user.value.displayName || user.value.username || t('sidebar.role'))
const phone = ref(user.value.phone || '')
const email = ref(user.value.email || '')
const avatarDataUrl = ref(localStorage.getItem(AVATAR_KEY) || '')
const uploadName = ref('')

const accountName = computed(() => user.value.username || 'admin')
const userRole = computed(() => user.value.role || t('userManagement.admin'))
const userStatus = computed(() => user.value.status || t('userManagement.enabled'))
const createdAt = computed(() => user.value.createdAt || '—')

const userInitial = computed(() => nickname.value.slice(0, 1).toUpperCase())
const hasAvatar = computed(() => !!avatarDataUrl.value)

const saveUser = () => {
  const nextUser = { ...user.value }
  if (nickname.value.trim()) nextUser.displayName = nickname.value.trim()
  if (phone.value.trim()) nextUser.phone = phone.value.trim()
  if (email.value.trim()) nextUser.email = email.value.trim()
  localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(nextUser))
  user.value = nextUser
  window.dispatchEvent(new CustomEvent('profile-updated', { detail: nextUser }))
}

const saveContact = () => {
  saveUser()
  alert(t('common.save') + ' ✓')
}

const handleAvatarUpload = (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  uploadName.value = file.name

  const reader = new FileReader()
  reader.onload = (e) => {
    avatarDataUrl.value = e.target.result
    localStorage.setItem(AVATAR_KEY, e.target.result)
    window.dispatchEvent(new CustomEvent('avatar-updated', { detail: e.target.result }))
    uploadName.value = file.name
  }
  reader.readAsDataURL(file)
}

const removeAvatar = () => {
  avatarDataUrl.value = ''
  uploadName.value = ''
  localStorage.removeItem(AVATAR_KEY)
  window.dispatchEvent(new CustomEvent('avatar-updated', { detail: '' }))
}
</script>

<template>
  <div class="profile-page">
    <!-- Hero 区域 -->
    <section class="profile-hero">
      <div class="hero-main">
        <label class="avatar-upload-trigger" title="点击更换头像">
          <input type="file" accept="image/*" @change="handleAvatarUpload" />
          <div v-if="hasAvatar" class="avatar-img-wrap">
            <img :src="avatarDataUrl" alt="头像" class="avatar-img" />
            <span class="avatar-overlay">换</span>
          </div>
          <div v-else class="avatar-letter">{{ userInitial }}</div>
        </label>
        <div class="hero-info">
          <h1>{{ nickname }}</h1>
          <p>{{ t('profileCenter.subtitle') }}</p>
          <span class="hero-role-tag">{{ userRole }}</span>
        </div>
      </div>
      <div class="hero-meta">
        <div class="meta-item">
          <span class="meta-label">{{ t('profileCenter.username') }}</span>
          <strong>{{ accountName }}</strong>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('profileCenter.role') }}</span>
          <strong>{{ userRole }}</strong>
        </div>
        <div class="meta-item">
          <span class="meta-label">{{ t('profileCenter.createdAt') }}</span>
          <strong>{{ createdAt }}</strong>
        </div>
      </div>
    </section>

    <!-- 编辑面板 -->
    <section class="profile-grid">
      <!-- 昵称 & 联系方式 -->
      <article class="panel scroll-reveal">
        <div class="panel-head">
          <h2>{{ t('profileCenter.editProfile') }}</h2>
          <p>{{ t('profileCenter.subtitle') }}</p>
        </div>
        <label class="field">
          <span>{{ t('profileCenter.displayName') }}</span>
          <input v-model="nickname" :placeholder="t('profileCenter.displayName')" />
        </label>
        <label class="field">
          <span>{{ t('profileCenter.phone') }}</span>
          <input v-model="phone" placeholder="+86" />
        </label>
        <label class="field">
          <span>{{ t('profileCenter.email') }}</span>
          <input v-model="email" placeholder="email@example.com" />
        </label>
        <button class="btn-primary" type="button" @click="saveContact">
          {{ t('common.save') }}
        </button>
      </article>

      <!-- 头像上传 -->
      <article class="panel scroll-reveal">
        <div class="panel-head">
          <h2>{{ t('login.loginTitle') }} {{ t('login.registerNow') }}</h2>
          <p>Avatar</p>
        </div>
        <div class="avatar-preview-box">
          <div v-if="hasAvatar" class="avatar-preview-img-wrap">
            <img :src="avatarDataUrl" alt="头像预览" class="avatar-preview-img" />
          </div>
          <div v-else class="avatar-preview-letter">{{ userInitial }}</div>
          <div>
            <strong>{{ hasAvatar ? uploadName || '自定义头像' : '字母头像' }}</strong>
            <p>{{ hasAvatar ? '图片已上传，将在顶栏和侧栏同步显示' : '点击下方上传本地图片作为头像' }}</p>
          </div>
        </div>
        <label class="upload-area">
          <input type="file" accept="image/*" @change="handleAvatarUpload" />
          <span class="upload-icon">📷</span>
          <span class="upload-text">{{ t('profileCenter.editProfile') }}</span>
          <small>{{ uploadName || '支持 JPG / PNG，自动替换' }}</small>
        </label>
        <button v-if="hasAvatar" class="btn-remove" type="button" @click="removeAvatar">
          移除头像，恢复字母头像
        </button>
      </article>

      <!-- 身份状态 -->
      <article class="panel scroll-reveal">
        <div class="panel-head">
          <h2>{{ t('settings.title') }}</h2>
          <p>Verification</p>
        </div>
        <div class="status-grid">
          <div class="status-item">
            <span class="status-key">{{ t('profileCenter.role') }}</span>
            <strong>{{ userRole }}</strong>
          </div>
          <div class="status-item">
            <span class="status-key">{{ t('userManagement.status') }}</span>
            <span class="status-badge on">{{ userStatus }}</span>
          </div>
        </div>
        <div class="steps">
          <div class="step done">✓ 账号已登录</div>
          <div class="step">○ 待接入实名验证</div>
          <div class="step">○ 待接入权限审核</div>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.profile-page { display: flex; flex-direction: column; gap: 18px; }

/* ===== Hero ===== */
.profile-hero {
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  min-height: 160px; padding: 28px 32px;
  border-radius: 22px; border: 1px solid #e8ecf1;
  background: linear-gradient(135deg, #fce4ec 0%, #e3f2fd 100%);
  box-shadow: 0 1px 4px rgba(15,23,42,.03), 0 8px 24px rgba(15,23,42,.06);
}
.hero-main { display: flex; align-items: center; gap: 18px; flex: 1; min-width: 0; }

/* Avatar upload trigger */
.avatar-upload-trigger { position: relative; cursor: pointer; flex-shrink: 0; }
.avatar-upload-trigger input { display: none; }
.avatar-letter {
  width: 80px; height: 80px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff; font-size: 32px; font-weight: 800;
  box-shadow: 0 8px 24px rgba(75,163,237,.25);
  transition: transform .2s ease, box-shadow .2s ease;
}
.avatar-upload-trigger:hover .avatar-letter { transform: scale(1.05); }
.avatar-img-wrap { position: relative; width: 80px; height: 80px; border-radius: 50%; overflow: hidden; box-shadow: 0 8px 24px rgba(15,23,42,.15); }
.avatar-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-overlay {
  position: absolute; inset: 0; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,.4); color: #fff; font-size: 14px; font-weight: 700;
  opacity: 0; transition: opacity .2s;
}
.avatar-img-wrap:hover .avatar-overlay { opacity: 1; }

.hero-info { min-width: 0; }
.hero-info h1 { margin: 0; font-size: 24px; font-weight: 800; color: #0f172a; }
.hero-info p { margin: 4px 0 0; color: #64748b; font-size: 13px; }
.hero-role-tag {
  display: inline-block; margin-top: 8px; padding: 3px 12px;
  border-radius: 99px; background: rgba(255,255,255,.7);
  font-size: 12px; font-weight: 700; color: #475569;
}

.hero-meta { display: flex; gap: 20px; flex-shrink: 0; }
.meta-item { text-align: center; }
.meta-label { display: block; font-size: 11px; color: #94a3b8; font-weight: 600; }
.meta-item strong { display: block; margin-top: 4px; font-size: 16px; font-weight: 800; color: #0f172a; }

/* ===== Grid ===== */
.profile-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }

.panel {
  min-height: 280px; padding: 22px; border-radius: 20px;
  background: #fff; border: 1px solid #e8ecf1;
  box-shadow: 0 1px 3px rgba(15,23,42,.03), 0 6px 18px rgba(15,23,42,.05);
  display: flex; flex-direction: column;
  transition: transform .24s ease, box-shadow .24s ease;
}
.panel:hover { transform: translateY(-2px); box-shadow: 0 2px 6px rgba(15,23,42,.05), 0 12px 32px rgba(15,23,42,.1); }

.panel-head { margin-bottom: 18px; }
.panel-head h2 { margin: 0; font-size: 17px; font-weight: 800; color: #0f172a; }
.panel-head p  { margin: 2px 0 0; font-size: 12px; color: #94a3b8; font-weight: 500; }

/* ===== Fields ===== */
.field { display: flex; flex-direction: column; gap: 6px; margin-bottom: 12px; }
.field span { font-size: 12px; color: #64748b; font-weight: 600; }
.field input {
  height: 40px; border: 1px solid #e2e8f0; border-radius: 12px;
  padding: 0 12px; background: #f8fafc; color: #0f172a; font-size: 14px;
}
.field input:focus { outline: none; border-color: #4BA3ED; box-shadow: 0 0 0 3px rgba(75,163,237,.1); }

.btn-primary {
  height: 40px; margin-top: auto; border: none; border-radius: 12px;
  background: linear-gradient(135deg, #4BA3ED, #6366f1); color: #fff;
  font-size: 14px; font-weight: 700; cursor: pointer;
  transition: transform .16s ease, box-shadow .16s ease;
}
.btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(75,163,237,.3); }

/* ===== Avatar Preview ===== */
.avatar-preview-box {
  display: flex; align-items: center; gap: 14px; padding: 14px;
  border-radius: 16px; background: #f8fafc; margin-bottom: 16px;
}
.avatar-preview-letter {
  width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #4BA3ED, #6366f1);
  color: #fff; font-size: 22px; font-weight: 800;
}
.avatar-preview-img-wrap { width: 56px; height: 56px; border-radius: 50%; overflow: hidden; flex-shrink: 0; }
.avatar-preview-img { width: 100%; height: 100%; object-fit: cover; }
.avatar-preview-box strong { display: block; font-size: 14px; font-weight: 700; color: #0f172a; }
.avatar-preview-box p { margin: 3px 0 0; font-size: 12px; color: #94a3b8; }

.upload-area {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  min-height: 90px; border: 2px dashed #cbd5e1; border-radius: 16px;
  background: #fafcfd; cursor: pointer; transition: all .18s;
  margin-top: auto;
}
.upload-area:hover { border-color: #4BA3ED; background: #f0f6ff; }
.upload-area input { display: none; }
.upload-icon { font-size: 28px; }
.upload-text { font-size: 13px; font-weight: 700; color: #4BA3ED; }
.upload-area small { font-size: 11px; color: #94a3b8; }

.btn-remove {
  margin-top: 10px; padding: 8px 0; border: none; background: transparent;
  color: #ef4444; font-size: 12px; font-weight: 600; cursor: pointer;
}
.btn-remove:hover { text-decoration: underline; }

/* ===== Status ===== */
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 16px; }
.status-item { padding: 14px; border-radius: 14px; background: #f8fafc; }
.status-key { display: block; font-size: 11px; color: #94a3b8; font-weight: 600; }
.status-item strong { display: block; margin-top: 4px; font-size: 16px; font-weight: 800; color: #0f172a; }
.status-badge.on {
  display: inline-block; margin-top: 4px; padding: 2px 10px;
  border-radius: 99px; background: #ecfdf5; color: #059669;
  font-size: 13px; font-weight: 700;
}

.steps { display: flex; flex-direction: column; gap: 8px; margin-top: auto; }
.step {
  padding: 10px 14px; border-radius: 12px; background: #f8fafc;
  font-size: 13px; color: #94a3b8; font-weight: 600;
}
.step.done { background: #eff6ff; color: #4BA3ED; font-weight: 700; }

/* ===== Responsive ===== */
@media (max-width: 1040px) { .profile-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 720px) {
  .profile-hero { flex-direction: column; align-items: flex-start; }
  .hero-meta { width: 100%; justify-content: space-around; }
}
</style>
