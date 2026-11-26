<template>
  <div class="as-list-page">
    <BuyerHeader />

    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>我的售后</span>
    </div>

    <main class="main">
      <div class="as-card">
        <div class="title-row">
          <h2 class="page-title">我的售后</h2>
          <div class="title-ops">
            <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px" @change="handleFilter">
              <el-option
                v-for="opt in AFTER_SALE_STATUS_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
            <span class="page-tip">只能查看本人发起的售后单</span>
          </div>
        </div>

        <div v-if="loading" class="state">加载中...</div>

        <div v-else-if="list.length === 0" class="state empty">
          <el-empty description="还没有售后单" />
          <el-button type="danger" @click="router.push('/buyer/orders')">去我的订单看看</el-button>
        </div>

        <template v-else>
          <el-table :data="list" style="width: 100%" stripe @row-click="goDetail">
            <el-table-column prop="afterSaleNo" label="售后单号" width="180" />
            <el-table-column prop="productName" label="商品名称" min-width="220" />
            <el-table-column prop="quantity" label="数量" width="90" align="center" />

            <el-table-column label="售后单状态" width="130" align="center">
              <template #default="{ row }">
                <!-- statusText 由后端下发，直接展示，不自己映射 -->
                <el-tag :type="afterSaleStatusTagType(row.status)">
                  {{ afterSaleStatusText(row.status, row.statusText) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="提交时间" width="180">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>

            <el-table-column label="操作" width="120" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="goDetail(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="prev, pager, next"
              :total="total"
              :page-size="queryParams.size"
              :current-page="queryParams.page"
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </div>
    </main>

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
import { getMyAfterSaleList } from '@/api/buyer'
import { formatTime } from '@/utils/format'
import {
  AFTER_SALE_STATUS_OPTIONS,
  afterSaleStatusText,
  afterSaleStatusTagType
} from '@/constants/afterSaleStatus'
import BuyerHeader from '@/components/BuyerHeader.vue'

const router = useRouter()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  size: 10,
  status: ''
})

async function loadList() {
  loading.value = true
  try {
    const res = await getMyAfterSaleList(queryParams.value)
    list.value = res?.list || []
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '获取售后单列表失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleFilter() {
  queryParams.value.page = 1
  loadList()
}

function handlePageChange(page) {
  queryParams.value.page = page
  loadList()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goDetail(row) {
  if (!row?.afterSaleNo) return
  router.push(`/buyer/after-sales/${encodeURIComponent(row.afterSaleNo)}`)
}

onMounted(loadList)
</script>

<style scoped>
.as-list-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

.breadcrumb {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 16px 20px;
  font-size: 13px; color: #999; box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

.main {
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px;
  box-sizing: border-box;
}
.as-card { background: #fff; padding: 30px 40px; min-height: 500px; }

.title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; }
.page-title { font-size: 20px; margin: 0; font-weight: 500; }
.title-ops { display: flex; align-items: center; gap: 14px; }
.page-tip { font-size: 12px; color: #999; }

.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

:deep(.el-table__row) { cursor: pointer; }

.pagination-wrap { display: flex; justify-content: center; margin-top: 30px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
