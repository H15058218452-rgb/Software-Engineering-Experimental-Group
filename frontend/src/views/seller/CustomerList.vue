<template>
  <div class="wrap">
    <div class="bar">
      <h3>客户管理</h3>
      <el-button type="primary" @click="loadData">刷新列表</el-button>
    </div>

    <el-table :data="list" border style="width: 100%" v-loading="loading">
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="phone" label="电话" width="150" />
      <el-table-column prop="address" label="默认交易地点" />
      <el-table-column label="注册时间" width="180">
        <template #default="{ row }">{{ formatTime(row.registerTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="openHistory(row)">查看历史</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 历史记录弹窗 -->
    <el-dialog v-model="dialogVisible" :title="`${currentCustomer?.username} 的购买历史`" width="600px">
      <el-table :data="historyList" border>
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column label="下单时间">
          <template #default="{ row }">{{ formatTime(row.orderTime) }}</template>
        </el-table-column>
        <el-table-column prop="result" label="交易结果" />
        <el-table-column label="交易时间">
          <template #default="{ row }">{{ formatTime(row.resultTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCustomerList, getCustomerHistory } from '@/api/seller'
import { ElMessage } from 'element-plus'

const list = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const currentCustomer = ref(null)
const historyList = ref([])

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  return timeStr.substring(0, 19).replace('T', ' ')
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getCustomerList()
    if (res.code === 0) list.value = res.data.list
  } catch (e) {
    ElMessage.error('获取客户列表失败')
  } finally {
    loading.value = false
  }
}

const openHistory = async (row) => {
  currentCustomer.value = row
  dialogVisible.value = true
  try {
    const res = await getCustomerHistory(row.id)
    if (res.code === 0) historyList.value = res.data
  } catch (e) {
    ElMessage.error('获取历史记录失败')
  }
}

onMounted(() => loadData())
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; }
.bar { display: flex; justify-content: space-between; margin-bottom: 20px; }
</style>