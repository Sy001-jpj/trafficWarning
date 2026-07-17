<script setup>
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useLocale } from '../composables/useLocale.js'

const { t } = useLocale()
const router = useRouter()
const emit = defineEmits(['login-success'])

// 当前模式：login / register / forgot
const mode = ref('login')

// 密码重置请求表单
const resetForm = ref({ username: '', phone: '', newPassword: '' })
const resetSubmitting = ref(false)
const PASSWORD_REQUESTS_KEY = 'traffic_password_requests'

const submitPasswordReset = () => {
  const { username, phone, newPassword } = resetForm.value
  if (!username.trim() || !phone.trim() || !newPassword.trim()) {
    alert(t('login.alertFillAll'))
    return
  }
  if (newPassword.length < 6) {
    alert(t('login.alertPasswordLength'))
    return
  }
  resetSubmitting.value = true
  setTimeout(() => {
    const requests = JSON.parse(localStorage.getItem(PASSWORD_REQUESTS_KEY) || '[]')
    requests.unshift({
      id: Date.now().toString(36),
      username: username.trim(),
      phone: phone.trim(),
      newPassword: newPassword.trim(),
      status: 'pending',
      createdAt: new Date().toLocaleString('zh-CN')
    })
    localStorage.setItem(PASSWORD_REQUESTS_KEY, JSON.stringify(requests.slice(0, 50)))
    resetSubmitting.value = false
    alert('密码重置请求已提交，请等待管理员审核')
    resetForm.value = { username: '', phone: '', newPassword: '' }
    mode.value = 'login'
  }, 1000)
}

// 密码可见切换
const showPassword = ref(false)
const togglePassword = () => { showPassword.value = !showPassword.value }
const accountFocused = ref(false)
const passwordFocused = ref(false)
const updateAccountActiveState = () => {
  const activeAccount = mode.value === 'register'
    ? registerForm.value.username
    : mode.value === 'forgot'
      ? resetForm.value.username
      : loginForm.value.username
  updateEmailActive(accountFocused.value || Boolean(activeAccount?.trim()))
}
const handleAccountFocus = () => {
  accountFocused.value = true
  updateAccountActiveState()
}
const handleAccountBlur = () => {
  accountFocused.value = false
  updateAccountActiveState()
}
const getActivePasswordValue = () => {
  if (mode.value === 'register') {
    return registerForm.value.password || registerForm.value.confirmPassword
  }
  if (mode.value === 'forgot') return resetForm.value.newPassword
  return loginForm.value.password
}
const updatePasswordUseState = () => {
  littleGuysState.passwordEngaged = passwordFocused.value || Boolean(getActivePasswordValue()?.trim())
  syncLittleGuysNeckTarget()
}
const handlePasswordFocus = () => {
  passwordFocused.value = true
  updatePasswordUseState()
}
const handlePasswordBlur = () => {
  passwordFocused.value = false
  updatePasswordUseState()
}

// 登录表单
const loginForm = ref({
  username: '',
  password: ''
})

// 注册表单
const registerForm = ref({
  username: '',
  password: '',
  confirmPassword: ''
})

const CURRENT_USER_KEY = 'traffic_current_user'
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api'

const submitAuth = async (path, payload) => {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok) {
    throw new Error('服务器连接失败')
  }

  const result = await response.json()
  if (result.code !== 200) {
    throw new Error(result.message || '操作失败')
  }
  return result.data
}

const saveLoginState = (authData) => {
  const user = authData.user
  if (typeof window !== 'undefined') {
    localStorage.setItem(CURRENT_USER_KEY, JSON.stringify(user))
    localStorage.setItem('token', authData.token)
  }
  setLoginSuccess()
  emit('login-success', user)
  window.setTimeout(() => router.push('/'), 900)
}

const loginLoading = ref(false)
const registerLoading = ref(false)

// 登录
const handleLogin = async () => {
  const { username, password } = loginForm.value
  if (!username || !password) {
    alert(t('login.alertUsernamePassword'))
    return
  }

  try {
    loginLoading.value = true
    const authData = await submitAuth('/auth/login', { username, password })
    saveLoginState(authData)
  } catch (error) {
    setLoginFailure()
    alert(error.message)
  } finally {
    loginLoading.value = false
  }
}

// 注册
const handleRegister = async () => {
  const { username, password, confirmPassword } = registerForm.value
  if (!username || !password || !confirmPassword) {
    alert(t('login.alertFillAll'))
    return
  }
  if (password.length < 6) {
    alert(t('login.alertPasswordLength'))
    return
  }
  if (password !== confirmPassword) {
    alert(t('login.alertPasswordMismatch'))
    return
  }

  try {
    registerLoading.value = true
    const authData = await submitAuth('/auth/register', {
      username,
      password,
      confirmPassword
    })
    alert(t('login.registerSuccess'))
    saveLoginState(authData)
    registerForm.value = { username: '', password: '', confirmPassword: '' }
  } catch (error) {
    alert(error.message)
  } finally {
    registerLoading.value = false
  }
}

// 切换模式（登录 / 注册）
const switchMode = (target) => {
  mode.value = target
  accountFocused.value = false
  loginForm.value = { username: '', password: '' }
  registerForm.value = { username: '', password: '', confirmPassword: '' }
  updateAccountActiveState()
}

// ── 左侧四小人画布 ──
const littleGuysCanvas = ref(null)
let littleGuysCtx = null
let littleGuysFrame = 0
const SOLO_SING_FRAMES = 150
const GROUP_SING_FRAMES = 300
const NOTE_LIFE_FRAMES = 190
const LOGIN_FAIL_FRAMES = 90

const littleGuysState = {
  mouseX: 250,
  mouseY: 260,
  mouseActive: false,
  emailActive: false,
  passwordEngaged: false,
  passwordRevealed: false,
  happy: false,
  neck: 0,
  targetNeck: 0,
  accountPeekX: 455,
  accountPeekY: 205,
  passwordPeekX: 455,
  passwordPeekY: 286,
  time: 0,
  orangeReactActive: false,
  orangeNodStart: -999,
  orangeSingStart: -999,
  orangeNotes: [],
  blackShakeStart: -999,
  purpleSingStart: -999,
  groupSingStart: -999,
  loginFailStart: -999,
  successStart: -999,
  partyPieces: [],
  hearts: []
}

let guysBaseline = 500
const guys = [
  { key: 'orange', color: '#F68B45', x: 132, y: guysBaseline, w: 210, h: 158, eye: [], eyeGap: 29, eyeOffset: 114, mouthOffset: 101, mouthWidth: 38, mouthCurve: 2, round: 76 },
  { key: 'purple', color: '#7C2DFF', x: 220, y: guysBaseline, w: 142, h: 278, eye: [], eyeGap: 24, eyeOffset: 242, mouthOffset: 218, mouthWidth: 12, mouthCurve: 0, noMouthWobble: true, tall: true, round: 4 },
  { key: 'black', color: '#3A3E4C', mouthColor: '#111827', x: 300, y: guysBaseline, w: 122, h: 215, eye: [], eyeGap: 21, eyeOffset: 172, mouthOffset: 149, mouthWidth: 26, mouthCurve: -2, angry: true, rectangular: true, round: 24 },
  { key: 'yellow', color: '#FFD600', x: 398, y: guysBaseline, w: 128, h: 184, eye: [], eyeGap: 20, eyeOffset: 148, mouthOffset: 125, mouthWidth: 32, mouthCurve: 5, playful: true, rectangular: true, round: 64 }
]

