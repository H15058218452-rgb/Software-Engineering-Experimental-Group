<template>
  <div class="as-detail-page">
    <BuyerHeader />

    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <router-link to="/buyer/after-sales">我的售后</router-link>
      <span class="sep">/</span>
      <span>售后详情</span>
    </div>

    <main class="main">
      <div v-if="loading" class="card state">加载中...</div>

      <div v-else-if="!detail" class="card state empty">
        <el-empty description="售后单不存在" />
        <el-button type="danger" @click="router.push('/buyer/after-sales')">返回我的售后</el-button>
      </div>

      <template v-else>
        <!-- 状态条 -->
        <section class="card status-card">
          <div class="status-left">
            <el-tag :type="afterSaleStatusTagType(detail.status)" effect="dark" size="large">
              {{ afterSaleStatusText(detail.status, detail.statusText) }}
            </el-tag>
            <div class="status-text">
              <p class="status-title">{{ statusHint.title }}</p>
              <p class="status-sub">{{ statusHint.sub }}</p>
            </div>
          </div>
          <div class="status-actions">
            <!-- 仅「待处理」可撤销（C.3-07） -->
            <el-button v-if="isAfterSaleCancellable(detail.status)" plain @click="handleCancel">
              撤销售后
            </el-button>
            <el-button type="primary" link @click="goOrder">查看订单</el-button>
          </div>
        </section>

        <!-- 售后商品 -->
        <section class="card">
          <h3 class="card-title">售后商品</h3>
          <div class="goods-block">
            <div class="goods-main">
              <p class="goods-name">{{ detail.productName }}</p>
              <p class="goods-meta">订单号：{{ detail.orderNo }}</p>
            </div>
            <div class="goods-nums">
              <span>申请数量：{{ detail.quantity }}</span>
              <span v-if="detail.price">单价：{{ formatMoney(detail.price) }}</span>
            </div>
          </div>
        </section>

        <!-- 申请信息 -->
        <section class="card">
          <h3 class="card-title">申请信息</h3>
          <div class="info-grid">
            <div class="info-row"><span>售后单号</span><span>{{ detail.afterSaleNo }}</span></div>
            <div class="info-row"><span>提交时间</span><span>{{ formatTime(detail.createdAt) }}</span></div>
            <div class="info-row col-span"><span>售后标题</span><span>{{ detail.title }}</span></div>
            <div class="info-row col-span desc-row">
              <span>问题描述</span>
              <span class="desc-text">{{ detail.description }}</span>
            </div>
          </div>

          <div class="images-block">
            <span class="images-label">图片</span>
            <div v-if="(detail.images || []).length === 0" class="images-empty">未上传图片</div>
            <div v-else class="images-list">
              <el-image
                v-for="(img, idx) in detail.images"
                :key="idx"
                :src="img"
                :preview-src-list="detail.images"
                :initial-index="idx"
                fit="cover"
                class="as-image"
                preview-teleported
              />
            </div>
          </div>
        </section>

        <!-- 商家处理结果 -->
        <section class="card">
          <h3 class="card-title">处理结果</h3>
          <div class="info-grid">
            <div class="info-row">
              <span>售后类型</span>
              <span>{{ detail.type ? AFTER_SALE_TYPE_TEXT[detail.type] || detail.type : '未判定' }}</span>
            </div>
            <div class="info-row">
              <span>当前状态</span>
              <span>{{ afterSaleStatusText(detail.status, detail.statusText) }}</span>
            </div>
            <div class="info-row">
              <span>退款金额</span>
              <span>{{ detail.refundAmount ? formatMoney(detail.refundAmount) : '-' }}</span>
            </div>
            <div class="info-row">
              <span>退款渠道</span>
              <span>{{ detail.refundChannel ? channelText(detail.refundChannel) : '-' }}</span>
            </div>
            <div class="info-row">
              <span>退款时间</span>
              <span>{{ detail.refundedAt ? formatTime(detail.refundedAt) : '-' }}</span>
            </div>
          </div>

          <el-alert
            v-if="detail.status === AFTER_SALE_STATUS.REJECTED"
            type="info"
            :closable="false"
            show-icon
            class="result-alert"
            title="商家未通过本次售后申请"
            description="该售后单不产生退款、不回补库存；如问题仍未解决，可就此商品再次申请（计入 3 次上限）"
          />
          <el-alert
            v-else-if="detail.status === AFTER_SALE_STATUS.WAIT_RETURN"
            type="warning"
            :closable="false"
            show-icon
            class="result-alert"
            title="商家已同意退货，请将商品寄回"
            description="寄回后由商家在系统中确认收货，确认前不产生退款、不回补库存"
          />
          <el-alert
            v-else-if="detail.status === AFTER_SALE_STATUS.CANCELLED"
            type="info"
            :closable="false"
            show-icon
            class="result-alert"
            title="售后申请已撤销"
            description="撤销后不可恢复，不产生退款；如需售后可重新提交申请"
          />
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
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAfterSaleDetail, cancelAfterSale, PAY_CHANNELS } from '@/api/buyer'
import { formatMoney, formatTime } from '@/utils/format'
import {
  AFTER_SALE_STATUS,
  AFTER_SALE_TYPE_TEXT,
  afterSaleStatusText,
  afterSaleStatusTagType,
  isAfterSaleCancellable
} from '@/constants/afterSaleStatus'
import BuyerHeader from '@/components/BuyerHeader.vue'

