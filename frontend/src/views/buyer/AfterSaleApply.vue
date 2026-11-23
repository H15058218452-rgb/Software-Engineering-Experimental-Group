<template>
  <div class="as-apply-page">
    <BuyerHeader />

    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <router-link to="/buyer/orders">我的订单</router-link>
      <span class="sep">/</span>
      <router-link :to="`/buyer/orders/${orderNo}`">订单详情</router-link>
      <span class="sep">/</span>
      <span>申请售后</span>
    </div>

    <main class="main">
      <div v-if="loading" class="card state">加载中...</div>

      <div v-else-if="!order || !item" class="card state empty">
        <el-empty description="订单或商品不存在，无法发起售后" />
        <el-button type="danger" @click="router.push('/buyer/orders')">返回我的订单</el-button>
      </div>

      <template v-else>
        <!-- 商品信息（售后粒度是商品，C.3-01） -->
        <section class="card">
          <h3 class="card-title">售后商品</h3>
          <div class="goods-block">
            <div class="goods-main">
              <p class="goods-name">{{ item.name }}</p>
              <p class="goods-meta">
                订单号：{{ order.orderNo }}　|　单价：{{ formatMoney(item.price) }}　|　购买数量：{{ item.quantity }}
              </p>
            </div>
            <div class="goods-right">
              <span class="goods-label">申请数量</span>
              <el-input-number
                v-model="form.quantity"
                :min="1"
                :max="Number(item.quantity)"
                size="small"
              />
            </div>
          </div>

          <el-alert
            v-if="ongoing"
            type="warning"
            :closable="false"
            show-icon
            class="tip-alert"
            :title="`该商品已有一条进行中的售后单（${afterSaleStatusText(ongoing.status, ongoing.statusText)}），请等待商家处理完成后再申请`"
          />
          <el-alert
            v-else-if="historyCount >= AFTER_SALE_MAX_TIMES"
            type="error"
            :closable="false"
            show-icon
            class="tip-alert"
            :title="`该商品累计售后已达 ${AFTER_SALE_MAX_TIMES} 次上限，无法再次申请`"
          />
        </section>

        <!-- 申请信息 -->
        <section class="card">
          <h3 class="card-title">问题描述</h3>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
            <el-form-item label="售后标题" prop="title">
              <el-input
                v-model="form.title"
                :maxlength="AFTER_SALE_TITLE_MAX"
                show-word-limit
                placeholder="一句话描述问题，如：键盘按键失灵"
              />
            </el-form-item>

            <el-form-item label="问题描述" prop="description">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="5"
                :maxlength="AFTER_SALE_DESC_MAX"
                show-word-limit
                placeholder="请详细描述商品问题、发生时间与已尝试的处理方式"
              />
            </el-form-item>

            <el-form-item label="上传图片">
              <div class="upload-block">
                <el-upload
                  v-model:file-list="fileList"
                  list-type="picture-card"
                  accept="image/jpeg,image/png,image/webp"
                  :limit="AFTER_SALE_IMAGE_RULE.maxCount"
                  :http-request="customUpload"
                  :before-upload="beforeUpload"
                  :on-success="onUploadSuccess"
                  :on-exceed="onExceed"
                >
                  <el-icon><Plus /></el-icon>
                </el-upload>
                <p class="upload-tip">
                  选填，最多 {{ AFTER_SALE_IMAGE_RULE.maxCount }} 张，单张不超过
                  {{ AFTER_SALE_IMAGE_RULE.maxSizeMB }}MB，支持 jpg / png / webp
                </p>
              </div>
            </el-form-item>
          </el-form>

          <div class="notice">
            <p>· 售后按商品维度提交，同一商品同时只能有一条进行中的售后单</p>
            <p>· 同一商品累计最多申请 3 次（含已拒绝、已撤销、已退款）</p>
            <p>· 提交后订单状态不变，商家处理进度可在「我的售后」中查看</p>
          </div>

          <div class="submit-bar">
            <el-button @click="router.back()">返回</el-button>
            <el-button
              type="danger"
              :loading="submitting"
              :disabled="!!ongoing || historyCount >= AFTER_SALE_MAX_TIMES"
              @click="handleSubmit"
            >
              提交申请
            </el-button>
          </div>
        </section>
      </template>
    </main>

    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 ShopMall. All Rights Reserved.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getOrderDetail, createAfterSale, uploadAfterSaleImage } from '@/api/buyer'
import { formatMoney } from '@/utils/format'
import {
  AFTER_SALE_IMAGE_RULE,
  AFTER_SALE_TITLE_MAX,
  AFTER_SALE_DESC_MAX,
  AFTER_SALE_MAX_TIMES,
  afterSaleStatusText,
  isAfterSaleOngoing
} from '@/constants/afterSaleStatus'
import { canApplyAfterSale } from '@/constants/orderStatus'
import BuyerHeader from '@/components/BuyerHeader.vue'

const route = useRoute()
const router = useRouter()

const orderNo = String(route.query.orderNo || '')
const productId = String(route.query.productId || '')

const loading = ref(true)
const submitting = ref(false)
const order = ref(null)
const fileList = ref([])
const formRef = ref(null)

