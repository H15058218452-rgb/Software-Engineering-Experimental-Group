<template>
  <div class="wrap">
    <div class="bar">
      <h3>客户订单</h3>
      <div class="bar-right">
        <el-select
          v-model="customerId"
          filterable
          placeholder="选择客户查看其订单"
          style="width: 200px; margin-right: 10px;"
          @change="handleCustomerChange"
        >
          <el-option
            v-for="c in customers"
            :key="c.id"
            :label="customerLabel(c)"
            :value="String(c.id)"
          />
        </el-select>
        <el-button type="primary" :disabled="!customerId" @click="loadOrders">刷新列表</el-button>
        <el-button @click="$router.push('/seller/customers')">去客户管理</el-button>
      </div>
    </div>

    <!-- B.1-4 裁定：商家不设全局订单列表、不按状态筛选，只能从客户进入看该客户的订单 -->
    <el-alert
      v-if="!customerId"
      type="info"
      :closable="false"
      show-icon
      title="请先在「客户管理」点某位客户的「查看订单」进入"
      description="卖家订单按客户查看：选中客户后展示其全部订单（含已取消），并可在此处推进状态或取消订单。"
    />

    <template v-else>
      <div class="customer-line">
        <span class="customer-name">{{ currentCustomerLabel }}</span>
        <span class="customer-tip">共 {{ total }} 笔订单，按下单时间倒序</span>
      </div>

      <el-table :data="list" style="width: 100%" border v-loading="loading">
        <!-- 展开行即明细：需求不做独立订单详情页路由 -->
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail-wrap">
              <div class="detail-head">订单 {{ row.orderNo }} 商品明细</div>
              <el-table :data="row.items || []" size="small" border style="width: 100%">
                <el-table-column prop="name" label="商品名称" min-width="200" />
                <el-table-column label="单价" width="110" align="right">
                  <template #default="{ row: item }">¥{{ item.price }}</template>
                </el-table-column>
                <el-table-column prop="quantity" label="数量" width="80" align="center" />
                <el-table-column label="小计" width="110" align="right">
                  <template #default="{ row: item }">¥{{ item.subtotal }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="orderNo" label="订单号" width="160" align="center" />

        <el-table-column label="下单时间" width="170" align="center">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column label="商品" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ summaryText(row) }}</template>
        </el-table-column>

        <el-table-column label="总金额" width="110" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ row.totalAmount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="当前状态" width="120" align="center">
          <template #default="{ row }">
            <!-- 口径以《订单状态流转》为准：优先用本地码值映射，
                 后端 statusText 目前是「商家已确认」「已发货」，与口径不符 -->
            <el-tag size="small" :type="orderStatusTagType(row.status)">
              {{ ORDER_STATUS_TEXT[row.status] || row.statusText || '-' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="最终结果" width="110" align="center">
          <template #default="{ row }">{{ finalResultText(row.finalResult) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="260" align="center">
          <template #default="{ row }">
            <div class="actions">
              <!-- 推进按钮按状态显示：confirm 仅 10、ready 仅 20、ship 仅 30、complete 仅 40 -->
              <el-button
                v-if="row.status === ORDER_STATUS.PENDING"
                size="small"
                type="success"
                @click="handleAdvance(row, 'confirm')"
              >商家确认</el-button>
              <el-button
                v-else-if="row.status === ORDER_STATUS.CONFIRMED"
                size="small"
                type="success"
                @click="handleAdvance(row, 'ready')"
              >备货完成</el-button>
              <el-button
                v-else-if="row.status === ORDER_STATUS.READY"
                size="small"
                type="success"
                @click="handleAdvance(row, 'ship')"
              >开始发货</el-button>
              <el-button
                v-else-if="row.status === ORDER_STATUS.SHIPPED"
                size="small"
                type="success"
                @click="handleAdvance(row, 'complete')"
              >交易完成</el-button>

              <!-- 商家可在「交易完成」前取消：10/20/30/40 显示，50/90 隐藏 -->
              <el-button
                v-if="isSellerCancellable(row.status)"
                size="small"
                type="danger"
                @click="handleCancel(row)"
              >取消订单</el-button>

              <span v-if="ORDER_TERMINAL_STATUS.includes(Number(row.status))" class="op-none">已结束</span>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && list.length === 0" description="该客户暂无订单记录" />

      <div class="pager">
        <el-button :disabled="page <= 1" @click="page--; loadOrders()">上一页</el-button>
        <span style="margin: 0 10px;">{{ page }} / {{ totalPages }}</span>
        <el-button :disabled="page >= totalPages" @click="page++; loadOrders()">下一页</el-button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomerList, getCustomerOrderList, advanceOrder, cancelOrder } from '@/api/seller'
import {
  ORDER_STATUS,
  ORDER_STATUS_TEXT,
  ORDER_TERMINAL_STATUS,
  orderStatusTagType,
  finalResultText
} from '@/constants/orderStatus'

const route = useRoute()
const router = useRouter()

const customers = ref([])
const customerId = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const customerLabel = (c) => {
  const name = c.username || c.nickname || `客户 ${c.id}`
  return c.phone ? `${name}（${c.phone}）` : name
}

const currentCustomerLabel = computed(() => {
  const hit = customers.value.find(c => String(c.id) === String(customerId.value))
  return hit ? customerLabel(hit) : `客户 ${customerId.value}`
})

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return String(timeStr).substring(0, 19).replace('T', ' ')
}

