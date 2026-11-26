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

        <el-table-column label="商品" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ summaryText(row) }}</template>
        </el-table-column>

        <el-table-column label="总金额" width="110" align="right">
          <template #default="{ row }">
            <span class="amount">¥{{ row.totalAmount }}</span>
          </template>
        </el-table-column>

        <el-table-column label="当前状态" width="120" align="center">
          <template #default="{ row }">
            <!-- 口径以《订单状态表》为准：优先用本地码值映射，
                 后端 statusText 仍是「商家已确认」「已发货」，与 C 的口径不符 -->
            <el-tag size="small" :type="orderStatusTagType(row.status)">
              {{ ORDER_STATUS_TEXT[row.status] || row.statusText || '-' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="最终结果" width="100" align="center">
          <template #default="{ row }">{{ finalResultText(row.finalResult) }}</template>
        </el-table-column>

        <el-table-column label="操作" width="280" align="center">
          <template #default="{ row }">
            <div class="actions">
              <!-- 推进按钮按状态显示：确认仅 18（已支付）、备货仅 20、发货仅 30 -->
              <!-- 10 是迭代 3 的遗留码值，后端升级到 C 后下单即进 15，这条兼容可以删 -->
              <el-button
                v-if="canConfirm(row.status)"
                size="small"
                type="success"
                @click="handleAdvance(row, 'confirm')"
              >确认订单</el-button>
              <el-button
                v-else-if="canReady(row.status)"
                size="small"
                type="success"
                @click="handleAdvance(row, 'ready')"
              >备货完成</el-button>
              <el-button
                v-else-if="canShip(row.status)"
                size="small"
                type="success"
                @click="openShipDialog(row)"
              >发货</el-button>

              <!-- 40 / 50 可查物流；40 还能改单号（客户未签收前） -->
              <el-button
                v-if="canViewLogistics(row.status)"
                size="small"
                @click="openLogistics(row)"
              >物流</el-button>

              <!-- 商家可取消：15 / 18 / 20 / 30 / 40 显示，50 及之后隐藏 -->
              <el-button
                v-if="isSellerCancellable(row.status)"
                size="small"
                type="danger"
                @click="handleCancel(row)"
              >取消订单</el-button>

              <!-- 交易完成只能由客户点「确认收货」触发，商家侧只显示结果 -->
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

    <!-- 发货弹窗：物流单号与快递公司必填（C.2-03） -->
    <el-dialog v-model="shipDialog.visible" title="发货" width="440px">
      <el-form label-width="90px">
        <el-form-item label="快递公司" required>
          <el-select
            v-model="shipDialog.carrier"
            filterable
            allow-create
            default-first-option
            placeholder="选择或直接输入快递公司"
            style="width: 100%"
          >
            <el-option v-for="c in CARRIERS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model.trim="shipDialog.logisticsNo" maxlength="40" placeholder="请输入物流单号" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">发货后订单进入「开始发货」，物流信息与订单状态同时生效，一个订单只有一条物流记录。</div>
      <template #footer>
        <el-button @click="shipDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="shipDialog.loading" @click="confirmShip">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 物流弹窗：只读展示，40 状态可改单号 -->
    <el-dialog v-model="logisticsDialog.visible" title="物流信息" width="480px">
      <div v-loading="logisticsDialog.loading">
        <template v-if="logisticsDialog.data">
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="订单号">{{ currentOrderNo }}</el-descriptions-item>
            <el-descriptions-item label="快递公司">{{ logisticsDialog.data.carrier || '-' }}</el-descriptions-item>
            <el-descriptions-item label="物流单号">{{ logisticsDialog.data.logisticsNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="物流状态">{{ logisticsDialog.data.statusText || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="trace-title">物流轨迹</div>
          <el-timeline v-if="(logisticsDialog.data.traces || []).length > 0">
            <el-timeline-item
              v-for="(t, i) in logisticsDialog.data.traces"
              :key="i"
              :timestamp="formatTime(t.time)"
              placement="top"
            >{{ t.desc }}</el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无轨迹信息" :image-size="60" />
        </template>
        <el-empty
          v-else-if="!logisticsDialog.loading"
          :description="logisticsDialog.error || '暂无物流信息'"
          :image-size="60"
        />
      </div>
      <template #footer>
        <el-button v-if="logisticsDialog.canEdit" @click="openEditLogistics">修改单号</el-button>
        <el-button @click="logisticsDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 修改物流单号：仅 40 且未签收可改 -->
    <el-dialog v-model="editLogisticsDialog.visible" title="修改物流单号" width="440px">
      <el-form label-width="90px">
        <el-form-item label="快递公司" required>
          <el-select
            v-model="editLogisticsDialog.carrier"
            filterable
            allow-create
            default-first-option
            placeholder="选择或直接输入快递公司"
            style="width: 100%"
          >
            <el-option v-for="c in CARRIERS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="物流单号" required>
          <el-input v-model.trim="editLogisticsDialog.logisticsNo" maxlength="40" placeholder="请输入物流单号" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">改完后客户查询到的是新单号；客户已签收则无法修改。</div>
      <template #footer>
        <el-button @click="editLogisticsDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="editLogisticsDialog.loading" @click="confirmEditLogistics">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getCustomerList,
  getCustomerOrderList,
  advanceOrder,
  cancelOrder,
  getOrderLogistics,
  updateOrderLogistics
} from '@/api/seller'
import {
  ORDER_STATUS_TEXT,
  ORDER_TERMINAL_STATUS,
  orderStatusTagType,
  finalResultText,
  isPaidStatus
} from '@/constants/orderStatus'

const route = useRoute()
const router = useRouter()

const CARRIERS = ['顺丰速运', '京东物流', '中通快递', '圆通速递', '韵达快递', '申通快递', 'EMS']

const customers = ref([])
const customerId = ref('')
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const currentOrderNo = ref('')

const shipDialog = reactive({ visible: false, logisticsNo: '', carrier: '顺丰速运', loading: false })
const logisticsDialog = reactive({ visible: false, loading: false, data: null, error: '', canEdit: false })
const editLogisticsDialog = reactive({ visible: false, logisticsNo: '', carrier: '', loading: false })

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
  return String(timeStr).replace('T', ' ').substring(0, 19)
}

// 商品摘要：首件商品名 + 总件数，明细在展开行里看
const summaryText = (row) => {
  const items = row.items || []
  if (items.length === 0) return `${row.itemCount ?? 0} 件商品`
  const first = items[0]?.name || '商品'
  return (row.itemCount ?? items.length) > 1 ? `${first} 等 ${row.itemCount} 件商品` : first
}

/* ---------------- 状态与按钮条件（C.1-05 / C.1-06） ---------------- */

// 18 已支付才可确认；10 为迭代 3 遗留码值，兼容保留
const canConfirm = (status) => [18, 10].includes(Number(status))
const canReady = (status) => Number(status) === 20
const canShip = (status) => Number(status) === 30
// 物流：40 开始发货、50 交易完成都能查
const canViewLogistics = (status) => [40, 50].includes(Number(status))
// 商家可取消：50 之前，含 15 / 18 / 20 / 30 / 40（10 为遗留码值）
const isSellerCancellable = (status) => [10, 15, 18, 20, 30, 40].includes(Number(status))

// 取消后果按状态区分，写进二次确认里，避免误点
const cancelTip = (status) => {
  const s = Number(status)
  if (s === 15) return '该订单尚未支付，取消后不产生退款，占用的库存会立即回补。'
  if (s === 40) return '该订单已发货，取消后将按原支付渠道退还货款，但库存不回补（商品已发出）。'
  return '该订单已支付，取消后将按原支付渠道退款，并回补库存。'
}

/* ---------------- 数据加载 ---------------- */

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

/* ---------------- 统一错误处理 ---------------- */

// 40921 状态不符 / 40923 已终态：说明这单刚被别人改过，提示后直接重拉，不停在旧数据上
const handleBizError = (e) => {
  if (!e || e === 'cancel' || e === 'close') return
  if (e.code === 40921 || e.code === 40923) {
    ElMessage.warning(`${e.message || '当前状态不允许该操作'}，已为您刷新订单状态`)
    loadOrders()
    return
  }
  ElMessage.error(e.message || '操作失败')
}

/* ---------------- 状态推进 ---------------- */

async function handleAdvance(row, action) {
  const label = { confirm: '商家确认', ready: '备货完成' }[action]
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
    handleBizError(e)
  }
}

/* ---------------- 发货 / 物流 ---------------- */

const openShipDialog = (row) => {
  currentOrderNo.value = row.orderNo
  shipDialog.logisticsNo = ''
  shipDialog.carrier = CARRIERS[0]
  shipDialog.loading = false
  shipDialog.visible = true
}

const confirmShip = async () => {
  if (!shipDialog.carrier) { ElMessage.warning('请选择快递公司'); return }
  if (!shipDialog.logisticsNo) { ElMessage.warning('请填写物流单号'); return }
  shipDialog.loading = true
  try {
    await advanceOrder(currentOrderNo.value, 'ship', {
      carrier: shipDialog.carrier,
      logisticsNo: shipDialog.logisticsNo
    })
    ElMessage.success('发货成功')
    shipDialog.visible = false
    loadOrders()
  } catch (e) {
    handleBizError(e)
  } finally {
    shipDialog.loading = false
  }
}

const openLogistics = async (row) => {
  currentOrderNo.value = row.orderNo
  logisticsDialog.visible = true
  logisticsDialog.loading = true
  logisticsDialog.data = null
  logisticsDialog.error = ''
  logisticsDialog.canEdit = Number(row.status) === 40
  try {
    const res = await getOrderLogistics(row.orderNo)
    logisticsDialog.data = res.data || null
  } catch (e) {
    logisticsDialog.error = e.message || '暂无物流信息'
  } finally {
    logisticsDialog.loading = false
  }
}

const openEditLogistics = () => {
  editLogisticsDialog.carrier = logisticsDialog.data?.carrier || ''
  editLogisticsDialog.logisticsNo = logisticsDialog.data?.logisticsNo || ''
  editLogisticsDialog.loading = false
  editLogisticsDialog.visible = true
}

const confirmEditLogistics = async () => {
  if (!editLogisticsDialog.carrier) { ElMessage.warning('请选择快递公司'); return }
  if (!editLogisticsDialog.logisticsNo) { ElMessage.warning('请填写物流单号'); return }
  editLogisticsDialog.loading = true
  try {
    await updateOrderLogistics(currentOrderNo.value, {
      carrier: editLogisticsDialog.carrier,
      logisticsNo: editLogisticsDialog.logisticsNo
    })
    ElMessage.success('物流信息已更新')
    // 同步弹窗里的展示，避免再查一次
    if (logisticsDialog.data) {
      logisticsDialog.data.carrier = editLogisticsDialog.carrier
      logisticsDialog.data.logisticsNo = editLogisticsDialog.logisticsNo
    }
    editLogisticsDialog.visible = false
  } catch (e) {
    if (e && e.code === 40921) {
      ElMessage.warning(`${e.message || '当前状态不允许修改'}，已为您刷新订单状态`)
      logisticsDialog.visible = false
      loadOrders()
      return
    }
    ElMessage.error(e?.message || '修改失败')
  } finally {
    editLogisticsDialog.loading = false
  }
}

/* ---------------- 取消订单 ---------------- */

// reason 选填（≤100 字）；取消不可撤销，二次确认里写清退款与库存的后果
async function handleCancel(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `${cancelTip(row.status)}取消后不可撤销。`,
      `取消订单 ${row.orderNo}`,
      {
        confirmButtonText: '确定取消',
        cancelButtonText: '再想想',
        inputPlaceholder: '取消原因（选填，最多 100 字）',
        inputValidator: (v) => (v && String(v).length > 100 ? '最多 100 字' : true),
        type: 'warning'
      }
    )
    reason = value || ''
  } catch {
    return
  }
  try {
    await cancelOrder(row.orderNo, reason)
    ElMessage.success(isPaidStatus(row.status) ? '订单已取消，款项将原路退回' : '订单已取消，库存已回补')
    loadOrders()
  } catch (e) {
    handleBizError(e)
  }
}

/* ---------------- 初始化与地址栏联动 ---------------- */

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
.dialog-tip { font-size: 12px; color: #909399; line-height: 1.6; }
.trace-title { font-size: 13px; color: #606266; margin: 14px 0 10px; }
</style>
