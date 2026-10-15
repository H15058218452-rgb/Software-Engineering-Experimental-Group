<template>
  <div class="goods-page">
    <!-- 顶部欢迎提示条：浮动框形式，3 秒后自动收起 -->
    <transition name="slide-up">
      <div v-if="showTopBar" class="top-bar">
        <div class="top-bar-inner">
          <span>欢迎来到 ShopMall，祝您购物愉快</span>
        </div>
      </div>
    </transition>

    <!-- 主头部 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
        <img :src="logoImg" alt="ShopMall" />
        </div>
        <nav class="nav">
          <router-link to="/buyer/goods" class="nav-item">首页</router-link>
        </nav>
        <router-link to="/seller/login" class="seller-entry">卖家入口</router-link>
      </div>
    </header>

    <!-- 主体 -->
    <main class="main">
      <div v-if="loading" class="state">加载中...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!product" class="state">暂无在售商品，请稍后再来</div>

      <div v-else class="goods-card">
        <div class="goods-card-body">
          <div class="goods-image">
            <img
              :src="product.imageUrl || '/placeholder.png'"
              alt="商品图片"
            />
          </div>
          <div class="goods-info">
            <h2 class="goods-name">{{ product.name }}</h2>
            <div class="goods-price">{{ formatMoney(product.price) }}</div>
            <div class="goods-status">
              状态：{{ PRODUCT_STATUS_TEXT[product.productStatus] || '未知' }}
            </div>
          </div>
        </div>

        <router-link to="/buyer/goods/detail" class="detail-btn-link">
          <button class="detail-btn">查看详情</button>
        </router-link>
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
import { ref, onMounted } from 'vue'
import { getCurrentProduct } from '@/api/buyer'
import { PRODUCT_STATUS_TEXT } from '@/constants/productStatus'
import { formatMoney } from '@/utils/format'
import logoImg from '@/assets/logo.png'

const product = ref(null)
const loading = ref(false)
const error = ref('')
const showTopBar = ref(true)

async function loadProduct() {
  loading.value = true
  error.value = ''
  try {
    // 临时 mock，等后端好了删掉
    //product.value = {
    //  id: '1',
    //  name: '测试商品',
    //  description: '这是临时 mock 数据，用来调样式。',
    //  imageUrl: '',
    //  price: '99.99',
    //  productStatus: 0,
    //  canSubmitIntent: true
    //}
     product.value = await getCurrentProduct()
  } catch (e) {
    error.value = e.message || '加载商品失败，请重试'
  } finally {
    loading.value = false
  }
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

/* 顶部欢迎提示条：浮动框，不占文档流 */
.top-bar {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  background: #000;
  color: #ccc;
  font-size: 12px;
  padding: 10px 24px;
  white-space: nowrap;
  pointer-events: none;
}

/* 向上收起动画 */
.slide-up-leave-active {
  transition: opacity 0.4s ease, transform 0.4s ease;
}
.slide-up-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-30px);
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

/* 主体 */
.main {
  flex: 1;
  padding: 60px 20px;
  display: flex;
  justify-content: center;
  align-items: flex-start;
}
.state {
  color: #999;
  font-size: 16px;
  padding: 80px 0;
}
.state.error { color: #f56c6c; }

/* 卡片 */
.goods-card {
  display: flex;
  flex-direction: column;
  max-width: 900px;
  width: 100%;
  background: #fff;
  border-radius: 0;
}
.goods-card-body {
  display: flex;
  gap: 40px;
  padding: 30px;
}
.goods-image {
  flex: 0 0 320px;
  width: 320px;
  height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 0;
}
.goods-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.goods-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: flex-start;
  text-align: left;
  padding-top: 10px;
}
.goods-name {
  font-size: 22px;
  color: #333;
  margin: 0 0 16px;
  font-weight: 500;
  line-height: 1.4;
  text-align: left;
}
.goods-price {
  font-size: 28px;
  color: #e60012;
  font-weight: bold;
  margin-bottom: 12px;
  text-align: left;
}
.goods-status {
  font-size: 14px;
  color: #999;
  text-align: left;
}

/* 下半：整宽按钮 */
.detail-btn-link {
  display: block;
  text-decoration: none;
}
.detail-btn {
  display: block;
  width: 100%;
  height: 50px;
  border: none;
  background: #e60012;
  color: #fff;
  font-size: 16px;
  letter-spacing: 2px;
  cursor: pointer;
  border-radius: 0;
  transition: background 0.2s;
}
.detail-btn:hover {
  background: #c5000f;
}

/* 底部 */
.footer {
  background: #1a1a1a;
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