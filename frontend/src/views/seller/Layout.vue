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
        <!-- 👇 B：购买意向机制已下线，不再提供「查看意向购买人」入口 -->
        <!-- 👇 新增这一行 -->
        <router-link to="/seller/customers">客户管理</router-link>
        <!-- 👇 挑战需求 B：订单按客户查看（从客户管理进入），历史为旧意向只读留档 -->
        <router-link to="/seller/orders">客户订单</router-link>
        <!-- 👇 挑战需求 C：售后服务升级，商家在此集中处理全部售后单 -->
        <router-link to="/seller/after-sales">售后管理</router-link>
        <router-link to="/seller/history">历史管理</router-link>
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
.main { 
  flex: 1; padding: 20px; width: 0; overflow-x: auto;}
</style>