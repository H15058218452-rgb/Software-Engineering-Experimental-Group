<template>
  <div>
    <div class="bar">
      <h3>意图管理</h3>
      <select v-model="status" @change="page = 1; load()">
        <option value="">全部状态</option>
        <option value="0">待处理</option>
        <option value="1">已同意</option>
        <option value="2">已拒绝</option>
        <option value="3">已完成</option>
      </select>
    </div>

    <table class="tbl">
      <thead>
        <tr><th>意图编号</th><th>商品</th><th>买家</th><th>出价</th><th>数量</th><th>状态</th><th>提交时间</th><th>操作</th></tr>
      </thead>
      <tbody>
        <tr v-for="it in list" :key="it.id">
          <td>{{ it.id }}</td>
          <td>{{ it.goodsName }}</td>
          <td>{{ it.buyerName }}</td>
          <td class="price">¥{{ it.price }}</td>
          <td>{{ it.quantity }}</td>
          <td>{{ statusText(it.status) }}</td>
          <td>{{ it.createTime }}</td>
          <td><router-link :to="`/seller/intents/${it.id}`">处理</router-link></td>
        </tr>
        <tr v-if="!list.length"><td colspan="8" class="empty">暂无意图</td></tr>
      </tbody>
    </table>

    <div class="pager">
      <button :disabled="page <= 1" @click="page--; load()">上一页</button>
      <span>{{ page }} / {{ totalPages }}</span>
      <button :disabled="page >= totalPages" @click="page++; load()">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getSellerIntents } from '@/api/seller'

const list = ref([])
const status = ref('')
const page = ref(1)
const size = 8
const totalPages = ref(1)

const statusText = (s) => ['待处理', '已同意', '已拒绝', '已完成'][s] || s

const load = async () => {
  const res = await getSellerIntents({ page: page.value, size, status: status.value })
  list.value = res.data.list || res.data.records || []
  totalPages.value = Math.max(1, Math.ceil((res.data.total || 0) / size))
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
select { padding: 6px 10px; }
.tbl { width: 100%; border-collapse: collapse; background: #fff; }
.tbl th, .tbl td { border: 1px solid #eee; padding: 10px; font-size: 14px; text-align: center; }
.price { color: #c7000b; }
.tbl a { color: #409eff; text-decoration: none; }
.empty { color: #999; padding: 30px 0; }
.pager { margin-top: 16px; text-align: center; }
.pager button { padding: 6px 14px; margin: 0 10px; }
</style>