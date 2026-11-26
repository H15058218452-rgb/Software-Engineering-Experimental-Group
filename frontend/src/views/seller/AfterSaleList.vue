<template>
  <div class="wrap">
    <div class="bar">
      <h3>售后管理</h3>
      <div class="bar-right">
        <el-select v-model="status" style="width: 150px; margin-right: 10px;" @change="handleFilter">
          <el-option label="全部状态" value="" />
          <el-option
            v-for="(text, code) in STATUS_TEXT"
            :key="code"
            :label="text"
            :value="code"
          />
        </el-select>
        <el-button type="primary" @click="load">刷新列表</el-button>
      </div>
    </div>

    <!-- 单卖家模式：这里就是全部售后单，不按店铺隔离、没有管理员视角 -->
    <el-table :data="list" border style="width: 100%" v-loading="loading">
      <el-table-column prop="afterSaleNo" label="售后单号" width="150" align="center" />

      <el-table-column label="客户" width="130" align="center">
        <template #default="{ row }">{{ customerText(row) }}</template>
      </el-table-column>

      <el-table-column prop="orderNo" label="订单号" width="150" align="center" />

      <el-table-column label="商品" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productName || row.productId || '-' }}</template>
      </el-table-column>

      <el-table-column prop="quantity" label="数量" width="70" align="center" />

      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTagType(row.status)">
            {{ row.statusText || STATUS_TEXT[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="提交时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <div class="actions">
            <el-button size="small" @click="openDetail(row)">详情</el-button>

            <!-- 待处理：同意退货 / 仅退款 / 拒绝；待收货：确认收到退货 -->
            <el-dropdown
              v-if="row.status === 'PENDING' || row.status === 'WAIT_RETURN'"
              trigger="click"
              @command="(cmd) => handleAction(cmd, row)"
            >
              <el-button size="small" type="primary">
                处理<el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <template v-if="row.status === 'PENDING'">
                    <el-dropdown-item command="agree-return">同意退货</el-dropdown-item>
                    <el-dropdown-item command="refund-only">仅退款</el-dropdown-item>
                    <el-dropdown-item command="reject">拒绝售后</el-dropdown-item>
                  </template>
                  <el-dropdown-item v-else command="receive">确认收到退货</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && list.length === 0" description="暂无售后单" />

    <div class="pager">
      <el-button :disabled="page <= 1" @click="page--; load()">上一页</el-button>
      <span style="margin: 0 10px;">{{ page }} / {{ totalPages }}</span>
      <el-button :disabled="page >= totalPages" @click="page++; load()">下一页</el-button>
    </div>

    <!-- 售后详情：订单与商品信息、问题描述、图片，底部按状态给出处理按钮 -->
    <el-dialog v-model="detailVisible" title="售后详情" width="640px">
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="售后单号">{{ detail.afterSaleNo }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusTagType(detail.status)">
                {{ detail.statusText || STATUS_TEXT[detail.status] || detail.status }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="关联订单">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="商品">{{ detail.productName || detail.productId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="数量">{{ detail.quantity }}</el-descriptions-item>
            <el-descriptions-item label="提交时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="block">
            <div class="block-title">问题标题</div>
            <div class="block-text">{{ detail.title || '-' }}</div>
          </div>

          <div class="block">
            <div class="block-title">问题描述</div>
            <div class="block-text pre">{{ detail.description || '-' }}</div>
          </div>

          <div class="block" v-if="(detail.images || []).length > 0">
            <div class="block-title">凭证图片</div>
            <div class="img-list">
              <el-image
                v-for="(img, i) in detail.images"
                :key="i"
                :src="img"
                :preview-src-list="detail.images"
                :initial-index="i"
                fit="cover"
                class="thumb"
              />
            </div>
          </div>

          <div class="block" v-if="detail.status === 'REFUNDED'">
            <div class="block-title">退款信息</div>
            <div class="block-text">
              ¥{{ detail.refundAmount || '-' }} · {{ channelText(detail.refundChannel) }} ·
              {{ formatTime(detail.refundedAt) }}
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <template v-if="detail && detail.status === 'PENDING'">
          <el-button type="success" @click="handleAction('agree-return', detail)">同意退货</el-button>
          <el-button type="primary" @click="handleAction('refund-only', detail)">仅退款</el-button>
          <el-button type="danger" @click="handleAction('reject', detail)">拒绝</el-button>
        </template>
        <el-button v-if="detail && detail.status === 'WAIT_RETURN'" type="primary" @click="handleAction('receive', detail)">
          确认收到退货
        </el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAfterSaleList, getAfterSaleDetail, operateAfterSale } from '@/api/seller'

const STATUS_TEXT = {
  PENDING: '待处理',
  WAIT_RETURN: '待收货',
  REFUNDED: '已退款',
  REJECTED: '已拒绝',
  CANCELLED: '已撤销'
}

const STATUS_TAG = {
  PENDING: 'warning',
  WAIT_RETURN: 'warning',
  REFUNDED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info'
}

const statusTagType = (s) => STATUS_TAG[s] || 'info'

const CHANNEL_TEXT = {
  BANK_CARD: '银行卡',
  CREDIT_CARD: '信用卡',
  ALIPAY: '支付宝',
  WECHAT: '微信'
}
const channelText = (c) => CHANNEL_TEXT[c] || c || '-'

const list = ref([])
const status = ref('')
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

const formatTime = (t) => (t ? String(t).replace('T', ' ').substring(0, 19) : '-')

// 后端客户字段命名未定，几种都可能，取到哪个用哪个
const customerText = (row) =>
  row.customerName || row.customerUsername || row.buyerName || row.customerId || '-'

async function load() {
  loading.value = true
  try {
    const res = await getAfterSaleList({ page: page.value, size, status: status.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '获取售后列表失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  page.value = 1
  load()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await getAfterSaleDetail(row.afterSaleNo)
    detail.value = res.data || null
  } catch (e) {
    ElMessage.error(e.message || '获取售后详情失败')
  } finally {
    detailLoading.value = false
  }
}

// 四个动作的前置状态与后果各不相同，文案按动作分开写
const ACTION_CONFIRM = {
  'agree-return': {
    title: '同意退货',
    text: '同意后售后单进入「待收货」，此时不退款、不回补库存，等收到退货后再点「确认收到退货」。'
  },
  'refund-only': {
    title: '仅退款',
    text: '将按原支付渠道退还该商品金额，不要求客户寄回商品，库存不回补。操作不可撤销。'
  },
  reject: {
    title: '拒绝售后',
    text: '拒绝后售后单结束，不退款、不回补库存。客户可就同一商品再次申请（同一商品最多 3 次）。'
  },
  receive: {
    title: '确认收到退货',
    text: '确认后按退货件数回补库存，并按原支付渠道退款。操作不可撤销。'
  }
}

async function handleAction(action, row) {
  const cfg = ACTION_CONFIRM[action]
  if (!cfg) return
  const afterSaleNo = row.afterSaleNo

  let note = ''
  if (action === 'receive') {
    try {
      const { value } = await ElMessageBox.prompt(cfg.text, cfg.title, {
        confirmButtonText: '确认收到并退款',
        cancelButtonText: '取消',
        inputPlaceholder: '退款备注（选填）',
        type: 'warning'
      })
      note = value || ''
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm(cfg.text, cfg.title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }
  }

  try {
    await operateAfterSale(afterSaleNo, action, { refundNote: note })
    ElMessage.success('操作成功')
    detailVisible.value = false
    load()
  } catch (e) {
    // 40966 状态不允许：多半是刚被别处改过，提示后重拉
    if (e && e.code === 40966) {
      ElMessage.warning(`${e.message || '售后单状态不允许该操作'}，已为您刷新列表`)
      detailVisible.value = false
      load()
      return
    }
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(load)
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; width: 100%; box-sizing: border-box; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.bar-right { display: flex; align-items: center; }
.actions { display: flex; gap: 5px; justify-content: center; align-items: center; }
.pager { margin-top: 16px; text-align: center; }
.block { margin-top: 16px; }
.block-title { font-size: 13px; color: #909399; margin-bottom: 6px; }
.block-text { font-size: 14px; color: #303133; line-height: 1.7; }
.block-text.pre { white-space: pre-wrap; }
.img-list { display: flex; flex-wrap: wrap; gap: 8px; }
.thumb { width: 80px; height: 80px; border-radius: 4px; border: 1px solid #ebeef5; }
</style>
