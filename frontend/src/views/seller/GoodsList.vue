<template>
  <div>
    <div class="bar">
      <h3>商品管理</h3>
      <router-link class="btn-add" to="/seller/publish">+ 发布商品</router-link>
    </div>

    <table class="tbl">
      <thead>
        <tr><th>图片</th><th>名称</th><th>价格</th><th>库存</th><th>状态</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="g in list" :key="g.id">
          <td><img :src="g.image" alt="" /></td>
          <td>{{ g.name }}</td>
          <td class="price">¥{{ g.price }}</td>
          <td>{{ g.stock }}</td>
          <td>{{ g.status === 1 ? '在售' : '已下架' }}</td>
          <td class="ops">
            <a @click="toggle(g)">{{ g.status === 1 ? '下架' : '上架' }}</a>
            <router-link :to="`/seller/edit/${g.id}`">编辑</router-link>
            <a class="del" @click="remove(g)">删除</a>
          </td>
        </tr>
        <tr v-if="!list.length"><td colspan="6" class="empty">暂无商品</td></tr>
      </tbody>
    </table>

    <div class="pager">
      <button :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="page++; load()">下一页</button>
    </div>
    <p v-if="msg" class="msg">{{ msg }}</p>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getMyGoods, deleteGoods, toggleGoodsStatus } from '@/api/seller'

const list = ref([])
const page = ref(1)
const size = 8
const total = ref(0)
const totalPages = ref(1)
const msg = ref('')

const flash = (t) => { msg.value = t; setTimeout(() => (msg.value = ''), 2000) }

const load = async () => {
  const res = await getMyGoods({ page: page.value, size })
  list.value = res.data.list || res.data.records || []
  total.value = res.data.total || 0
  totalPages.value = Math.max(1, Math.ceil(total.value / size))
}

const toggle = async (g) => {
  await toggleGoodsStatus(g.id, g.status === 1 ? 0 : 1)
  flash('操作成功'); load()
}

const remove = async (g) => {
  if (!confirm(`确定删除商品「${g.name}」吗?`)) return
  await deleteGoods(g.id)
  flash('删除成功'); load()
}

onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.btn-add { background: #c7000b; color: #fff; padding: 8px 16px; text-decoration: none; font-size: 14px; }
.tbl { width: 100%; border-collapse: collapse; background: #fff; }
.tbl th, .tbl td { border: 1px solid #eee; padding: 10px 12px; font-size: 14px; text-align: center; }
.tbl img { width: 56px; height: 56px; object-fit: cover; }
.price { color: #c7000b; }
.ops a { margin: 0 6px; color: #409eff; cursor: pointer; text-decoration: none; }
.ops .del { color: #c7000b; }
.empty { color: #999; padding: 30px 0; }
.pager { margin-top: 16px; text-align: center; }
.pager button { padding: 6px 14px; margin: 0 10px; cursor: pointer; }
.msg { color: #67c23a; margin-top: 10px; }
</style>