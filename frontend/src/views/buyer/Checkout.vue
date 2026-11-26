<template>
  <div class="checkout-page">
    <BuyerHeader />

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <router-link to="/buyer/cart">购物车</router-link>
      <span class="sep">/</span>
      <span>确认订单</span>
    </div>

    <main class="main">
      <div v-if="loading" class="checkout-card state">加载中...</div>

      <div v-else-if="items.length === 0" class="checkout-card state empty">
        <el-empty description="没有待结算的商品，请回购物车重新选择" />
        <el-button type="danger" @click="router.push('/buyer/cart')">返回购物车</el-button>
      </div>

      <template v-else>
        <!-- 收货地址（C.2-02：默认带入默认地址，可切换到其他已保存地址） -->
        <section class="checkout-card">
          <div class="section-head">
            <h3 class="section-title">收货地址</h3>
            <div class="section-ops">
              <el-button link type="primary" @click="goAddressManage">管理收货地址</el-button>
              <el-button link type="primary" :disabled="!canAddAddress" @click="openAddressDialog">
                新增地址
              </el-button>
            </div>
          </div>

          <el-alert
            v-if="addressList.length === 0"
            type="warning"
            :closable="false"
            show-icon
            title="您还没有收货地址，无法提交订单，请先新增地址"
            class="no-address-tip"
          />

          <div v-else class="addr-pick">
            <div
              v-for="addr in addressList"
              :key="addr.addressId"
              class="addr-option"
              :class="{ active: selectedAddressId === addr.addressId }"
              @click="selectedAddressId = addr.addressId"
            >
              <div class="addr-line1">
                <span class="receiver">{{ addr.receiver }}</span>
                <span class="phone">{{ addr.phone }}</span>
                <el-tag v-if="addr.isDefault" size="small" type="danger" effect="plain">默认</el-tag>
              </div>
              <div class="addr-line2">{{ addr.region }} {{ addr.detail }}</div>
              <i v-if="selectedAddressId === addr.addressId" class="picked">
                <el-icon><Select /></el-icon>
              </i>
            </div>
          </div>
        </section>

        <!-- 商品明细 -->
        <section class="checkout-card">
          <div class="section-head">
            <h3 class="section-title">商品明细</h3>
            <span class="page-tip">共 {{ totalQuantity }} 件</span>
          </div>

          <div class="item-head">
            <span class="col-goods">商品</span>
            <span class="col-price">单价</span>
            <span class="col-qty">数量</span>
            <span class="col-subtotal">小计</span>
          </div>

          <div v-for="item in items" :key="item.productId" class="item-row">
            <span class="col-goods">
              <span class="thumb">
                <img v-if="item.image" :src="item.image" alt="商品图片" @error="onImgError" />
                <span v-else class="thumb-ph">{{ (item.name || '商').slice(0, 1) }}</span>
              </span>
              <span class="goods-name">{{ item.name }}</span>
            </span>
            <span class="col-price">{{ formatMoney(item.price) }}</span>
            <span class="col-qty">× {{ item.quantity }}</span>
            <span class="col-subtotal">{{ formatMoney(item.subtotal) }}</span>
          </div>

          <div class="total-bar">
            <span class="total-label">应付金额</span>
            <span class="total-price">{{ formatMoney(totalAmount) }}</span>
          </div>
          <p class="amount-tip">订单金额 = 商品快照单价 × 数量之和，无运费、无优惠</p>
        </section>

        <!-- 提交栏 -->
        <div class="submit-bar">
          <div class="submit-left">
            <span class="addr-brief">
              寄送至：{{ selectedAddress ? `${selectedAddress.region} ${selectedAddress.detail}` : '未选择收货地址' }}
            </span>
            <span class="addr-brief" v-if="selectedAddress">
              收货人：{{ selectedAddress.receiver }} {{ selectedAddress.phone }}
            </span>
          </div>
          <div class="submit-right">
            <span class="pay-label">应付：</span>
            <span class="pay-amount">{{ formatMoney(totalAmount) }}</span>
            <button
              class="submit-btn"
              :disabled="!canSubmit || submitting"
              @click="handleSubmit"
            >
              {{ submitting ? '提交中...' : '提交订单' }}
            </button>
          </div>
        </div>
      </template>
    </main>

    <!-- 新增地址弹窗（与地址管理页同规则：四项必填 + 手机号 11 位） -->
    <el-dialog v-model="addressDialogVisible" title="新增收货地址" width="520px" :close-on-click-modal="false">
      <el-form ref="addressFormRef" :model="addressForm" :rules="addressRules" label-width="88px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="addressForm.receiver" maxlength="20" placeholder="请填写收货人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="addressForm.phone" maxlength="11" placeholder="请填写 11 位手机号" />
        </el-form-item>
        <el-form-item label="所在地区" prop="region">
          <el-input v-model="addressForm.region" maxlength="50" placeholder="如：浙江省杭州市西湖区" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="addressForm.detail"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="如：文一西路 100 号 3 幢 502"
          />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="addressForm.isDefault" :disabled="addressList.length === 0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="addressDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="addressSaving" @click="handleAddAddress">保存</el-button>
      </template>
    </el-dialog>

    <footer class="footer">
      <div class="footer-inner">
        <span>© 2026 ShopMall. All Rights Reserved.</span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Select } from '@element-plus/icons-vue'
