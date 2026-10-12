<template>
  <div class="goods-page">
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
      <span>商品详情</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div v-if="loading" class="state">加载中...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!product" class="state">暂无在售商品，请稍后再来</div>

      <div v-else class="detail-card">
        <div class="detail-card-body">
          <!-- 左：商品图 -->
          <div class="product-image">
            <img :src="product.imageUrl || '/placeholder.png'" alt="商品图片" />
          </div>

          <!-- 右：商品信息 -->
          <div class="product-info">
            <h1 class="product-name">{{ product.name }}</h1>
            <div class="product-price">{{ formatMoney(product.price) }}</div>

            <div class="product-status">
              状态：{{ PRODUCT_STATUS_TEXT[product.productStatus] || '未知' }}
            </div>

            <div v-if="!product.canSubmitIntent" class="tips">
              商品交易中，暂时无法提交购买意向
            </div>

            <button
              class="buy-btn"
              :disabled="!product.canSubmitIntent || submitting"
              @click="goSubmit"
            >
              {{ product.canSubmitIntent ? '提交购买意向' : '商品交易中' }}
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- 商品详情 -->
    <section v-if="product" class="detail-section">
      <div class="detail-tabs">
        <span class="tab active">商品详情</span>
      </div>
      <div class="detail-content">
        <h3 class="detail-title">商品介绍</h3>
        <p class="detail-text">{{ product.description || '暂无详情' }}</p>

        <h3 class="detail-title">基本信息</h3>
        <table class="detail-table">
          <tbody>
            <tr>
              <td class="label">商品名称</td>
              <td>{{ product.name }}</td>
            </tr>
            <tr>
              <td class="label">商品价格</td>
              <td>{{ formatMoney(product.price) }}</td>
            </tr>
            <tr>
              <td class="label">商品状态</td>
              <td>{{ PRODUCT_STATUS_TEXT[product.productStatus] || '未知' }}</td>
            </tr>
            <tr>
              <td class="label">发布时间</td>
              <td>{{ formatTime(product.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 底部 -->
    <footer class="footer">
      <div class="footer-inner">
        <router-link to="/buyer/intent/query" class="footer-link">意向管理</router-link>
      </div>
    </footer>

    <!-- 提交意向弹窗 -->
    <SubmitIntentModal
      v-model:visible="showSubmitModal"
      :product="product"
      @success="onSubmitSuccess"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCurrentProduct } from '@/api/buyer'
import { PRODUCT_STATUS_TEXT } from '@/constants/productStatus'
import { formatMoney, formatTime } from '@/utils/format'
import logoImg from '@/assets/logo.png'
import SubmitIntentModal from '@/components/SubmitIntentModal.vue'

const router = useRouter()
const product = ref(null)
const loading = ref(false)
const error = ref('')
const submitting = ref(false)
const showTopBar = ref(true)
const showSubmitModal = ref(false)

async function loadProduct() {
  loading.value = true
  error.value = ''
  try {
    // 临时 mock，等后端好了删掉
    product.value = {
      id: '1',
      name: '测试商品',
      description:
        '这是临时 mock 数据，用来调样式。\n\n这里是商品详情，可以很长，用于验证下方详情区展示。',
      imageUrl: '',
      price: '99.99',
      productStatus: 0,
      canSubmitIntent: true,
      createdAt: new Date().toISOString()
    }
    // product.value = await getCurrentProduct()
  } catch (e) {
    error.value = e.message || '加载商品失败，请重试'
  } finally {
    loading.value = false
  }
}

function goSubmit() {
  if (!product.value?.canSubmitIntent) return
  showSubmitModal.value = true
}

function goHome() {
  router.push('/buyer/goods')
}

function onSubmitSuccess() {
  // 提交成功后可以重新拉取商品，同步最新状态
  // loadProduct()
}

onMounted(() => {
  loadProduct()
  setTimeout(() => {
    showTopBar.value = false
  }, 3000)
})
</script>

<style scoped>
.goods-page {
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
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px 40px;
  box-sizing: border-box;
}
.state {
  text-align: center;
  color: #999;
  padding: 80px 0;
  font-size: 16px;
}
.state.error { color: #f56c6c; }

/* 详情卡片 */
.detail-card {
  background: #fff;
  border-radius: 0;
}
.detail-card-body {
  display: flex;
  gap: 60px;
  padding: 40px;
}

/* 左侧图片 */
.product-image {
  flex: 0 0 460px;
  width: 460px;
  height: 460px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
}
.product-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

/* 右侧信息 */
.product-info {
  flex: 1;
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
}
.product-name {
  font-size: 24px;
  font-weight: 500;
  color: #333;
  margin: 0 0 20px;
  line-height: 1.4;
}
.product-price {
  font-size: 32px;
  color: #e60012;
  font-weight: bold;
  margin-bottom: 16px;
}
.product-status {
  font-size: 14px;
  color: #999;
  margin-bottom: 20px;
}
.tips {
  font-size: 14px;
  color: #e60012;
  margin-bottom: 16px;
}
.buy-btn {
  width: 200px;
  height: 50px;
  border: none;
  background: #e60012;
  color: #fff;
  font-size: 16px;
  letter-spacing: 2px;
  cursor: pointer;
  border-radius: 0;
  transition: background 0.2s;
  margin-top: 10px;
}
.buy-btn:hover:not(:disabled) {
  background: #c5000f;
}
.buy-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

/* 商品详情 */
.detail-section {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 0 20px 60px;
  box-sizing: border-box;
}
.detail-tabs {
  background: #fff;
  padding: 0 40px;
  margin-bottom: 0;
}
.tab {
  display: inline-block;
  padding: 18px 0;
  font-size: 16px;
  color: #333;
  border-bottom: 2px solid #e60012;
  margin-bottom: -1px;
  font-weight: 500;
}
.detail-content {
  background: #fff;
  padding: 30px 40px 40px;
  max-width: 100%;
}
.detail-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin: 24px 0 12px;
}
.detail-title:first-child { margin-top: 0; }
.detail-text {
  font-size: 14px;
  color: #666;
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
}
.detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.detail-table td {
  border: 1px solid #f0f0f0;
  padding: 10px 14px;
  color: #666;
}
.detail-table .label {
  width: 140px;
  background: #fafafa;
  color: #333;
}

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