<template>
  <div class="order-detail-page">
    <BuyerHeader />

    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <router-link to="/buyer/orders">我的订单</router-link>
      <span class="sep">/</span>
      <span>订单详情</span>
    </div>

    <main class="main">
      <div v-if="loading" class="card state">加载中...</div>

      <div v-else-if="!order" class="card state empty">
        <el-empty description="订单不存在或已被删除" />
        <el-button type="danger" @click="router.push('/buyer/orders')">返回我的订单</el-button>
      </div>

      <template v-else>
        <!-- 状态条 -->
        <section class="card status-card">
          <div class="status-left">
            <el-tag :type="orderStatusTagType(order.status)" effect="dark" size="large">
              {{ order.statusText || ORDER_STATUS_TEXT[order.status] || '-' }}
            </el-tag>
            <div class="status-text">
              <p class="status-title">{{ statusHint.title }}</p>
              <p class="status-sub">{{ statusHint.sub }}</p>
            </div>
          </div>

          <div class="status-right">
            <!-- 待支付：展示 expireAt 倒计时 -->
            <div v-if="isPendingPay" class="countdown">
              <span class="cd-label">支付剩余</span>
              <span class="cd-value">{{ countdownText }}</span>
            </div>

            <div class="status-actions">
              <el-button v-if="isPendingPay" type="danger" @click="goPay">去支付</el-button>
              <el-button v-if="canReceiveOrder(order.status)" type="danger" @click="handleReceive">
                确认收货
              </el-button>
              <el-button v-if="hasLogistics(order.status)" plain @click="scrollToLogistics">
                查看物流
              </el-button>
              <el-button v-if="isCustomerCancellable(order.status)" plain @click="openCancel">
                取消订单
              </el-button>
            </div>
          </div>
        </section>

        <!-- 收货信息（下单时的地址快照，不随地址库变化） -->
        <section class="card">
          <h3 class="card-title">收货信息</h3>
          <div v-if="snapshot" class="addr-block">
            <div class="addr-line1">
              <span class="receiver">{{ snapshot.receiver }}</span>
              <span class="phone">{{ snapshot.phone }}</span>
            </div>
            <div class="addr-line2">{{ snapshot.region }} {{ snapshot.detail }}</div>
            <p class="addr-tip">此为本订单下单时保存的地址快照，收货信息不可修改</p>
          </div>
          <div v-else class="empty-line">未记录收货信息</div>
        </section>

        <!-- 物流信息（C.2-04：40 及之后展示） -->
        <section v-if="hasLogistics(order.status)" ref="logisticsRef" class="card">
          <h3 class="card-title">物流信息</h3>

          <div v-if="logisticsLoading" class="empty-line">加载中...</div>
          <div v-else-if="!logistics" class="empty-line">暂无物流信息</div>
          <div v-else>
            <div class="logi-head">
              <span class="logi-carrier">{{ logistics.carrier }}</span>
              <span class="logi-no">运单号：{{ logistics.logisticsNo }}</span>
              <el-tag size="small" :type="logistics.status === 'SIGNED' ? 'success' : 'warning'">
                {{ logistics.statusText }}
              </el-tag>
            </div>

            <el-timeline v-if="(logistics.traces || []).length" class="trace-list">
              <el-timeline-item
                v-for="(t, idx) in logistics.traces"
                :key="idx"
                :timestamp="formatTime(t.time)"
                placement="top"
                :type="idx === 0 ? 'danger' : ''"
              >
                <span :class="{ 'trace-latest': idx === 0 }">{{ t.desc }}</span>
              </el-timeline-item>
            </el-timeline>
            <div v-else class="empty-line">暂无轨迹记录</div>
            <p class="logi-tip">一个订单对应一个运单号，不拆分包裹</p>
          </div>
        </section>

        <!-- 商品明细：40 / 50 时逐件申请售后 -->
        <section class="card">
          <div class="card-head">
            <h3 class="card-title">商品明细</h3>
            <span class="page-tip">共 {{ order.itemCount ?? totalQuantity }} 件</span>
          </div>

          <div class="item-head">
            <span class="col-goods">商品</span>
            <span class="col-price">单价</span>
            <span class="col-qty">数量</span>
            <span class="col-subtotal">小计</span>
            <span class="col-op">售后</span>
          </div>

          <div v-for="item in order.items || []" :key="item.productId" class="item-row">
            <span class="col-goods">
              <span class="goods-name" @click="goGoods(item.productId)">{{ item.name }}</span>
            </span>
            <span class="col-price">{{ formatMoney(item.price) }}</span>
            <span class="col-qty">× {{ item.quantity }}</span>
            <span class="col-subtotal">{{ formatMoney(item.subtotal) }}</span>
            <span class="col-op">
              <template v-if="canApplyAfterSale(order.status)">
                <el-button
                  v-if="ongoingAfterSaleOf(item.productId)"
                  link
                  type="info"
                  @click="goAfterSaleDetail(ongoingAfterSaleOf(item.productId).afterSaleNo)"
                >
                  售后处理中
                </el-button>
                <el-button v-else link type="danger" @click="goApply(item)">
                  申请售后
                </el-button>
              </template>
              <span v-else class="op-none">-</span>
            </span>
          </div>

          <div class="amount-bar">
            <span class="amount-label">订单金额</span>
            <span class="amount-value">{{ formatMoney(order.totalAmount) }}</span>
          </div>
          <p class="amount-tip">订单金额 = 商品快照单价 × 数量之和，无运费、无优惠</p>
        </section>

        <!-- 支付信息 -->
        <section class="card">
          <h3 class="card-title">支付信息</h3>
          <div class="info-grid">
            <div class="info-row"><span>订单号</span><span>{{ order.orderNo }}</span></div>
            <div class="info-row"><span>下单时间</span><span>{{ formatTime(order.createdAt) }}</span></div>
            <div class="info-row">
              <span>支付渠道</span>
              <span>{{ channelText(order.payChannel) }}</span>
            </div>
            <div class="info-row">
              <span>支付时间</span>
              <span>{{ order.paidAt ? formatTime(order.paidAt) : '未支付' }}</span>
            </div>
            <div v-if="order.completedAt" class="info-row">
              <span>完成时间</span><span>{{ formatTime(order.completedAt) }}</span>
            </div>
            <div v-if="order.cancelReason" class="info-row">
              <span>取消原因</span><span>{{ order.cancelReason }}</span>
            </div>
            <div v-if="order.refundAmount" class="info-row">
              <span>退款信息</span>
              <span class="refund-text">
                已按原渠道（{{ channelText(order.refundChannel) }}）退回 {{ formatMoney(order.refundAmount) }}
              </span>
            </div>
          </div>
        </section>

        <!-- 关联售后单（C.3-02：订单详情可跳转该订单关联的售后单） -->
        <section class="card">
          <div class="card-head">
            <h3 class="card-title">售后记录</h3>
            <el-button link type="primary" @click="router.push('/buyer/after-sales')">全部售后单</el-button>
          </div>

          <div v-if="afterSaleList.length === 0" class="empty-line">该订单暂无售后记录</div>
          <div v-else class="as-list">
            <div
              v-for="as in afterSaleList"
              :key="as.afterSaleNo"
              class="as-item"
              @click="goAfterSaleDetail(as.afterSaleNo)"
            >
              <div class="as-main">
                <span class="as-title">{{ as.title }}</span>
                <span class="as-product">{{ as.productName }} × {{ as.quantity }}</span>
              </div>
              <div class="as-right">
                <el-tag size="small" :type="afterSaleStatusTagType(as.status)">
                  {{ afterSaleStatusText(as.status, as.statusText) }}
                </el-tag>
                <span class="as-time">{{ formatTime(as.createdAt) }}</span>
              </div>
            </div>
          </div>
        </section>
      </template>
    </main>

    <!-- 取消订单弹窗：原因选填，最长 100 字；已支付需提示原路退款 -->
    <el-dialog v-model="cancelDialogVisible" title="取消订单" width="480px" :close-on-click-modal="false">
      <div class="cancel-body">
        <p class="cancel-warn">
          <template v-if="isPaid(order.status)">
            该订单已支付，取消后款项将按原支付渠道（{{ channelText(order.payChannel) }}）退回，
            库存同时回补。取消后订单进入终态，不可恢复。
          </template>
          <template v-else>
            该订单尚未支付，取消后订单进入终态，占用的库存将被释放。取消后不可恢复。
          </template>
        </p>
        <el-input
          v-model="cancelReason"
          type="textarea"
          :rows="3"
          maxlength="100"
          show-word-limit
          placeholder="请填写取消原因（选填）"
        />
      </div>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">再想想</el-button>
        <el-button type="danger" :loading="cancelling" @click="handleCancel">确定取消订单</el-button>
      </template>
    </el-dialog>

    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 ShopMall. All Rights Reserved.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getOrderDetail, getLogistics, cancelOrder, receiveOrder, PAY_CHANNELS } from '@/api/buyer'
