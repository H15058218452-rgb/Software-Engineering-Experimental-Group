<template>
  <div>
    <div class="bar">
      <h3>意图管理</h3>
      <!-- 筛选下拉框：符合需求说明书中的意向状态枚举 -->
      <select v-model="status" @change="page = 1; load()">
        <option value="">全部状态</option>
        <option value="0">排队中</option>
        <option value="1">交易中</option>
        <option value="2">交易成功</option>
        <option value="3">已作废</option>
        <option value="4">已撤销</option>
        <option value="5">重新排队中</option>
        <option value="6">最终失败</option>
      </select>
    </div>

    <table class="tbl">
      <thead>
        <tr>
          <th>排队位次</th>
          <th>姓名</th>
          <th>电话</th>
          <th>提交时间</th>
          <th>交易结果</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="it in list" :key="it.id">
          <td>{{ it.queuePosition }}</td>
          <td>{{ it.buyerName }}</td>
          <td>{{ it.buyerPhone }}</td>
          <td>{{ it.createTime }}</td>
          <td>{{ statusText(it.intentStatus) }}</td>
        </tr>
        <tr v-if="!list.length"><td colspan="5" class="empty">暂无意图</td></tr>
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

// 状态文字映射表（与需求说明书的枚举值一一对应）
const statusText = (s) => {
  const map = {
    0: '排队中',
    1: '交易中',
    2: '交易成功',
    3: '已作废',
    4: '已撤销',
    5: '重新排队中',
    6: '最终失败'
  }
  return map[s] ?? '未知'
}

const load = async () => {
  const res = await getSellerIntents({ page: page.value, size, status: status.value })
  list.value = res.data.list || res.data.records || []
  totalPages.value = Math.max(1, Math.ceil((res.data.total || 0) / size))
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
select { padding: 6px 10px; border: 1px solid #dcdfe6; border-radius: 4px; }
.tbl { width: 100%; border-collapse: collapse; background: #fff; }
.tbl th, .tbl td { border: 1px solid #eee; padding: 10px; font-size: 14px; text-align: center; }
.empty { color: #999; padding: 30px 0; }
.pager { margin-top: 16px; text-align: center; }
.pager button { padding: 6px 14px; margin: 0 10px; cursor: pointer; }
</style>