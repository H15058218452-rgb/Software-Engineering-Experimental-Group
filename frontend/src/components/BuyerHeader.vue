<template>
  <!-- 买家端公共头部：样式与列表页 / 详情页保持完全一致，仅导航项统一到 4 个入口 -->
  <header class="header">
    <div class="header-inner">
      <div class="logo">
        <img :src="logoImg" alt="ShopMall" @click="router.push('/buyer/goods')" />
      </div>
      <nav class="nav">
        <router-link
          v-for="item in navList"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          {{ item.label }}
        </router-link>
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
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import logoImg from '@/assets/logo.png'

const route = useRoute()
const router = useRouter()

// 导航项：首页 / 购物车 / 我的收藏 / 我的订单
const navList = [
  { path: '/buyer/goods', label: '首页' },
  { path: '/buyer/cart', label: '购物车' },
  { path: '/buyer/favorites', label: '我的收藏' },
  { path: '/buyer/orders', label: '我的订单' }
]

const isLoggedIn = computed(() => !!localStorage.getItem(STORAGE_KEYS.BUYER_TOKEN))

function isActive(path) {
  // 商品列表与商品详情都归属「首页」
  if (path === '/buyer/goods') return route.path.startsWith('/buyer/goods')
  return route.path === path
}

function handleLogout() {
  localStorage.removeItem(STORAGE_KEYS.BUYER_TOKEN)
  ElMessage.success('已退出登录')
  // 整页跳转，确保各页面登录态一起刷新
  window.location.href = '/buyer/goods'
}
</script>

<style scoped>
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
</style>