const syncGuysBaseline = (baseline) => {
  guysBaseline = Math.max(390, Math.min(520, baseline))
  guys.forEach((guy) => {
    guy.y = guysBaseline
    const eyeY = guysBaseline - guy.eyeOffset
    guy.eye = [
      [guy.x - guy.eyeGap, eyeY],
      [guy.x + guy.eyeGap, eyeY]
    ]
  })
}

syncGuysBaseline(guysBaseline)

const alignLittleGuysToLoginCard = () => {
  const canvas = littleGuysCanvas.value
  const card = document.querySelector('.right .card')
  if (!canvas || !card) return
  const canvasRect = canvas.getBoundingClientRect()
  const cardRect = card.getBoundingClientRect()
  const baseline = ((cardRect.bottom - canvasRect.top) / canvasRect.height) * canvas.height - 104
  syncGuysBaseline(baseline)
}

const roundedRect = (ctx, x, y, w, h, r) => {
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.lineTo(x + w - r, y)
  ctx.quadraticCurveTo(x + w, y, x + w, y + r)
  ctx.lineTo(x + w, y + h)
  ctx.lineTo(x, y + h)
  ctx.lineTo(x, y + r)
  ctx.quadraticCurveTo(x, y, x + r, y)
  ctx.closePath()
}

