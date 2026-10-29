<template>
  <div class="wrap">
    <div class="header-bar">
      <h3>{{ isEdit ? '编辑商品' : '发布商品' }}</h3>
    </div>

    <form class="form" @submit.prevent="onSubmit">
      <div class="row">
        <label>商品名称</label>
        <input v-model.trim="form.name" placeholder="请输入商品名称" />
      </div>

      <div class="row">
        <label>价格(元)</label>
        <input 
          v-model="form.price" 
          type="number" 
          min="0" 
          step="0.01" 
          placeholder="请输入价格，如 99.99" 
        />
      </div>

      <div class="row">
        <label>库存数量</label>
        <input 
          v-model.number="form.stock" 
          type="number" 
          min="1" 
          placeholder="请输入库存数量，至少为 1" 
        />
      </div>

      <!-- ⚠️ 修改：动态渲染后端返回的两级分类 -->
      <div class="row">
        <label>所属类别</label>
        <select v-model="form.categoryId">
          <option value="" disabled>请选择二级商品类别</option>
          <!-- 遍历一级分类 -->
          <template v-for="cat1 in categoryList" :key="cat1.id">
            <!-- 遍历二级分类 -->
            <option 
              v-for="cat2 in (cat1.children || [])" 
              :key="cat2.id" 
              :value="cat2.id"
            >
              {{ cat1.name }} / {{ cat2.name }}
            </option>
          </template>
        </select>
      </div>

      <div class="row">
        <label>商品图片</label>
        <input type="file" accept="image/*" @change="onFile" />
        <img v-if="form.image" :src="form.image" class="preview" />
        <div class="tip">支持 JPG/PNG/WebP，单张不超过 5MB</div>
      </div>

      <div class="row">
        <label>商品描述</label>
        <textarea v-model="form.description" rows="5" placeholder="请输入商品描述（10-2000字）"></textarea>
      </div>

      <div class="row">
        <button type="submit" :disabled="loading">
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
import { publishProduct, updateGoods, getGoodsDetail, uploadImage, getCategoryTree } from '@/api/seller'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const msg = ref('')
const isErr = ref(false)

// 存储真实的分类数据
const categoryList = ref([])

const form = reactive({ name: '', price: '', stock: 1, categoryId: '', image: '', description: '' })

const flash = (t, err = false) => { msg.value = t; isErr.value = err; setTimeout(() => (msg.value = ''), 2500) }

const onFile = async (e) => {
  const file = e.target.files[0]
  if (!file) return

  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  if (!allowedTypes.includes(file.type)) {
    flash('图片格式必须是 JPG/PNG/WebP', true)
    return
  }
  if (file.size / 1024 / 1024 > 5) {
    flash('图片大小不能超过 5MB', true)
    return
  }

  try {
    const res = await uploadImage(file)
    form.image = res.data.url || res.data
  } catch {
    form.image = URL.createObjectURL(file)
  }
}

const onSubmit = async () => {
  if (!form.name || !form.price) { 
    flash('请完整填写名称和价格', true)
    return 
  }

  const priceStr = String(form.price)
  if (!/^\d+(\.\d{1,2})?$/.test(priceStr) || Number(priceStr) <= 0) {
    flash('价格必须大于0，且最多两位小数', true)
    return
  }

  if (!form.stock || form.stock < 1) {
    flash('库存必须大于等于1', true)
    return
  }

  if (!form.categoryId) {
    flash('请选择商品类别', true)
    return
  }

  if (!form.image) {
    flash('请上传商品图片', true)
    return
  }

  loading.value = true
  try {
    if (isEdit.value) {
      await updateGoods(route.params.id, form)
    } else {
      // 注意：后端的 PublishRequest 里可能字段叫 categoryId
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
  // 1. 页面加载时，先拉取真实的两级分类列表
  try {
    const catRes = await getCategoryTree()
    if (catRes.code === 0) {
      categoryList.value = catRes.data || []
    }
  } catch (e) {
    console.error('获取分类失败，请检查数据库是否有数据', e)
    flash('获取分类列表失败，请先在后台添加分类', true)
  }

  // 2. 编辑模式回显
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
input, textarea, select { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; box-sizing: border-box; font-size: 14px; border-radius: 4px; }
input:focus, textarea:focus, select:focus { outline: none; border-color: #c7000b; }
.preview { width: 120px; height: 120px; object-fit: cover; margin-top: 8px; display: block; border: 1px solid #eee; border-radius: 4px; }
.tip { font-size: 12px; color: #999; margin-top: 5px; }
button { background: #c7000b; color: #fff; border: none; padding: 10px 28px; font-size: 15px; cursor: pointer; border-radius: 4px; width: 100%; }
button:disabled { opacity: .6; cursor: not-allowed; }
.msg { color: #67c23a; margin-top: 10px; text-align: center; }
.msg.err { color: #c7000b; }
</style>