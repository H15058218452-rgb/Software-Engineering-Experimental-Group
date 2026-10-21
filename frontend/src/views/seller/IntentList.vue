<template>
  <div>
    <div class="bar">
      <h3>意图管理</h3>
      <!-- 筛选下拉框 -->
      <select v-model="status" @change="load">
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
          <td>{{ it.name || it.buyerName || '-' }}</td>
          <td>{{ it.phone || it.buyerPhone || '-' }}</td>
          <!-- 这里调用了下面写的格式化函数 -->
          <td>{{ formatTime(it.createdAt || it.createTime) }}</td>
          <td>{{ statusText(it.status ?? it.intentStatus) }}</td>
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
import { useRoute } from 'vue-router'
import { getSellerIntents, getCurrentProduct } from '@/api/seller' // 引入获取当前商品的接口

const route = useRoute()
const list = ref([])
const status = ref('')
const page = ref(1)
const size = 8
const totalPages = ref(1)

// 1. 状态文字映射
const statusText = (s) => {
  const map = {
    0: '排队中', 1: '交易中', 2: '交易成功',
    3: '已作废', 4: '已撤销', 5: '重新排队中', 6: '最终失败'
  }
  return map[s] ?? '未知'
}

// 2. 格式化时间函数：把 2026-10-21T15:07:03+08:00 变成 2026-10-21 15:07:03
const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  // 截取前 19 个字符，替换中间的 'T' 为空格
  return timeStr.substring(0, 19).replace('T', ' ')
}

const load = async () => {
  let productId = route.query.productId

  // 3. 如果是点击左侧菜单进来的，URL里没有 ID，就自动去查当前在售商品
  if (!productId) {
    try {
      const currentRes = await getCurrentProduct()
      if (currentRes.code === 0 && currentRes.data) {
        productId = currentRes.data.id // 拿到当前商品 ID
      } else {
        console.warn('当前没有在售商品，无法查看意向')
        return
      }
    } catch (error) {
      console.error('获取当前商品失败', error)
      return
    }
  }

  try {
    const res = await getSellerIntents(String(productId))
    if (res.code === 0) {
      list.value = res.data.list || res.data.records || []
      totalPages.value = Math.max(1, Math.ceil((res.data.total || 0) / size))
    }
  } catch (error) {
    console.error('获取意向列表失败：', error)
  }
}

onMounted(() => {
  load()
})
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