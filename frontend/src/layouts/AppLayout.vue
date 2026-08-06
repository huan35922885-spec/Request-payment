<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { getApiErrorMessage } from '../utils/apiError'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const user = computed(() => authStore.user)
const showMasterDataAdmin = computed(() =>
  user.value?.roles.includes('MASTER_DATA_ADMIN') === true,
)

async function logout(): Promise<void> {
  try {
    await authStore.logout()
    await router.replace({ name: 'login' })
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  }
}
</script>

<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div class="brand">
        <span class="brand-mark">PA</span>
        <div>
          <strong>請款簽核系統</strong>
          <small>Payment Approval</small>
        </div>
      </div>
      <div v-if="user" class="user-panel">
        <div class="user-summary">
          <strong>{{ user.displayName }}</strong>
          <small>{{ user.username }}</small>
        </div>
        <el-tag
          v-for="role in user.roles"
          :key="role"
          type="info"
          effect="plain"
        >
          {{ role }}
        </el-tag>
        <el-button
          class="logout-button"
          link
          :loading="authStore.loggingOut"
          :disabled="authStore.loggingOut"
          @click="logout"
        >
          登出
        </el-button>
      </div>
    </el-header>

    <el-container>
      <el-aside width="240px" class="app-sidebar">
        <el-menu :default-active="route.path" router class="app-menu">
          <el-menu-item index="/">
            <el-icon><House /></el-icon>
            <span>首頁</span>
          </el-menu-item>
          <el-menu-item index="/payment-requests/new">
            <el-icon><DocumentAdd /></el-icon>
            <span>新增請款草稿</span>
          </el-menu-item>
          <el-menu-item index="/payment-requests">
            <span>我的請款</span>
          </el-menu-item>
          <el-menu-item index="/manager/payment-requests">
            <el-icon><UserFilled /></el-icon>
            <span>主管待辦</span>
          </el-menu-item>
          <el-menu-item index="/cashier/payment-requests">
            <el-icon><Wallet /></el-icon>
            <span>出納待辦</span>
          </el-menu-item>
          <el-menu-item index="/payment/payment-requests">
            <el-icon><Wallet /></el-icon>
            <span>付款登記（出納）</span>
          </el-menu-item>
          <el-menu-item
            v-if="showMasterDataAdmin"
            index="/admin/expense-types"
          >
            <span>主檔：費用類型</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: #f5f7fa;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  background: #1f3a5f;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-mark {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 8px;
  background: #60a5fa;
  font-weight: 700;
}

.brand strong,
.brand small {
  display: block;
}

.brand small {
  margin-top: 2px;
  color: #bfdbfe;
  font-size: 12px;
}

.user-panel {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-summary {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.user-summary small {
  color: #bfdbfe;
  font-size: 12px;
}

.logout-button {
  color: #fff;
}

.logout-button:hover {
  color: #bfdbfe;
}

.app-sidebar {
  border-right: 1px solid #e5e7eb;
  background: #fff;
}

.app-menu {
  border-right: 0;
}

.app-main {
  padding: 28px;
}
</style>
