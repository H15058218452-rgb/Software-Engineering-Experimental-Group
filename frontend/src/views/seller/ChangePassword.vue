<template>
  <div class="wrap">
    <h3>修改密码</h3>
    <form class="form" @submit.prevent="onSubmit">
      <div class="row"><label>原密码</label><input v-model="form.oldPassword" type="password" /></div>
      <div class="row"><label>新密码</label><input v-model="form.newPassword" type="password" placeholder="6-20 位" /></div>
      <div class="row"><label>确认新密码</label><input v-model="form.confirmPassword" type="password" /></div>
      <div class="row"><button type="submit" :disabled="loading">确认修改</button></div>
    </form>
    <p v-if="msg" class="msg" :class="{ err: isErr }">{{ msg }}</p>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { changePassword } from '@/api/seller'

const router = useRouter()
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const loading = ref(false)
const msg = ref('')
const isErr = ref(false)

const flash = (t, err = false) => { msg.value = t; isErr.value = err; setTimeout(() => (msg.value = ''), 2500) }

const onSubmit = async () => {
  if (!form.oldPassword || !form.newPassword) return flash('请填写完整', true)
  if (form.newPassword.length < 6) return flash('新密码至少 6 位', true)
  if (form.newPassword !== form.confirmPassword) return flash('两次输入的新密码不一致', true)
  loading.value = true
  try {
    await changePassword(form)
    flash('修改成功,请重新登录')
    setTimeout(() => {
      localStorage.removeItem('seller_token')
      router.push('/seller/login')
    }, 1000)
  } catch (e) {
    flash(e.message || '修改失败', true)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.wrap { background: #fff; padding: 24px; max-width: 480px; }
.row { margin-bottom: 16px; }
label { display: block; margin-bottom: 6px; color: #666; font-size: 14px; }
input { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; box-sizing: border-box; }
button { background: #c7000b; color: #fff; border: none; padding: 10px 28px; cursor: pointer; }
button:disabled { opacity: .6; }
.msg { color: #67c23a; } .msg.err { color: #c7000b; }
</style>