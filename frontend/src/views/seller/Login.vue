<template>
  <div class="login-page">
    <div class="login-box">
      <div class="brand">MVP购物系统 · 卖家中心</div>
      <h2>卖家登录</h2>
      <form @submit.prevent="onSubmit">
        <label>账号</label>
        <input v-model.trim="form.username" placeholder="请输入卖家账号" />
        <label>密码</label>
        <input v-model="form.password" type="password" placeholder="请输入密码" />
        <button type="submit" :disabled="loading">{{ loading ? '登录中...' : '登 录' }}</button>
      </form>
      <div class="tips">买家请回 <router-link to="/">商城首页</router-link></div>
      <p v-if="errMsg" class="err">{{ errMsg }}</p>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
// ⚠️ 改动1：把 sellerLogin 改成了 login，与 api/seller.js 保持一致
import { login } from '@/api/seller'

const router = useRouter()
const route = useRoute()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errMsg = ref('')

const onSubmit = async () => {
  errMsg.value = ''
  if (!form.username || !form.password) { errMsg.value = '账号和密码不能为空'; return }
  loading.value = true
  try {
    // ⚠️ 改动2：把 sellerLogin 改成了 login
    const res = await login(form)
    
    // 存 Token（Mock 阶段会存一个 mock-token-123）
    localStorage.setItem('seller_token', res.data.token)
    localStorage.setItem('seller_info', JSON.stringify(res.data.seller || res.data))
    
    // 跳转逻辑
    router.replace(route.query.redirect || '/seller')
  } catch (e) {
    errMsg.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f5f6f8; }
.login-box { width: 380px; background: #fff; padding: 40px 36px; border: 1px solid #e5e5e5; }
.brand { color: #c7000b; font-weight: 700; }
.login-box h2 { margin: 8px 0 24px; font-size: 22px; }
label { display: block; margin: 12px 0 6px; color: #666; font-size: 14px; }
input { width: 100%; height: 40px; padding: 0 10px; border: 1px solid #dcdfe6; box-sizing: border-box; }
input:focus { outline: none; border-color: #c7000b; }
button { width: 100%; height: 42px; margin-top: 24px; background: #c7000b; color: #fff; border: none; font-size: 16px; cursor: pointer; }
button:disabled { opacity: .6; }
.tips { margin-top: 16px; font-size: 13px; color: #999; }
.err { color: #c7000b; font-size: 13px; margin-top: 10px; }
</style>