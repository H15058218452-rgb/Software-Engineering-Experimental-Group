<template>
  <div class="wrap">
    <div class="bar">
      <h3>订单管理</h3>
      <div>
        <el-select v-model="status" placeholder="全部状态" clearable style="width: 140px; margin-right: 10px;" @change="loadData">
          <el-option label="客户下单" :value="0" />
          <el-option label="商家确认" :value="1" />
          <el-option label="备货完成" :value="2" />
          <el-option label="开始发货" :value="3" />
          <el-option label="交易完成" :value="4" />
          <el-option label="已取消" :value="5" />
        </el-select>
        <el-button type="primary" @click="loadData">刷新列表</el-button>
      </div>
    </div>

    <el-table :data="list" style="width: 100%" border v-loading="loading">
      <el-table-column prop="orderNo" label="订单号" width="180" align="center">
        <template #default="{ row }">{{ row.orderNo || row.id }}</template>
      </el-table-column>

      <el-table-column label="客户" min-width="120" align="center">
        <template #default="{ row }">{{ row.buyerName || row.username || '-' }}</template>
      </el-table-column>

      <el-table-column label="商品名称" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.productName || '-' }}</template>
      </el-table-column>

      <el-table-column label="金额" width="100" align="center">
        <template #default="{ row }">¥{{ row.amount ?? row.price ?? '-' }}</template>
      </el-table-column>

      <el-table-column label="下单时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.createdAt || row.createTime) }}</template>
      </el-table-column>

      <el-table-column label="订单状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="220" align="center">
        <template #default="{ row }">
          <div class="actions">
            <el-button v-if="row.status === 0" size="small" type="success" @click="advance(row, 'confirm')">商家确认</el-button>
            <el-button v-if="row.status === 1" size="small" type="success" @click="advance(row, 'stocked')">备货完成</el-button>
            <el-button v-if="row.status === 2" size="small" type="success" @click="advance(row, 'ship')">开始发货</el-button>
            <el-button v-if="row.status === 3" size="small" type="success" @click="advance(row, 'complete')">交易完成</el-button>
            <!-- 交易完成(4)前商家可取消订单 -->
            <el-button v-if="row.status != null && row.status >= 0 && row.status <= 3" size="small" type="danger" @click="cancel(row)">取消订单</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-button :disabled="page <= 1" @click="page--; loadData()">上一页</el-button>
      <span style="margin: 0 10px;">{{ page }} / {{ totalPages }}</span>
      <el-button :disabled="page >= totalPages" @click="page++; loadData()">下一页</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// ===== 模拟数据（后端接口就位后，替换为真实 API 调用）=====
const MOCK_ORDERS = [
  { id: 1, orderNo: '202611151230450001', buyerName: '张三', productName: '无线蓝牙耳机', amount: '199.00', status: 0, createdAt: '2026-11-15 12:30:45' },
  { id: 2, orderNo: '202611151235120002', buyerName: '李四', productName: '机械键盘', amount: '299.00', status: 1, createdAt: '2026-11-15 12:35:12' },
  { id: 3, orderNo: '202611151240330003', buyerName: '王五', productName: '保温杯', amount: '59.90', status: 2, createdAt: '2026-11-15 12:40:33' },
  { id: 4, orderNo: '202611151245080004', buyerName: '赵六', productName: '运动鞋', amount: '399.00', status: 3, createdAt: '2026-11-15 12:45:08' },
  { id: 5, orderNo: '202611151250210005', buyerName: '孙七', productName: '蓝牙音箱', amount: '159.00', status: 4, createdAt: '2026-11-15 12:50:21' },
  { id: 6, orderNo: '202611151255440006', buyerName: '周八', productName: '数据线', amount: '29.90', status: 5, createdAt: '2026-11-15 12:55:44' }
]

const list = ref([])
const status = ref('')
const page = ref(1)
const size = 10
const totalPages = ref(1)
const loading = ref(false)

// 订单状态：0 客户下单 / 1 商家确认 / 2 备货完成 / 3 开始发货 / 4 交易完成 / 5 已取消
const statusMap = {
  0: { text: '客户下单', type: 'warning' },
  1: { text: '商家确认', type: 'primary' },
  2: { text: '备货完成', type: 'primary' },
  3: { text: '开始发货', type: 'primary' },
  4: { text: '交易完成', type: 'success' },
  5: { text: '已取消', type: 'info' }
}
const getStatusText = (s) => statusMap[s]?.text || '未知'
const getStatusType = (s) => statusMap[s]?.type || 'info'

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return String(timeStr).substring(0, 19).replace('T', ' ')
}

const loadData = () => {
  loading.value = true
  try {
    // 模拟：按状态筛选 + 前端分页
    let data = [...MOCK_ORDERS]
    if (status.value !== '' && status.value != null) {
      data = data.filter(it => it.status === Number(status.value))
    }
    totalPages.value = Math.max(1, Math.ceil(data.length / size))
    const start = (page.value - 1) * size
    list.value = data.slice(start, start + size)
  } finally {
    loading.value = false
  }
}

// 商家推进订单状态：confirm -> stocked -> ship -> complete
const advance = (row, action) => {
  const next = { confirm: 1, stocked: 2, ship: 3, complete: 4 }[action]
  row.status = next
  ElMessage.success('操作成功')
  loadData()
}

// 商家取消订单（交易完成前可取消）
const cancel = async (row) => {
  try {
    await ElMessageBox.confirm(`确定取消订单 ${row.orderNo || row.id} 吗？`, '取消订单', { type: 'warning' })
  } catch {
    return
  }
  row.status = 5
  ElMessage.success('订单已取消')
  loadData()
}

onMounted(() => loadData())
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; width: 100%; box-sizing: border-box; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.actions { display: flex; gap: 5px; flex-wrap: nowrap; align-items: center; justify-content: center; }
.actions :deep(.el-button) { padding: 5px 8px; }
.pager { margin-top: 16px; text-align: center; }
</style>