const drawEye = (ctx, x, y, targetX, targetY, options = {}) => {
  if (options.closed || options.blink) {
    ctx.strokeStyle = '#111827'
    ctx.lineWidth = 4
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(x - 8, y)
    ctx.quadraticCurveTo(x, y + 4, x + 8, y)
    ctx.stroke()
    return
  }

  const dx = targetX - x
  const dy = targetY - y
  const distance = Math.max(Math.hypot(dx, dy), 1)
  const pupilX = x + (dx / distance) * 5
  const pupilY = y + (dy / distance) * 4

  if (options.squint) {
    ctx.strokeStyle = '#111827'
    ctx.lineWidth = 4
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(pupilX - 6, pupilY)
    ctx.quadraticCurveTo(pupilX, pupilY + 2, pupilX + 6, pupilY)
    ctx.stroke()
    return
  }

  if (options.dotOnly) {
    ctx.fillStyle = '#111827'
    ctx.beginPath()
    ctx.arc(pupilX, pupilY, options.dotRadius || 6, 0, Math.PI * 2)
    ctx.fill()
    return
  }

  ctx.fillStyle = '#ffffff'
  ctx.beginPath()
  ctx.arc(x, y, 12, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = '#111827'
  ctx.beginPath()
  ctx.arc(pupilX, pupilY, 5, 0, Math.PI * 2)
  ctx.fill()
}

const drawMouth = (ctx, guy, neckOffset = 0, bounce = 0, options = {}) => {
  const y = guy.y - guy.mouthOffset - neckOffset + bounce
  const mood = options.mood || 'idle'
  const wobble = guy.noMouthWobble ? 0 : Math.sin(littleGuysState.time * 0.08 + guy.x * 0.04)
  const tilt = options.lookDx ? options.lookDx * 4 : 0
  const halfWidth = (guy.mouthWidth || 34) / 2
  const baseCurve = guy.mouthCurve ?? 3
  ctx.strokeStyle = guy.mouthColor || '#111827'
  ctx.lineWidth = 5
  ctx.lineCap = 'round'
  ctx.beginPath()

  if (mood === 'back') {
    ctx.moveTo(guy.x - halfWidth * 0.78, y)
    ctx.quadraticCurveTo(guy.x, y + baseCurve * 0.55, guy.x + halfWidth * 0.78, y)
  } else if (mood === 'wave') {
    ctx.moveTo(guy.x - halfWidth, y + 2)
    ctx.bezierCurveTo(
      guy.x - halfWidth * 0.45, y - 6,
      guy.x - halfWidth * 0.15, y + 10,
      guy.x,
      y + 2
    )
    ctx.bezierCurveTo(
      guy.x + halfWidth * 0.15,
      y - 6,
      guy.x + halfWidth * 0.45,
      y + 10,
      guy.x + halfWidth,
      y + 2
    )
  } else if (mood === 'unhappy') {
    ctx.moveTo(guy.x - halfWidth, y + 8)
    ctx.quadraticCurveTo(guy.x, y - 6, guy.x + halfWidth, y + 8)
  } else if (mood === 'sing') {
    ctx.moveTo(guy.x - halfWidth, y + 1)
    ctx.quadraticCurveTo(guy.x, y + 11, guy.x + halfWidth, y + 1)
  } else if (mood === 'peek') {
    ctx.moveTo(guy.x - halfWidth, y + 2)
    ctx.quadraticCurveTo(guy.x + 2, y + baseCurve + 7, guy.x + halfWidth + 2, y + 1)
  } else if (mood === 'watch') {
    ctx.moveTo(guy.x - halfWidth, y + tilt * 0.25)
    ctx.quadraticCurveTo(guy.x + tilt, y + baseCurve + Math.abs(tilt) * 0.2, guy.x + halfWidth, y - tilt * 0.25)
  } else {
    ctx.moveTo(guy.x - halfWidth, y + wobble * 0.8)
    ctx.quadraticCurveTo(guy.x, y + baseCurve + wobble * 1.2, guy.x + halfWidth, y - wobble * 0.8)
  }

  ctx.stroke()
}

const drawOrangeMouth = (ctx, guy, neckOffset = 0, bounce = 0, options = {}) => {
  const centerX = options.centerX ?? ((guy.eye[0][0] + guy.eye[1][0]) / 2)
  const eyeY = guy.eye[0][1] - neckOffset + bounce
  const y = eyeY + 14

  if (options.closed) {
    ctx.strokeStyle = '#111827'
    ctx.lineWidth = 5
    ctx.lineCap = 'round'
    ctx.beginPath()
    ctx.moveTo(centerX - 14, y + 1)
    ctx.quadraticCurveTo(centerX, y + 3, centerX + 14, y + 1)
    ctx.stroke()
    return
  }

  ctx.fillStyle = '#111827'

  if (options.mood === 'o') {
    ctx.beginPath()
    ctx.ellipse(centerX, y + 7, options.singing ? 11 : 8, options.singing ? 8 : 10, 0, 0, Math.PI * 2)
    ctx.fill()
    return
  }

  const squint = Boolean(options.squint)
  const halfWidth = squint ? 9 : 12
  const sideDepth = squint ? 5 : 7
  const bottomDepth = squint ? 8 : 14
  ctx.beginPath()
  ctx.moveTo(centerX - halfWidth, y)
  ctx.lineTo(centerX + halfWidth, y)
  ctx.quadraticCurveTo(centerX + halfWidth - 1, y + sideDepth, centerX + 4, y + bottomDepth - 2)
  ctx.quadraticCurveTo(centerX, y + bottomDepth, centerX - 4, y + bottomDepth - 2)
  ctx.quadraticCurveTo(centerX - halfWidth + 1, y + sideDepth, centerX - halfWidth, y)
  ctx.closePath()
  ctx.fill()
}

const drawBlackSingMouth = (ctx, guy, neckOffset = 0, bounce = 0, open = 0) => {
  const y = guy.y - guy.mouthOffset - neckOffset + bounce + 5
  ctx.fillStyle = '#111827'
  ctx.beginPath()
  ctx.ellipse(guy.x, y, 7 + open * 3, 3 + open * 7, 0, 0, Math.PI * 2)
  ctx.fill()
}

const drawOvalSingMouth = (ctx, guy, neckOffset = 0, bounce = 0, open = 0) => {
  const y = guy.y - guy.mouthOffset - neckOffset + bounce + 4
  const small = guy.key === 'purple'
  const width = small ? 5 + open * 1.5 : 8 + open * 2
  const height = small ? 6 + open * 2 : 8 + open * 2.5
  ctx.fillStyle = '#111827'
  ctx.beginPath()
  ctx.ellipse(guy.x, y, width, height, 0, 0, Math.PI * 2)
  ctx.fill()
}

const drawBrows = (ctx, guy, neckOffset = 0, bounce = 0, options = {}) => {
  if (!guy.angry && !options.force) return
  const y = guy.y - guy.eyeOffset - neckOffset + bounce - 20
  ctx.strokeStyle = '#111827'
  ctx.lineWidth = 4
  ctx.lineCap = 'round'
  ctx.beginPath()
  ctx.moveTo(guy.x - guy.eyeGap - 13, y - 4)
  ctx.lineTo(guy.x - guy.eyeGap + 10, y + 2)
  ctx.moveTo(guy.x + guy.eyeGap - 10, y + 2)
  ctx.lineTo(guy.x + guy.eyeGap + 13, y - 4)
  ctx.stroke()
}

const drawHappyFace = (ctx, guy, neckOffset = 0) => {
  const eyeY = guy.eye[0][1] - neckOffset
  ctx.strokeStyle = '#111827'
  ctx.lineWidth = 4
  ctx.lineCap = 'round'
  guy.eye.forEach(([x]) => {
    ctx.beginPath()
    ctx.arc(x, eyeY, 9, Math.PI * 0.1, Math.PI * 0.9)
    ctx.stroke()
  })
  ctx.beginPath()
  ctx.arc(guy.x, guy.y - neckOffset + 18, 24, 0.1 * Math.PI, 0.9 * Math.PI)
  ctx.stroke()
  ctx.fillStyle = 'rgba(255, 120, 140, 0.42)'
  ctx.beginPath()
  ctx.arc(guy.x - 42, guy.y - neckOffset + 14, 11, 0, Math.PI * 2)
  ctx.arc(guy.x + 42, guy.y - neckOffset + 14, 11, 0, Math.PI * 2)
  ctx.fill()
}

const drawSuccessFace = (ctx, guy, neckOffset = 0, bounce = 0, open = 0) => {
  guy.eye.forEach(([x, y]) => {
    drawEye(ctx, x, y - neckOffset + bounce, guy.x, guy.y, { squint: true })
  })

  if (guy.key === 'orange') {
    drawOrangeMouth(ctx, guy, neckOffset, bounce, { mood: 'o', singing: true })
  } else if (guy.key === 'black') {
    drawBlackSingMouth(ctx, guy, neckOffset, bounce, open)
  } else {
    drawOvalSingMouth(ctx, guy, neckOffset, bounce, open)
  }
}

const drawBack = (ctx, guy, top, left, width, height) => {
  ctx.fillStyle = 'rgba(15, 23, 42, 0.08)'
  ctx.beginPath()
  ctx.ellipse(guy.x, top + height * 0.36, 24, 10, 0, 0, Math.PI * 2)
  ctx.fill()
}

const drawSoftBody = (ctx, guy, left, top, width, height) => {
  const bottom = top + height
  const r = Math.min(guy.round || 24, width * 0.48, height * 0.48)
  if (guy.rectangular) {
    ctx.beginPath()
    ctx.moveTo(left, bottom)
    ctx.lineTo(left + width, bottom)
    ctx.lineTo(left + width, top + r)
    ctx.bezierCurveTo(left + width, top + r * 0.35, left + width - r * 0.35, top, left + width - r, top)
    ctx.lineTo(left + r, top)
    ctx.bezierCurveTo(left + r * 0.35, top, left, top + r * 0.35, left, top + r)
    ctx.lineTo(left, bottom)
    ctx.closePath()
    ctx.fill()
    return
  }
  ctx.beginPath()
  ctx.moveTo(left + r, top)
  ctx.bezierCurveTo(left + width * 0.74, top - 4, left + width, top + height * 0.12, left + width, top + r)
  ctx.bezierCurveTo(left + width + 3, top + height * 0.48, left + width * 0.9, bottom - 8, left + width * 0.68, bottom)
  ctx.lineTo(left + width * 0.24, bottom)
  ctx.bezierCurveTo(left + 8, bottom - 2, left - 3, top + height * 0.58, left, top + r)
  ctx.bezierCurveTo(left + 2, top + height * 0.12, left + width * 0.22, top - 2, left + r, top)
  ctx.closePath()
  ctx.fill()
}

const drawGuy = (ctx, guy) => {
  const isPurple = guy.key === 'purple'
  const passwordPeek = littleGuysState.passwordEngaged && littleGuysState.passwordRevealed
  const passwordHiddenWatch = littleGuysState.passwordEngaged && !littleGuysState.passwordRevealed
  const neckOffset = isPurple && !passwordPeek ? littleGuysState.neck : 0
  const neckProgress = Math.min(1, neckOffset / 28)
  const idle = !littleGuysState.mouseActive && !littleGuysState.happy
  const idlePhase = littleGuysState.time * 0.045 + guy.x * 0.018
  const breathing = idle ? Math.sin(idlePhase) : 0
  const blinkOffsets = { orange: 0, purple: -176, black: -362, yellow: -534 }
  const blinkFrame = (littleGuysState.time + blinkOffsets[guy.key] + 720) % 720
  const blink = idle && blinkFrame >= 0 && blinkFrame < 4
  const groupProgress = Math.max(0, Math.min(1, (littleGuysState.time - littleGuysState.groupSingStart) / GROUP_SING_FRAMES))
  const groupSinging = groupProgress > 0 && groupProgress < 1
  const singProgress = guy.key === 'orange'
    ? Math.max(0, Math.min(1, (littleGuysState.time - littleGuysState.orangeSingStart) / SOLO_SING_FRAMES))
    : guy.key === 'purple'
      ? Math.max(0, Math.min(1, (littleGuysState.time - littleGuysState.purpleSingStart) / SOLO_SING_FRAMES))
      : 1
  const orangeSinging = guy.key === 'orange' && (groupSinging || (singProgress > 0 && singProgress < 1))
  const purpleSoloSinging = guy.key === 'purple' && singProgress > 0 && singProgress < 1 && !groupSinging
  const yellowSinging = guy.key === 'yellow' && groupSinging
  const blackReluctantSinging = guy.key === 'black' && groupSinging
  const activeSingProgress = groupSinging ? groupProgress : singProgress
  const singAmplitude = yellowSinging ? 12 : purpleSoloSinging ? 6 : groupSinging || orangeSinging ? 7 : 0
  const singWave = groupSinging && isPurple
    ? Math.sin(groupProgress * Math.PI * 7.2 + 0.85)
    : purpleSoloSinging
      ? Math.sin(singProgress * Math.PI * 7.2 + 0.85)
    : Math.sin(activeSingProgress * Math.PI * 10)
  const singBounce = singAmplitude ? singWave * singAmplitude : 0
  const shakeProgress = guy.key === 'black'
    ? Math.max(0, Math.min(1, (littleGuysState.time - littleGuysState.blackShakeStart) / 90))
    : 1
  const blackShaking = guy.key === 'black' && shakeProgress > 0 && shakeProgress < 1
  const shake = blackShaking ? Math.sin(shakeProgress * Math.PI * 8) * (1 - shakeProgress) : 0
  const failProgress = Math.max(0, Math.min(1, (littleGuysState.time - littleGuysState.loginFailStart) / LOGIN_FAIL_FRAMES))
  const loginFailing = failProgress > 0 && failProgress < 1
  const failShake = loginFailing ? Math.sin(failProgress * Math.PI * 9) * (1 - failProgress) : 0
  const totalShake = shake + failShake
  const successAge = Math.max(0, littleGuysState.time - littleGuysState.successStart)
  const successJump = {
    orange: 13,
    purple: 19,
    black: 10,
    yellow: 16
  }[guy.key] || 12
  const successPhase = {
    orange: 0,
    purple: 0.8,
    black: 1.45,
    yellow: 0.35
  }[guy.key] || 0
  const bounce = littleGuysState.happy
    ? -Math.abs(Math.sin(successAge * 0.17 + successPhase)) * successJump
    : breathing * 3.1 + singBounce
  const breatheScaleY = idle ? 1 + breathing * 0.017 : 1
  const breatheScaleX = idle ? 1 - breathing * 0.006 : 1
  const left = guy.x - guy.w / 2
  const top = guy.y - guy.h + bounce
  const height = guy.h
  const visualTop = top - neckOffset
  const visualHeight = guy.h + (isPurple ? neckOffset : 0)
  const activeLookX = littleGuysState.passwordEngaged
    ? littleGuysState.passwordPeekX
    : littleGuysState.emailActive
      ? littleGuysState.accountPeekX
      : littleGuysState.mouseX
  const activeLookY = littleGuysState.passwordEngaged
    ? littleGuysState.passwordPeekY
    : littleGuysState.emailActive
      ? littleGuysState.accountPeekY
      : littleGuysState.mouseY
  const shouldLook = (littleGuysState.mouseActive || littleGuysState.emailActive || littleGuysState.passwordEngaged) && !littleGuysState.happy
  const lookDx = shouldLook
    ? Math.max(-1, Math.min(1, (activeLookX - guy.x) / 240))
    : 0
  const peekLean = 0
  const turningAway = passwordPeek && !guy.playful
  const sway = blackShaking
    ? totalShake * 0.2
    : loginFailing
      ? failShake * 0.18
    : yellowSinging || (groupSinging && isPurple) || purpleSoloSinging
      ? Math.sin((purpleSoloSinging ? singProgress : groupProgress) * Math.PI * (isPurple ? 5.6 : 8) + (isPurple ? 0.7 : 0)) * 0.12
      : passwordHiddenWatch || (isPurple && littleGuysState.emailActive)
      ? 0
      : lookDx * 0.055

  ctx.save()
  ctx.translate(guy.x + totalShake * 7, guy.y + bounce)
  ctx.rotate(sway)
  ctx.scale(breatheScaleX, breatheScaleY)
  ctx.translate(-guy.x, -guy.y - bounce)
  ctx.fillStyle = guy.color
  ctx.shadowColor = 'rgba(15, 23, 42, 0.12)'
  ctx.shadowBlur = 14
  ctx.shadowOffsetY = 8

  if (guy.key === 'orange') {
    ctx.beginPath()
    ctx.moveTo(left + 24, guy.y + bounce)
    ctx.lineTo(left + guy.w - 24, guy.y + bounce)
    ctx.quadraticCurveTo(left + guy.w, guy.y + bounce, left + guy.w, guy.y + bounce - 24)
    ctx.quadraticCurveTo(left + guy.w, top + 18, guy.x, top + 18)
    ctx.quadraticCurveTo(left, top + 18, left, guy.y + bounce - 24)
    ctx.quadraticCurveTo(left, guy.y + bounce, left + 24, guy.y + bounce)
    ctx.closePath()
    ctx.fill()
  } else if (guy.key === 'purple') {
    const leanX = neckProgress * 18
    const corner = guy.round || 8
    const bodyBottom = guy.y + bounce
    const bodyTop = top + 2
    const neckTop = bodyTop - neckOffset
    const upperLeft = left + leanX
    const upperRight = left + guy.w + leanX

    ctx.beginPath()
    ctx.moveTo(left, bodyBottom)
    ctx.lineTo(left + guy.w, bodyBottom)
    ctx.lineTo(upperRight, neckTop + corner)
    ctx.quadraticCurveTo(upperRight, neckTop, upperRight - corner, neckTop)
    ctx.lineTo(upperLeft + corner, neckTop)
    ctx.quadraticCurveTo(upperLeft, neckTop, upperLeft, neckTop + corner)
    ctx.lineTo(left, bodyBottom)
    ctx.closePath()
    ctx.fill()
  } else {
    drawSoftBody(ctx, guy, left, top, guy.w, height)
  }

  ctx.shadowColor = 'transparent'
  ctx.shadowBlur = 0
  ctx.shadowOffsetY = 0

  if (littleGuysState.happy) {
    const successOpen = Math.sin(successAge * 0.2 + successPhase) * 0.5 + 0.5
    drawSuccessFace(ctx, guy, neckOffset, bounce, successOpen)
    ctx.restore()
    return
  }

  const targetX = shouldLook ? activeLookX : guy.x
  const targetY = shouldLook ? activeLookY : guy.y - guy.h * 0.5
  const orangeReact = guy.key === 'orange' && (littleGuysState.emailActive || littleGuysState.passwordEngaged)
  const orangeSquint = orangeSinging
  const chorusSquint = (groupSinging && (guy.key === 'yellow' || guy.key === 'purple')) || purpleSoloSinging
  const singOpen = Math.sin(activeSingProgress * Math.PI * 10) * 0.5 + 0.5
  const nodProgress = orangeReact ? Math.min(1, Math.max(0, (littleGuysState.time - littleGuysState.orangeNodStart) / 92)) : 1
  const nodEase = nodProgress < 1 ? Math.sin(nodProgress * Math.PI * 4) ** 2 : 0
  const faceY = guy.y - guy.eyeOffset - neckOffset + bounce + 18 + (orangeReact ? nodEase * 8 : 0)
  const passwordClosedTurn = passwordPeek && !guy.playful ? -0.12 : 0
  const faceTurn = shouldLook ? lookDx * 0.09 + (isPurple ? peekLean * 0.45 : 0) + passwordClosedTurn + (orangeReact ? nodEase * 0.035 : 0) : 0
  const faceShift = shouldLook ? lookDx * 3 + (isPurple ? neckProgress * 10 : 0) + (passwordPeek && !guy.playful ? -14 : 0) + (orangeReact ? 28 : 0) : 0

  ctx.save()
  ctx.translate(guy.x + faceShift, faceY)
  ctx.rotate(faceTurn)
  ctx.translate(-guy.x, -faceY)

  if (passwordPeek && guy.playful) {
    drawEye(ctx, guy.eye[0][0], guy.eye[0][1] + bounce, targetX, targetY, { closed: true })
    drawEye(ctx, guy.eye[1][0], guy.eye[1][1] + bounce, targetX, targetY, { blink })
    drawMouth(ctx, guy, neckOffset, bounce, { mood: 'peek', lookDx })
  } else if (passwordPeek) {
    guy.eye.forEach(([x, y]) => drawEye(ctx, x, y - neckOffset + bounce, targetX, targetY, { closed: true }))
    drawBrows(ctx, guy, neckOffset, bounce)
    if (guy.key === 'orange') {
      drawOrangeMouth(ctx, guy, neckOffset, bounce, { closed: true })
    } else {
      drawMouth(ctx, guy, neckOffset, bounce, { mood: 'back', lookDx })
    }
  } else {
    guy.eye.forEach(([x, y]) => drawEye(ctx, x, y - neckOffset + bounce, targetX, targetY, {
      blink,
      dotOnly: guy.key === 'orange',
      dotRadius: guy.key === 'orange' ? 5.2 : 6,
      squint: loginFailing || orangeSquint || chorusSquint
    }))
    drawBrows(ctx, guy, neckOffset, bounce, { force: blackReluctantSinging || loginFailing })
    if (loginFailing) {
      drawMouth(ctx, guy, neckOffset, bounce, { mood: 'wave' })
    } else if (guy.key === 'orange') {
      drawOrangeMouth(ctx, guy, neckOffset, bounce, {
        mood: orangeReact || orangeSinging ? 'o' : 'idle',
        squint: orangeSquint,
        singing: orangeSinging
      })
    } else if (blackReluctantSinging) {
      drawBlackSingMouth(ctx, guy, neckOffset, bounce, singOpen)
    } else if ((groupSinging && (guy.key === 'yellow' || guy.key === 'purple')) || purpleSoloSinging) {
      drawOvalSingMouth(ctx, guy, neckOffset, bounce, singOpen)
    } else {
      drawMouth(ctx, guy, neckOffset, bounce, {
        mood: blackShaking ? 'unhappy' : groupSinging ? 'sing' : shouldLook ? 'watch' : 'idle',
        lookDx
      })
    }
  }
  ctx.restore()
  ctx.restore()
}

const drawHearts = (ctx) => {
  if (!littleGuysState.happy) return
  ctx.fillStyle = 'rgba(244, 99, 128, .72)'
  littleGuysState.hearts.forEach((heart) => {
    heart.y -= heart.speed
    heart.alpha -= 0.004
    if (heart.alpha <= 0) {
      heart.y = 190 + Math.random() * 120
      heart.x = 95 + Math.random() * 320
      heart.alpha = 1
    }
    ctx.globalAlpha = heart.alpha
    ctx.beginPath()
    ctx.arc(heart.x - 5, heart.y, 6, 0, Math.PI * 2)
    ctx.arc(heart.x + 5, heart.y, 6, 0, Math.PI * 2)
    ctx.moveTo(heart.x - 11, heart.y + 3)
    ctx.lineTo(heart.x, heart.y + 16)
    ctx.lineTo(heart.x + 11, heart.y + 3)
    ctx.fill()
  })
  ctx.globalAlpha = 1
}

const drawOrangeNotes = (ctx) => {
  if (littleGuysState.happy) return

  littleGuysState.orangeNotes = littleGuysState.orangeNotes.filter((note) => {
    const age = littleGuysState.time - note.start
    const life = note.life || NOTE_LIFE_FRAMES
    return age < life
  })

  ctx.save()
  littleGuysState.orangeNotes.forEach((note) => {
    const age = littleGuysState.time - note.start
    if (age < 0) return
    const life = note.life || NOTE_LIFE_FRAMES
    const progress = Math.min(1, age / life)
    const fadeIn = Math.min(1, age / 36)
    const fadeOut = Math.min(1, (life - age) / 54)
    const rise = progress * (note.rise || 94)
    const drift = Math.sin(age * 0.045 + note.phase) * (note.drift || 5)
    ctx.globalAlpha = Math.max(0, Math.min(fadeIn, fadeOut))
    if (note.type === 'flower') {
      const x = note.x + drift
      const y = note.y - rise
      ctx.fillStyle = '#F9A8D4'
      for (let index = 0; index < 5; index += 1) {
        const angle = (Math.PI * 2 * index) / 5
        ctx.beginPath()
        ctx.ellipse(x + Math.cos(angle) * 7, y + Math.sin(angle) * 7, 5, 7, angle, 0, Math.PI * 2)
        ctx.fill()
      }
      ctx.fillStyle = '#FDE68A'
      ctx.beginPath()
      ctx.arc(x, y, 4, 0, Math.PI * 2)
      ctx.fill()
    } else {
      ctx.fillStyle = '#111827'
      ctx.font = `${note.size}px sans-serif`
      ctx.fillText(note.symbol, note.x + drift, note.y - rise)
    }
  })
  ctx.restore()
  ctx.globalAlpha = 1
}

const drawSuccessParty = (ctx) => {
  if (!littleGuysState.happy) return

  const age = Math.max(0, littleGuysState.time - littleGuysState.successStart)
  const titleFloat = Math.sin(age * 0.06) * 4

  ctx.save()
  ctx.textAlign = 'center'
  ctx.font = '700 44px sans-serif'
  ctx.lineWidth = 8
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.9)'
  ctx.fillStyle = '#111827'
  ctx.strokeText('Welcome!!', 250, 112 + titleFloat)
  ctx.fillText('Welcome!!', 250, 112 + titleFloat)

  littleGuysState.partyPieces.forEach((piece) => {
    const t = age + piece.delay
    const x = (piece.x + Math.sin(t * piece.wobble + piece.phase) * piece.drift + 520) % 520 - 10
    const y = (piece.y + t * piece.speed) % 230 + 18
    ctx.save()
    ctx.translate(x, y)
    ctx.rotate(t * piece.spin + piece.phase)
    ctx.fillStyle = piece.color
    ctx.globalAlpha = 0.76
    ctx.fillRect(-piece.w / 2, -piece.h / 2, piece.w, piece.h)
    ctx.restore()
  })

  ctx.restore()
  ctx.globalAlpha = 1
}