const form = reactive({
  quantity: 1,
  title: '',
  description: ''
})

const rules = {
  title: [
    { required: true, message: '请填写售后标题', trigger: 'blur' },
    { max: AFTER_SALE_TITLE_MAX, message: `标题不能超过 ${AFTER_SALE_TITLE_MAX} 个字`, trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请填写问题描述', trigger: 'blur' },
    { max: AFTER_SALE_DESC_MAX, message: `问题描述不能超过 ${AFTER_SALE_DESC_MAX} 个字`, trigger: 'blur' }
  ]
}

// 当前商品
const item = computed(() => {
  const items = order.value?.items || []
  return items.find(i => String(i.productId) === productId) || null
})

// 该商品的售后历史
const productAfterSales = computed(() =>
  (order.value?.afterSales || []).filter(a => String(a.productId) === productId)
)

// 进行中的售后单（PENDING / WAIT_RETURN）
const ongoing = computed(() => productAfterSales.value.find(a => isAfterSaleOngoing(a.status)) || null)
const historyCount = computed(() => productAfterSales.value.length)

/* ---------------- 上传 ---------------- */

// 上传前校验：格式 / 大小 / 数量（40968 同口径）
function beforeUpload(file) {
  const { accept, maxSizeMB, maxCount } = AFTER_SALE_IMAGE_RULE
  if (!accept.includes(file.type)) {
    ElMessage.error('仅支持 jpg / png / webp 格式的图片')
    return false
  }
  if (file.size / 1024 / 1024 > maxSizeMB) {
    ElMessage.error(`单张图片不能超过 ${maxSizeMB}MB`)
    return false
  }
  if (fileList.value.length >= maxCount) {
    ElMessage.warning(`最多上传 ${maxCount} 张图片`)
    return false
  }
  return true
}

function onExceed() {
  ElMessage.warning(`最多上传 ${AFTER_SALE_IMAGE_RULE.maxCount} 张图片`)
}

async function customUpload(options) {
  const { file, onSuccess, onError } = options
  try {
    const res = await uploadAfterSaleImage(file)
    onSuccess(res, file)
  } catch (e) {
    ElMessage.error(e.message || '图片上传失败')
    onError(e)
  }
}

function onUploadSuccess(res, file) {
  // 把服务端返回的图片地址挂到 file，提交时取值
  file.url = res?.url || file.url
  file.response = res
}

/* ---------------- 提交 ---------------- */

async function loadOrder() {
  loading.value = true
  try {
    if (!orderNo || !productId) {
      order.value = null
      return
    }
    order.value = await getOrderDetail(orderNo)

    // 状态不满足 40 / 50 时不允许发起售后（C.3-01 / 40962）
    if (!canApplyAfterSale(order.value?.status)) {
      ElMessage.warning('当前订单状态不可发起售后，发货前退款请使用「取消订单」')
    }
  } catch (e) {
    ElMessage.error(e.message || '获取订单信息失败')
    order.value = null
  } finally {
    loading.value = false
  }
}

function collectImages() {
  return fileList.value
    .map(f => f.url || f.response?.url || '')
    .filter(Boolean)
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  const images = collectImages()
  if (images.length > AFTER_SALE_IMAGE_RULE.maxCount) {
    return ElMessage.error(`图片最多 ${AFTER_SALE_IMAGE_RULE.maxCount} 张`)
  }

  submitting.value = true
  try {
    const res = await createAfterSale({
      orderNo,
      productId,
      quantity: Number(form.quantity),
      title: form.title.trim(),
      description: form.description.trim(),
      images
    })
    ElMessage.success('售后申请已提交，请等待商家处理')
    router.replace(`/buyer/after-sales/${encodeURIComponent(res.afterSaleNo)}`)
  } catch (e) {
    if (e.code === 40964 || e.code === 40965 || e.code === 40962) {
      ElMessage.warning(e.message)
      await loadOrder()
    } else if (e.code === 40963) {
      ElMessage.error(e.message || '售后数量超过购买数量')
    } else {
      ElMessage.error(e.message || '提交失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadOrder)
</script>

<style scoped>
.as-apply-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

.breadcrumb {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 16px 20px;
  font-size: 13px; color: #999; box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

.main {
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px;
  box-sizing: border-box; display: flex; flex-direction: column; gap: 16px;
}
.card { background: #fff; padding: 24px 32px; }
.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

.card-title { font-size: 16px; font-weight: 500; margin: 0 0 18px; }

/* 商品 */
.goods-block { display: flex; align-items: center; justify-content: space-between; gap: 24px; }
.goods-name { font-size: 15px; margin-bottom: 8px; }
.goods-meta { font-size: 13px; color: #999; }
.goods-right { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.goods-label { font-size: 14px; color: #666; }
.tip-alert { margin-top: 18px; }

/* 上传 */
.upload-block { display: flex; flex-direction: column; gap: 8px; }
.upload-tip { font-size: 12px; color: #999; }

.notice {
  margin-top: 8px; padding: 14px 18px; background: #fafafa;
  font-size: 12px; color: #999; line-height: 2;
}

.submit-bar {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  margin-top: 24px; padding-top: 20px; border-top: 1px solid #f2f2f2;
}

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