import { formatMoney, formatTime } from '@/utils/format'
import {
  ORDER_STATUS,
  ORDER_STATUS_TEXT,
  isCustomerCancellable,
  isAwaitingPayment,
  canReceiveOrder,
  hasLogistics,
  canApplyAfterSale,
  isPaidStatus,
  orderStatusTagType
} from '@/constants/orderStatus'
import {
  afterSaleStatusText,
  afterSaleStatusTagType,
  isAfterSaleOngoing
} from '@/constants/afterSaleStatus'
import BuyerHeader from '@/components/BuyerHeader.vue'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo || '')

const loading = ref(true)
const order = ref(null)
const logistics = ref(null)
const logisticsLoading = ref(false)
const logisticsRef = ref(null)

const cancelDialogVisible = ref(false)
const cancelReason = ref('')
const cancelling = ref(false)

const nowTs = ref(Date.now())
let clockTimer = null

const snapshot = computed(() => order.value?.addressSnapshot || null)
const afterSaleList = computed(() => order.value?.afterSales || [])
const isPendingPay = computed(() => isAwaitingPayment(order.value?.status))

const totalQuantity = computed(() =>
  (order.value?.items || []).reduce((s, i) => s + Number(i.quantity || 0), 0)
)

const remainMs = computed(() => {
  if (!order.value?.expireAt) return 0
  return Math.max(0, new Date(order.value.expireAt).getTime() - nowTs.value)
})

