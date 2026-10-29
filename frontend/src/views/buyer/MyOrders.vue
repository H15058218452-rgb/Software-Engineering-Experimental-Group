<template>
  <div class="orders-page">
    <!-- 主头部 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
          <img :src="logoImg" alt="ShopMall" @click="router.push('/buyer/goods')" />
        </div>
        <nav class="nav">
          <router-link to="/buyer/goods" class="nav-item">首页</router-link>
          <router-link to="/buyer/orders" class="nav-item active">我的订单</router-link>
        </nav>
        <div class="user-actions">
          <el-button link type="danger" @click="handleLogout">退出登录</el-button>
          <router-link to="/seller/login" class="seller-entry">卖家入口</router-link>
        </div>
      </div>
    </header>

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>我的订单</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div class="orders-card">
        <h2 class="page-title">历史下单记录</h2>
        
        <div v-if="loading" class="state">加载中...</div>
        <div v-else-if="orderList.length === 0" class="state empty">
          <el-empty description="您还没有任何下单记录" />
          <el-button type="danger" @click="router.push('/buyer/goods')">去逛逛</el-button>
        </div>

        <template v-else>
          <el-table :data="orderList" style="width: 100%" stripe>
            <el-table-column prop="productName" label="商品名称" min-width="200">
              <template #default="{ row }">
                <span class="product-name">{{ row.productName || '商品已删除' }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="createTime" label="下单时间" width="200">
              <template #default="{ row }">
                {{ formatTime(row.createTime || row.queuedAt) }}
              </template>
            </el-table-column>

            <el-table-column label="交易结果" width="150" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status !== undefined ? row.status : row.intentStatus)">
                  {{ getStatusText(row.status !== undefined ? row.status : row.intentStatus) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="交易时间" width="200">
              <template #default="{ row }">
                <!-- 只有成功或失败才有交易时间 -->
                {{ row.dealTime || row.updateTime ? formatTime(row.dealTime || row.updateTime) : '-' }}
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页器 -->
          <div class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="total"
              :page-size="queryParams.pageSize"
              :current-page="queryParams.page"
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </div>
    </main>

    <!-- 底部 -->
    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 ShopMall. All Rights Reserved.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyOrders } from '@/api/buyer'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import { formatTime } from '@/utils/format'
import logoImg from '@/assets/logo.png'

const router = useRouter()

const loading = ref(false)
const orderList = ref([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  pageSize: 10
})

// 状态映射 (兼容后端返回的 status 或 intentStatus)
function getStatusText(status) {
  const map = {
    0: '待交易', // 排队中
    1: '待交易', // 交易中
    2: '交易成功',
    3: '交易失败', // 已作废
    4: '已取消',   // 已撤销
    5: '待交易',   // 重新排队
    6: '交易失败'  // 最终失败
  }
  return map[status] || '未知'
}

function getStatusType(status) {
  if (status === 2) return 'success'
  if (status === 3 || status === 6) return 'danger'
  if (status === 4) return 'info'
  return 'warning' // 0, 1, 5 都是 warning (待交易)
}

async function loadOrders() {
  loading.value = true
  try {
    const res = await getMyOrders(queryParams.value)
    orderList.value = res.list || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '获取订单记录失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  queryParams.value.page = page
  loadOrders()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function handleLogout() {
  localStorage.removeItem(STORAGE_KEYS.BUYER_TOKEN)
  ElMessage.success('已退出登录')
  router.push('/buyer/login')
}

onMounted(() => {
  loadOrders()
})
</script>

<style scoped>
.orders-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

/* 头部 */
.header { background: #fff; }
.header-inner {
  max-width: 1200px; margin: 0 auto; height: 80px;
  display: flex; align-items: center; padding: 0 20px; gap: 24px;
}
.logo img { height: 28px; cursor: pointer; }
.nav { flex: 1; display: flex; gap: 32px; margin-left: 20px; }
.nav-item { color: #333; text-decoration: none; font-size: 15px; }
.nav-item:hover, .nav-item.active { color: #e60012; font-weight: bold; }
.user-actions { display: flex; align-items: center; gap: 15px; font-size: 14px; }
.seller-entry { color: #999; text-decoration: none; }

/* 面包屑 */
.breadcrumb {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 16px 20px;
  font-size: 13px; color: #999; box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

/* 主体 */
.main {
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px;
  box-sizing: border-box;
}
.orders-card {
  background: #fff; padding: 30px 40px; min-height: 500px;
}
.page-title { font-size: 20px; margin: 0 0 24px; font-weight: 500; }

.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

.product-name { font-weight: 500; color: #333; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 30px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>