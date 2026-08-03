<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/auth'
import { getApiErrorMessage } from '../../utils/apiError'

interface LoginForm {
  username: string
  password: string
}

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()
const form = reactive<LoginForm>({ username: '', password: '' })

const rules: FormRules<LoginForm> = {
  username: [{ required: true, message: '請輸入帳號', trigger: 'blur' }],
  password: [{ required: true, message: '請輸入密碼', trigger: 'blur' }],
}

function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/'
  }

  return value
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  try {
    await authStore.login(form.username, form.password)
    ElMessage.success('登入成功')
    await router.replace(safeRedirect(route.query.redirect))
  } catch (error: unknown) {
    ElMessage.error(getApiErrorMessage(error))
  }
}
</script>

<template>
  <main class="login-page">
    <el-card shadow="never" class="login-card">
      <div class="login-brand">
        <span class="login-brand-mark">PA</span>
        <div>
          <p class="eyebrow">PAYMENT APPROVAL</p>
          <h1>請款簽核系統</h1>
        </div>
      </div>
      <p class="login-subtitle">請使用系統帳號登入</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="帳號" prop="username">
          <el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="請輸入帳號"
            :prefix-icon="User"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item label="密碼" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="請輸入密碼"
            :prefix-icon="Lock"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-button
          native-type="submit"
          type="primary"
          class="login-submit"
          :loading="authStore.loggingIn"
          :disabled="authStore.loggingIn"
        >
          登入
        </el-button>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
  background: linear-gradient(135deg, #eff6ff, #f8fafc);
}

.login-card {
  width: min(100%, 430px);
  padding: 18px;
  border: 0;
  border-radius: 18px;
  box-shadow: 0 18px 50px rgb(30 58 95 / 12%);
}

.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.login-brand-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 12px;
  background: #2563eb;
  color: #fff;
  font-weight: 800;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2563eb;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.14em;
}

h1 {
  margin: 0;
  color: #1f2937;
  font-size: 26px;
}

.login-subtitle {
  margin: 22px 0;
  color: #6b7280;
}

.login-submit {
  width: 100%;
  margin-top: 8px;
}
</style>
