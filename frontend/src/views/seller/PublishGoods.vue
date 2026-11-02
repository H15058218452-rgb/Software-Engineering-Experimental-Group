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
        <input v-model="form.price" type="number" min="0" step="0.01" placeholder="请输入价格，如 99.99" />
      </div>

      <div class="row">
        <label>库存数量</label>
        <input v-model.number="form.stock" type="number" min="1" placeholder="请输入库存数量，至少为 1" />
      </div>

      <div class="row">
        <label>所属类别</label>
        <select v-model="form.categoryId">
          <option value="" disabled>请选择二级商品类别</option>
          <template v-for="cat1 in categoryList" :key="cat1.id">
            <option v-for="cat2 in (cat1.children || [])" :key="cat2.id" :value="cat2.id">
              {{ cat1.name }} / {{ cat2.name }}
            </option>
          </template>
        </select>
      </div>

      <!-- 多图上传区域 -->
      <div class="row">
        <label>商品图片（最多 9 张）</label>
        <input type="file" accept="image/jpeg,image/png,image/webp" multiple @change="onFile" />
        <div class="tip">支持 JPG/PNG/WebP，单张不超过 5MB，第一张将作为主图</div>
        
        <div class="preview-list" v-if="form.images.length > 0">
          <div class="preview-item" v-for="(img, index) in form.images" :key="index">
            <img :src="img" />
            <div class="delete-btn" @click="removeImage(index)">×</div>
            <div class="main-tag" v-if="index === 0">主图</div>
          </div>
        </div>
      </div>

      <!-- ⚠️ 替换为富文本编辑器 -->
      <div class="row">
        <label>商品描述（富媒体）</label>
        <div style="border: 1px solid #dcdfe6; border-radius: 4px; z-index: 100;">
          <Toolbar :editor="editorRef" :defaultConfig="toolbarConfig" mode="default" style="border-bottom: 1px solid #dcdfe6;" />
          <Editor 
            v-model="form.description" 
            :defaultConfig="editorConfig" 
            mode="default" 
            style="height: 300px; overflow-y: hidden;" 
            @onCreated="handleCreated" 
          />
        </div>
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
import { reactive, ref, computed, onMounted, shallowRef, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publishProduct, updateGoods, getGoodsDetail, uploadImage, getCategoryTree } from '@/api/seller'
// ⚠️ 引入 WangEditor 组件和样式
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const msg = ref('')
const isErr = ref(false)
const categoryList = ref([])

const form = reactive({ name: '', price: '', stock: 1, categoryId: '', images: [], description: '' })

// ⚠️ 初始化富文本编辑器
const editorRef = shallowRef()
const toolbarConfig = {
  excludeKeys: ['group-video'] // 暂不开启视频，如果老师要求可以去掉这行
}
const editorConfig = { 
  placeholder: '请输入商品描述，可加粗排版、图文混排...',
  // 自定义图片上传，当用户点富文本里的图片按钮时，走你已有的 uploadImage 接口
  MENU_CONF: {
    uploadImage: {
      async customUpload(file, insertFn) {
        try {
          const res = await uploadImage(file)
          const url = res.data.url || res.data
          insertFn(url, '图片', url) // 将图片插入到富文本中
        } catch (error) {
          alert('富文本图片上传失败')
        }
      }
    }
  }
}

const handleCreated = (editor) => {
  editorRef.value = editor
}

// 组件销毁前，销毁编辑器实例
onBeforeUnmount(() => {
  if (editorRef.value) {
    editorRef.value.destroy()
  }
})

const flash = (t, err = false) => { msg.value = t; isErr.value = err; setTimeout(() => (msg.value = ''), 2500) }

const onFile = async (e) => {
  // ...（保持你原来的多图上传逻辑不变）...
  const files = e.target.files
  if (!files || files.length === 0) return
  if (form.images.length + files.length > 9) {
    flash('商品图片最多只能上传 9 张', true); e.target.value = ''; return
  }
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp']
  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    if (!allowedTypes.includes(file.type)) { flash(`图片 ${file.name} 格式不对`, true); continue }
    if (file.size / 1024 / 1024 > 5) { flash(`图片 ${file.name} 大于 5MB`, true); continue }
    try {
      const res = await uploadImage(file)
      form.images.push(res.data.url || res.data)
    } catch { flash('图片上传失败', true) }
  }
  e.target.value = ''
}

const removeImage = (index) => { form.images.splice(index, 1) }

const onSubmit = async () => {
  if (!form.name || !form.price) { flash('请完整填写名称和价格', true); return }
  const priceStr = String(form.price)
  if (!/^\d+(\.\d{1,2})?$/.test(priceStr) || Number(priceStr) <= 0) {
    flash('价格必须大于0，且最多两位小数', true); return
  }
  if (!form.stock || form.stock < 1) { flash('库存必须大于等于1', true); return }
  if (!form.categoryId) { flash('请选择商品类别', true); return }
  if (form.images.length === 0) { flash('请至少上传一张商品图片', true); return }
  
  // 校验富文本是否为空 (WangEditor 空状态是一个 <p><br></p>)
  if (!form.description || form.description === '<p><br></p>') {
    flash('请填写商品描述', true); return
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
  try {
    const catRes = await getCategoryTree()
    if (catRes.code === 0) categoryList.value = catRes.data || []
  } catch (e) { console.error('获取分类失败', e) }

  if (!isEdit.value) return
  const res = await getGoodsDetail(route.params.id)
  if (res.code === 0) {
    Object.assign(form, res.data)
    if (typeof form.images === 'string') form.images = form.images ? [form.images] : []
  }
})
</script>

<style scoped>
/* ... 保持你原来的样式不变 ... */
.wrap { background: #fff; padding: 24px; max-width: 640px; border-radius: 8px; }
.header-bar { margin-bottom: 16px; }
.row { margin-bottom: 16px; }
label { display: block; margin-bottom: 6px; color: #666; font-size: 14px; }
input, textarea, select { width: 100%; padding: 8px 10px; border: 1px solid #dcdfe6; box-sizing: border-box; font-size: 14px; border-radius: 4px; }
input:focus, textarea:focus, select:focus { outline: none; border-color: #c7000b; }
.tip { font-size: 12px; color: #999; margin-top: 5px; }
.preview-list { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 10px; }
.preview-item { position: relative; width: 100px; height: 100px; border: 1px solid #eee; border-radius: 4px; overflow: hidden; }
.preview-item img { width: 100%; height: 100%; object-fit: cover; }
.delete-btn { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; background: rgba(0, 0, 0, 0.6); color: white; border-radius: 50%; text-align: center; line-height: 18px; cursor: pointer; font-size: 14px; }
.delete-btn:hover { background: red; }
.main-tag { position: absolute; bottom: 0; left: 0; width: 100%; background: rgba(199, 0, 11, 0.8); color: white; font-size: 12px; text-align: center; padding: 2px 0; }
button { background: #c7000b; color: #fff; border: none; padding: 10px 28px; font-size: 15px; cursor: pointer; border-radius: 4px; width: 100%; }
button:disabled { opacity: .6; cursor: not-allowed; }
.msg { color: #67c23a; margin-top: 10px; text-align: center; }
.msg.err { color: #c7000b; }
</style>