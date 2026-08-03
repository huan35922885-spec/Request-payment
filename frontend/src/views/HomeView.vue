<script setup lang="ts">
import { ArrowRight, Connection, DocumentAdd } from '@element-plus/icons-vue'
</script>

<template>
  <section class="page-container">
    <div class="hero-card">
      <div>
        <p class="eyebrow">PAYMENT APPROVAL</p>
        <h1>請款簽核系統</h1>
        <p class="hero-copy">
          Vue 3 前端基礎架構已建立，主檔 API 連線驗證頁面可供後續請款流程開發使用。
        </p>
      </div>
      <el-icon :size="76" color="#93c5fd"><Connection /></el-icon>
    </div>

    <div class="section-heading">
      <div>
        <h2>目前可用功能</h2>
        <p>本階段先完成前端骨架與後端主檔資料連線。</p>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :md="12">
        <el-card shadow="hover" class="feature-card">
          <el-icon :size="28" color="#2563eb"><DocumentAdd /></el-icon>
          <h3>新增請款草稿</h3>
          <p>載入公司、客戶、費用類型與有效價格設定。</p>
          <el-button type="primary" link @click="$router.push('/payment-requests/new')">
            開啟連線驗證頁
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="hover" class="feature-card">
          <el-icon :size="28" color="#059669"><Connection /></el-icon>
          <h3>後端 API 連線</h3>
          <p>透過 Vite /api proxy 連接 localhost:8080 Spring Boot。</p>
          <el-tag type="success">Proxy ready</el-tag>
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<style scoped>
.page-container {
  max-width: 1120px;
  margin: 0 auto;
}

.hero-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 38px 42px;
  border-radius: 16px;
  background: linear-gradient(135deg, #1e3a5f, #2563a5);
  color: white;
}

.eyebrow {
  margin: 0 0 10px;
  color: #bfdbfe;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

h1,
h2,
h3,
p {
  margin-top: 0;
}

h1 {
  margin-bottom: 12px;
  font-size: 32px;
}

.hero-copy {
  max-width: 650px;
  margin-bottom: 0;
  color: #dbeafe;
  line-height: 1.7;
}

.section-heading {
  margin: 32px 0 18px;
}

.section-heading h2 {
  margin-bottom: 6px;
  color: #1f2937;
}

.section-heading p,
.feature-card p {
  color: #6b7280;
}

.feature-card {
  min-height: 170px;
}

.feature-card h3 {
  margin: 14px 0 8px;
  color: #1f2937;
}

.feature-card p {
  line-height: 1.6;
}
</style>
