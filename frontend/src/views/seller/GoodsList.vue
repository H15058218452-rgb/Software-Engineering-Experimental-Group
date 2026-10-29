<template>
  <div class="wrap">
    <div class="bar">
      <h3>商品管理</h3>
      <div>
        <el-button type="primary" @click="loadData">刷新列表</el-button>
        <el-button type="success" @click="$router.push('/seller/publish')">+ 发布新商品</el-button>
      </div>
    </div>

    <!-- 多商品列表 -->
    <el-table :data="productList" style="width: 100%" border v-loading="loading">
      <el-table-column label="图片" width="100">
        <template #default="{ row }">
          <img :src="row.image || 'https://via.placeholder.com/60'" style="width: 60px; height: 60px; object-fit: contain;" />
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商品名称" min-width="150" />
      <el-table-column prop="categoryName" label="所属类别" width="150">
        <template #default="{ row }">
          {{ row.categoryName || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      
      <!-- 库存列，带修改功能 -->
      <el-table-column label="库存" width="120">
        <template #default="{ row }">
          <span :style="{ color: row.stock === 0 ? 'red' : 'green', fontWeight: 'bold' }">
            {{ row.stock }}
          </span>
          <el-button link type="primary" size="small" @click="openStockDialog(row)">修改</el-button>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <div class="actions">
            <!-- 状态 0：在售 -->
            <template v-if="row.status === 0">
              <el-button size="small" type="warning" @click="operate(row, 'freeze')">冻结</el-button>
              <el-button size="small" type="danger" @click="operate(row, 'off-shelf')">下架</el-button>
              <el-button size="small" type="success" @click="operate(row, 'start-trade')">开始交易</el-button>
            </template>

            <!-- 状态 1：手动冻结 -->
            <template v-else-if="row.status === 1">
              <el-button size="small" type="success" @click="operate(row, 'unfreeze')">解冻</el-button>
              <el-button size="small" type="danger" @click="operate(row, 'off-shelf')">下架</el-button>
            </template>

            <!-- 状态 2：交易中 -->
            <template v-else-if="row.status === 2">
              <el-button size="small" type="success" :loading="loading" @click="handleTrade(row, 'confirm-sold')">确认成交</el-button>
              <el-button size="small" type="danger" :loading="loading" @click="handleTrade(row, 'confirm-failed')">交易失败</el-button>
            </template>
            
            <!-- 状态 3：已下架/售罄 -->
            <template v-else-if="row.status === 3 || !row.status">
              <el-button v-if="row.stock > 0" size="small" type="success" @click="operate(row, 'shelf-on')">重新上架</el-button>
              <el-button v-else size="small" type="info" disabled>库存为0</el-button>
            </template>
            
            <!-- 所有状态都可以查看意向 -->
            <el-button size="small" type="primary" @click="goToIntents(row.id)">查看意向</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 修改库存弹窗 -->
    <el-dialog v-model="stockDialog.visible" title="修改库存" width="300px">
      <el-input-number v-model="stockDialog.stock" :min="0" style="width: 100%;" />
      <template #footer>
        <el-button @click="stockDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmStock">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductList, operateProduct, getSellerIntents, updateStock } from '@/api/seller'

const router = useRouter()
const productList = ref([])
const loading = ref(false)

const stockDialog = reactive({ 
  visible: false, 
  productId: '', 
  stock: 0, 
  oldStock: 0 
})

const statusMap = {
  0: { text: '在售', type: 'success' },
  1: { text: '手动冻结', type: 'warning' },
  2: { text: '交易中', type: 'danger' },
  3: { text: '已下架/售罄', type: 'info' }
}
const getStatusText = (status) => statusMap[status]?.text || '未知'
const getStatusType = (status) => statusMap[status]?.type || 'info'

const loadData = async () => {
  loading.value = true
  try {
    const res = await getProductList({ page: 1, pageSize: 100 })
    if (res.code === 0) productList.value = res.data.list
  } catch (e) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

// 打开修改库存弹窗
const openStockDialog = (row) => {
  stockDialog.productId = row.id
  stockDialog.stock = row.stock
  stockDialog.oldStock = row.stock 
  stockDialog.visible = true
}

// 确认修改库存
const confirmStock = async () => {
  try {
    await updateStock(stockDialog.productId, stockDialog.stock, stockDialog.oldStock)
    ElMessage.success('库存修改成功')
    stockDialog.visible = false
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '修改失败')
  }
}

// 普通操作
const operate = async (row, action) => {
  loading.value = true
  try {
    await operateProduct(action, { productId: row.id })
    ElMessage.success('操作成功')
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

// 复杂的交易处理（确认成交/失败）
const handleTrade = async (row, action) => {
  loading.value = true
  try {
    let payload = { productId: row.id }
    
    const intentRes = await getSellerIntents(row.id)
    const tradingIntent = (intentRes.data.list || []).find(it => (it.status ?? it.intentStatus) === 1)
    
    if (!tradingIntent) throw new Error('未找到正在交易的意向，请刷新页面重试')
    payload.intentId = tradingIntent.id

    if (action === 'confirm-failed') {
      try {
        await ElMessageBox.confirm('请选择交易失败后的处理方式', '交易失败', {
          distinguishCancelAndClose: true,
          confirmButtonText: '作废 (VOID)',
          cancelButtonText: '重新排队 (REQUEUE)',
          type: 'warning'
        })
        payload.disposition = 'VOID'
      } catch (error) {
        if (error === 'cancel') payload.disposition = 'REQUEUE'
        else { loading.value = false; return }
      }
    }

    await operateProduct(action, payload)
    ElMessage.success('交易状态已更新')
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

const goToIntents = (id) => router.push(`/seller/intents?productId=${id}`)

onMounted(() => loadData())
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.actions { display: flex; gap: 8px; flex-wrap: wrap; }
</style>