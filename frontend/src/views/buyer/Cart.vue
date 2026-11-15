<template>
  <div class="cart-page">
    <!-- 主头部（公共组件） -->
    <BuyerHeader />

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>购物车</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div class="cart-card">
        <div class="title-row">
          <h2 class="page-title">我的购物车</h2>
          <span class="page-tip">共 {{ cartList.length }} 条（上限 50 条）</span>
        </div>

        <div v-if="loading" class="state">加载中...</div>

        <div v-else-if="cartList.length === 0" class="state empty">
          <el-empty description="购物车还是空的，去挑几件喜欢的商品吧" />
          <el-button type="danger" @click="router.push('/buyer/goods')">去逛逛</el-button>
        </div>

        <template v-else>
          <!-- 列表表头 -->
          <div class="cart-head">
            <span class="col-check"></span>
            <span class="col-goods">商品</span>
            <span class="col-price">单价</span>
            <span class="col-qty">数量</span>
            <span class="col-subtotal">小计</span>
            <span class="col-op">操作</span>
          </div>

          <!-- 列表行 -->
          <div
            v-for="item in cartList"
            :key="item.cartId"
            class="cart-row"
            :class="{ 'row-off': !item.available, 'row-error': item.shortage }"
          >
            <span class="col-check">
              <!-- 已下架商品禁用勾选，结算时不可提交 -->
              <el-checkbox v-model="item.checked" :disabled="!item.available" />
            </span>

            <span class="col-goods">
              <span class="thumb">
                <!-- 购物车接口未强制返回图片，有则展示、无则占位，避免裂图 -->
                <img v-if="coverOf(item)" :src="getImageUrl(coverOf(item))" alt="商品图片" @error="handleImageError" />
                <span v-else class="thumb-ph">{{ (item.name || '商').slice(0, 1) }}</span>
              </span>
              <span class="goods-text">
                <span class="goods-name" @click="goDetail(item.productId)">{{ item.name }}</span>
                <span class="tag-row">
                  <el-tag v-if="!item.available" size="small" type="info">已下架</el-tag>
                  <span v-if="item.shortage" class="shortage-tip">库存不足，请剔除后重新提交</span>
                </span>
              </span>
            </span>

            <span class="col-price">{{ formatMoney(item.price) }}</span>

            <span class="col-qty">
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :max="99"
                :disabled="!item.available"
                size="small"
                @change="handleQuantityChange(item)"
              />
            </span>

            <span class="col-subtotal">{{ formatMoney(item.subtotal) }}</span>

            <span class="col-op">
              <el-button link type="danger" @click="handleRemove(item)">移除</el-button>
            </span>
          </div>

          <!-- 结算栏 -->
          <div class="cart-foot">
            <div class="foot-left">
              <el-button :disabled="!hasChecked || converting" @click="handleToFavorite">
                {{ converting ? '处理中...' : '批量转收藏' }}
              </el-button>
              <span class="checked-tip">已选 {{ checkedList.length }} 件（合计只统计在售商品）</span>
            </div>
            <div class="foot-right">
              <span class="total-label">合计：</span>
              <span class="total-price">{{ formatMoney(checkedAmount) }}</span>
              <button class="checkout-btn" :disabled="!hasChecked || submitting" @click="handleCheckout">
                {{ submitting ? '提交中...' : '结算' }}
              </button>
            </div>
          </div>
        </template>
      </div>
    </main>

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
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCartList,
  updateCartQuantity,
  removeCartItem,
  checkoutCart,
  cartToFavorite
} from '@/api/buyer'
import { formatMoney } from '@/utils/format'
import BuyerHeader from '@/components/BuyerHeader.vue'

const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const converting = ref(false)
const cartList = ref([])

// 已勾选且在售的条目（已下架不可勾选，天然被排除）
const checkedList = computed(() => cartList.value.filter(item => item.checked && item.available))
const hasChecked = computed(() => checkedList.value.length > 0)

// 合计只累加在售且已勾选的商品（与后端 totalAmount 口径一致）
const checkedAmount = computed(() => {
  const sum = checkedList.value.reduce((acc, item) => {
    return acc + Number(item.price || 0) * Number(item.quantity || 0)
  }, 0)
  return sum.toFixed(2)
})

// 兼容后端可能的不同图片字段命名
function coverOf(item) {
  return item?.image || item?.mainImage || item?.imageUrl || item?.cover || ''
}

function getImageUrl(url) {
  if (!url) return 'https://via.placeholder.com/160x160?text=No+Image'
  if (url.startsWith('http') || url.startsWith('data:')) return url
  return `http://localhost:8080/${url.startsWith('/') ? url.slice(1) : url}`
}

function handleImageError(e) {
  if (e.target.src.includes('placeholder.com')) return
  e.target.src = 'https://via.placeholder.com/160x160?text=图片加载失败'
}

// 加载购物车：补齐勾选态、缺货标红态与本地小计
async function loadCart() {
  loading.value = true
  try {
    const res = await getCartList()
    const list = res?.list || []
    cartList.value = list.map(item => ({
      ...item,
      // 默认勾选在售商品，已下架不勾选
      checked: !!item.available,
      shortage: false,
      subtotal: item.subtotal ?? (Number(item.price || 0) * Number(item.quantity || 0)).toFixed(2)
    }))
  } catch (e) {
    ElMessage.error(e.message || '获取购物车失败')
    cartList.value = []
  } finally {
    loading.value = false
  }
}

// 修改数量：成功后本地重算小计，失败回滚到服务端数据
async function handleQuantityChange(item) {
  const target = Number(item.quantity)
  try {
    await updateCartQuantity(item.cartId, target)
    item.subtotal = (Number(item.price || 0) * target).toFixed(2)
  } catch (e) {
    ElMessage.error(e.message || '修改数量失败')
    await loadCart()
  }
}

