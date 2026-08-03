<script setup lang="ts">
import { useAuthStore } from './stores/auth'

const authStore = useAuthStore()
</script>

<template>
  <main v-if="authStore.errorMessage" class="app-error-page">
    <el-card shadow="never" class="app-error-card">
      <el-alert
        title="無法取得登入狀態"
        :description="authStore.errorMessage"
        type="error"
        show-icon
        :closable="false"
      />
    </el-card>
  </main>
  <router-view v-else />
</template>

<style scoped>
.app-error-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background: #f5f7fa;
}

.app-error-card {
  width: min(100%, 560px);
}
</style>
