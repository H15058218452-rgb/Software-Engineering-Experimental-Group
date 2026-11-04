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
      
      <!-- 预留默认交易地点（等后端加） -->
      <el-table-column label="默认交易地点" min-width="180" align="center">
        <template #default="{ row }">
          <!-- 后端还没这个字段，暂时显示横杠 -->
          {{ row.address || row.defaultAddress || '-' }}
        </template>
      </el-table-column>

      <el-table-column label="注册时间" width="180" align="center">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      
      <!-- 操作列固定在右侧 -->
      <el-table-column label="操作" width="120" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link @click="openHistory(row)">查看历史</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 历史记录弹窗 -->
    <el-dialog v-model="dialogVisible" :title="`${currentCustomer?.username || '客户'} 的购买历史`" width="600px">
      <el-table :data="historyList" border v-loading="historyLoading">
        <el-table-column prop="productName" label="商品名称" min-width="150" />
        <el-table-column label="下单时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="交易结果" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.statusText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交易时间" width="160" align="center">
          <template #default="{ row }">{{ formatTime(row.updatedAt || row.queuedAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!historyLoading && historyList.length === 0" description="该客户暂无购买记录" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCustomerList, getCustomerHistory } from '@/api/seller'
import { ElMessage } from 'element-plus'

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