// 移除单条
async function handleRemove(item) {
  try {
    await removeCartItem(item.cartId)
    ElMessage.success('已移除该商品')
    await loadCart()
  } catch (e) {
    ElMessage.error(e.message || '移除失败')
  }
}

// 批量转收藏（B.2-4）
async function handleToFavorite() {
  const ids = checkedList.value.map(item => item.cartId)
  if (ids.length === 0) return ElMessage.warning('请先勾选要转收藏的商品')

  try {
    await ElMessageBox.confirm(
      `将选中的 ${ids.length} 件商品转为收藏？转换成功后这些商品会从购物车移除。`,
      '批量转收藏',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return // 用户取消
  }

  converting.value = true
  try {
    const res = await cartToFavorite(ids)
    const count = res?.converted ?? ids.length
    ElMessage.success(`已转收藏 ${count} 件商品`)
    await loadCart()
  } catch (e) {
    ElMessage.error(e.message || '转收藏失败')
    // 收藏已达 200 条上限等情况，刷新保持与后端一致
    if (e.code === 40931) await loadCart()
  } finally {
    converting.value = false
  }
}

// 结算下单（B.2-3）：统一走 /api/cart/checkout，不直接调 /api/order
async function handleCheckout() {
  const ids = checkedList.value.map(item => item.cartId)
  if (ids.length === 0) return ElMessage.warning('请先勾选要结算的商品')
  if (submitting.value) return

  submitting.value = true
  try {
    const res = await checkoutCart(ids)
    ElMessage.success(`下单成功${res?.orderNo ? `，订单号 ${res.orderNo}` : ''}`)
    await loadCart()
    router.push('/buyer/orders')
  } catch (e) {
    // 40924：库存不足，按 data.shortage 标红对应商品，提示剔除后重下
    if (e.code === 40924) {
      markShortage(e.data?.shortage || [])
      ElMessage.error('部分商品库存不足，已标红的商品请剔除后重新提交')
    } else if (e.code === 40926) {
      ElMessage.error('购物车中含已下架商品，请剔除后重新提交')
      await loadCart()
    } else if (e.code === 40933) {
      ElMessage.warning('请先勾选要结算的商品')
    } else {
      ElMessage.error(e.message || '结算失败，请重试')
      await loadCart()
    }
  } finally {
    submitting.value = false
  }
}

// 缺货标红：命中的商品置红并取消勾选，避免重复提交
function markShortage(shortage) {
  const shortageIds = shortage.map(s => String(s.productId))
  cartList.value.forEach(item => {
    const hit = shortageIds.includes(String(item.productId))
    item.shortage = hit
    if (hit) item.checked = false
  })
}

function goDetail(productId) {
  router.push(`/buyer/goods/detail/${productId}`)
}

onMounted(() => {
  loadCart()
})
</script>

<style scoped>
.cart-page {
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
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px;
  box-sizing: border-box;
}
.cart-card { background: #fff; padding: 30px 40px; min-height: 500px; }

.title-row { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 20px; margin: 0; font-weight: 500; }
.page-tip { font-size: 12px; color: #999; }

.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

/* 列表：表头 + 行 */
.cart-head, .cart-row {
  display: flex; align-items: center; gap: 12px;
}
.cart-head {
  font-size: 13px; color: #999; padding: 0 0 12px; border-bottom: 1px solid #f0f0f0;
}
.cart-row {
  padding: 18px 0; border-bottom: 1px solid #f5f5f5; font-size: 14px;
}
.cart-row.row-off { color: #bbb; }
.cart-row.row-error { background: #fff8f8; }

.col-check { width: 32px; flex: 0 0 32px; display: flex; align-items: center; }
.col-goods { flex: 1; display: flex; align-items: center; gap: 14px; min-width: 0; }
.col-price { width: 110px; flex: 0 0 110px; }
.col-qty { width: 140px; flex: 0 0 140px; }
.col-subtotal { width: 110px; flex: 0 0 110px; color: #e60012; font-weight: 500; }
/* 表头「小计」保持浅灰，避免整行表头被价格色抢掉 */
.cart-head .col-subtotal { color: #999; font-weight: normal; }
.col-op { width: 70px; flex: 0 0 70px; text-align: right; }

.thumb {
  width: 80px; height: 80px; flex: 0 0 80px; background: #fafafa;
  display: flex; align-items: center; justify-content: center;
}
.thumb img { width: 100%; height: 100%; object-fit: contain; }
.thumb-ph { color: #ccc; font-size: 24px; }
.goods-text { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.goods-name {
  font-size: 15px; color: #333; cursor: pointer;
  overflow: hidden; text-overflow: ellipsis; display: -webkit-box;
  -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical;
}
.goods-name:hover { color: #e60012; }
.tag-row { display: flex; align-items: center; gap: 10px; }
.shortage-tip { font-size: 12px; color: #e60012; }

/* 结算栏 */
.cart-foot {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24px 0 0; margin-top: 10px;
}
.foot-left { display: flex; align-items: center; gap: 16px; }
.checked-tip { font-size: 13px; color: #999; }
.foot-right { display: flex; align-items: center; gap: 14px; }
.total-label { font-size: 14px; color: #666; }
.total-price { font-size: 26px; color: #e60012; font-weight: bold; }
.checkout-btn {
  width: 140px; height: 46px; border: none; background: #e60012; color: #fff;
  font-size: 16px; cursor: pointer; transition: background 0.2s;
}
.checkout-btn:hover:not(:disabled) { background: #c5000f; }
.checkout-btn:disabled { background: #ccc; cursor: not-allowed; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
