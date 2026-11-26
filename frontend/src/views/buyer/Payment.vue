<template>
  <div class="pay-page">
    <BuyerHeader />

    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <router-link to="/buyer/orders">我的订单</router-link>
      <span class="sep">/</span>
      <span>订单支付</span>
    </div>

    <main class="main">
      <div v-if="loading" class="pay-card state">加载中...</div>

      <div v-else-if="!order" class="pay-card state empty">
        <el-empty description="订单不存在或已被删除" />
        <el-button type="danger" @click="router.push('/buyer/orders')">返回我的订单</el-button>
      </div>

      <template v-else>
        <!-- 待支付：选渠道 + 倒计时 -->
        <section v-if="isPendingPay" class="pay-card">
          <div class="pay-head">
            <div class="head-left">
              <h2 class="page-title">订单支付</h2>
              <p class="order-no">订单号：{{ order.orderNo }}</p>
            </div>
            <div class="countdown">
              <span class="cd-label">支付剩余时间</span>
              <span class="cd-value">{{ countdownText }}</span>
            </div>
          </div>

          <div class="amount-row">
            <span class="amount-label">应付金额</span>
            <span class="amount-value">{{ formatMoney(order.totalAmount) }}</span>
          </div>

          <!-- 支付渠道：四个渠道单选，不支持组合支付 -->
          <div class="channel-block">
            <div class="block-title">选择支付渠道</div>
            <div class="channel-grid">
              <div
                v-for="ch in PAY_CHANNELS"
                :key="ch.value"
                class="channel-item"
                :class="{ active: channel === ch.value, disabled: payState === 'polling' }"
                @click="pickChannel(ch.value)"
              >
                <span class="channel-icon">{{ channelIcon(ch.value) }}</span>
                <span class="channel-name">{{ ch.label }}</span>
                <i v-if="channel === ch.value" class="channel-picked">
                  <el-icon><Select /></el-icon>
                </i>
              </div>
            </div>
            <p class="channel-tip">一次只能选择一个渠道，暂不支持多渠道组合支付</p>
          </div>

          <!-- 轮询中 -->
          <div v-if="payState === 'polling'" class="polling-block">
            <el-icon class="is-loading"><Loading /></el-icon>
            <div class="polling-text">
              <p class="polling-title">正在确认支付结果…</p>
              <p class="polling-sub">支付完成后订单会自动变为「已支付」，请勿关闭本页面</p>
            </div>
          </div>

          <!-- 支付未完成（C.1-03：订单不被关闭，可重新选择渠道） -->
          <el-alert
            v-else-if="payState === 'failed'"
            type="error"
            :closable="false"
            show-icon
            title="支付未完成"
            description="订单仍为「待支付」，您可以重新选择渠道再次支付；订单在剩余时间内不会被关闭。"
            class="fail-alert"
          />

          <div class="pay-actions">
            <el-button
              v-if="payState === 'polling'"
              type="primary"
              plain
              @click="pollOnce"
            >
              我已完成支付
            </el-button>
            <button
              v-else
              class="pay-btn"
              :disabled="!channel || submitting"
              @click="handlePay"
            >
              {{ submitting ? '发起支付...' : payState === 'failed' ? '重新支付' : `立即支付 ${formatMoney(order.totalAmount)}` }}
            </button>
            <el-button link type="info" @click="router.push('/buyer/orders')">稍后支付</el-button>
          </div>

          <p class="foot-tip">
            订单在 24 小时内未支付将被自动关闭，库存同时释放；关闭后不可再支付。
          </p>
        </section>

        <!-- 已支付 / 已关闭等非待支付状态 -->
        <section v-else class="pay-card result-card">
          <el-icon class="result-icon" :class="resultType">
            <component :is="resultType === 'success' ? 'CircleCheckFilled' : 'CircleCloseFilled'" />
          </el-icon>
          <h2 class="result-title">{{ resultTitle }}</h2>
          <p class="result-sub">{{ resultSub }}</p>

          <div class="result-info">
            <div class="info-row"><span>订单号</span><span>{{ order.orderNo }}</span></div>
            <div class="info-row"><span>订单金额</span><span>{{ formatMoney(order.totalAmount) }}</span></div>
            <div v-if="paymentInfo.paymentNo" class="info-row">
              <span>支付单号</span><span>{{ paymentInfo.paymentNo }}</span>
            </div>
            <div v-if="paymentInfo.paidAt" class="info-row">
              <span>支付时间</span><span>{{ formatTime(paymentInfo.paidAt) }}</span>
            </div>
          </div>

          <div class="result-actions">
            <el-button type="danger" @click="goOrderDetail">查看订单详情</el-button>
            <el-button @click="router.push('/buyer/orders')">返回我的订单</el-button>
          </div>
        </section>
      </template>
    </main>

    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 ShopMall. All Rights Reserved.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Select, Loading } from '@element-plus/icons-vue'
