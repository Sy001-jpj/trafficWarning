<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useTheme } from './composables/useTheme.js'
import Header from './components/Header.vue'
import Sidebar from './components/Sidebar.vue'

useTheme()
const route = useRoute()
const isLoginPage = computed(() => route.name === 'Login')
</script>

<template>
  <!-- 登录页：单独全屏展示 -->
  <div v-if="isLoginPage" class="login-page-wrapper">
    <router-view />
  </div>

  <!-- 业务主界面：头部 + 侧边栏 + 内容区 -->
  <div v-else class="layout">
    <Header />
    <div class="layout-body">
      <Sidebar />
      <main class="layout-content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  min-width: 0;
  background:
    radial-gradient(circle at 18% 12%, rgba(47, 124, 246, 0.18), transparent 32%),
    radial-gradient(circle at 82% 8%, rgba(231, 106, 162, 0.16), transparent 30%),
    var(--app-bg);
}

.layout-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.layout-content {
  flex: 1;
  min-width: 0;
  padding: 24px 28px 32px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.64) 0%, rgba(255, 255, 255, 0.2) 230px),
    rgba(244, 247, 252, 0.72);
  overflow-y: auto;
}

.login-page-wrapper {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: url('@/assets/photo.png') center / cover no-repeat;
}

@media (max-width: 860px) {
  .layout-body {
    flex-direction: column;
  }

  .layout-content {
    padding: 16px;
  }
}
</style>
