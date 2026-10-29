<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2>买家注册</h2>
      
      <div class="form-item">
        <input v-model="form.username" type="text" placeholder="用户名（唯一）" />
      </div>
      <div class="form-item">
        <input v-model="form.password" type="password" placeholder="密码（至少8位）" />
      </div>
      <div class="form-item">
        <input v-model="form.phone" type="text" placeholder="联系电话" maxlength="11" />
      </div>
      <div class="form-item">
        <input v-model="form.address" type="text" placeholder="默认交易地点（选填）" />
      </div>
      
      <div v-if="error" class="error-tip">{{ error }}</div>
      
      <button class="submit-btn" :disabled="loading" @click="handleRegister">
        {{ loading ? '注册中...' : '注 册' }}
      </button>
      
      <div class="bottom-link">
        已有账号？<router-link to="/buyer/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '@/api/buyer'

const router = useRouter()
const form = ref({ username: '', password: '', phone: '', address: '' })
const loading = ref(false)
const error = ref('')

async function handleRegister() {
  if (!form.value.username) return error.value = '请输入用户名'
  if (!form.value.password || form.value.password.length < 8) return error.value = '密码不能少于8位'
  if (!form.value.phone) return error.value = '请输入联系电话'

  loading.value = true
  error.value = ''
  try {
    await register(form.value)
    // 注册成功直接跳登录
    router.push('/buyer/login')
  } catch (e) {
    error.value = e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
/* 样式和 Login.vue 完全一样，直接复制过来即可 */
.auth-page { min-height: 100vh; display: flex; justify-content: center; align-items: center; background: #f5f5f5; }
.auth-card { width: 400px; background: #fff; padding: 40px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
h2 { text-align: center; margin: 0 0 30px; color: #333; }
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