const renderLittleGuys = () => {
  const canvas = littleGuysCanvas.value
  if (!canvas || !littleGuysCtx) return

  const ctx = littleGuysCtx
  littleGuysState.time += 1
  littleGuysState.neck += (littleGuysState.targetNeck - littleGuysState.neck) * 0.12

  ctx.clearRect(0, 0, canvas.width, canvas.height)

  drawGuy(ctx, guys.find((guy) => guy.key === 'yellow'))
  guys.filter((guy) => !['orange', 'yellow'].includes(guy.key)).forEach((guy) => drawGuy(ctx, guy))
  drawGuy(ctx, guys.find((guy) => guy.key === 'orange'))
  drawOrangeNotes(ctx)
  drawSuccessParty(ctx)
  littleGuysFrame = requestAnimationFrame(renderLittleGuys)
}

const updateEmailActive = (isActive) => {
  if (littleGuysState.happy) return
  littleGuysState.emailActive = Boolean(isActive)
  syncLittleGuysNeckTarget()
}

const updatePasswordRevealed = (isRevealed) => {
  if (littleGuysState.happy) return
  littleGuysState.passwordRevealed = Boolean(isRevealed)
  syncLittleGuysNeckTarget()
}

const syncLittleGuysNeckTarget = () => {
  if (littleGuysState.happy) return
  const passwordHiddenWatch = littleGuysState.passwordEngaged && !littleGuysState.passwordRevealed
  littleGuysState.targetNeck = (littleGuysState.emailActive || passwordHiddenWatch) ? 28 : 0
  syncOrangeReaction()
}

