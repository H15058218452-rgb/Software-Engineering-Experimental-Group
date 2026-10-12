<template>
  <div class="wrap">
    <h3>{{ isEdit ? '编辑商品' : '发布商品' }}</h3>
    <form class="form" @submit.prevent="onSubmit">
      <div class="row"><label>商品名称</label><input v-model.trim="form.name" placeholder="请输入商品名称" /></div>
      <div class="row"><label>价格(元)</label><input v-model.number="form.price" type="number" min="0" step="0.01" /></div>
      <div class="row"><label>库存</label><input v-model.number="form.stock" type="number" min="0" /></div>
      <div class="row"><label>分类</label>
        <select v-model="form.category">
          <option value="手机">手机</option><option value="笔记本">笔记本</option>
          <option value="平板">平板</option><option value="穿戴">穿戴</option>
          <option value="配件">配件</option><option value="其他">其他</option>
        </select>
      </div>
      <div class="row"><label>商品图片</label>
        <input type="file" accept="image/*" @change="onFile" />
        <img v-if="form.image" :src="form.image" class="preview" />
      </div>
      <div class="row"><label>商品描述</label><textarea v-model="form.description" rows="5"></textarea></div>
      <div class="row"><button type="submit" :disabled="loading">{{ isEdit ? '保存修改' : '立即发布' }}</button></div>
    </form>
    <p v-if="msg" class="msg" :class="{ err: isErr }">{{ msg }}</p>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publishGoods, updateGoods, getGoodsDetail, uploadImage } from '@/api/seller'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const msg = ref('')
const isErr = ref(false)

const form = reactive({ name: '', price: null, stock: null, category: '手机', image: '', description: '' })

const flash = (t, err = false) => { msg.value = t; isErr.value = err; setTimeout(() => (msg.value = ''), 2500) }

const onFile = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  try {
    const res = await uploadImage(file)
    form.image = res.data.url || res.data
  } catch {
    // 后端未提供上传接口时,本地预览兜底
    form.image = URL.createObjectURL(file)
  }
}

const onSubmit = async () => {
  if (!form.name || form.price === null || form.price < 0) { flash('请完整填写名称和价格', true); return }
  loading.value = true
  try {
    if (isEdit.value) await updateGoods(route.params.id, form)
    else await publishGoods(form)
    flash(isEdit.value ? '保存成功' : '发布成功')
    setTimeout(() => router.push('/seller/goods'), 600)
  } catch (e) {
    flash(e.message || '操作失败', true)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (!isEdit.value) return
  const res = await getGoodsDetail(route.params.id)
  Object.assign(form, res.data)
})
</script>

<style scoped>
.wrap { background: #fff; padding: 24px; max-width: 640px; }
.row { margin-bottom: 16px; }
label { display: block; margin-bottom: 6px; color: #666; font-size: 14px; }
input, select, textarea { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; box-sizing: border-box; font-size: 14px; }
.preview { width: 120px; height: 120px; object-fit: cover; margin-top: 8px; display: block; border: 1px solid #eee; }
button { background: #c7000b; color: #fff; border: none; padding: 10px 28px; font-size: 15px; cursor: pointer; }
button:disabled { opacity: .6; }
.msg { color: #67c23a; } .msg.err { color: #c7000b; }
</style>