import { ref, watchEffect } from 'vue'

const THEME_KEY = 'traffic_theme'
const theme = ref(localStorage.getItem(THEME_KEY) || 'light')

export function useTheme() {
  const applyTheme = (value) => {
    document.documentElement.classList.toggle('dark', value === 'dark')
  }

  watchEffect(() => {
    localStorage.setItem(THEME_KEY, theme.value)
    applyTheme(theme.value)
  })

  const toggleTheme = () => {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  const setTheme = (value) => {
    theme.value = value
  }

  return { theme, toggleTheme, setTheme }
}
