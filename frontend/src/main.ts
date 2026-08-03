import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import { House, DocumentAdd, UserFilled, Wallet } from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import './styles.css'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)
app.use(ElementPlus)
app.component('House', House)
app.component('DocumentAdd', DocumentAdd)
app.component('UserFilled', UserFilled)
app.component('Wallet', Wallet)

const authStore = useAuthStore(pinia)
window.addEventListener('auth:unauthorized', () => {
  authStore.clearAuthentication()
  if (router.currentRoute.value.name !== 'login') {
    void router.replace({
      name: 'login',
      query: { redirect: router.currentRoute.value.fullPath },
    })
  }
})
window.addEventListener('csrf:invalid', () => {
  ElMessage.error('CSRF token 已失效，請重新嘗試。')
})

app.mount('#app')
