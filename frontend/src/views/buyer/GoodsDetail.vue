<template>
  <div class="goods-page">
    <!-- 主头部（公共组件） -->
    <BuyerHeader />

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
                  <img :src="getImageUrl(img)" alt="商品图片" />
                </div>
              </el-carousel-item>
            </el-carousel>
            <!-- 没有图时显示占位图 -->
            <div v-else class="carousel-img-wrap">
              <img :src="getImageUrl('')" alt="暂无图片" />
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

            <!-- 异常提示（商品状态已收敛为在售 / 已下架两态） -->
            <div v-if="!isOnSale" class="tips error-tip">
              商品已下架，暂时无法购买
            </div>
            <div v-else-if="Number(product.stock) <= 0" class="tips error-tip">
              商品已售罄
            </div>

            <!-- 购买数量（同款可买多件，单条上限 99 件） -->
            <div class="qty-row">
              <span class="qty-label">数量</span>
              <el-input-number
                v-model="quantity"
                :min="1"
                :max="maxQuantity"
                :disabled="!canBuy"
                size="default"
              />
              <span class="qty-tip">单次最多 {{ maxQuantity }} 件，加入购物车后可再修改</span>
            </div>

            <!-- 操作区：详情页只做「加入购物车」，不提供立即购买旁路 -->
            <div class="btn-row">
              <button class="buy-btn" :disabled="!canBuy || adding" @click="handleAddCart">
                {{ addBtnText }}
              </button>
              <button
                class="fav-btn"
                :class="{ 'is-fav': isFavorited }"
                @click="handleFavorite"
              >
                {{ isFavorited ? '已收藏' : '收藏' }}
              </button>
            </div>
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
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductDetail, addToCart, addFavorite, removeFavorite, getFavoriteList } from '@/api/buyer'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import { formatMoney } from '@/utils/format'
import { pushPendingCart } from '@/utils/pendingCart'
import BuyerHeader from '@/components/BuyerHeader.vue'

const route = useRoute()
const router = useRouter()

const productId = route.params.id
const product = ref(null)
const loading = ref(false)
const error = ref('')

const quantity = ref(1)
const adding = ref(false)
const isFavorited = ref(false)

const isLoggedIn = computed(() => !!localStorage.getItem(STORAGE_KEYS.BUYER_TOKEN))

// 商品是否在售：迭代 3 起只有「在售 / 已下架」两态，0 = 在售
const isOnSale = computed(() => Number(product.value?.status) === 0)

// 判断是否能购买：在售且库存 > 0
const canBuy = computed(() => isOnSale.value && Number(product.value?.stock) > 0)

// 可购数量上限：库存与单条 99 件取小
const maxQuantity = computed(() => {
  const stock = Number(product.value?.stock ?? 1)
  return Math.max(1, Math.min(99, stock > 0 ? stock : 1))
})

const addBtnText = computed(() => {
  if (adding.value) return '加入中...'
  if (!isOnSale.value) return '已下架'
  if (Number(product.value?.stock) <= 0) return '已售罄'
  return '加入购物车'
})

// 状态映射：两态口径
function getStatusText(status) {
  return Number(status) === 0 ? '在售' : '已下架'
}

// 处理图片路径拼接，防止红屏和裂图
function getImageUrl(url) {
  // 如果没有图片，返回在线占位图
  if (!url) return 'https://via.placeholder.com/460x460?text=No+Image'
  // 如果是完整的网络地址，直接返回
  if (url.startsWith('http')) return url
  // 否则拼接后端地址 (假设后端在 8080 端口)
  return `http://localhost:8080/${url.startsWith('/') ? url.slice(1) : url}`
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

// 查询当前商品是否已被收藏（收藏上限 200 条，一次拉全即可判断，不额外增接口）
async function loadFavoriteState() {
  if (!isLoggedIn.value || !productId) return
  try {
    const res = await getFavoriteList({ page: 1, size: 200 })
    const list = res?.list || []
    isFavorited.value = list.some(item => String(item.productId) === String(productId))
  } catch (e) {
    /* 收藏状态查询失败不影响主流程，静默处理 */
  }
}

// 未登录统一引导登录，并记住回跳页面
function redirectToLogin() {
  router.push({ path: '/buyer/login', query: { redirect: route.fullPath } })
}

// 加入购物车
async function handleAddCart() {
  if (!canBuy.value || adding.value) return

  // 1. 未登录：先存临时加购，登录成功后自动恢复（B.2-2 组内拟定）
  if (!isLoggedIn.value) {
    const result = pushPendingCart(productId, quantity.value)
    if (!result.ok) return ElMessage.warning(result.reason)
    ElMessage.warning('请先登录，登录后将自动把该商品加入购物车')
    redirectToLogin()
    return
  }

  // 2. 已登录：直接加购
  adding.value = true
  try {
    await addToCart(productId, quantity.value)
    ElMessage.success('已加入购物车')
  } catch (e) {
    ElMessage.error(e.message || '加入购物车失败')
    // 数量超限 / 购物车已满 / 商品已下架，刷新详情保证展示的是最新状态
    if ([40931, 40932, 40926].includes(e.code)) loadDetail()
  } finally {
    adding.value = false
  }
}

// 收藏 / 取消收藏
async function handleFavorite() {
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再收藏')
    return redirectToLogin()
  }
  try {
    if (isFavorited.value) {
      await removeFavorite(productId)
      isFavorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(productId)
      isFavorited.value = true
      ElMessage.success('收藏成功')
    }
  } catch (e) {
    if (e.code === 40931) {
      ElMessage.warning('收藏已达 200 条上限，请先到「我的收藏」清理后再收藏')
    } else {
      ElMessage.error(e.message || '操作失败，请重试')
    }
  }
}

onMounted(() => {
  loadDetail()
  loadFavoriteState()
})
</script>

<style scoped>
.goods-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

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

/* 数量选择 */
.qty-row { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
.qty-label { font-size: 14px; color: #666; }
.qty-tip { font-size: 12px; color: #999; }

/* 操作按钮 */
.btn-row { display: flex; gap: 12px; margin-top: auto; }
.buy-btn {
  width: 200px; height: 50px; border: none; background: #e60012; color: #fff;
  font-size: 16px; cursor: pointer; transition: background 0.2s;
}
.buy-btn:hover:not(:disabled) { background: #c5000f; }
.buy-btn:disabled { background: #ccc; cursor: not-allowed; }
.fav-btn {
  width: 140px; height: 50px; border: 1px solid #ddd; background: #fff; color: #333;
  font-size: 16px; cursor: pointer; transition: all 0.2s;
}
.fav-btn:hover, .fav-btn.is-fav { border-color: #e60012; color: #e60012; }

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
</style>