const syncOrangeReaction = () => {
  const orangeActive = !littleGuysState.happy && (littleGuysState.emailActive || littleGuysState.passwordEngaged)
  if (orangeActive && !littleGuysState.orangeReactActive) {
    littleGuysState.orangeNodStart = littleGuysState.time
  }
  littleGuysState.orangeReactActive = orangeActive
}

const setLoginSuccess = () => {
  littleGuysState.happy = true
  littleGuysState.passwordRevealed = true
  littleGuysState.targetNeck = 0
  littleGuysState.orangeReactActive = false
  littleGuysState.purpleSingStart = -999
  littleGuysState.groupSingStart = -999
  littleGuysState.successStart = littleGuysState.time
  const colors = ['#F97316', '#22C55E', '#3B82F6', '#E879F9', '#FACC15', '#EF4444']
  littleGuysState.partyPieces = Array.from({ length: 46 }, (_, index) => ({
    x: 22 + Math.random() * 456,
    y: Math.random() * 190,
    w: 5 + Math.random() * 8,
    h: 13 + Math.random() * 17,
    speed: 0.35 + Math.random() * 0.75,
    spin: 0.025 + Math.random() * 0.04,
    wobble: 0.028 + Math.random() * 0.025,
    drift: 5 + Math.random() * 14,
    phase: Math.random() * Math.PI * 2,
    delay: index * 2,
    color: colors[index % colors.length]
  }))
  littleGuysState.hearts = []
}

