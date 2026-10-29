<template>
  <div class="goods-page">
    <!-- 主头部 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
          <img :src="logoImg" alt="ShopMall" @click="router.push('/buyer/goods')" />
        </div>
        <nav class="nav">
          <router-link to="/buyer/goods" class="nav-item">首页</router-link>
          <router-link to="/buyer/orders" class="nav-item">我的订单</router-link>
        </nav>
        <div class="user-actions">
          <template v-if="isLoggedIn">
            <el-button link type="danger" @click="handleLogout">退出</el-button>
          </template>
          <template v-else>
            <router-link to="/buyer/login" class="login-btn">登录</router-link>
            <span class="divider">|</span>
            <router-link to="/buyer/register" class="register-btn">注册</router-link>
          </template>
          <router-link to="/seller/login" class="seller-entry">卖家入口</router-link>
        </div>
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
      <div v-else-if="!product" class="state">商品不存在或已删除</div>

      <div v-else class="detail-card">
        <div class="detail-card-body">
          <!-- 左：商品图库（轮播） -->
          <div class="product-gallery">
            <el-carousel v-if="product.images && product.images.length > 0" height="460px" :autoplay="false">
              <el-carousel-item v-for="(img, index) in product.images" :key="index">
                <div class="carousel-img-wrap">
                  <img :src="img || '/placeholder.png'" alt="商品图片" />
                </div>
              </el-carousel-item>
            </el-carousel>
            <!-- 没有图时显示占位图 -->
            <div v-else class="carousel-img-wrap">
              <img src="/placeholder.png" alt="暂无图片" />
            </div>
          </div>

          <!-- 右：商品信息 -->
          <div class="product-info">
            <h1 class="product-name">{{ product.name }}</h1>
            <div class="product-price">{{ formatMoney(product.price) }}</div>

            <div class="product-meta">
              <span class="meta-item">分类：{{ product.categoryName || '未分类' }}</span>
              <span class="meta-item">库存：{{ product.stock }} 件</span>
              <span class="meta-item">状态：{{ getStatusText(product.status) }}</span>
            </div>

            <!-- 异常提示 -->
            <div v-if="product.status !== 0" class="tips error-tip">
              商品已下架或冻结，暂时无法购买
            </div>
            <div v-else-if="product.stock <= 0" class="tips error-tip">
              商品已售罄
            </div>

            <button
              class="buy-btn"
              :disabled="!canBuy"
              @click="handleBuyClick"
            >
              {{ canBuy ? '提交购买意向' : (product.stock <= 0 ? '已售罄' : '暂不可买') }}
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- 商品图文详情（富文本） -->
    <section v-if="product && product.description" class="detail-section">
      <div class="detail-tabs">
        <span class="tab active">商品详情</span>
      </div>
      <div class="detail-content">
        <!-- 注意：v-html 渲染富文本 -->
        <div class="rich-text" v-html="product.description"></div>
      </div>
    </section>

    <!-- 底部 -->
    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 ShopMall. All Rights Reserved.</span>
      </div>
    </footer>

    <!-- 购买意向确认弹窗 -->
    <transition name="modal-fade">
      <div v-if="showBuyModal" class="modal-mask" @click.self="showBuyModal = false">
        <div class="modal">
          <div class="modal-header">
            <h3>确认提交意向</h3>
            <button class="close-btn" @click="showBuyModal = false">×</button>
          </div>
          <div class="modal-body">
            <p class="confirm-text">您确定要提交对该商品的购买意向吗？</p>
            <p class="confirm-tip">提交后将进入排队队列，请耐心等待卖家处理。</p>
          </div>
          <div class="modal-footer">
            <button class="btn-cancel" @click="showBuyModal = false">取消</button>
            <button class="btn-submit" :disabled="submitting" @click="submitBuyIntent">
              {{ submitting ? '提交中...' : '确认提交' }}
            </button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail, submitIntent } from '@/api/buyer'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import { formatMoney } from '@/utils/format'
import logoImg from '@/assets/logo.png'

const route = useRoute()
const router = useRouter()

const productId = route.params.id
const product = ref(null)
const loading = ref(false)
const error = ref('')

const showBuyModal = ref(false)
const submitting = ref(false)

const isLoggedIn = computed(() => !!localStorage.getItem(STORAGE_KEYS.BUYER_TOKEN))

// 判断是否能购买
const canBuy = computed(() => {
  if (!product.value) return false
  // 状态 0 为在售，且库存 > 0
  return product.value.status === 0 && product.value.stock > 0
})

// 状态映射
function getStatusText(status) {
  const map = { 0: '在售', 1: '手动冻结', 2: '交易中冻结', 3: '已下架' }
  return map[status] || '未知'
}

async function loadDetail() {
  if (!productId) return router.push('/buyer/goods')
  loading.value = true
  error.value = ''
  try {
    // 请求详情
    const res = await getProductDetail(productId)
    product.value = res
    // 兼容后端可能的图片字段
    if (!product.value.images && product.value.imageUrl) {
      product.value.images = [product.value.imageUrl]
    }
  } catch (e) {
    error.value = e.message || '加载失败'
  } finally {
    loading.value = false
  }
}

// 点击购买按钮
function handleBuyClick() {
  if (!canBuy.value) return

  // 1. 未登录拦截
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再提交购买意向')
    // 记录当前页面路径，登录后跳回来
    router.push({ path: '/buyer/login', query: { redirect: route.fullPath } })
    return
  }
  
  // 2. 已登录，弹窗确认
  showBuyModal.value = true
}

