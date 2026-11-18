<template>
  <div class="wrap">
    <div class="bar">
      <h3>客户管理</h3>
      <div>
        <el-input 
          v-model="keyword" 
          placeholder="搜索手机号/昵称" 
          style="width: 200px; margin-right: 10px;" 
          clearable
          @keyup.enter="loadData"
        />
        <el-button type="primary" @click="loadData">搜索</el-button>
        <el-button @click="loadData">刷新</el-button>
      </div>
    </div>

    <!-- 客户列表 -->
    <el-table :data="list" border style="width: 100%" v-loading="loading">
      <!-- 核心修复：优先显示 username -->
      <el-table-column label="用户名" min-width="150" align="center">
        <template #default="{ row }">
          {{ row.username || row.nickname || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column prop="phone" label="电话" width="150" align="center" />
      
      <!-- 默认交易地点：账号档案字段（buyer.address），仅商家在此可见；订单本身不记交易地点 -->
      <el-table-column label="默认交易地点" min-width="180" align="center">
        <template #default="{ row }">
          {{ row.address || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="注册时间" width="180" align="center">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      
      <!-- 操作列固定在右侧 -->
      <el-table-column label="操作" width="190" fixed="right" align="center">
        <template #default="{ row }">
          <!-- B.1-4：商家从这里进入该客户的订单记录，并在那边推进状态 / 取消 -->
          <el-button type="primary" link @click="openOrders(row)">查看订单</el-button>
          <el-button type="primary" link @click="openHistory(row)">历史意向</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 历史意向弹窗：迭代 2 的购买意向在 B 中已下线，这里只读留档，不再参与任何流转 -->
    <el-dialog v-model="dialogVisible" :title="`${currentCustomer?.username || '客户'} 的历史意向`" width="600px">
      <el-table :data="historyList" border v-loading="historyLoading">
        <el-table-column prop="productName" label="商品名称" min-width="150" />
        <el-table-column label="提交时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="交易结果" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排队时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.queuedAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyLoading && historyList.length === 0" description="该客户暂无历史意向记录" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getCustomerList, getCustomerHistory } from '@/api/seller'
import { ElMessage } from 'element-plus'

const router = useRouter()

const list = ref([])
const loading = ref(false)
const keyword = ref('')

const dialogVisible = ref(false)
const currentCustomer = ref(null)
const historyList = ref([])
const historyLoading = ref(false)

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.substring(0, 19).replace('T', ' ')
}

const getStatusType = (status) => {
  const map = { 2: 'success', 3: 'info', 4: 'info', 6: 'danger' }
  return map[status] || 'warning'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCustomerList({ keyword: keyword.value, page: 1, pageSize: 100 })
    if (res.code === 0) {
      list.value = res.data.list
    }
  } catch (e) {
    ElMessage.error('获取客户列表失败')
  } finally {
    loading.value = false
  }
}

const openOrders = (row) => {
  // 带 customerId 进入订单页，后端只按客户查，不提供全局订单列表
  router.push({ path: '/seller/orders', query: { customerId: String(row.id) } })
}

const openHistory = async (row) => {
  currentCustomer.value = row
  dialogVisible.value = true
  historyList.value = [] 
  historyLoading.value = true
  try {
    const res = await getCustomerHistory(row.id)
    if (res.code === 0) {
      historyList.value = res.data
    }
  } catch (e) {
    ElMessage.error('获取历史记录失败')
  } finally {
    historyLoading.value = false
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; width: 100%; box-sizing: border-box; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
</style>