const route = useRoute()
const router = useRouter()

const afterSaleNo = String(route.params.afterSaleNo || '')

const loading = ref(true)
const detail = ref(null)

const statusHint = computed(() => {
  const s = detail.value?.status
  const map = {
    [AFTER_SALE_STATUS.PENDING]: {
      title: '等待商家处理',
      sub: '商家处理前您可随时撤销售后申请'
    },
    [AFTER_SALE_STATUS.WAIT_RETURN]: {
      title: '商家已同意退货',
      sub: '请按商家要求寄回商品，商家确认收到退货后办理退款'
    },
    [AFTER_SALE_STATUS.REFUNDED]: {
      title: '售后已完成',
      sub: '退款已按原支付渠道退回，到账时间以渠道为准'
    },
    [AFTER_SALE_STATUS.REJECTED]: {
      title: '售后已被拒绝',
      sub: '可就此商品再次提交申请（同一商品累计不超过 3 次）'
    },
    [AFTER_SALE_STATUS.CANCELLED]: {
      title: '售后已撤销',
      sub: '撤销后不可恢复，如仍有问题可重新申请'
    }
  }
  return map[s] || { title: '售后单状态', sub: '' }
})

function channelText(code) {
  const hit = PAY_CHANNELS.find(c => c.value === code)
  return hit ? hit.label : code
}

function goOrder() {
  if (!detail.value?.orderNo) return
  router.push(`/buyer/orders/${encodeURIComponent(detail.value.orderNo)}`)
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await getAfterSaleDetail(afterSaleNo)
  } catch (e) {
    ElMessage.error(e.message || '获取售后单失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm(
      '撤销后售后单立即进入「已撤销」，不可恢复，且不产生退款。确定撤销吗？',
      '撤销售后申请',
      { confirmButtonText: '确定撤销', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    await cancelAfterSale(afterSaleNo)
    ElMessage.success('售后申请已撤销')
    await loadDetail()
  } catch (e) {
    if (e.code === 40966) {
      ElMessage.warning(`${e.message}，已为您刷新售后单状态`)
      await loadDetail()
    } else {
      ElMessage.error(e.message || '撤销失败，请重试')
    }
  }
}

onMounted(loadDetail)
</script>

<style scoped>
.as-detail-page {
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

.card-title { font-size: 16px; font-weight: 500; margin: 0 0 18px; }

/* 状态条 */
.status-card {
  display: flex; align-items: center; justify-content: space-between; gap: 24px;
  border-left: 4px solid #e60012;
}
.status-left { display: flex; align-items: center; gap: 18px; }
.status-title { font-size: 16px; font-weight: 500; margin-bottom: 6px; }
.status-sub { font-size: 13px; color: #999; }
.status-actions { display: flex; align-items: center; gap: 14px; }

/* 商品 */
.goods-block { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.goods-name { font-size: 15px; margin-bottom: 8px; }
.goods-meta { font-size: 13px; color: #999; }
.goods-nums { display: flex; align-items: center; gap: 20px; font-size: 13px; color: #666; flex: 0 0 auto; }

/* 信息表 */
.info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 2px 40px; }
.info-row {
  display: flex; justify-content: space-between; gap: 20px; font-size: 14px; color: #666;
  padding: 10px 0; border-bottom: 1px dashed #f2f2f2;
}
.info-row span:last-child { color: #333; }
.info-row.col-span { grid-column: 1 / -1; }
.desc-row { align-items: flex-start; }
.desc-text { text-align: right; max-width: 720px; line-height: 1.7; white-space: pre-wrap; }

/* 图片 */
.images-block { display: flex; gap: 16px; margin-top: 18px; }
.images-label { font-size: 14px; color: #666; flex: 0 0 70px; }
.images-empty { font-size: 14px; color: #999; }
.images-list { display: flex; flex-wrap: wrap; gap: 12px; }
.as-image { width: 96px; height: 96px; border: 1px solid #f0f0f0; }

.result-alert { margin-top: 18px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