// 真正提交请求
async function submitBuyIntent() {
  if (submitting.value) return
  submitting.value = true
  try {
    await submitIntent({ productId: productId })
    showBuyModal.value = false
    ElMessage.success('购买意向提交成功！请在我的订单查看进度')
  } catch (e) {
    ElMessage.error(e.message || '提交失败，请重试')
    // 如果是库存不足或已下架，刷新页面状态
    if (e.code === 40903 || e.code === 40909) {
      showBuyModal.value = false
      loadDetail()
    }
  } finally {
    submitting.value = false
  }
}

function handleLogout() {
  localStorage.removeItem(STORAGE_KEYS.BUYER_TOKEN)
  ElMessage.success('已退出登录')
  window.location.reload()
}

onMounted(() => {
  loadDetail()
})
</script>

<style scoped>
.goods-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

/* 头部（复用列表页） */
.header { background: #fff; }
.header-inner {
  max-width: 1200px; margin: 0 auto; height: 80px;
  display: flex; align-items: center; padding: 0 20px; gap: 24px;
}
.logo img { height: 28px; cursor: pointer; }
.nav { flex: 1; display: flex; gap: 32px; margin-left: 20px; }
.nav-item { color: #333; text-decoration: none; font-size: 15px; }
.nav-item:hover { color: #e60012; }
.user-actions { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.user-actions a { color: #333; text-decoration: none; }
.login-btn:hover, .register-btn:hover { color: #e60012; }
.seller-entry { color: #999 !important; margin-left: 10px; }

/* 面包屑 */
.breadcrumb {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 16px 20px;
  font-size: 13px; color: #999; box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

/* 主体 */
.main {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px; box-sizing: border-box;
}
.state { text-align: center; color: #999; padding: 80px 0; font-size: 16px; }
.state.error { color: #e60012; }

/* 详情卡片 */
.detail-card { background: #fff; }
.detail-card-body { display: flex; gap: 60px; padding: 40px; }

/* 左侧图库 */
.product-gallery { flex: 0 0 460px; width: 460px; background: #fafafa; }
.carousel-img-wrap { width: 100%; height: 100%; display: flex; justify-content: center; align-items: center; }
.carousel-img-wrap img { width: 100%; height: 100%; object-fit: contain; }
/* 覆盖 Element 轮播图默认样式 */
:deep(.el-carousel__container) { height: 460px !important; }

/* 右侧信息 */
.product-info { flex: 1; display: flex; flex-direction: column; text-align: left; }
.product-name { font-size: 24px; font-weight: 500; margin: 0 0 20px; line-height: 1.4; }
.product-price { font-size: 32px; color: #e60012; font-weight: bold; margin-bottom: 20px; }
.product-meta {
  display: flex; gap: 20px; font-size: 14px; color: #666; margin-bottom: 20px;
  padding-bottom: 20px; border-bottom: 1px solid #f0f0f0;
}
.tips { font-size: 14px; color: #e60012; margin-bottom: 16px; background: #fff0f0; padding: 10px; }
.buy-btn {
  width: 200px; height: 50px; border: none; background: #e60012; color: #fff;
  font-size: 16px; cursor: pointer; transition: background 0.2s; margin-top: auto;
}
.buy-btn:hover:not(:disabled) { background: #c5000f; }
.buy-btn:disabled { background: #ccc; cursor: not-allowed; }

/* 图文详情 */
.detail-section {
  max-width: 1200px; width: 100%; margin: 0 auto 60px; padding: 0 20px; box-sizing: border-box;
}
.detail-tabs { background: #fff; padding: 0 40px; }
.tab {
  display: inline-block; padding: 18px 0; font-size: 16px;
  color: #333; border-bottom: 2px solid #e60012; font-weight: 500;
}
.detail-content { background: #fff; padding: 30px 40px 40px; }
.rich-text { font-size: 14px; color: #333; line-height: 1.8; }
/* 让富文本里的图片自适应宽度 */
.rich-text :deep(img) { max-width: 100%; height: auto; display: block; margin: 10px 0; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }

/* 弹窗 */
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 2000; display: flex; align-items: center; justify-content: center; }
.modal { background: #fff; width: 400px; max-width: 92vw; box-shadow: 0 4px 24px rgba(0,0,0,0.15); }
.modal-header { position: relative; padding: 20px; border-bottom: 1px solid #f0f0f0; text-align: center; }
.modal-header h3 { margin: 0; font-size: 18px; color: #333; }
.close-btn { position: absolute; top: 50%; right: 20px; transform: translateY(-50%); border: none; background: transparent; font-size: 24px; color: #999; cursor: pointer; }
.modal-body { padding: 30px 20px; text-align: center; }
.confirm-text { font-size: 16px; color: #333; margin: 0 0 10px; font-weight: 500; }
.confirm-tip { font-size: 13px; color: #999; margin: 0; }
.modal-footer { display: flex; padding: 0 20px 20px; gap: 12px; }
.btn-cancel, .btn-submit { flex: 1; height: 40px; border: none; cursor: pointer; font-size: 14px; }
.btn-cancel { background: #f5f5f5; color: #333; }
.btn-submit { background: #e60012; color: #fff; }
.btn-submit:disabled { background: #ccc; }

/* 过渡 */
.modal-fade-enter-active, .modal-fade-leave-active { transition: opacity 0.25s ease; }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
</style>