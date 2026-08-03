import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import { useAuthStore } from '../stores/auth'
import { getApiErrorCode } from '../utils/apiError'

function safeRedirect(value: unknown): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return '/'
  }

  return value
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/auth/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/',
      component: AppLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: 'payment-requests',
          name: 'my-payment-requests',
          component: () => import('../views/payment/MyPaymentRequestsView.vue'),
        },
        {
          path: '',
          name: 'home',
          component: () => import('../views/HomeView.vue'),
        },
        {
          path: 'payment-requests/new',
          name: 'payment-request-create',
          component: () => import('../views/payment/PaymentRequestCreateView.vue'),
        },
        {
          path: 'manager/payment-requests',
          name: 'manager-payment-requests',
          component: () => import('../views/manager/ManagerPendingListView.vue'),
        },
        {
          path: 'manager/payment-requests/:id',
          name: 'manager-payment-request-detail',
          component: () => import('../views/payment/PaymentRequestDetailView.vue'),
        },
        {
          path: 'cashier/payment-requests',
          name: 'cashier-payment-requests',
          component: () => import('../views/cashier/CashierPendingListView.vue'),
        },
        {
          path: 'cashier/payment-requests/:id',
          name: 'cashier-payment-request-detail',
          component: () => import('../views/payment/PaymentRequestDetailView.vue'),
        },
        {
          path: 'payment/payment-requests',
          name: 'payment-pending-requests',
          component: () => import('../views/payment/PaymentPendingListView.vue'),
        },
        {
          path: 'payment/payment-requests/:id',
          name: 'payment-pending-request-detail',
          component: () => import('../views/payment/PaymentRequestDetailView.vue'),
        },
        {
          path: 'payment-requests/:id',
          name: 'payment-request-detail',
          component: () => import('../views/payment/PaymentRequestDetailView.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (!authStore.initialized) {
    try {
      await authStore.fetchCurrentUser()
    } catch (error: unknown) {
      if (getApiErrorCode(error) === 'UNAUTHENTICATED') {
        authStore.clearAuthentication()
      } else {
        return false
      }
    }
  }

  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth === true)

  if (to.name === 'login' && authStore.user !== null) {
    return safeRedirect(to.query.redirect)
  }

  if (requiresAuth && authStore.user === null) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }

  return true
})

export default router
