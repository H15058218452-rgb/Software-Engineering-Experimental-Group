<template>
  <div class="wrap">
    <div class="bar">
      <h3>历史管理</h3>
      <el-button type="primary" @click="load">刷新列表</el-button>
    </div>

    <!-- 已下架商品历史列表 -->
    <el-table :data="list" style="width: 100%" border v-loading="loading">
      <el-table-column label="图片" width="100" align="center">
        <template #default="{ row }">
          <img :src="row.imageUrl || 'https://via.placeholder.com/60'" style="width: 60px; height: 60px; object-fit: contain;" />
        </template>
      </el-table-column>

      <el-table-column prop="name" label="商品名称" min-width="150" show-overflow-tooltip />

      <el-table-column label="价格" width="100" align="center">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>

      <el-table-column label="库存" width="90" align="center">
        <template #default="{ row }">{{ row.stock }}</template>
      </el-table-column>

      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.statusText || '已下架' }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="下架时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.offShelfAt) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="140" align="center">
        <template #default="{ row }">
          <el-button type="primary" link @click="openIntents(row)">查看历史意向</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-button :disabled="page <= 1" @click="page--; load()">上一页</el-button>
      <span style="margin: 0 10px;">{{ page }} / {{ totalPages }}</span>
      <el-button :disabled="page >= totalPages" @click="page++; load()">下一页</el-button>
    </div>

    <!-- 历史意向弹窗 -->
    <el-dialog v-model="dialogVisible" :title="`${currentProduct?.name || '商品'} 的历史意向`" width="600px">
      <el-table :data="intentList" border v-loading="intentLoading">
        <el-table-column label="姓名" min-width="100" align="center">
          <template #default="{ row }">{{ row.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="电话" width="130" align="center">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="交易结果" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusType(row.status)">{{ row.statusText || statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!intentLoading && intentList.length === 0" description="该商品暂无历史意向" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

// ===== 模拟数据（后端接口就位后，替换为真实 API 调用）=====
const MOCK_PRODUCTS = [
  { id: 1, name: '无线蓝牙耳机', imageUrl: '', price: '199.00', stock: 0, statusText: '已下架', offShelfAt: '2026-11-10 18:20:00' },
  { id: 2, name: '机械键盘', imageUrl: '', price: '299.00', stock: 0, statusText: '已下架', offShelfAt: '2026-11-09 15:10:00' }
]
const MOCK_INTENTS = {
  1: [
    { id: 101, name: '张三', phone: '13800000001', createdAt: '2026-11-08 10:00:00', status: 2, statusText: '交易成功' },
    { id: 102, name: '李四', phone: '13800000002', createdAt: '2026-11-09 11:30:00', status: 3, statusText: '已作废' }
  ],
  2: [
    { id: 201, name: '王五', phone: '13800000003', createdAt: '2026-11-07 09:20:00', status: 6, statusText: '最终失败' }
  ]
}

const list = ref([])
const page = ref(1)
const size = 10
const totalPages = ref(1)
const loading = ref(false)

const dialogVisible = ref(false)
const currentProduct = ref(null)
const intentList = ref([])
const intentLoading = ref(false)

const statusText = (s) => {
  const map = {
    0: '排队中', 1: '交易中', 2: '交易成功',
    3: '已作废', 4: '已撤销', 5: '重新排队中', 6: '最终失败'
  }
  return map[s] ?? '未知'
}

const getStatusType = (s) => {
  const map = { 2: 'success', 3: 'info', 4: 'info', 6: 'danger' }
  return map[s] || 'warning'
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return String(timeStr).substring(0, 19).replace('T', ' ')
}

const load = () => {
  loading.value = true
  try {
    // 模拟：前端分页
    const data = [...MOCK_PRODUCTS]
    totalPages.value = Math.max(1, Math.ceil(data.length / size))
    const start = (page.value - 1) * size
    list.value = data.slice(start, start + size)
  } finally {
    loading.value = false
  }
}

const openIntents = (row) => {
  currentProduct.value = row
  dialogVisible.value = true
  intentLoading.value = true
  try {
    // 模拟：读取该商品的历史意向
    intentList.value = MOCK_INTENTS[row.id] || []
  } finally {
    intentLoading.value = false
  }
}

onMounted(() => load())
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; width: 100%; box-sizing: border-box; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.pager { margin-top: 16px; text-align: center; }
</style>