import { getCartList, getAddressList, createAddress, checkoutCart } from '@/api/buyer'
import { formatMoney } from '@/utils/format'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import BuyerHeader from '@/components/BuyerHeader.vue'

const router = useRouter()

const MAX_ADDRESS = 10

const loading = ref(true)
const submitting = ref(false)
const items = ref([])
const addressList = ref([])
const selectedAddressId = ref('')
const productIds = ref([])

const addressDialogVisible = ref(false)
const addressSaving = ref(false)
const addressFormRef = ref(null)
const addressForm = reactive({ receiver: '', phone: '', region: '', detail: '', isDefault: false })

const addressRules = {
  receiver: [{ required: true, message: '请填写收货人', trigger: 'blur' }],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号须为 11 位数字', trigger: 'blur' }
  ],
  region: [{ required: true, message: '请填写所在地区', trigger: 'blur' }],
  detail: [{ required: true, message: '请填写详细地址', trigger: 'blur' }]
}

const selectedAddress = computed(
  () => addressList.value.find(a => a.addressId === selectedAddressId.value) || null
)

const totalQuantity = computed(() =>
  items.value.reduce((sum, i) => sum + Number(i.quantity || 0), 0)
)

// 应付金额 = 商品快照单价 × 数量之和（无运费、无优惠）
const totalAmount = computed(() => {
  const sum = items.value.reduce((acc, i) => acc + Number(i.price || 0) * Number(i.quantity || 0), 0)
  return sum.toFixed(2)
})

const canAddAddress = computed(() => addressList.value.length < MAX_ADDRESS)
// 无地址时不允许提交订单（C.2-02）
const canSubmit = computed(() => !!selectedAddressId.value && items.value.length > 0)

function onImgError(e) {
  e.target.style.display = 'none'
}

/* ---------------- 数据加载 ---------------- */

// 从购物车带过来的勾选商品ID（刷新时仍可恢复）
function readPendingSelection() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEYS.PENDING_CHECKOUT)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed?.productIds) ? parsed.productIds.map(String) : []
  } catch (e) {
    return []
  }
}

function clearPendingSelection() {
  sessionStorage.removeItem(STORAGE_KEYS.PENDING_CHECKOUT)
}

async function loadData() {
  loading.value = true
  try {
    productIds.value = readPendingSelection()

    const [cartRes, addrRes] = await Promise.all([getCartList(), getAddressList()])
    const cartItems = cartRes?.list || []

    // 只保留本次勾选的商品
    items.value = productIds.value.length
      ? cartItems.filter(i => productIds.value.includes(String(i.productId)))
      : []

    addressList.value = addrRes?.list || []
    // 默认带入默认地址；没有默认地址时取第一条
    const def = addressList.value.find(a => a.isDefault)
    selectedAddressId.value = def?.addressId || addressList.value[0]?.addressId || ''
  } catch (e) {
    ElMessage.error(e.message || '加载结算信息失败')
    items.value = []
  } finally {
    loading.value = false
  }
}

async function loadAddressList() {
  try {
    const res = await getAddressList()
    addressList.value = res?.list || []
    if (!selectedAddressId.value && addressList.value.length > 0) {
      const def = addressList.value.find(a => a.isDefault)
      selectedAddressId.value = def?.addressId || addressList.value[0].addressId
    }
  } catch (e) {
    ElMessage.error(e.message || '获取地址列表失败')
  }
}

/* ---------------- 地址 ---------------- */

function goAddressManage() {
  router.push('/buyer/address')
}

function openAddressDialog() {
  if (!canAddAddress.value) {
    return ElMessage.warning('地址数量已达上限（10 个），请先在地址管理中删除不用的地址')
  }
  addressForm.receiver = ''
  addressForm.phone = ''
  addressForm.region = ''
  addressForm.detail = ''
  addressForm.isDefault = addressList.value.length === 0
  addressFormRef.value?.clearValidate()
  addressDialogVisible.value = true
}

async function handleAddAddress() {
  if (!addressFormRef.value) return
  try {
    await addressFormRef.value.validate()
  } catch (e) {
    return
  }

  addressSaving.value = true
  try {
    const created = await createAddress({
      receiver: addressForm.receiver.trim(),
      phone: addressForm.phone.trim(),
      region: addressForm.region.trim(),
      detail: addressForm.detail.trim(),
      isDefault: addressForm.isDefault
    })
    ElMessage.success('地址已新增')
    addressDialogVisible.value = false
    await loadAddressList()
    // 新地址自动选中，省一次点击
    if (created?.addressId) selectedAddressId.value = created.addressId
  } catch (e) {
    ElMessage.error(e.message || '保存失败，请重试')
  } finally {
    addressSaving.value = false
  }
}

/* ---------------- 提交订单 ---------------- */

