import { ref, watchEffect, computed } from 'vue'
import zh from '../locales/zh.js'
import en from '../locales/en.js'

const LOCALE_KEY = 'traffic_locale'
const locale = ref(localStorage.getItem(LOCALE_KEY) || 'zh')

const messages = computed(() => (locale.value === 'zh' ? zh : en))

export function useLocale() {
  watchEffect(() => {
    localStorage.setItem(LOCALE_KEY, locale.value)
  })

  const setLocale = (value) => {
    locale.value = value
  }

  const toggleLocale = () => {
    locale.value = locale.value === 'zh' ? 'en' : 'zh'
  }

  /* t('header.search') → translates using dot-path */
  const t = (path) => {
    const keys = path.split('.')
    let result = messages.value
    for (const key of keys) {
      if (result == null) return path
      result = result[key]
    }
    return result ?? path
  }

  /* Convenience: t() as a global helper */
  if (typeof window !== 'undefined') {
    window.__t = t
  }

  return { locale, setLocale, toggleLocale, t, messages }
}
