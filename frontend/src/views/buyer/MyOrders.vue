<template>
  <div class="orders-page">
    <!-- 主头部（公共组件） -->
    <BuyerHeader />

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>我的订单</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div class="orders-card">
        <div class="title-row">
          <h2 class="page-title">我的订单</h2>
          <span class="page-tip">按下单时间倒序，含已取消订单</span>
        </div>

        <div v-if="loading" class="state">加载中...</div>

        <div v-else-if="orderList.length === 0" class="state empty">
          <el-empty description="您还没有任何订单" />
          <el-button type="danger" @click="router.push('/buyer/goods')">去逛逛</el-button>
        </div>

        <template v-else>
          <el-table :data="orderList" style="width: 100%" stripe>
            <!-- 展开行即明细：不做独立订单详情页路由 -->
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="detail-wrap">
                  <div class="detail-head">订单 {{ row.orderNo }} 商品明细</div>
                  <el-table :data="row.items || []" size="small" border style="width: 100%">
                    <el-table-column prop="name" label="商品名称" min-width="220" />
                    <el-table-column prop="price" label="单价" width="120" align="right">
                      <template #default="{ row: item }">{{ formatMoney(item.price) }}</template>
                    </el-table-column>
                    <el-table-column prop="quantity" label="数量" width="90" align="center" />
                    <el-table-column prop="subtotal" label="小计" width="120" align="right">
                      <template #default="{ row: item }">{{ formatMoney(item.subtotal) }}</template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
            </el-table-column>

            <el-table-column prop="orderNo" label="订单号" width="170" />

            <el-table-column label="下单时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.createdAt || row.createTime) }}
              </template>
            </el-table-column>

            <el-table-column label="商品" min-width="200">
              <template #default="{ row }">
                <span class="goods-summary">{{ summaryText(row) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="总金额" width="130" align="right">
              <template #default="{ row }">
                <span class="amount">{{ formatMoney(row.totalAmount) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="当前状态" width="120" align="center">
              <template #default="{ row }">
                <!-- statusText 由后端下发，直接展示，不自己映射码值 -->
                <el-tag :type="orderStatusTagType(row.status)">
                  {{ row.statusText || ORDER_STATUS_TEXT[row.status] || '-' }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="最终结果" width="110" align="center">
              <template #default="{ row }">
                {{ finalResultText(row.finalResult) }}
              </template>
            </el-table-column>

            <el-table-column label="操作" width="150" align="center">
              <template #default="{ row }">
                <div class="op-cell">
                  <!-- 待支付：去支付（15） -->
                  <el-button
                    v-if="isAwaitingPayment(row.status)"
                    link
                    type="danger"
                    @click="goPay(row)"
                  >
                    去支付
                  </el-button>

                  <!-- 确认收货：只有「开始发货」40 展示（C.1-07） -->
                  <el-button
                    v-if="canReceiveOrder(row.status)"
                    link
                    type="danger"
                    @click="handleReceive(row)"
                  >
                    确认收货
                  </el-button>

                  <!-- 取消：15 / 18 / 20 / 30 显示；40 及之后隐藏（C.1-04） -->
                  <el-button
                    v-if="isCustomerCancellable(row.status)"
                    link
                    type="danger"
                    @click="handleCancel(row)"
                  >
                    取消订单
                  </el-button>

                  <el-button link type="primary" @click="goDetail(row)">订单详情</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页器 -->
          <div class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="total"
              :page-size="queryParams.size"
              :current-page="queryParams.page"
              @current-change="handlePageChange"
            />
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
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyOrderList, cancelOrder, receiveOrder } from '@/api/buyer'
import { formatMoney, formatTime } from '@/utils/format'
import {
  ORDER_STATUS_TEXT,
  isCustomerCancellable,
  isAwaitingPayment,
  canReceiveOrder,
  isPaidStatus,
  orderStatusTagType,
  finalResultText
} from '@/constants/orderStatus'
import BuyerHeader from '@/components/BuyerHeader.vue'

const router = useRouter()

const loading = ref(false)
const orderList = ref([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  size: 10 // 列表每页 10 条
})

// 商品摘要：首件商品名 + 其余件数，明细在展开行里看
function summaryText(row) {
  const items = row.items || []
  if (items.length === 0) return `${row.itemCount ?? 0} 件商品`
  const first = items[0]?.name || '商品'
  const count = row.itemCount ?? items.length
  return count > 1 ? `${first} 等 ${count} 件商品` : first
}

async function loadOrders() {
  loading.value = true
  try {
    const res = await getMyOrderList(queryParams.value)
    orderList.value = res?.list || []
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '获取订单列表失败')
    orderList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 取消订单（C.1-04）：15 / 18 / 20 / 30 可取消；已支付需原路退款，取消原因选填
async function handleCancel(row) {
  const paid = isPaidStatus(row.status)
  const tip = paid
    ? `该订单已支付，取消后款项将按原支付渠道原路退回，库存同时回补。取消后订单进入终态，不可恢复。`
    : `该订单尚未支付，取消后立即进入「已取消」终态，占用的库存将被释放，不可恢复。`

  let reason = ''
  try {
    const res = await ElMessageBox.prompt(tip, `取消订单 ${row.orderNo}`, {
      confirmButtonText: '确定取消',
      cancelButtonText: '再想想',
      inputPlaceholder: '取消原因（选填，最多 100 字）',
      inputType: 'textarea',
      inputValidator: v => String(v || '').length <= 100 || '取消原因最多 100 字',
      type: 'warning'
    })
    reason = res.value || ''
  } catch (e) {
    return // 用户放弃
  }

  try {
    const res = await cancelOrder(row.orderNo, reason)
    if (res?.refundAmount) {
      ElMessage.success(`订单已取消，${formatMoney(res.refundAmount)} 已按原渠道退回`)
    } else {
      ElMessage.success('订单已取消，库存已释放')
    }
    await loadOrders()
  } catch (e) {
    // 40921 时机不符 / 40923 已终态：状态已被变更，提示后刷新列表
    if (e.code === 40921 || e.code === 40923) {
      ElMessage.warning(`${e.message}，已为您刷新订单状态`)
      await loadOrders()
    } else {
      ElMessage.error(e.message || '取消失败，请重试')
    }
  }
}

// 确认收货（C.1-07）：40 → 50 交易完成
async function handleReceive(row) {
  try {
    await ElMessageBox.confirm(
      `确认已经收到订单 ${row.orderNo} 的商品吗？确认后订单进入「交易完成」终态，不可撤销。`,
      '确认收货',
      { confirmButtonText: '确认收货', cancelButtonText: '还没收到', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    await receiveOrder(row.orderNo)
    ElMessage.success('已确认收货，交易完成')
    await loadOrders()
  } catch (e) {
    if (e.code === 40921 || e.code === 40923) {
      ElMessage.warning(`${e.message}，已为您刷新订单状态`)
      await loadOrders()
    } else {
      ElMessage.error(e.message || '确认收货失败，请重试')
    }
  }
}

// 去支付（15 待支付 → 支付页）
function goPay(row) {
  router.push(`/buyer/pay/${encodeURIComponent(row.orderNo)}`)
}

// 订单详情：地址快照 / 物流 / 售后入口都在详情页
function goDetail(row) {
  router.push(`/buyer/orders/${encodeURIComponent(row.orderNo)}`)
}

function handlePageChange(page) {
  queryParams.value.page = page
  loadOrders()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.orders-page {
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
.orders-card {
  background: #fff; padding: 30px 40px; min-height: 500px;
}
.title-row { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 20px; margin: 0; font-weight: 500; }
.page-tip { font-size: 12px; color: #999; }

.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

.goods-summary { color: #333; }
.amount { color: #e60012; font-weight: 500; }

/* 操作列：按钮纵向排列，避免挤压 */
.op-cell { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.op-cell :deep(.el-button) { margin-left: 0; height: 22px; }

/* 展开行：订单明细 */
.detail-wrap { padding: 16px 20px 20px; background: #fafafa; }
.detail-head { font-size: 13px; color: #666; margin-bottom: 12px; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 30px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
