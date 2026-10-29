<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2>买家登录</h2>
      <p class="tip">登录后即可提交购买意向</p>
      
      <div class="form-item">
        <input v-model="form.username" type="text" placeholder="请输入用户名" />
      </div>
      <div class="form-item">
        <input v-model="form.password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
      </div>
      
      <div v-if="error" class="error-tip">{{ error }}</div>
      
      <button class="submit-btn" :disabled="loading" @click="handleLogin">
        {{ loading ? '登录中...' : '登 录' }}
      </button>
      
      <div class="bottom-link">
        还没有账号？<router-link to="/buyer/register">立即注册</router-link>
      </div>
      <div class="bottom-link">
        <router-link to="/buyer/goods">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '@/api/buyer' // 待添加
import { STORAGE_KEYS } from '@/constants/storageKeys'

const router = useRouter()
const form = ref({ username: '', password: '' })
const loading = ref(false)
const error = ref('')

async function handleLogin() {
  if (!form.value.username || !form.value.password) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    // 调用后端登录接口
    const res = await login(form.value)
    localStorage.setItem(STORAGE_KEYS.BUYER_TOKEN, res.token)
    router.push('/buyer/goods')
  } catch (e) {
    error.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page { min-height: 100vh; display: flex; justify-content: center; align-items: center; background: #f5f5f5; }
.auth-card { width: 400px; background: #fff; padding: 40px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
h2 { text-align: center; margin: 0 0 10px; color: #333; }
.tip { text-align: center; color: #999; font-size: 13px; margin-bottom: 30px; }
.form-item { margin-bottom: 20px; }
.form-item input { width: 100%; height: 44px; padding: 0 12px; border: 1px solid #eee; background: #fafafa; outline: none; box-sizing: border-box; }
.form-item input:focus { border-color: #e60012; background: #fff; }
.error-tip { color: #e60012; font-size: 13px; margin-bottom: 15px; text-align: center; }
.submit-btn { width: 100%; height: 44px; background: #e60012; color: #fff; border: none; font-size: 16px; cursor: pointer; transition: 0.2s; }
.submit-btn:hover:not(:disabled) { background: #c5000f; }
.submit-btn:disabled { background: #ccc; }
.bottom-link { text-align: center; margin-top: 20px; font-size: 13px; color: #666; }
.bottom-link a { color: #e60012; text-decoration: none; }
</style>