const setLoginFailure = () => {
  if (littleGuysState.happy) return
  littleGuysState.loginFailStart = littleGuysState.time
}

const onLittleGuysMove = (event) => {
  if (littleGuysState.happy) return
  const canvas = littleGuysCanvas.value
  if (!canvas) return
  const rect = canvas.getBoundingClientRect()
  littleGuysState.mouseActive = true
  littleGuysState.mouseX = ((event.clientX - rect.left) / rect.width) * canvas.width
  littleGuysState.mouseY = ((event.clientY - rect.top) / rect.height) * canvas.height
}

const onLittleGuysLeave = () => {
  littleGuysState.mouseActive = false
}

const onLittleGuysClick = (event) => {
  if (littleGuysState.happy) return
  const canvas = littleGuysCanvas.value
  const orange = guys.find((guy) => guy.key === 'orange')
  const purple = guys.find((guy) => guy.key === 'purple')
  const black = guys.find((guy) => guy.key === 'black')
  const yellow = guys.find((guy) => guy.key === 'yellow')
  if (!canvas || !orange || !purple || !black || !yellow) return

  const rect = canvas.getBoundingClientRect()
  const clickX = ((event.clientX - rect.left) / rect.width) * canvas.width
  const clickY = ((event.clientY - rect.top) / rect.height) * canvas.height
  const isHit = (guy) => {
    const left = guy.x - guy.w / 2
    const top = guy.y - guy.h
    return clickX >= left && clickX <= left + guy.w && clickY >= top && clickY <= guy.y
  }

  if (isHit(black)) {
    littleGuysState.blackShakeStart = littleGuysState.time
    return
  }

  if (isHit(purple)) {
    littleGuysState.purpleSingStart = littleGuysState.time
    littleGuysState.orangeNotes = ['♪', '♫', '♪', '♬', '♫'].map((symbol, index) => ({
      symbol,
      x: purple.x - 52 + index * 26,
      y: purple.y - purple.h - 20 - (index % 2) * 12,
      start: littleGuysState.time + index * 9,
      phase: index * 1.4,
      size: 20 + (index % 2) * 4,
      life: 140,
      rise: 72,
      drift: 5
    }))
    return
  }

  if (isHit(yellow)) {
    littleGuysState.groupSingStart = littleGuysState.time
    const groupNotes = [
      ['♪', 78, 226, 0, 22], ['♫', 130, 214, 28, 24], ['♬', 186, 229, 56, 21],
      ['♪', 246, 208, 86, 23], ['♫', 316, 222, 116, 25], ['♩', 390, 210, 146, 22],
      ['♬', 112, 242, 176, 23], ['♪', 218, 238, 206, 20], ['♫', 342, 246, 236, 24],
      ['♪', 436, 232, 266, 22]
    ]
    littleGuysState.orangeNotes = groupNotes.map(([symbol, x, y, delay, size], index) => ({
      symbol,
      x,
      y,
      start: littleGuysState.time + delay,
      phase: index * 1.15,
      size,
      life: GROUP_SING_FRAMES - delay + 16,
      rise: 82 + (index % 4) * 10,
      drift: 4 + (index % 3) * 2
    }))
    littleGuysState.orangeNotes.push(
      { type: 'flower', x: yellow.x - 26, y: yellow.y - yellow.h + 16, start: littleGuysState.time + 76, phase: 0.6, life: GROUP_SING_FRAMES - 76 + 18, rise: 96, drift: 3 },
      { type: 'flower', x: yellow.x + 28, y: yellow.y - yellow.h + 4, start: littleGuysState.time + 150, phase: 2.1, life: GROUP_SING_FRAMES - 150 + 18, rise: 108, drift: 4 }
    )
    return
  }

  if (!isHit(orange)) return

  littleGuysState.orangeSingStart = littleGuysState.time
  littleGuysState.orangeNotes = ['♪', '♫', '♪', '♬'].map((symbol, index) => ({
    symbol,
    x: orange.x - 48 + index * 28,
    y: orange.y - orange.h - 18 - (index % 2) * 8,
    start: littleGuysState.time + index * 11,
    phase: index * 1.7,
    size: 22 + (index % 2) * 4,
    life: 140,
    rise: 72,
    drift: 5
  }))
}

