<template>
  <div class="goods-page">
    <!-- 顶部欢迎提示条 -->
    <transition name="slide-up">
      <div v-if="showTopBar" class="top-bar">
        欢迎来到 ShopMall，祝您购物愉快
      </div>
    </transition>

    <!-- 主头部 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
          <img :src="logoImg" alt="ShopMall" @click="router.push('/buyer/goods')" />
        </div>
        <nav class="nav">
          <router-link to="/buyer/goods" class="nav-item active">首页</router-link>
          <router-link to="/buyer/orders" class="nav-item">我的订单</router-link>
        </nav>
        <div class="user-actions">
          <template v-if="isLoggedIn">
            <span class="welcome">Hi, 用户</span>
            <el-button link type="danger" @click="handleLogout">退出</el-button>
          </template>
          <template v-else>
            <router-link to="/buyer/login" class="login-btn">登录</router-link>
            <span class="divider">|</span>
            <router-link to="/buyer/register" class="register-btn">注册</router-link>
          </template>
          <router-link to="/seller/login" class="seller-entry">卖家入口</router-link>
        </div>
      </div>
    </header>

    <!-- 搜索与筛选区 -->
    <div class="filter-bar">
      <div class="filter-inner">
        <div class="category-filter">
          <el-cascader
            v-model="selectedCategory"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            placeholder="全部分类"
            clearable
            @change="handleCategoryChange"
          />
        </div>
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="搜索商品名称或描述..."
            clearable
            @keyup.enter="handleSearch"
          >
            <template #append>
              <el-button @click="handleSearch"><el-icon><Search /></el-icon></el-button>
            </template>
          </el-input>
        </div>
      </div>
    </div>

    <!-- 主体：商品列表 -->
    <main class="main">
      <div v-if="loading" class="state">正在加载商品...</div>
      <div v-else-if="productList.length === 0" class="state empty">
        <el-empty description="未找到相关商品" />
      </div>

      <div v-else class="goods-grid">
        <div
          class="goods-card"
          v-for="item in productList"
          :key="item.id"
          @click="goToDetail(item.id)"
        >
          <div class="goods-image">
            <!-- 主图取图库第一张，若无则显示占位图 -->
            <img :src="item.mainImage || '/placeholder.png'" alt="商品图片" />
          </div>
          <div class="goods-info">
            <h3 class="goods-name">{{ item.name }}</h3>
            <p class="goods-category">{{ item.categoryName || '默认分类' }}</p>
            <div class="goods-price">{{ formatMoney(item.price) }}</div>
          </div>
        </div>
      </div>

      <!-- 分页器 -->
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="queryParams.pageSize"
          :current-page="queryParams.page"
          @current-change="handlePageChange"
        />
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
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { getProductList, getCategories } from '@/api/buyer'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import { formatMoney } from '@/utils/format'
import logoImg from '@/assets/logo.png'

const router = useRouter()

// 登录状态
const isLoggedIn = computed(() => {
  return !!localStorage.getItem(STORAGE_KEYS.BUYER_TOKEN)
})

// 数据状态
const loading = ref(false)
const productList = ref([])
const total = ref(0)
const showTopBar = ref(true)

// 筛选状态
const categoryOptions = ref([])
const selectedCategory = ref([]) // 级联选择器绑定的数组 [一级ID, 二级ID]
const keyword = ref('')

// 查询参数
const queryParams = ref({
  page: 1,
  pageSize: 10,
  keyword: '',
  categoryId: '' // 只传二级分类ID给后端
})

// 获取分类树
async function loadCategories() {
  try {
    const res = await getCategories()
    categoryOptions.value = res || []
  } catch (e) {
    console.error('获取分类失败', e)
  }
}

// 获取商品列表
async function loadProducts() {
  loading.value = true
  try {
    // 构造请求参数
    const params = { ...queryParams.value }
    // 如果选择了分类，级联数组的最后一个元素就是二级分类ID
    if (selectedCategory.value && selectedCategory.value.length > 0) {
      params.categoryId = selectedCategory.value[selectedCategory.value.length - 1]
    } else {
      params.categoryId = ''
    }

    const res = await getProductList(params)
    productList.value = res.list || []
    total.value = res.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载商品列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索触发
function handleSearch() {
  queryParams.value.keyword = keyword.value
  queryParams.value.page = 1 // 搜索重置回第一页
  loadProducts()
}

// 分类改变触发
function handleCategoryChange() {
  queryParams.value.page = 1
  loadProducts()
}

// 分页改变触发
function handlePageChange(page) {
  queryParams.value.page = page
  loadProducts()
  window.scrollTo({ top: 0, behavior: 'smooth' }) // 翻页后回到顶部
}

// 跳转详情
function goToDetail(id) {
  router.push(`/buyer/goods/detail/${id}`)
}

// 退出登录
function handleLogout() {
  localStorage.removeItem(STORAGE_KEYS.BUYER_TOKEN)
  ElMessage.success('已退出登录')
  // 刷新页面重置状态
  window.location.reload()
}

onMounted(() => {
  loadCategories()
  loadProducts()
  setTimeout(() => {
    showTopBar.value = false
  }, 3000)
})
</script>

<style scoped>
.goods-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #333;
}

/* 提示条 */
.top-bar {
  position: fixed; top: 20px; left: 50%; transform: translateX(-50%);
  z-index: 1000; background: #000; color: #ccc; font-size: 12px;
  padding: 10px 24px; white-space: nowrap; pointer-events: none;
}
.slide-up-leave-active { transition: opacity 0.4s ease, transform 0.4s ease; }
.slide-up-leave-to { opacity: 0; transform: translateX(-50%) translateY(-30px); }

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
.user-actions { display: flex; align-items: center; gap: 10px; font-size: 14px; }
.user-actions a { color: #333; text-decoration: none; }
.login-btn:hover, .register-btn:hover { color: #e60012; }
.seller-entry { color: #999 !important; margin-left: 10px; }

/* 筛选区 */
.filter-bar { background: #fff; border-top: 1px solid #f0f0f0; padding: 16px 0; }
.filter-inner {
  max-width: 1200px; margin: 0 auto; padding: 0 20px;
  display: flex; gap: 20px; align-items: center;
}
.category-filter { width: 250px; }
.search-box { width: 400px; }

/* 主体列表 */
.main {
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 24px 20px;
  box-sizing: border-box;
}
.state { text-align: center; padding: 80px 0; color: #999; }
.goods-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px;
}
.goods-card {
  background: #fff; cursor: pointer; transition: transform 0.2s, box-shadow 0.2s;
  display: flex; flex-direction: column;
}
.goods-card:hover {
  transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}
.goods-image {
  width: 100%; aspect-ratio: 1; background: #fafafa;
  display: flex; justify-content: center; align-items: center;
}
.goods-image img { width: 100%; height: 100%; object-fit: contain; }
.goods-info { padding: 16px; display: flex; flex-direction: column; flex: 1; }
.goods-name {
  font-size: 16px; color: #333; margin: 0 0 8px; font-weight: 500;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.goods-category { font-size: 12px; color: #999; margin-bottom: 12px; }
.goods-price { color: #e60012; font-size: 20px; font-weight: bold; margin-top: auto; }

/* 分页 */
.pagination-wrap { display: flex; justify-content: center; margin-top: 40px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }

/* 响应式调整 */
@media (max-width: 1024px) { .goods-grid { grid-template-columns: repeat(3, 1fr); } }
@media (max-width: 768px) { .goods-grid { grid-template-columns: repeat(2, 1fr); } .filter-inner { flex-direction: column; align-items: stretch; } .category-filter, .search-box { width: 100%; } }
</style>