<template>
  <div class="fav-page">
    <!-- 主头部（公共组件） -->
    <BuyerHeader />

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>我的收藏</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div class="title-row">
        <h2 class="page-title">我的收藏</h2>
        <span class="page-tip">共 {{ total }} 件（上限 200 条）</span>
      </div>

      <div v-if="loading" class="state">加载中...</div>

      <div v-else-if="favList.length === 0" class="state empty">
        <el-empty description="还没有收藏任何商品" />
        <el-button type="danger" @click="router.push('/buyer/goods')">去逛逛</el-button>
      </div>

      <template v-else>
        <div class="fav-grid">
          <div
            v-for="item in favList"
            :key="item.productId"
            class="fav-card"
            :class="{ 'is-off': !item.available }"
            @click="goDetail(item.productId)"
          >
            <div class="fav-image">
              <!-- 收藏接口未强制返回图片，有则展示、无则占位，避免裂图 -->
              <img v-if="coverOf(item)" :src="getImageUrl(coverOf(item))" alt="商品图片" @error="handleImageError" />
              <span v-else class="fav-image-ph">{{ (item.name || '商').slice(0, 1) }}</span>
              <!-- 已下架：置灰并标注 -->
              <span v-if="!item.available" class="off-tag">已下架</span>
            </div>
            <div class="fav-info">
              <h3 class="fav-name">{{ item.name }}</h3>
              <div class="fav-price">{{ formatMoney(item.price) }}</div>
              <div class="fav-op">
                <!-- 收藏列表只提供「取消收藏」与「进入详情」，不提供加购 / 下单 -->
                <el-button link type="danger" @click.stop="handleRemove(item)">取消收藏</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 分页器 -->
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
import { getFavoriteList, removeFavorite } from '@/api/buyer'
import { formatMoney } from '@/utils/format'
import BuyerHeader from '@/components/BuyerHeader.vue'

const router = useRouter()

const loading = ref(false)
const favList = ref([])
const total = ref(0)

const queryParams = ref({
  page: 1,
  size: 10
})

// 兼容后端可能的不同图片字段命名
function coverOf(item) {
  return item?.image || item?.mainImage || item?.imageUrl || item?.cover || ''
}

function getImageUrl(url) {
  if (!url) return 'https://via.placeholder.com/300x300?text=No+Image'
  if (url.startsWith('http') || url.startsWith('data:')) return url
  return `http://localhost:8080/${url.startsWith('/') ? url.slice(1) : url}`
}

function handleImageError(e) {
  if (e.target.src.includes('placeholder.com')) return
  e.target.src = 'https://via.placeholder.com/300x300?text=图片加载失败'
}

async function loadFavorites() {
  loading.value = true
  try {
    const res = await getFavoriteList(queryParams.value)
    favList.value = res?.list || []
    total.value = res?.total || 0
  } catch (e) {
    ElMessage.error(e.message || '获取收藏列表失败')
    favList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

// 取消收藏：成功后本地移除，避免整页刷新
async function handleRemove(item) {
  try {
    await removeFavorite(item.productId)
    ElMessage.success('已取消收藏')
    // 若当前页只剩这一条且不是第一页，回退一页
    if (favList.value.length === 1 && queryParams.value.page > 1) {
      queryParams.value.page -= 1
    }
    await loadFavorites()
  } catch (e) {
    ElMessage.error(e.message || '取消收藏失败')
  }
}

function handlePageChange(page) {
  queryParams.value.page = page
  loadFavorites()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goDetail(productId) {
  router.push(`/buyer/goods/detail/${productId}`)
}

onMounted(() => {
  loadFavorites()
})
</script>

<style scoped>
.fav-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

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
.title-row {
  display: flex; align-items: baseline; justify-content: space-between;
  background: #fff; padding: 30px 40px 0;
}
.page-title { font-size: 20px; margin: 0; font-weight: 500; }
.page-tip { font-size: 12px; color: #999; }

.state { text-align: center; padding: 80px 0; color: #999; background: #fff; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

/* 收藏卡片网格（与商品列表卡片保持一致） */
.fav-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px;
  background: #fff; padding: 24px 40px 10px;
}
.fav-card {
  background: #fff; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;
  display: flex; flex-direction: column;
}
.fav-card:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.08); }

.fav-image {
  position: relative; width: 100%; aspect-ratio: 1; background: #fafafa;
  display: flex; justify-content: center; align-items: center;
}
.fav-image img { width: 100%; height: 100%; object-fit: contain; }
.fav-image-ph { color: #e0e0e0; font-size: 56px; font-weight: 500; user-select: none; }
/* 已下架：图片整体置灰 */
.fav-card.is-off .fav-image img { opacity: 0.45; }
/* 已下架：名称与价格同步弱化，和购物车里「已下架」的处理保持一致 */
.fav-card.is-off .fav-name,
.fav-card.is-off .fav-price,
.fav-card.is-off .fav-image-ph { color: #c0c0c0; }
.off-tag {
  position: absolute; left: 0; top: 0; background: rgba(0,0,0,0.55); color: #fff;
  font-size: 12px; padding: 4px 10px; letter-spacing: 1px;
}

.fav-info { padding: 16px; display: flex; flex-direction: column; flex: 1; }
.fav-name {
  font-size: 15px; color: #333; margin: 0 0 10px; font-weight: 500;
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.fav-price { color: #e60012; font-size: 18px; font-weight: bold; margin-top: auto; }
.fav-op { margin-top: 10px; }

/* 分页 */
.pagination-wrap {
  display: flex; justify-content: center; margin-top: 0;
  background: #fff; padding: 20px 40px 30px;
}

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }

/* 响应式调整（与商品列表一致） */
@media (max-width: 1024px) { .fav-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .fav-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
