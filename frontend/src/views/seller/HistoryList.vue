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
        <template #default="{ row }">{{ formatTime(row.offShelfTime) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="200" align="center">
        <template #default="{ row }">
          <!-- 已下架商品的重新上架入口只在历史管理里（商品管理只列在售） -->
          <el-button v-if="row.stock > 0" type="success" link @click="shelfOn(row)">重新上架</el-button>
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

    <!-- 历史意向弹窗：迭代 2 的购买意向在 B 中已下线，这里只读查看，不再参与流转 -->
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
import { computed, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getHistoryProducts, getHistoryIntents, operateProduct } from '@/api/seller'

const list = ref([])
const page = ref(1)
const size = 10
const total = ref(0)
const loading = ref(false)

const dialogVisible = ref(false)
const currentProduct = ref(null)
const intentList = ref([])
const intentLoading = ref(false)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

// 迭代 2 意向状态：0 排队中 / 1 交易中 / 2 交易成功 / 3 已作废 / 4 已撤销 / 5 重新排队 / 6 最终失败
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

// 已下架商品历史：GET /seller/history/products（只读）
const load = async () => {
  loading.value = true
  try {
    const res = await getHistoryProducts({ page: page.value, pageSize: size })
    if (res.code === 0) {
      list.value = res.data.list || []
      total.value = Number(res.data.total || 0)
    }
  } catch (e) {
    ElMessage.error(e.message || '获取历史商品失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 重新上架：已下架且库存 > 0 才能上架，上架后商品从历史列表消失、回到商品管理
const shelfOn = async (row) => {
  try {
    await operateProduct('shelf-on', { productId: row.id })
    ElMessage.success('已重新上架')
    load()
  } catch (e) {
    ElMessage.error(e.message || '上架失败')
  }
}

const openIntents = async (row) => {
  currentProduct.value = row
  dialogVisible.value = true
  intentList.value = []
  intentLoading.value = true
  try {
    const res = await getHistoryIntents(row.id)
    if (res.code === 0) intentList.value = Array.isArray(res.data) ? res.data : (res.data.list || [])
  } catch (e) {
    ElMessage.error(e.message || '获取历史意向失败')
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
