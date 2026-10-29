<template>
  <div>
    <!-- 当前在售商品（看板） -->
    <div class="current-box">
      <div class="bar">
        <h3>当前商品</h3>
        <el-tag v-if="currentProduct" :type="getStatusType(currentProduct.status)">
          {{ getStatusText(currentProduct.status) }}
        </el-tag>
        <el-button link type="primary" @click="loadData">刷新状态</el-button>
      </div>
      
      <div v-if="currentProduct" class="product-card">
        <img :src="currentProduct.image" class="product-img" />
        <div class="product-info">
          <h2>{{ currentProduct.name }}</h2>
          <p class="desc">{{ currentProduct.description }}</p >
          <p class="price">¥{{ currentProduct.price }}</p >
          
          <div class="actions">
            <!-- 根据商品状态显示不同按钮 -->
            <template v-if="currentProduct.status === 0">
              <el-button type="warning" @click="operate('freeze')">手动冻结</el-button>
              <el-button type="danger" @click="operate('off-shelf')">手动下架</el-button>
              <el-button type="success" @click="operate('start-trade')">开始交易</el-button>
              <el-button type="primary" @click="goToIntents">查看意向</el-button>
            </template>
            <template v-else-if="currentProduct.status === 1">
              <el-button type="success" @click="operate('unfreeze')">手动解冻</el-button>
              <el-button type="danger" @click="operate('off-shelf')">手动下架</el-button>
              <el-button type="primary" @click="goToIntents">查看意向</el-button>
            </template>
            <template v-else-if="currentProduct.status === 2">
              <el-alert title="商品交易中，禁止解冻和下架" type="info" show-icon :closable="false" style="margin-bottom:10px;" />
              <el-button type="success" :loading="loading" @click="operate('confirm-sold')">确认成交</el-button>
              <el-button type="danger" :loading="loading" @click="operate('confirm-failed')">交易失败</el-button>
              <el-button type="primary" @click="goToIntents">查看意向</el-button>
            </template>
            <template v-else>
              <el-empty description="当前没有在售商品" :image-size="80" />
            </template>
          </div>
        </div>
      </div>
      <el-empty v-else description="当前没有在售商品" />
    </div>

    <!-- 历史商品列表 -->
    <div class="history-box">
      <h3>历史商品</h3>
      <el-table :data="historyList" style="width: 100%; margin-top: 10px;">
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="price" label="价格" width="120" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag type="info">已下架</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="goToIntentsHistory(row.id)">查看意向</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus' // 必须引入 ElMessageBox
import { getCurrentProduct, getHistoryProducts, operateProduct, getSellerIntents } from '@/api/seller'

const router = useRouter()
const currentProduct = ref(null)
const historyList = ref([])
const loading = ref(false)

const statusMap = {
  0: { text: '在售', type: 'success' },
  1: { text: '手动冻结', type: 'warning' },
  2: { text: '交易中冻结', type: 'danger' },
  3: { text: '已下架', type: 'info' }
}

const getStatusText = (status) => statusMap[status]?.text || '未知'
const getStatusType = (status) => statusMap[status]?.type || 'info'

const loadData = async () => {
  try {
    const res1 = await getCurrentProduct()
    if (res1.code === 0) currentProduct.value = res1.data

    const res2 = await getHistoryProducts()
    if (res2.code === 0) historyList.value = res2.data.list
  } catch (e) {
    console.error('加载数据失败', e)
  }
}

const operate = async (action) => {
  if (!currentProduct.value) return
  loading.value = true

  try {
    let payload = { productId: currentProduct.value.id }

    // ⚠️ 如果是确认成交或交易失败，必须去拿当前正在交易的 intentId
    if (action === 'confirm-sold' || action === 'confirm-failed') {
      // 1. 查意向列表，找到状态为 1（交易中）的买家
      const intentRes = await getSellerIntents(currentProduct.value.id)
      if (intentRes.code !== 0) throw new Error('获取意向列表失败')
      
      const list = intentRes.data.list || []
      const tradingIntent = list.find(it => (it.status ?? it.intentStatus) === 1)
      
      if (!tradingIntent) {
        throw new Error('未找到正在交易的买家意向，请刷新页面后重试')
      }
      
      payload.intentId = tradingIntent.id // 拿到意向 ID 填入 payload

      // 2. 如果是交易失败，必须要弹窗让卖家选择处置方式
      if (action === 'confirm-failed') {
        try {
          await ElMessageBox.confirm('请选择交易失败后的处理方式', '交易失败', {
            distinguishCancelAndClose: true,
            confirmButtonText: '作废 (VOID)',
            cancelButtonText: '重新排队 (REQUEUE)',
            type: 'warning'
          })
          payload.disposition = 'VOID' // 点了确定，作废
        } catch (error) {
          if (error === 'cancel') {
            payload.disposition = 'REQUEUE' // 点了取消，重新排队
          } else {
            loading.value = false
            return // 点了右上角关闭，直接中止
          }
        }
      }
    }

    // 3. 发送真实请求
    const res = await operateProduct(action, payload)
    if (res.code === 0) {
      ElMessage.success('操作成功')
      loadData() // 刷新数据
    }
  } catch (error) {
    // ⚠️ 捕获 api 里抛出的错误（比如参数缺失、后端报错）
    ElMessage.error(error.message || '操作失败，请检查网络')
    console.error('操作报错:', error)
  } finally {
    loading.value = false
  }
}

const goToIntents = () => {
  router.push(`/seller/intents?productId=${currentProduct.value.id}`)
}

const goToIntentsHistory = (id) => {
  router.push(`/seller/intents?productId=${id}`)
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.current-box { background: #fff; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.history-box { background: #fff; padding: 20px; border-radius: 8px; }
.product-card { display: flex; gap: 20px; }
.product-img { width: 200px; height: 200px; object-fit: contain; background: #fafafa; border-radius: 8px; }
.product-info { flex: 1; display: flex; flex-direction: column; justify-content: center; }
.product-info h2 { margin: 0 0 10px 0; font-size: 20px; }
.desc { color: #666; margin-bottom: 15px; }
.price { color: #c7000b; font-size: 24px; font-weight: bold; margin-bottom: 20px; }
</style>