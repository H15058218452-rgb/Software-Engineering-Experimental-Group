<template>
  <div class="wrap">
    <div class="header-bar">
      <h3>{{ isEdit ? '编辑商品' : '发布商品' }}</h3>
      <!-- 提示信息 -->
      <el-alert 
        v-if="hasProduct && !isEdit" 
        title="当前已有在售商品，请先下架后再发布新商品" 
        type="warning" 
        show-icon 
        :closable="false" 
        style="margin-bottom: 16px;" 
      />
    </div>

    <form class="form" @submit.prevent="onSubmit">
      <div class="row">
        <label>商品名称</label>
        <input v-model.trim="form.name" placeholder="请输入商品名称" :disabled="hasProduct && !isEdit" />
      </div>

      <div class="row">
        <label>价格(元)</label>
        <input 
          v-model="form.price" 
          type="number" 
          min="0" 
          step="0.01" 
          placeholder="请输入价格，如 99.99" 
          :disabled="hasProduct && !isEdit"
        />
      </div>

      <div class="row">
        <label>商品图片</label>
        <input type="file" accept="image/*" @change="onFile" :disabled="hasProduct && !isEdit" />
        <img v-if="form.image" :src="form.image" class="preview" />
        <div class="tip">支持 JPG/PNG/WebP，单张不超过 5MB</div>
      </div>

      <div class="row">
        <label>商品描述</label>
        <textarea v-model="form.description" rows="5" placeholder="请输入商品描述（10-2000字）" :disabled="hasProduct && !isEdit"></textarea>
      </div>

      <div class="row">
        <button type="submit" :disabled="loading || (hasProduct && !isEdit)">
          {{ loading ? '提交中...' : (isEdit ? '保存修改' : '立即发布') }}
        </button>
      </div>
    </form>
    <p v-if="msg" class="msg" :class="{ err: isErr }">{{ msg }}</p>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
// 引入需要的接口，注意这里用的是 publishProduct（和 api/seller.js 对齐）
import { publishProduct, updateGoods, getGoodsDetail, uploadImage, getCurrentProduct } from '@/api/seller'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const msg = ref('')
const isErr = ref(false)
const hasProduct = ref(false) // 当前是否已有在售商品

// 只保留基线要求的字段
const form = reactive({ name: '', price: '', image: '', description: '' })

const flash = (t, err = false) => { msg.value = t; isErr.value = err; setTimeout(() => (msg.value = ''), 2500) }

const onFile = async (e) => {
  const file = e.target.files[0]
  if (!file) return

  // 1. 图片格式和大小校验
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    flash('图片格式必须是 JPG/PNG/WebP', true)
    return
  }
  if (file.size / 1024 / 1024 > 5) {
    flash('图片大小不能超过 5MB', true)
    return
  }

  // 2. 上传
  try {
    const res = await uploadImage(file)
    form.image = res.data.url || res.data
  } catch {
    // 后端未提供上传接口时，本地预览兜底
    form.image = URL.createObjectURL(file)
  }
}

const onSubmit = async () => {
  // 1. 必填校验
  if (!form.name || !form.price) { 
    flash('请完整填写名称和价格', true)
    return 
  }

  // 2. 价格格式校验（>0，最多两位小数）
  const priceStr = String(form.price)
  if (!/^\d+(\.\d{1,2})?$/.test(priceStr) || Number(priceStr) <= 0) {
    flash('价格必须大于0，且最多两位小数', true)
    return
  }

  // 3. 图片必填校验
  if (!form.image) {
    flash('请上传商品图片', true)
    return
  }

  loading.value = true
  try {
    if (isEdit.value) {
      await updateGoods(route.params.id, form)
    } else {
      await publishProduct(form)
    }
    flash(isEdit.value ? '保存成功' : '发布成功')
    setTimeout(() => router.push('/seller/goods'), 600)
  } catch (e) {
    flash(e.message || '操作失败', true)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 1. 检查当前是否有在售商品
  const resCurrent = await getCurrentProduct()
  if (resCurrent.code === 0 && resCurrent.data) {
    hasProduct.value = true
  }

  // 2. 如果是编辑模式，加载商品详情
  if (!isEdit.value) return
  const res = await getGoodsDetail(route.params.id)
  if (res.code === 0) {
    Object.assign(form, res.data)
  }
})
</script>

<style scoped>
.wrap { background: #fff; padding: 24px; max-width: 640px; border-radius: 8px; }
.header-bar { margin-bottom: 16px; }
.row { margin-bottom: 16px; }
label { display: block; margin-bottom: 6px; color: #666; font-size: 14px; }
input, textarea { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; box-sizing: border-box; font-size: 14px; border-radius: 4px; }
input:focus, textarea:focus { outline: none; border-color: #c7000b; }
input:disabled, textarea:disabled { background-color: #f5f7fa; cursor: not-allowed; }
.preview { width: 120px; height: 120px; object-fit: cover; margin-top: 8px; display: block; border: 1px solid #eee; border-radius: 4px; }
.tip { font-size: 12px; color: #999; margin-top: 5px; }
button { background: #c7000b; color: #fff; border: none; padding: 10px 28px; font-size: 15px; cursor: pointer; border-radius: 4px; width: 100%; }
button:disabled { opacity: .6; cursor: not-allowed; }
.msg { color: #67c23a; margin-top: 10px; text-align: center; }
.msg.err { color: #c7000b; }
</style>