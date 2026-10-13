<template>
  <div class="seller-layout">
    <header class="top">
      <div class="logo">MVP购物系统 · 卖家中心</div>
      <div class="right">
        <span>{{ sellerName }}</span>
        <router-link to="/">回商城</router-link>
        <a href="javascript:;" @click="logout">退出</a>
      </div>
    </header>
    <div class="body">
      <aside class="side">
        <router-link to="/seller/goods">商品管理</router-link>
        <router-link to="/seller/publish">发布商品</router-link>
        <router-link to="/seller/intents">查看意向购买人</router-link>
        <router-link to="/seller/password">修改密码</router-link>
      </aside>
      <main class="main"><router-view /></main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const sellerName = computed(() => {
  try { return JSON.parse(localStorage.getItem('seller_info') || '{}').username || '卖家' }
  catch { return '卖家' }
})
const logout = () => {
  localStorage.removeItem('seller_token')
  localStorage.removeItem('seller_info')
  router.push('/seller/login')
}
</script>

<style scoped>
.seller-layout { min-height: 100vh; background: #f5f6f8; }
.top { height: 56px; background: #222; color: #fff; display: flex; align-items: center; justify-content: space-between; padding: 0 24px; }
.logo { color: #fff; font-weight: 700; }
.right a, .right span { color: #ddd; margin-left: 18px; font-size: 14px; text-decoration: none; }
.body { display: flex; }
.side { width: 180px; background: #fff; border-right: 1px solid #e5e5e5; padding-top: 12px; min-height: calc(100vh - 56px); }
.side a { display: block; padding: 14px 24px; color: #333; text-decoration: none; font-size: 14px; }
.side a.router-link-active { background: #c7000b; color: #fff; }
.main { flex: 1; padding: 20px; }
</style>