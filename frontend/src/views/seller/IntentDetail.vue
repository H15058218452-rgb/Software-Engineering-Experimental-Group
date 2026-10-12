<template>
  <div class="wrap" v-if="detail">
    <h3>意图详情</h3>
    <div class="grid">
      <div class="item"><span>意图编号</span>{{ detail.id }}</div>
      <div class="item"><span>状态</span>{{ statusText(detail.status) }}</div>
      <div class="item"><span>商品名称</span>{{ detail.goodsName }}</div>
      <div class="item"><span>商品图片</span><img :src="detail.goodsImage" /></div>
      <div class="item"><span>买家出价</span><b class="price">¥{{ detail.price }}</b></div>
      <div class="item"><span>购买数量</span>{{ detail.quantity }}</div>
      <div class="item"><span>买家</span>{{ detail.buyerName }}({{ detail.buyerPhone }})</div>
      <div class="item"><span>提交时间</span>{{ detail.createTime }}</div>
      <div class="item full"><span>买家留言</span>{{ detail.message || '无' }}</div>
    </div>

    <!-- 待处理:同意 / 拒绝 -->
    <div class="ops" v-if="detail.status === 0">
      <button class="ok" @click="audit(true)">同意该意图</button>
      <button class="no" @click="showReject = true">拒绝</button>
    </div>
    <!-- 已同意:发货 -->
    <div class="ops" v-else-if="detail.status === 1">
      <button class="ok" @click="ship">确认发货</button>
    </div>
    <!-- 已发货(用3表示完成前的可选状态自行调整) -->
    <div class="ops" v-else-if="detail.status === 4">
      <button class="ok" @click="finish">确认完成</button>
    </div>

    <div class="reject-box" v-if="showReject">
      <textarea v-model="rejectReason" rows="3" placeholder="请填写拒绝理由"></textarea>
      <button class="no" @click="audit(false)">确认拒绝</button>
      <button @click="showReject = false">取消</button>
    </div>
    <p v-if="msg" class="msg">{{ msg }}</p>
    <router-link class="back" to="/seller/intents">返回意图列表</router-link>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getSellerIntentDetail, auditIntent, shipIntent, finishIntent } from '@/api/seller'

const route = useRoute()
const detail = ref(null)
const showReject = ref(false)
const rejectReason = ref('')
const msg = ref('')

const statusText = (s) => ['待处理', '已同意', '已拒绝', '已完成', '已发货'][s] || s

const load = async () => {
  const res = await getSellerIntentDetail(route.params.id)
  detail.value = res.data
}

const audit = async (pass) => {
  await auditIntent(detail.value.id, { pass, reason: rejectReason.value })
  showReject.value = false
  msg.value = '处理成功'
  load()
}
const ship = async () => { await shipIntent(detail.value.id); msg.value = '已发货'; load() }
const finish = async () => { await finishIntent(detail.value.id); msg.value = '已完成'; load() }

onMounted(load)
</script>

<style scoped>
.wrap { background: #fff; padding: 24px; }
.grid { display: flex; flex-wrap: wrap; gap: 14px 40px; margin: 16px 0; }
.item { width: 40%; font-size: 14px; }
.item.full { width: 100%; }
.item span { color: #999; margin-right: 10px; }
.item img { width: 90px; height: 90px; object-fit: cover; }
.price { color: #c7000b; }
.ops button, .reject-box button { padding: 8px 22px; margin-right: 12px; border: none; cursor: pointer; background: #eee; }
.ops .ok, .reject-box .no { background: #c7000b; color: #fff; }
.reject-box textarea { width: 100%; margin-bottom: 10px; padding: 8px; box-sizing: border-box; }
.msg { color: #67c23a; }
.back { display: inline-block; margin-top: 18px; color: #409eff; text-decoration: none; font-size: 14px; }
</style>