const countdownText = computed(() => {
  const total = Math.floor(remainMs.value / 1000)
  const h = String(Math.floor(total / 3600)).padStart(2, '0')
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const s = String(total % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
})

/* 状态提示文案（按钮与文案都以后端 status 为准） */
const statusHint = computed(() => {
  const s = Number(order.value?.status)
  const map = {
    [ORDER_STATUS.PENDING_PAY]: {
      title: '等待付款',
      sub: '请在倒计时结束前完成支付，超时订单将自动关闭并释放库存'
    },
    [ORDER_STATUS.PAID]: {
      title: '已付款，等待商家确认',
      sub: '商家确认后会继续备货、发货，期间可申请取消订单'
    },
    [ORDER_STATUS.CONFIRMED]: {
      title: '商家已确认订单',
      sub: '商家正在备货，发货前可申请取消订单'
    },
    [ORDER_STATUS.READY]: {
      title: '备货完成',
      sub: '等待商家发货，发货后即可查看物流并申请售后'
    },
    [ORDER_STATUS.SHIPPED]: {
      title: '商家已发货',
      sub: '收到商品后请点击「确认收货」，发货后不再支持取消订单'
    },
    [ORDER_STATUS.COMPLETED]: {
      title: '交易完成',
      sub: '如商品有问题，可在商品明细中逐件申请售后'
    },
    [ORDER_STATUS.CANCELLED]: {
      title: '订单已取消',
      sub: '订单已进入终态，不可恢复；如已支付，款项将原路退回'
    }
  }
  return map[s] || { title: '订单状态', sub: '' }
})

function channelText(code) {
  if (!code) return '-'
  const hit = PAY_CHANNELS.find(c => c.value === code)
  return hit ? hit.label : code
}

function isPaid(status) {
  return isPaidStatus(status)
}

// 该商品是否已有进行中的售后单（PENDING / WAIT_RETURN）
function ongoingAfterSaleOf(productId) {
  return afterSaleList.value.find(
    a => String(a.productId) === String(productId) && isAfterSaleOngoing(a.status)
  )
}

/* ---------------- 数据 ---------------- */

async function loadOrder() {
  loading.value = true
  try {
    order.value = await getOrderDetail(orderNo)
    if (hasLogistics(order.value?.status)) {
      loadLogistics()
    }
  } catch (e) {
    ElMessage.error(e.message || '获取订单详情失败')
    order.value = null
  } finally {
    loading.value = false
  }
}

async function loadLogistics() {
  logisticsLoading.value = true
  try {
    logistics.value = await getLogistics(orderNo)
  } catch (e) {
    // 40971 未发货 / 暂无物流：直接展示订单里带的物流快照或空态
    logistics.value = order.value?.logistics || null
  } finally {
    logisticsLoading.value = false
  }
}

/* ---------------- 倒计时 ---------------- */
function startClock() {
  if (!isPendingPay.value) return
  clockTimer = setInterval(() => {
    nowTs.value = Date.now()
    if (remainMs.value <= 0) {
      clearInterval(clockTimer)
      clockTimer = null
      ElMessage.warning('订单支付超时，已自动关闭')
      loadOrder()
    }
  }, 1000)
}

/* ---------------- 操作 ---------------- */

function goPay() {
  router.push(`/buyer/pay/${encodeURIComponent(orderNo)}`)
}

function goGoods(productId) {
  router.push(`/buyer/goods/detail/${productId}`)
}

function goApply(item) {
  router.push({
    path: '/buyer/after-sale/apply',
    query: { orderNo, productId: String(item.productId) }
  })
}

function goAfterSaleDetail(afterSaleNo) {
  router.push(`/buyer/after-sales/${encodeURIComponent(afterSaleNo)}`)
}

function scrollToLogistics() {
  nextTick(() => {
    logisticsRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

// 确认收货：40 → 50，之后进入售后保障范围
async function handleReceive() {
  try {
    await ElMessageBox.confirm(
      '确认已经收到商品吗？确认后订单将进入「交易完成」终态，不可撤销。',
      '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '还没收到', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    await receiveOrder(orderNo)
    ElMessage.success('已确认收货，交易完成')
    await loadOrder()
  } catch (e) {
    if (e.code === 40921 || e.code === 40923) {
      ElMessage.warning(`${e.message}，已为您刷新订单状态`)
      await loadOrder()
    } else {
      ElMessage.error(e.message || '确认收货失败，请重试')
    }
  }
}

function openCancel() {
  cancelReason.value = ''
  cancelDialogVisible.value = true
}

async function handleCancel() {
  cancelling.value = true
  try {
    const res = await cancelOrder(orderNo, cancelReason.value.trim())
    cancelDialogVisible.value = false
    if (res?.refundAmount) {
      ElMessage.success(`订单已取消，${formatMoney(res.refundAmount)} 已按原渠道退回`)
    } else {
      ElMessage.success('订单已取消，库存已释放')
    }
    await loadOrder()
  } catch (e) {
    if (e.code === 40921 || e.code === 40923) {
      cancelDialogVisible.value = false
      ElMessage.warning(`${e.message}，已为您刷新订单状态`)
      await loadOrder()
    } else {
      ElMessage.error(e.message || '取消失败，请重试')
    }
  } finally {
    cancelling.value = false
  }
}

onMounted(async () => {
  await loadOrder()
  startClock()
})

onBeforeUnmount(() => {
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.order-detail-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

.breadcrumb {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 16px 20px;
  font-size: 13px; color: #999; box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

.main {
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px;
  box-sizing: border-box; display: flex; flex-direction: column; gap: 16px;
}
.card { background: #fff; padding: 24px 32px; }
.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

.card-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.card-title { font-size: 16px; font-weight: 500; margin: 0 0 16px; }
.card-head .card-title { margin-bottom: 0; }
.page-tip { font-size: 12px; color: #999; }
.empty-line { color: #999; font-size: 14px; padding: 12px 0; }

/* 状态条 */
.status-card {
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  border-left: 4px solid #e60012;
}
.status-left { display: flex; align-items: center; gap: 18px; }
.status-title { font-size: 16px; font-weight: 500; margin-bottom: 6px; }
.status-sub { font-size: 13px; color: #999; }
.status-right { display: flex; align-items: center; gap: 24px; }
.countdown { text-align: right; display: flex; flex-direction: column; gap: 4px; }
.cd-label { font-size: 12px; color: #999; }
.cd-value { font-size: 22px; color: #e60012; font-weight: bold; letter-spacing: 1px; }
.status-actions { display: flex; align-items: center; gap: 8px; }

/* 地址 */
.addr-line1 { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.receiver { font-size: 15px; font-weight: 500; }
.phone { font-size: 14px; color: #666; }
.addr-line2 { font-size: 14px; color: #666; }
.addr-tip { font-size: 12px; color: #bbb; margin-top: 10px; }

/* 物流 */
.logi-head { display: flex; align-items: center; gap: 16px; margin-bottom: 18px; }
.logi-carrier { font-size: 15px; font-weight: 500; }
.logi-no { font-size: 14px; color: #666; }
.trace-list { padding-left: 4px; }
.trace-latest { color: #e60012; }
.logi-tip { font-size: 12px; color: #bbb; margin-top: 8px; }

/* 商品明细 */
.item-head, .item-row { display: flex; align-items: center; gap: 12px; }
.item-head { font-size: 13px; color: #999; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }
.item-row { padding: 16px 0; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
.col-goods { flex: 1; min-width: 0; }
.col-price { width: 110px; flex: 0 0 110px; }
.col-qty { width: 80px; flex: 0 0 80px; color: #666; }
.col-subtotal { width: 110px; flex: 0 0 110px; color: #e60012; font-weight: 500; }
.col-op { width: 110px; flex: 0 0 110px; text-align: right; }
.item-head .col-subtotal, .item-head .col-op { color: #999; font-weight: normal; }
.goods-name { cursor: pointer; }
.goods-name:hover { color: #e60012; }
.op-none { color: #ccc; }

.amount-bar { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding-top: 20px; }
.amount-label { font-size: 14px; color: #666; }
.amount-value { font-size: 24px; color: #e60012; font-weight: bold; }
.amount-tip { text-align: right; font-size: 12px; color: #999; margin-top: 6px; }

/* 信息表 */
.info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 2px 40px; }
.info-row {
  display: flex; justify-content: space-between; font-size: 14px; color: #666;
  padding: 10px 0; border-bottom: 1px dashed #f2f2f2;
}
.info-row span:last-child { color: #333; }
.refund-text { color: #e60012 !important; }

/* 售后记录 */
.as-list { display: flex; flex-direction: column; }
.as-item {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  padding: 14px 4px; border-bottom: 1px solid #f5f5f5; cursor: pointer;
}
.as-item:hover { background: #fafafa; }
.as-main { display: flex; flex-direction: column; gap: 4px; }
.as-title { font-size: 14px; }
.as-product { font-size: 12px; color: #999; }
.as-right { display: flex; align-items: center; gap: 12px; }
.as-time { font-size: 12px; color: #999; }

/* 取消弹窗 */
.cancel-warn { font-size: 14px; color: #666; line-height: 1.7; margin-bottom: 16px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