import { getOrderDetail, createPayment, getPaymentResult, PAY_CHANNELS } from '@/api/buyer'
import { formatMoney, formatTime } from '@/utils/format'
import { ORDER_STATUS, ORDER_STATUS_TEXT } from '@/constants/orderStatus'
import BuyerHeader from '@/components/BuyerHeader.vue'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.params.orderNo || '')

const loading = ref(true)
const submitting = ref(false)
const order = ref(null)
const channel = ref('ALIPAY')

// payState：idle 待选渠道 / polling 轮询中 / failed 支付未完成
const payState = ref('idle')
const paymentInfo = ref({ paymentNo: '', paidAt: null })

const nowTs = ref(Date.now())
let clockTimer = null
let pollTimer = null
let pollCount = 0

const POLL_INTERVAL = 2000   // 每 2 秒轮询一次
const POLL_MAX_TIMES = 30    // 最多轮询 60 秒

const isPendingPay = computed(() => Number(order.value?.status) === ORDER_STATUS.PENDING_PAY)

/* ---------------- 倒计时（expireAt 由后端下发，前端只负责展示） ---------------- */
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

const resultType = computed(() => {
  const s = Number(order.value?.status)
  if (s === ORDER_STATUS.CANCELLED) return 'danger'
  return 'success'
})

const resultTitle = computed(() => {
  const s = Number(order.value?.status)
  if (s === ORDER_STATUS.CANCELLED) return '订单已关闭'
  return '支付成功'
})

const resultSub = computed(() => {
  const s = Number(order.value?.status)
  if (s === ORDER_STATUS.CANCELLED) {
    return order.value?.cancelReason
      ? `取消原因：${order.value.cancelReason}`
      : '订单已取消，无法继续支付'
  }
  return '订单已进入「已支付」，等待商家确认，请留意订单页状态'
})

/* ---------------- 数据加载 ---------------- */
async function loadOrder() {
  try {
    order.value = await getOrderDetail(orderNo)
    // 订单详情不一定带支付单号，缺省时保留轮询得到的支付信息
    paymentInfo.value = {
      paymentNo: order.value?.paymentNo || paymentInfo.value.paymentNo || '',
      paidAt: order.value?.paidAt || paymentInfo.value.paidAt || null
    }
  } catch (e) {
    ElMessage.error(e.message || '获取订单失败')
    order.value = null
  }
}

async function init() {
  loading.value = true
  await loadOrder()
  loading.value = false

  // 非待支付：直接展示结果态，不用再轮询
  if (!isPendingPay.value) return

  startClock()

  // 进入页面时若已有支付单在途，续上轮询（例如从渠道页返回）
  try {
    const res = await getPaymentResult(orderNo)
    paymentInfo.value = { paymentNo: res.paymentNo, paidAt: res.paidAt }
    if (res.orderStatus !== ORDER_STATUS.PENDING_PAY) {
      await handlePayFinished(res.orderStatus)
    }
  } catch (e) {
    /* 没有支付记录属正常情况，忽略 */
  }
}

/* ---------------- 时钟 ---------------- */
function startClock() {
  stopClock()
  clockTimer = setInterval(() => {
    nowTs.value = Date.now()
    // 倒计时归零 → 订单被系统关闭（C.1-02）
    if (remainMs.value <= 0 && isPendingPay.value) {
      stopClock()
      stopPolling()
      ElMessage.warning('订单支付超时，已自动关闭')
      loadOrder()
    }
  }, 1000)
}

function stopClock() {
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = null
  }
}

/* ---------------- 支付 ---------------- */
function pickChannel(value) {
  if (payState.value === 'polling') return
  channel.value = value
}

function channelIcon(value) {
  if (value === 'ALIPAY') return '支'
  if (value === 'WECHAT') return '微'
  if (value === 'CREDIT_CARD') return '信'
  return '卡'
}

async function handlePay() {
  if (!channel.value) return ElMessage.warning('请选择支付渠道')
  if (submitting.value) return

  submitting.value = true
  try {
    const res = await createPayment(orderNo, channel.value)
    paymentInfo.value = { paymentNo: res.paymentNo, paidAt: null }
    // 真实对接：跳转 res.payUrl 完成支付，回到本页后由轮询确认结果
    // 沙箱 / Mock 场景下 payUrl 为占位地址，直接在当前页轮询
    startPolling()
  } catch (e) {
    if (e.code === 40941) {
      ElMessage.error(e.message || '订单不是待支付状态，无法支付')
      await loadOrder()
      if (!isPendingPay.value) stopPolling()
    } else {
      ElMessage.error(e.message || '发起支付失败，请重试')
      payState.value = 'failed'
    }
  } finally {
    submitting.value = false
  }
}

// 轮询 GET /api/order/{orderNo}/pay
function startPolling() {
  stopPolling()
  payState.value = 'polling'
  pollCount = 0
  pollTimer = setInterval(pollOnce, POLL_INTERVAL)
  pollOnce()
}