onMounted(() => {
  const canvas = littleGuysCanvas.value
  if (!canvas) return
  littleGuysCtx = canvas.getContext('2d')
  updatePasswordRevealed(showPassword.value)
  window.fourLittleGuys = { updateEmailActive, updatePasswordRevealed, setLoginSuccess }
  requestAnimationFrame(alignLittleGuysToLoginCard)
  window.addEventListener('resize', alignLittleGuysToLoginCard)
  renderLittleGuys()
})

onBeforeUnmount(() => {
  cancelAnimationFrame(littleGuysFrame)
  window.removeEventListener('resize', alignLittleGuysToLoginCard)
  if (window.fourLittleGuys?.setLoginSuccess === setLoginSuccess) {
    delete window.fourLittleGuys
  }
})

watch(showPassword, (value) => updatePasswordRevealed(value), { immediate: true })
watch(
  () => [mode.value, loginForm.value.username, registerForm.value.username, resetForm.value.username],
  updateAccountActiveState
)
watch(
  () => [mode.value, loginForm.value.password, registerForm.value.password, registerForm.value.confirmPassword, resetForm.value.newPassword],
  updatePasswordUseState
)
watch(mode, () => {
  passwordFocused.value = false
  updatePasswordUseState()
  requestAnimationFrame(alignLittleGuysToLoginCard)
})

</script>
<template>
  <div class="login-page">
    <div class="panel">
      <!-- 左侧四小人区 -->
      <div class="left">
        <canvas
          ref="littleGuysCanvas"
          class="little-guys-canvas"
          width="500"
          height="550"
          aria-label="四个卡通小人"
          @mousemove="onLittleGuysMove"
          @mouseleave="onLittleGuysLeave"
          @click="onLittleGuysClick"
        ></canvas>
      </div>

      <div class="right">
        <form v-if="mode === 'login'" class="card" @submit.prevent="handleLogin">
          <div class="card-header card-header-center">
            <div class="card-logo"><svg viewBox="0 0 48 48" class="card-logo-svg"><circle class="logo-ring" cx="24" cy="24" r="16"/><path class="logo-road" d="M24 5v38M5 24h38"/><circle class="logo-node" cx="24" cy="24" r="5"/><circle class="logo-dot" cx="24" cy="5" r="2.5"/><circle class="logo-dot" cx="43" cy="24" r="2.5"/><circle class="logo-dot" cx="24" cy="43" r="2.5"/><circle class="logo-dot" cx="5" cy="24" r="2.5"/></svg></div>
            <div class="card-title">{{ t('login.brandName') }}</div>
            <div class="card-sub">{{ t('login.loginSub') }}</div>
          </div>
          <div class="field"><label>{{ t('login.username') }}</label><div class="input-wrap"><span class="prefix">👤</span><input v-model="loginForm.username" :placeholder="t('login.usernamePlaceholder')" @focus="handleAccountFocus" @blur="handleAccountBlur"/></div></div>
          <div class="field"><label>{{ t('login.password') }}</label><div class="input-wrap"><span class="prefix">🔒</span><input v-model="loginForm.password" :type="showPassword ? 'text' : 'password'" :placeholder="t('login.passwordPlaceholder')" @focus="handlePasswordFocus" @blur="handlePasswordBlur"/><button type="button" class="pw-toggle" @click="togglePassword">{{ showPassword ? '🙈' : '👁' }}</button></div></div>
          <button class="btn-primary" type="submit" :disabled="loginLoading">{{ loginLoading ? t('login.loggingIn') : t('login.loginBtn') }}</button>
          <div class="switch-row"><span class="tip">{{ t('login.noAccount') }}</span><button type="button" class="link-btn" @click="switchMode('register')">{{ t('login.registerNow') }}</button></div>
          <div class="switch-row"><button type="button" class="link-btn" @click="switchMode('forgot')">忘记密码？</button></div>
          <p class="hint">{{ t('login.hint') }} <strong>{{ t('login.hintBold') }}</strong> {{ t('login.hintEnd') }}</p>
        </form>

        <form v-else-if="mode === 'register'" class="card" @submit.prevent="handleRegister">
          <div class="card-header"><div class="card-title">{{ t('login.registerTitle') }}</div><div class="card-sub">{{ t('login.registerSub') }}</div></div>
          <div class="field"><label>{{ t('login.username') }}</label><div class="input-wrap"><span class="prefix">👤</span><input v-model="registerForm.username" :placeholder="t('login.newUsername')" @focus="handleAccountFocus" @blur="handleAccountBlur"/></div></div>
          <div class="field"><label>{{ t('login.password') }}</label><div class="input-wrap"><span class="prefix">🔒</span><input v-model="registerForm.password" :type="showPassword ? 'text' : 'password'" :placeholder="t('login.passwordMin')" @focus="handlePasswordFocus" @blur="handlePasswordBlur"/><button type="button" class="pw-toggle" @click="togglePassword">{{ showPassword ? '🙈' : '👁' }}</button></div></div>
          <div class="field"><label>{{ t('login.confirmPassword') }}</label><div class="input-wrap"><span class="prefix">🔒</span><input v-model="registerForm.confirmPassword" :type="showPassword ? 'text' : 'password'" :placeholder="t('login.confirmPlaceholder')" @focus="handlePasswordFocus" @blur="handlePasswordBlur"/><button type="button" class="pw-toggle" @click="togglePassword">{{ showPassword ? '🙈' : '👁' }}</button></div></div>
          <button class="btn-primary" type="submit" :disabled="registerLoading">{{ registerLoading ? t('login.registering') : t('login.registerBtn') }}</button>
          <div class="switch-row"><span class="tip">{{ t('login.hasAccount') }}</span><button type="button" class="link-btn" @click="switchMode('login')">{{ t('login.goLogin') }}</button></div>
          <p class="hint">{{ t('login.registerHint') }}<strong>{{ t('login.registerHintRole') }}</strong>{{ t('login.registerHintEnd') }}</p>
        </form>

        <form v-else class="card" @submit.prevent="submitPasswordReset">
          <div class="card-header"><div class="card-title">重置密码</div><div class="card-sub">通过手机号验证，提交密码重置请求给管理员审核</div></div>
          <div class="field"><label>{{ t('login.username') }}</label><div class="input-wrap"><span class="prefix">👤</span><input v-model="resetForm.username" placeholder="请输入用户名" @focus="handleAccountFocus" @blur="handleAccountBlur"/></div></div>
          <div class="field"><label>手机号</label><div class="input-wrap"><span class="prefix">📱</span><input v-model="resetForm.phone" placeholder="请输入注册手机号"/></div></div>
          <div class="field"><label>新密码</label><div class="input-wrap"><span class="prefix">🔒</span><input v-model="resetForm.newPassword" :type="showPassword ? 'text' : 'password'" placeholder="至少 6 位" @focus="handlePasswordFocus" @blur="handlePasswordBlur"/><button type="button" class="pw-toggle" @click="togglePassword">{{ showPassword ? '🙈' : '👁' }}</button></div></div>
          <button class="btn-primary" type="submit" :disabled="resetSubmitting">{{ resetSubmitting ? '提交中...' : '提交重置请求' }}</button>
          <div class="switch-row"><button type="button" class="link-btn" @click="switchMode('login')">返回登录</button></div>
        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page{min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;position:relative;z-index:1}