// 商品摘要：首件商品名 + 总件数，明细在展开行里看
const summaryText = (row) => {
  const items = row.items || []
  if (items.length === 0) return `${row.itemCount ?? 0} 件商品`
  const first = items[0]?.name || '商品'
  return (row.itemCount ?? items.length) > 1 ? `${first} 等 ${row.itemCount} 件商品` : first
}

// 商家可取消：交易完成（50）之前，即 10/20/30/40
const isSellerCancellable = (status) =>
  [ORDER_STATUS.PENDING, ORDER_STATUS.CONFIRMED, ORDER_STATUS.READY, ORDER_STATUS.SHIPPED]
    .includes(Number(status))

async function loadCustomers() {
  try {
    const res = await getCustomerList({ page: 1, pageSize: 50 })
    if (res.code === 0) customers.value = res.data.list || []
  } catch (e) {
    ElMessage.error(e.message || '获取客户列表失败')
  }
}

async function loadOrders() {
  if (!customerId.value) {
    list.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const res = await getCustomerOrderList(customerId.value, { page: page.value, size })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '获取订单列表失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleCustomerChange = (val) => {
  page.value = 1
  // 同步到地址栏，便于刷新后仍停在同一个客户
  router.replace({ path: '/seller/orders', query: val ? { customerId: val } : {} })
  loadOrders()
}

// 商家推进状态：必须按 10→20→30→40→50 顺序，重复提交/跳态后端统一 40921
async function handleAdvance(row, action) {
  const label = { confirm: '商家确认', ready: '备货完成', ship: '开始发货', complete: '交易完成' }[action]
  try {
    await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 推进到「${label}」吗？`, label, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await advanceOrder(row.orderNo, action)
    ElMessage.success('操作成功')
    loadOrders()
  } catch (e) {
    // 40921 状态不符 / 40923 已终态：状态已被别人变更，提示后刷新
    if (e.code === 40921 || e.code === 40923) {
      ElMessage.warning(`${e.message}，已为您刷新订单状态`)
      loadOrders()
    } else {
      ElMessage.error(e.message || '操作失败')
    }
  }
}

// 商家取消：即时生效、无对方确认；取消后库存自动回补
async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消订单 ${row.orderNo} 吗？取消后立即进入「已取消」终态，库存自动回补，且不可撤销。`,
      '取消订单',
      { confirmButtonText: '确定取消', cancelButtonText: '再想想', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await cancelOrder(row.orderNo)
    ElMessage.success('订单已取消，库存已回补')
    loadOrders()
  } catch (e) {
    if (e.code === 40921 || e.code === 40923) {
      ElMessage.warning(`${e.message}，已为您刷新订单状态`)
      loadOrders()
    } else {
      ElMessage.error(e.message || '取消失败，请重试')
    }
  }
}

watch(
  () => route.query.customerId,
  (val) => {
    const next = val == null ? '' : String(val)
    if (next === customerId.value) return
    customerId.value = next
    page.value = 1
    loadOrders()
  }
)

onMounted(async () => {
  customerId.value = route.query.customerId ? String(route.query.customerId) : ''
  await loadCustomers()
  if (customerId.value) loadOrders()
})
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; width: 100%; box-sizing: border-box; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.bar-right { display: flex; align-items: center; }
.customer-line { display: flex; align-items: baseline; gap: 12px; margin-bottom: 12px; }
.customer-name { font-size: 15px; font-weight: 600; color: #303133; }
.customer-tip { font-size: 12px; color: #909399; }
.amount { color: #c7000b; font-weight: 500; }
.op-none { color: #c0c4cc; font-size: 13px; }
.actions { display: flex; gap: 5px; flex-wrap: nowrap; align-items: center; justify-content: center; }
.actions :deep(.el-button) { padding: 5px 8px; }
.detail-wrap { padding: 12px 16px 16px; background: #fafafa; }
.detail-head { font-size: 13px; color: #606266; margin-bottom: 10px; }
.pager { margin-top: 16px; text-align: center; }
</style>