async function pollOnce() {
  try {
    const res = await getPaymentResult(orderNo)
    paymentInfo.value = { paymentNo: res.paymentNo, paidAt: res.paidAt }

    if (res.orderStatus !== ORDER_STATUS.PENDING_PAY) {
      await handlePayFinished(res.orderStatus)
      return
    }

    pollCount += 1
    // 轮询超时仍未成功 → 提示支付未完成，订单保持待支付，可重新选择渠道
    if (pollCount >= POLL_MAX_TIMES) {
      stopPolling()
      payState.value = 'failed'
    }
    // 订单已到期待关闭时，刷新订单状态
    if (remainMs.value <= 0) {
      stopPolling()
      await loadOrder()
    }
  } catch (e) {
    // 40941 等业务错误：订单已不是待支付，刷新页面状态
    if (e.code === 40941) {
      stopPolling()
      await loadOrder()
    }
  }
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (payState.value === 'polling') payState.value = 'idle'
}

async function handlePayFinished(status) {
  stopPolling()
  stopClock()
  payState.value = 'idle'
  await loadOrder()

  if (Number(status) === ORDER_STATUS.PAID || Number(status) >= ORDER_STATUS.PAID) {
    ElMessage.success('支付成功，订单已进入「已支付」')
  } else if (Number(status) === ORDER_STATUS.CANCELLED) {
    ElMessage.warning('订单已关闭，无法继续支付')
  }
}

function goOrderDetail() {
  router.replace(`/buyer/orders/${encodeURIComponent(orderNo)}`)
}

onMounted(init)
onBeforeUnmount(() => {
  stopClock()
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.pay-page {
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
  box-sizing: border-box;
}
.pay-card { background: #fff; padding: 32px 40px; min-height: 420px; }
.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

/* 头部 */
.pay-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.page-title { font-size: 20px; margin: 0 0 8px; font-weight: 500; }
.order-no { font-size: 13px; color: #999; }
.countdown { text-align: right; display: flex; flex-direction: column; gap: 6px; }
.cd-label { font-size: 12px; color: #999; }
.cd-value { font-size: 26px; color: #e60012; font-weight: bold; letter-spacing: 1px; }

/* 金额 */
.amount-row {
  display: flex; align-items: baseline; justify-content: center; gap: 14px;
  padding: 32px 0; margin: 20px 0; background: #fffafa; border: 1px solid #ffe4e6;
}
.amount-label { font-size: 14px; color: #666; }
.amount-value { font-size: 38px; color: #e60012; font-weight: bold; }

/* 渠道 */
.channel-block { margin-bottom: 8px; }
.block-title { font-size: 15px; margin-bottom: 14px; }
.channel-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.channel-item {
  position: relative; height: 76px; border: 1px solid #e5e5e5; background: #fafafa;
  display: flex; align-items: center; justify-content: center; gap: 10px;
  cursor: pointer; transition: all 0.2s; font-size: 15px;
}
.channel-item:hover { border-color: #f5a3a9; }
.channel-item.active { border-color: #e60012; background: #fff7f7; color: #e60012; font-weight: 500; }
.channel-item.disabled { cursor: not-allowed; opacity: 0.6; }
.channel-icon {
  width: 26px; height: 26px; border-radius: 50%; background: #e60012; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 14px;
}
.channel-item.active .channel-icon { background: #e60012; }
.channel-picked {
  position: absolute; right: 8px; top: 6px; color: #e60012; font-size: 15px; display: flex;
}
.channel-tip { font-size: 12px; color: #999; margin-top: 12px; }

/* 轮询 */
.polling-block {
  display: flex; align-items: center; gap: 16px; margin-top: 24px;
  padding: 18px 20px; background: #f7f9fc; border: 1px solid #e6eefb;
}
.polling-block .el-icon { font-size: 22px; color: #409eff; }
.polling-title { font-size: 15px; margin-bottom: 4px; }
.polling-sub { font-size: 12px; color: #999; }

.fail-alert { margin-top: 24px; }

/* 操作 */
.pay-actions {
  display: flex; align-items: center; justify-content: center; gap: 16px; margin-top: 32px;
}
.pay-btn {
  min-width: 260px; height: 48px; border: none; background: #e60012; color: #fff;
  font-size: 16px; cursor: pointer; transition: background 0.2s;
}
.pay-btn:hover:not(:disabled) { background: #c5000f; }
.pay-btn:disabled { background: #ccc; cursor: not-allowed; }
.foot-tip { text-align: center; font-size: 12px; color: #999; margin-top: 20px; }

/* 结果态 */
.result-card { display: flex; flex-direction: column; align-items: center; padding-top: 60px; }
.result-icon { font-size: 64px; }
.result-icon.success { color: #67c23a; }
.result-icon.danger { color: #909399; }
.result-title { font-size: 22px; margin: 18px 0 8px; font-weight: 500; }
.result-sub { font-size: 14px; color: #666; margin-bottom: 28px; }
.result-info {
  width: 520px; border-top: 1px solid #f0f0f0; border-bottom: 1px solid #f0f0f0;
  padding: 8px 0; margin-bottom: 28px;
}
.info-row {
  display: flex; justify-content: space-between; font-size: 14px;
  padding: 10px 4px; color: #666;
}
.info-row span:last-child { color: #333; }
.result-actions { display: flex; gap: 12px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
