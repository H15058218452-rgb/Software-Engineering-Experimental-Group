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
      <el-table-column label="图片" width="100" align="center">
        <template #default="{ row }">
          <img :src="row.image || 'https://via.placeholder.com/60'" style="width: 60px; height: 60px; object-fit: contain;" />
        </template>
      </el-table-column>
      
      <el-table-column prop="name" label="商品名称" min-width="150" show-overflow-tooltip />
      
      <el-table-column prop="categoryName" label="所属类别" width="110">
        <template #default="{ row }">
          {{ row.categoryName || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column prop="price" label="价格" width="90">
        <template #default="{ row }">¥{{ row.price }}</template>
      </el-table-column>
      
      <!-- 库存列 -->
      <el-table-column label="库存" width="100" align="center">
        <template #default="{ row }">
          <span :style="{ color: row.stock === 0 ? 'red' : 'green', fontWeight: 'bold' }">
            {{ row.stock }}
          </span>
          <el-button link type="primary" size="small" @click="openStockDialog(row)">修改</el-button>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="140" align="center">
        <template #default="{ row }">
          <!-- B.1-5：商品只有「在售 / 已下架」两态，下架原因单独打标签 -->
          <el-tag size="small" :type="isOnSale(row) ? 'success' : 'info'">{{ isOnSale(row) ? '在售' : '已下架' }}</el-tag>
          <el-tag v-if="!isOnSale(row) && offShelfReasonText(row.offShelfReason)" size="small" type="warning" style="margin-left: 4px;">
            {{ offShelfReasonText(row.offShelfReason) }}
          </el-tag>
        </template>
      </el-table-column>

      <!-- ⚠️ 去掉了 fixed="right"，让表格自己正常撑开 -->
      <el-table-column label="操作" width="230">
        <template #default="{ row }">
          <div class="actions">
            <!-- 在售：只能下架，不能删除 -->
            <template v-if="isOnSale(row)">
              <el-button size="small" type="danger" @click="operate(row, 'off-shelf')">下架</el-button>
            </template>
            <!-- 已下架：库存 > 0 才能重新上架（B.1-5） -->
            <template v-else>
              <el-button v-if="row.stock > 0" size="small" type="success" @click="operate(row, 'shelf-on')">重新上架</el-button>
              <el-button v-else size="small" type="info" disabled>库存为0</el-button>
            </template>

            <el-button size="small" type="primary" @click="goToEdit(row)">编辑</el-button>
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
import { ElMessage } from 'element-plus'
import { getProductList, operateProduct, updateStock } from '@/api/seller'

const router = useRouter()
const productList = ref([])
const loading = ref(false)

const stockDialog = reactive({
  visible: false,
  productId: '',
  stock: 0,
  oldStock: 0
})

// B.1-5：商品状态收敛为两态。后端仍是 0 在售 / 1 手动冻结 / 2 交易中 / 3 已下架，
// 冻结与交易中属迭代 2 遗留，按 B 口径一律视为「在售」。
const isOnSale = (row) => Number(row.status) !== 3

// 已下架原因：后端暂未下发 offShelfReason 字段，拿到就展示，拿不到就不显示
const offShelfReasonText = (reason) => ({
  SOLD: '已售',
  SOLD_OUT: '库存耗尽',
  MANUAL: '手动下架'
}[reason] || '')

const loadData = async () => {
  loading.value = true
  try {
    const res = await getProductList({ page: 1, pageSize: 100 })
    // 商品管理只列在售商品；已下架的统一在「历史管理」里看
    if (res.code === 0) productList.value = (res.data.list || []).filter(isOnSale)
  } catch (e) {
    ElMessage.error('获取列表失败')
  } finally {
    loading.value = false
  }
}

const openStockDialog = (row) => {
  stockDialog.productId = row.id
  stockDialog.stock = row.stock
  stockDialog.oldStock = row.stock
  stockDialog.visible = true
}

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

const operate = async (row, action) => {
  loading.value = true
  try {
    await operateProduct(action, { productId: row.id })
    ElMessage.success(action === 'shelf-on' ? '已重新上架' : '已下架')
    loadData()
  } catch (error) {
    ElMessage.error(error.message || '操作失败')
  } finally {
    loading.value = false
  }
}

const goToEdit = (row) => router.push(`/seller/publish/${row.id}`)

onMounted(() => loadData())
</script>

<style scoped>
.wrap { padding: 20px; background: #f5f7fa; min-height: 100vh; width: 100%; box-sizing: border-box; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }

/* 强制按钮在一行内，不换行 */
.actions { 
  display: flex; 
  gap: 5px; 
  flex-wrap: nowrap; 
  align-items: center;
}
/* 让按钮小一点，防止宽度不够 */
.actions :deep(.el-button) {
  padding: 5px 8px;
}
</style>