.panel{width:960px;max-width:96%;min-height:460px;background:#c8e6c9;border:1px solid rgba(200,230,201,.6);border-radius:28px;box-shadow:0 4px 20px rgba(15,23,42,.06),0 16px 48px rgba(15,23,42,.1),0 34px 88px rgba(15,23,42,.2);display:grid;grid-template-columns:1.1fr 1fr;overflow:hidden}
.left{--goose-panel-bg:linear-gradient(145deg,#f1f7ff 0%,#e7f0fe 58%,#d9f2e7 100%);padding:28px 32px 18px;background:var(--goose-panel-bg);color:var(--text-main);display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;position:relative;isolation:isolate;overflow:hidden}
.left::before{content:'';position:absolute;left:50%;bottom:48px;width:310px;height:82px;border-radius:50%;background:radial-gradient(ellipse at center,rgba(46,125,50,.2) 0%,rgba(76,175,80,.12) 48%,rgba(76,175,80,0) 72%);transform:translateX(-50%);z-index:0}
.left::after{content:'';position:absolute;inset:auto 28px 18px 28px;height:1px;background:rgba(0,0,0,.06);z-index:1}
.little-guys-canvas{position:relative;z-index:2;width:min(100%,500px);max-width:500px;aspect-ratio:10/11;display:block;cursor:crosshair}
.right{padding:32px 34px;display:flex;align-items:center;justify-content:center;background:#c8e6c9}
.card{width:100%;max-width:360px;background:#fff;border:1px solid rgba(200,230,201,.65);border-radius:22px;box-shadow:0 1px 4px rgba(15,23,42,.04),0 4px 12px rgba(15,23,42,.06),0 10px 30px rgba(15,23,42,.1),0 20px 60px rgba(15,23,42,.16);padding:30px 28px 26px;transform:translateY(0);transition:transform .32s cubic-bezier(.34,1.56,.64,1),box-shadow .32s ease,border-color .32s ease}
.card:hover{transform:translateY(-6px);border-color:rgba(129,199,132,.55);box-shadow:0 2px 8px rgba(15,23,42,.05),0 8px 20px rgba(15,23,42,.08),0 16px 44px rgba(15,23,42,.14),0 28px 80px rgba(15,23,42,.22)}
.card-header{margin-bottom:20px}.card-header-center{display:flex;flex-direction:column;align-items:center;text-align:center}
.card-logo{width:56px;height:56px;border-radius:16px;background:linear-gradient(135deg,rgba(75,163,237,.1),rgba(99,102,241,.08));display:flex;align-items:center;justify-content:center;margin-bottom:14px}
.card-logo-svg{width:38px;height:38px;overflow:visible}
.card-logo-svg .logo-ring{fill:rgba(75,163,237,.1);stroke:rgba(75,163,237,.25);stroke-width:2}
.card-logo-svg .logo-road{fill:none;stroke:var(--primary);stroke-linecap:round;stroke-width:4}
.card-logo-svg .logo-node{fill:#fff;stroke:var(--primary);stroke-width:2}
.card-logo-svg .logo-dot{fill:var(--accent)}
.card-title{font-size:20px;font-weight:700;color:var(--text-main)}
.card-sub{margin-top:4px;font-size:13px;color:var(--text-secondary)}
.field{display:flex;flex-direction:column;gap:4px;margin-bottom:12px}
label{font-size:12px;color:var(--text-secondary);font-weight:600}
.input-wrap{display:flex;align-items:center;border-radius:14px;border:1px solid #c8e6c9;padding:0 10px;background:#f1f8e9;box-shadow:0 1px 3px rgba(15,23,42,.03),0 3px 8px rgba(15,23,42,.05);transition:border-color .18s ease,box-shadow .18s ease,transform .18s ease}
.input-wrap:focus-within{border-color:#66bb6a;transform:translateY(-1px);box-shadow:0 2px 6px rgba(15,23,42,.05),0 6px 16px rgba(15,23,42,.08),0 0 0 3px rgba(102,187,106,.16)}
.prefix{font-size:14px;margin-right:6px;color:var(--text-muted)}
input{flex:1;border:none;background:transparent;padding:9px 4px;font-size:13px;color:var(--text-main)}input:focus{outline:none}
.pw-toggle{border:none;background:none;font-size:15px;cursor:pointer;padding:2px 4px;line-height:1;flex-shrink:0;opacity:.6;transition:opacity .15s}.pw-toggle:hover{opacity:1}
.btn-primary{width:100%;margin-top:6px;padding:10px 0;border-radius:14px;border:none;background:linear-gradient(135deg,#66bb6a,#43a047);color:#fff;font-size:14px;font-weight:700;cursor:pointer;box-shadow:0 6px 18px rgba(102,187,106,.3);transition:transform .16s ease,box-shadow .16s ease}
.btn-primary:hover{transform:translateY(-1px);box-shadow:0 8px 24px rgba(67,160,71,.38)}
.btn-primary:disabled{cursor:not-allowed;opacity:.65;box-shadow:none;transform:none}
.switch-row{margin-top:12px;display:flex;justify-content:center;font-size:13px;align-items:center;gap:4px}
.tip{color:var(--text-muted)}
.link-btn{border:none;background:transparent;color:#43a047;font-size:13px;font-weight:600;cursor:pointer}
.link-btn:hover{color:#2e7d32}
.hint{margin-top:8px;font-size:11px;color:var(--text-muted);line-height:1.6}
@media(max-width:900px){.panel{grid-template-columns:minmax(0,1fr)}.left{display:none}.right{padding:24px}}
</style>