async function handleSubmit() {
  if (submitting.value) return
  if (!selectedAddressId.value) {
    return ElMessage.warning('请先选择收货地址')
  }

  submitting.value = true
  try {
    // api 层内部：库存校验 → 建单（带 addressId）→ 已下单条目出车
    const res = await checkoutCart(productIds.value, selectedAddressId.value)
    clearPendingSelection()
    ElMessage.success(`下单成功，订单号 ${res.orderNo}`)
    // 下单后进入「待支付」，直接去支付页（C.1-01）
    router.replace(`/buyer/pay/${encodeURIComponent(res.orderNo)}`)
  } catch (e) {
    if (e.code === 40954) {
      ElMessage.error('您还没有收货地址，请先新增地址')
      await loadAddressList()
    } else if (e.code === 40924) {
      const shortage = e.data?.shortage || []
      const names = shortage.map(s => s.name).filter(Boolean).join('、')
      ElMessage.error(`库存不足：${names || '部分商品'}，请回购物车调整数量`)
    } else if (e.code === 40926) {
      ElMessage.error('订单中含已下架商品，请回购物车剔除后重新提交')
    } else if (e.code === 40953) {
      ElMessage.error('收货地址无效，请重新选择')
      await loadAddressList()
    } else {
      ElMessage.error(e.message || '提交订单失败，请重试')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.checkout-page {
  min-height: 100vh; display: flex; flex-direction: column;
  background: #f5f5f5; font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', sans-serif;
  color: #333;
}

/* 面包屑 */
.breadcrumb {
  max-width: 1200px; width: 100%; margin: 0 auto; padding: 16px 20px;
  font-size: 13px; color: #999; box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

/* 主体 */
.main {
  flex: 1; max-width: 1200px; width: 100%; margin: 0 auto; padding: 0 20px 40px;
  box-sizing: border-box; display: flex; flex-direction: column; gap: 16px;
}
.checkout-card { background: #fff; padding: 24px 32px; }
.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.section-title { font-size: 16px; font-weight: 500; margin: 0; }
.section-ops { display: flex; align-items: center; gap: 8px; }
.page-tip { font-size: 12px; color: #999; }

.no-address-tip { margin-bottom: 4px; }

/* 地址选择 */
.addr-pick { display: grid; grid-template-columns: repeat(auto-fill, minmax(330px, 1fr)); gap: 12px; }
.addr-option {
  position: relative; padding: 14px 16px; border: 1px solid #eee; cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s; background: #fafafa;
}
.addr-option:hover { border-color: #f5a3a9; }
.addr-option.active { border-color: #e60012; background: #fff7f7; box-shadow: 0 0 0 1px #e60012 inset; }
.addr-line1 { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
.receiver { font-size: 15px; font-weight: 500; }
.phone { font-size: 13px; color: #666; }
.addr-line2 { font-size: 13px; color: #666; line-height: 1.6; padding-right: 24px; }
.picked {
  position: absolute; right: 12px; bottom: 10px; color: #e60012; font-size: 18px;
  display: flex; align-items: center;
}

/* 商品明细 */
.item-head, .item-row { display: flex; align-items: center; gap: 12px; }
.item-head { font-size: 13px; color: #999; padding-bottom: 12px; border-bottom: 1px solid #f0f0f0; }
.item-row { padding: 16px 0; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
.col-goods { flex: 1; display: flex; align-items: center; gap: 14px; min-width: 0; }
.col-price { width: 110px; flex: 0 0 110px; }
.col-qty { width: 90px; flex: 0 0 90px; color: #666; }
.col-subtotal { width: 110px; flex: 0 0 110px; color: #e60012; font-weight: 500; }
.item-head .col-subtotal { color: #999; font-weight: normal; }

.thumb {
  width: 64px; height: 64px; flex: 0 0 64px; background: #fafafa;
  display: flex; align-items: center; justify-content: center;
}
.thumb img { width: 100%; height: 100%; object-fit: contain; }
.thumb-ph { color: #ccc; font-size: 20px; }
.goods-name {
  font-size: 15px; color: #333; overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical;
}

.total-bar {
  display: flex; align-items: center; justify-content: flex-end; gap: 12px;
  padding-top: 20px;
}
.total-label { font-size: 14px; color: #666; }
.total-price { font-size: 24px; color: #e60012; font-weight: bold; }
.amount-tip { text-align: right; font-size: 12px; color: #999; margin-top: 6px; }

/* 提交栏 */
.submit-bar {
  background: #fff; padding: 20px 32px; display: flex; align-items: center;
  justify-content: space-between; gap: 20px; position: sticky; bottom: 0;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);
}
.submit-left { display: flex; flex-direction: column; gap: 4px; font-size: 13px; color: #666; }
.addr-brief { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 620px; }
.submit-right { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.pay-label { font-size: 14px; color: #666; }
.pay-amount { font-size: 24px; color: #e60012; font-weight: bold; }
.submit-btn {
  width: 150px; height: 46px; border: none; background: #e60012; color: #fff;
  font-size: 16px; cursor: pointer; transition: background 0.2s;
}
.submit-btn:hover:not(:disabled) { background: #c5000f; }
.submit-btn:disabled { background: #ccc; cursor: not-allowed; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
