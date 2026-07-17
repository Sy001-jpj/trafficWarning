<script setup>
import { ref } from 'vue'
import { useLocale } from '../composables/useLocale.js'
import { useTheme } from '../composables/useTheme.js'

defineOptions({ name: 'SettingsPage' })

const { locale, setLocale } = useLocale()
const { theme, setTheme } = useTheme()

// ── 实名认证 ──
const VERIFY_KEY = 'traffic_realname_verify'
const savedVerify = () => { try { return JSON.parse(localStorage.getItem(VERIFY_KEY) || 'null') } catch { return null } }
const verifyStatus = ref(savedVerify()?.status || 'unverified')
const verifyForm = ref({ name: savedVerify()?.name || '', idNumber: savedVerify()?.idNumber || '', phone: savedVerify()?.phone || '' })
const verifySubmitting = ref(false)

const submitVerify = () => {
  const { name, idNumber, phone } = verifyForm.value
  if (!name.trim() || !idNumber.trim() || !phone.trim()) {
    alert(locale.value === 'zh' ? '请完整填写所有认证信息' : 'Please fill in all verification fields')
    return
  }
  verifySubmitting.value = true
  setTimeout(() => {
    verifyStatus.value = 'verified'
    localStorage.setItem(VERIFY_KEY, JSON.stringify({ ...verifyForm.value, status: 'verified' }))
    verifySubmitting.value = false
    alert(locale.value === 'zh' ? '实名认证提交成功！' : 'Identity verification submitted successfully!')
  }, 1200)
}

const resetVerify = () => {
  verifyStatus.value = 'unverified'
  verifyForm.value = { name: '', idNumber: '', phone: '' }
  localStorage.removeItem(VERIFY_KEY)
}

const langOptions = [
  {
    value: 'zh',
    flag: '🇨🇳',
    name: '简体中文',
    nameEn: 'Simplified Chinese',
    desc: '默认语言，完整中文界面'
  },
  {
    value: 'en',
    flag: '🇺🇸',
    name: 'English',
    nameEn: '英语',
    desc: 'Switch interface to English'
  }
]

const themeOptions = [
  {
    value: 'light',
    icon: '☀️',
    name: '白昼模式',
    nameEn: 'Light Mode',
    desc: '浅色界面，适合明亮环境'
  },
  {
    value: 'dark',
    icon: '🌙',
    name: '夜间模式',
    nameEn: 'Dark Mode',
    desc: '深色界面，减轻眼部疲劳'
  }
]
</script>

<template>
  <div class="settings-page">
    <header class="settings-head">
      <h1>{{ locale === 'zh' ? '系统设置' : 'System Settings' }}</h1>
      <p>{{ locale === 'zh' ? '管理应用偏好与语言选项' : 'Manage preferences and language' }}</p>
    </header>

    <!-- 语言切换 -->
    <section class="settings-section">
      <h2 class="section-title">{{ locale === 'zh' ? '界面语言' : 'Interface Language' }}</h2>
      <p class="section-desc">{{ locale === 'zh' ? '选择你偏好的显示语言，即时切换' : 'Choose your preferred display language, switch instantly' }}</p>

      <div class="lang-list">
        <button
          v-for="lang in langOptions"
          :key="lang.value"
          :class="['lang-card', locale === lang.value && 'active']"
          @click="setLocale(lang.value)"
        >
          <span class="lang-flag">{{ lang.flag }}</span>
          <div class="lang-info">
            <div class="lang-name-row">
              <strong>{{ lang.name }}</strong>
              <span class="lang-name-en">{{ lang.nameEn }}</span>
            </div>
            <small>{{ lang.desc }}</small>
          </div>
          <span v-if="locale === lang.value" class="lang-check">✓</span>
        </button>
      </div>
    </section>

    <!-- 外观主题 -->
    <section class="settings-section">
      <h2 class="section-title">{{ locale === 'zh' ? '外观主题' : 'Appearance' }}</h2>
      <p class="section-desc">{{ locale === 'zh' ? '选择适合你的界面配色方案' : 'Choose your preferred color scheme' }}</p>

      <div class="lang-list">
        <button
          v-for="opt in themeOptions"
          :key="opt.value"
          :class="['lang-card', theme === opt.value && 'active']"
          @click="setTheme(opt.value)"
        >
          <span class="lang-flag">{{ opt.icon }}</span>
          <div class="lang-info">
            <div class="lang-name-row">
              <strong>{{ opt.name }}</strong>
              <span class="lang-name-en">{{ opt.nameEn }}</span>
            </div>
            <small>{{ opt.desc }}</small>
          </div>
          <span v-if="theme === opt.value" class="lang-check">✓</span>
        </button>
      </div>
    </section>

    <!-- 实名认证 -->
    <section class="settings-section">
      <h2 class="section-title">{{ locale === 'zh' ? '实名认证' : 'Identity Verification' }}</h2>
      <p class="section-desc">{{ locale === 'zh' ? '进行身份认证以获取完整系统权限' : 'Verify your identity to access full system permissions' }}</p>

      <div v-if="verifyStatus === 'verified'" class="verify-success">
        <span class="verify-badge">✓</span>
        <div>
          <strong>{{ locale === 'zh' ? '已通过实名认证' : 'Identity Verified' }}</strong>
          <p>{{ verifyForm.name }} · {{ verifyForm.idNumber }}</p>
        </div>
        <button class="reset-link" @click="resetVerify">{{ locale === 'zh' ? '重新认证' : 'Re-verify' }}</button>
      </div>

      <form v-else class="verify-form" @submit.prevent="submitVerify">
        <label class="field">
          <span>{{ locale === 'zh' ? '真实姓名' : 'Full Name' }}</span>
          <input v-model="verifyForm.name" :placeholder="locale === 'zh' ? '请输入身份证姓名' : 'Enter your legal name'" />
        </label>
        <label class="field">
          <span>{{ locale === 'zh' ? '身份证号' : 'ID Number' }}</span>
          <input v-model="verifyForm.idNumber" :placeholder="locale === 'zh' ? '请输入18位身份证号' : 'Enter 18-digit ID number'" maxlength="18" />
        </label>
        <label class="field">
          <span>{{ locale === 'zh' ? '手机号码' : 'Phone Number' }}</span>
          <input v-model="verifyForm.phone" :placeholder="locale === 'zh' ? '请输入手机号接收验证码' : 'Enter phone for verification code'" />
        </label>
        <button class="verify-btn" type="submit" :disabled="verifySubmitting">
          {{ verifySubmitting ? (locale === 'zh' ? '提交中...' : 'Submitting...') : (locale === 'zh' ? '提交认证' : 'Submit Verification') }}
        </button>
      </form>
    </section>

  </div>
