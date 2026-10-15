<template>
  <div class="query-page">
    <!-- 主头部 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
          <img :src="logoImg" alt="ShopMall" @click="goHome" />
        </div>
        <nav class="nav">
          <router-link to="/buyer/goods" class="nav-item">首页</router-link>
        </nav>
        <router-link to="/seller/login" class="seller-entry">卖家入口</router-link>
      </div>
    </header>

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>意向管理</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div class="query-card">
        <h2 class="card-title">管理购买意向</h2>
        <p class="card-tip">
          请输入提交购买意向时系统生成的口令码，查询您的排队位次和意向状态
        </p>

        <div class="form-item">
          <label class="form-label">口令码</label>
          <input
            v-model="code"
            type="text"
            class="form-input"
            placeholder="请输入口令码"
            @keyup.enter="handleQuery"
            @input="error = ''"
          />
        </div>

        <div v-if="error" class="error-tip">{{ error }}</div>

        <button
          class="query-btn"
          :disabled="loading"
          @click="handleQuery"
        >
          {{ loading ? '查询中...' : '查询意向' }}
        </button>

        <div class="back-row">
          <router-link to="/buyer/goods" class="back-link">返回首页</router-link>
        </div>
      </div>
    </main>

    <!-- 底部 -->
    <footer class="footer">
      <div class="footer-inner">
        <router-link to="/buyer/intent/query" class="footer-link">意向管理</router-link>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { queryIntent } from '@/api/buyer'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import logoImg from '@/assets/logo.png'

const router = useRouter()
const code = ref('')
const loading = ref(false)
const error = ref('')

function validateCode() {
  const v = (code.value || '').trim()
  if (!v) return '请输入口令码'
  return ''
}

async function handleQuery() {
  if (loading.value) return

  const v = validateCode()
  if (v) {
    error.value = v
    return
  }

  loading.value = true
  error.value = ''
  try {
    // ============ 真实调用（后端好了用这段） ============
     const res = await queryIntent(code.value.trim())
     localStorage.setItem(STORAGE_KEYS.INTENT_CODE, code.value.trim())
     router.push('/buyer/intent/detail')

    // ============ 临时 mock（后端好了删掉） ============
    //await new Promise((r) => setTimeout(r, 500))
    //if (code.value.trim().length < 8) {
    //  error.value = '口令码已失效，无对应意向信息'
    //  return
    //}
    //localStorage.setItem(STORAGE_KEYS.INTENT_CODE, code.value.trim())
    //router.push('/buyer/intent/detail')
    // ============ mock end ============
  } catch (e) {
    if (e.code === 40905) {
      error.value = '口令码已失效，无对应意向信息'
    } else {
      error.value = e.message || '查询失败，请重试'
    }
  } finally {
    loading.value = false
  }
}

function goHome() {
  router.push('/buyer/goods')
}
</script>

<style scoped>
.query-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #333;
}

/* 主头部 */
.header {
  background: #fff;
}
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 24px;
}
.logo {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.logo img {
  height: 28px;
  width: auto;
  object-fit: contain;
}
.nav {
  flex: 1;
  display: flex;
  gap: 32px;
  margin-left: 20px;
}
.nav-item {
  color: #333;
  text-decoration: none;
  font-size: 15px;
  transition: color 0.2s;
}
.nav-item:hover { color: #e60012; }
.seller-entry {
  color: #666;
  text-decoration: none;
  font-size: 14px;
}

/* 面包屑 */
.breadcrumb {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 20px;
  font-size: 13px;
  color: #999;
  box-sizing: border-box;
}
.breadcrumb a {
  color: #666;
  text-decoration: none;
}
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

/* 主体 */
.main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 60px 20px;
}

/* 卡片 */
.query-card {
  width: 520px;
  max-width: 100%;
  background: #fff;
  padding: 40px;
  box-sizing: border-box;
}

.card-title {
  font-size: 20px;
  font-weight: 500;
  color: #333;
  margin: 0 0 10px;
  text-align: center;
}
.card-tip {
  font-size: 13px;
  color: #999;
  line-height: 1.6;
  text-align: center;
  margin: 0 0 30px;
}

.form-item {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}
.form-label {
  flex: 0 0 80px;
  font-size: 14px;
  color: #333;
  text-align: right;
  padding-right: 12px;
  box-sizing: border-box;
}
.form-input {
  flex: 1;
  height: 40px;
  border: 1px solid #e5e5e5;
  background: #fafafa;
  padding: 0 12px;
  font-size: 14px;
  color: #333;
  outline: none;
  border-radius: 0;
  box-sizing: border-box;
  transition: border-color 0.2s, background 0.2s;
}
.form-input::placeholder { color: #bbb; }
.form-input:focus {
  border-color: #e60012;
  background: #fff;
}

.error-tip {
  font-size: 13px;
  color: #e60012;
  margin-left: 80px;
  margin-bottom: 12px;
  line-height: 1.4;
}

.query-btn {
  width: 100%;
  height: 46px;
  border: none;
  background: #e60012;
  color: #fff;
  font-size: 16px;
  letter-spacing: 2px;
  cursor: pointer;
  border-radius: 0;
  transition: background 0.2s;
  margin-top: 12px;
}
.query-btn:hover:not(:disabled) {
  background: #c5000f;
}
.query-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.back-row {
  text-align: center;
  margin-top: 20px;
}
.back-link {
  font-size: 13px;
  color: #999;
  text-decoration: none;
}
.back-link:hover { color: #e60012; }

/* 底部 */
.footer {
  background: #1a1a1a;
  margin-top: auto;
}
.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 20px;
  display: flex;
  justify-content: center;
  gap: 60px;
}
.footer-link {
  color: #ccc;
  text-decoration: none;
  font-size: 14px;
}
.footer-link:hover { color: #e60012; }
.footer-link.disabled {
  color: #666;
  cursor: not-allowed;
}
</style>