</template>

<style scoped>
.settings-page {
  max-width: 680px;
  margin: 0 auto;
  padding: 8px 4px;
}

.settings-head {
  margin-bottom: 32px;
}
.settings-head h1 {
  margin: 0;
  font-size: 26px;
  font-weight: 800;
  color: var(--text-main);
  letter-spacing: -0.3px;
}
.settings-head p {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.settings-section {
  background: var(--surface);
  border: 1px solid var(--line-soft);
  border-radius: 20px;
  padding: 28px 30px;
  box-shadow:
    0 1px 3px rgba(15,23,42,.03),
    0 6px 18px rgba(15,23,42,.05);
  margin-bottom: 20px;
}
.section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 800;
  color: var(--text-main);
}
.section-desc {
  margin: 4px 0 20px;
  font-size: 13px;
  color: var(--text-muted);
  font-weight: 500;
}

/* Language Cards */
.lang-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.lang-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border: 2px solid var(--line-soft);
  border-radius: 16px;
  background: var(--surface-soft);
  cursor: pointer;
  text-align: left;
  transition: all .2s ease;
}
.lang-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 4px 20px rgba(75,163,237,.08);
  transform: translateY(-1px);
}
.lang-card.active {
  border-color: var(--primary);
  background: var(--primary-soft);
  box-shadow: 0 2px 14px rgba(75,163,237,.14);
}

.lang-flag {
  font-size: 36px;
  flex-shrink: 0;
}
.lang-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.lang-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.lang-name-row strong {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-main);
}
.lang-name-en {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}
.lang-info small {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.lang-check {
  position: absolute;
  top: 14px;
  right: 16px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

/* ── 实名认证 ── */
.verify-success {
  display: flex; align-items: center; gap: 14px;
  padding: 20px; border-radius: 16px;
  background: #f0fdf4; border: 1px solid #bbf7d0;
}
.verify-badge {
  width: 44px; height: 44px; border-radius: 50%;
  background: #22c55e; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 22px; font-weight: 700; flex-shrink: 0;
}
.verify-success strong { display: block; font-size: 15px; font-weight: 700; color: #0f172a; }
.verify-success p { margin: 3px 0 0; font-size: 13px; color: #64748b; }
.reset-link { margin-left: auto; border: none; background: none; color: #ef4444; font-size: 13px; font-weight: 600; cursor: pointer; }
.reset-link:hover { text-decoration: underline; }

.verify-form { display: flex; flex-direction: column; gap: 16px; }
.verify-form .field { display: flex; flex-direction: column; gap: 6px; }
.verify-form .field span { font-size: 13px; font-weight: 600; color: var(--text-secondary); }
.verify-form .field input {
  height: 42px; border: 1px solid #e2e8f0; border-radius: 12px;
  padding: 0 14px; font-size: 14px; background: #f8fafc; color: #0f172a;
}
.verify-form .field input:focus { outline: none; border-color: #4BA3ED; box-shadow: 0 0 0 3px rgba(75,163,237,.1); }

.verify-btn {
  height: 44px; border: none; border-radius: 14px;
  background: linear-gradient(135deg, #4BA3ED, #6366f1); color: #fff;
  font-size: 14px; font-weight: 700; cursor: pointer;
  transition: all .18s;
}
.verify-btn:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(75,163,237,.3); }
.verify-btn:disabled { opacity: .6; cursor: not-allowed; }

</style>
