<template>
  <div class="intent-page">
    <!-- 主头部 -->
    <header class="header">
      <div class="header-inner">
        <div class="logo">
          <img :src="logoImg" alt="ShopMall" @click="goHome" />
        </div>
        <nav class="nav">
          <router-link to="/buyer/goods" class="nav-item">首页</router-link>
        </nav>
        <router-link to="/seller/login" class="seller-entry">卖家入口</router-link>
      </div>
    </header>

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <router-link to="/buyer/intent/query">意向管理</router-link>
      <span class="sep">/</span>
      <span>意向详情</span>
    </div>

    <!-- 主体 -->
    <main class="main">
      <div v-if="loading" class="state">加载中...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>

      <div v-else-if="intent" class="detail-card">
        <!-- 顶部状态条 -->
        <div class="status-bar" :class="statusClass">
          <span class="status-text">{{ statusText }}</span>
        </div>

        <!-- 排队中 / 重新排队中 -->
        <template v-if="canOperate">
          <div class="card-body">
            <h2 class="card-title">意向详情</h2>

            <div class="info-list">
              <div class="info-row">
                <span class="label">商品名称</span>
                <span class="value">{{ intent.productName || '加载中...' }}</span>
              </div>
              <div class="info-row">
                <span class="label">商品价格</span>
                <span class="value price">{{ intent.productPrice ? formatMoney(intent.productPrice) : '--' }}</span>
              </div>
              <div class="info-row">
                <span class="label">意向状态</span>
                <span class="value">{{ statusText }}</span>
              </div>
              <div class="info-row">
                <span class="label">当前排队位次</span>
                <span class="value">第 {{ intent.queuePosition }} 位</span>
              </div>
              <div class="info-row">
                <span class="label">提交时间</span>
                <span class="value">{{ formatTime(intent.queuedAt) }}</span>
              </div>
              <div class="info-row">
                <span class="label">买家姓名</span>
                <span class="value">{{ intent.name }}</span>
              </div>
              <div class="info-row">
                <span class="label">联系电话</span>
                <span class="value">{{ intent.phone }}</span>
              </div>
            </div>

            <div v-if="isRequeued" class="notice">
              交易暂未完成，您的意向已重新进入排队队列，原口令码继续有效
            </div>

            <div class="action-row">
              <button class="btn-secondary" @click="openEdit">修改联系信息</button>
              <button class="btn-danger" @click="openCancel">撤销购买意向</button>
            </div>
          </div>
        </template>

        <!-- 正在交易 -->
        <template v-else-if="isTrading">
          <div class="card-body">
            <div class="icon-trading">⏳</div>
            <h2 class="card-title center">当前已进入交易流程</h2>
            <p class="center tip">请等待卖家确认交易结果</p>

            <div class="info-list">
              <div class="info-row">
                <span class="label">商品名称</span>
                <span class="value">{{ intent.productName || '加载中...' }}</span>
              </div>
              <div class="info-row">
                <span class="label">商品价格</span>
                <span class="value price">{{ intent.productPrice ? formatMoney(intent.productPrice) : '--' }}</span>
              </div>
              <div class="info-row">
                <span class="label">买家姓名</span>
                <span class="value">{{ intent.name }}</span>
              </div>
              <div class="info-row">
                <span class="label">联系电话</span>
                <span class="value">{{ intent.phone }}</span>
              </div>
            </div>

            <p class="notice-warning">
              交易过程中无法修改信息，也不能自行撤销。如有问题请联系卖家。
            </p>
          </div>
        </template>

        <!-- 已失效 -->
        <template v-else>
          <div class="card-body">
            <div class="icon-invalid">✕</div>
            <h2 class="card-title center">当前购买意向已失效</h2>
            <p class="center tip">无法继续管理</p>

            <div class="info-list">
              <div class="info-row">
                <span class="label">商品名称</span>
                <span class="value">{{ intent.productName || '加载中...' }}</span>
              </div>
              <div class="info-row">
                <span class="label">最终状态</span>
                <span class="value">{{ statusText }}</span>
              </div>
            </div>

            <div class="action-row single">
              <router-link to="/buyer/goods" class="btn-primary">
                返回首页
              </router-link>
            </div>
          </div>
        </template>
      </div>
    </main>

    <!-- 底部 -->
    <footer class="footer">
      <div class="footer-inner">
        <router-link to="/buyer/intent/query" class="footer-link">意向管理</router-link>
      </div>
    </footer>

    <!-- 修改联系信息弹窗 -->
    <transition name="modal-fade">
      <div v-if="showEdit" class="modal-mask" @click.self="showEdit = false">
        <div class="modal">
          <div class="modal-header">
            <h3>修改联系信息</h3>
            <button class="close-btn" @click="showEdit = false">×</button>
          </div>

          <div class="modal-body">
            <div class="form-item">
              <div class="form-row">
                <label class="form-label">买家姓名 <span class="required">*</span></label>
                <input
                  v-model="editForm.name"
                  type="text"
                  class="form-input"
                  maxlength="20"
                  @input="editErrors.name = ''"
                />
              </div>
              <div v-if="editErrors.name" class="error-tip">{{ editErrors.name }}</div>
            </div>

            <div class="form-item">
              <div class="form-row">
                <label class="form-label">联系电话 <span class="required">*</span></label>
                <input
                  v-model="editForm.phone"
                  type="text"
                  class="form-input"
                  maxlength="11"
                  @input="editErrors.phone = ''"
                />
              </div>
              <div v-if="editErrors.phone" class="error-tip">{{ editErrors.phone }}</div>
            </div>

            <p class="edit-notice">修改联系信息不会影响您的排队顺序</p>
          </div>

          <div class="modal-footer">
            <button class="btn-cancel" @click="showEdit = false">取消</button>
            <button class="btn-submit" :disabled="saving" @click="handleSave">
              {{ saving ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 撤销确认弹窗 -->
    <transition name="modal-fade">
      <div v-if="showCancel" class="modal-mask" @click.self="showCancel = false">
        <div class="modal small">
          <div class="modal-header">
            <h3>确认撤销</h3>
            <button class="close-btn" @click="showCancel = false">×</button>
          </div>

          <div class="modal-body">
            <p class="cancel-text">确认撤销本次购买意向？撤销后将退出排队，无法恢复。</p>
          </div>

          <div class="modal-footer">
            <button class="btn-cancel" @click="showCancel = false">再想想</button>
            <button class="btn-danger" :disabled="cancelling" @click="handleCancel">
              {{ cancelling ? '撤销中...' : '确认撤销' }}
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Toast -->
    <Teleport to="body">
      <transition name="toast-fade">
        <div v-if="toast" class="toast">{{ toast }}</div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { queryIntent, updateIntent, cancelIntent, getCurrentProduct } from '@/api/buyer'
import { STORAGE_KEYS } from '@/constants/storageKeys'
import { INTENT_STATUS, INTENT_STATUS_TEXT } from '@/constants/intentStatus'
import { formatMoney, formatTime } from '@/utils/format'
import logoImg from '@/assets/logo.png'

const router = useRouter()
const intent = ref(null)
const loading = ref(false)
const error = ref('')

const showEdit = ref(false)
const editForm = ref({ name: '', phone: '' })
const editErrors = ref({ name: '', phone: '' })
const saving = ref(false)

const showCancel = ref(false)
const cancelling = ref(false)

const toast = ref('')

function showToast(msg, duration = 2000) {
  toast.value = msg
  setTimeout(() => (toast.value = ''), duration)
}

// 状态派生
const statusText = computed(() => {
  if (!intent.value) return ''
  return INTENT_STATUS_TEXT[intent.value.intentStatus] || '未知'
})

const canOperate = computed(() => {
  const s = intent.value?.intentStatus
  return s === INTENT_STATUS.QUEUING || s === INTENT_STATUS.REQUEUED
})

const isTrading = computed(() => {
  return intent.value?.intentStatus === INTENT_STATUS.TRADING
})

const isRequeued = computed(() => {
  return intent.value?.intentStatus === INTENT_STATUS.REQUEUED
})

const statusClass = computed(() => {
  const s = intent.value?.intentStatus
  if (s === INTENT_STATUS.TRADING) return 'status-trading'
  if (s === INTENT_STATUS.SUCCESS) return 'status-success'
  if (
    s === INTENT_STATUS.VOID ||
    s === INTENT_STATUS.CANCELLED ||
    s === INTENT_STATUS.FINAL_FAILED
  )
    return 'status-invalid'
  return 'status-queuing'
})

// 加载意向
async function loadIntent() {
  const code = localStorage.getItem(STORAGE_KEYS.INTENT_CODE)
  if (!code) {
    error.value = '口令码已失效，无对应意向信息'
    return
  }

  loading.value = true
  error.value = ''
  try {
    // 1. 查询意向详情
    const res = await queryIntent(code)
    
    // 2. 临时补救：因为后端没返回商品名称和价格，顺手查一下当前商品
    try {
      const product = await getCurrentProduct()
      if (product) {
        res.productName = product.name
        res.productPrice = product.price
      }
    } catch (err) {
      // 如果查不到当前商品（比如商品下架了），就忽略，留空
    }

    intent.value = res
  } catch (e) {
    if (e.code === 40905) {
      error.value = '口令码已失效，无对应意向信息'
    } else {
      error.value = e.message || '加载失败，请重试'
    }
  } finally {
    loading.value = false
  }
}

// 打开编辑弹窗
function openEdit() {
  editForm.value = {
    name: intent.value.name,
    phone: intent.value.phone
  }
  editErrors.value = { name: '', phone: '' }
  showEdit.value = true
}

// 保存修改（已解开真实接口）
async function handleSave() {
  if (saving.value) return

  const name = (editForm.value.name || '').trim()
  const phone = (editForm.value.phone || '').trim()
  editErrors.value = { name: '', phone: '' }

  if (!name) editErrors.value.name = '姓名不能为空，请填写买家姓名'
  else if (name.length > 20) editErrors.value.name = '姓名长度不能超过20个字符'
  if (!phone) editErrors.value.phone = '联系电话不能为空，请填写联系电话'
  else if (!/^1[3-9]\d{9}$/.test(phone))
    editErrors.value.phone = '请填写正确的 11 位手机号'

  if (editErrors.value.name || editErrors.value.phone) return

  saving.value = true
  try {
    // 真实调用后端接口
    await updateIntent({ name, phone })

    // 成功后更新本地数据并提示
    intent.value.name = name
    intent.value.phone = phone
    showEdit.value = false
    showToast('联系信息修改成功，您的排队顺序不变')
  } catch (e) {
    editErrors.value.name = e.message || '保存失败，请重试'
  } finally {
    saving.value = false
  }
}

// 打开撤销确认
function openCancel() {
  showCancel.value = true
}

// 撤销（已解开真实接口）
async function handleCancel() {
  if (cancelling.value) return
  cancelling.value = true
  try {
    // 真实调用后端接口撤销
    await cancelIntent()

    // 成功后关闭弹窗并跳转
    showCancel.value = false
    showToast('购买意向已撤销，您已退出排队')
    setTimeout(() => {
      router.push('/buyer/goods')
    }, 1500)
  } catch (e) {
    showCancel.value = false
    showToast(e.message || '撤销失败，请重试')
  } finally {
    cancelling.value = false
  }
}

function goHome() {
  router.push('/buyer/goods')
}

onMounted(loadIntent)
</script>

<style scoped>
/* 此处样式与原来保持一致，只加了 .action-row 的 white-space: nowrap; */
.intent-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #333;
}

/* 主头部 */
.header { background: #fff; }
.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 24px;
}
.logo { display: flex; align-items: center; cursor: pointer; }
.logo img { height: 28px; width: auto; object-fit: contain; }
.nav { flex: 1; display: flex; gap: 32px; margin-left: 20px; }
.nav-item {
  color: #333;
  text-decoration: none;
  font-size: 15px;
  transition: color 0.2s;
}
.nav-item:hover { color: #e60012; }
.seller-entry {
  color: #666;
  text-decoration: none;
  font-size: 14px;
}

/* 面包屑 */
.breadcrumb {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 20px;
  font-size: 13px;
  color: #999;
  box-sizing: border-box;
}
.breadcrumb a { color: #666; text-decoration: none; }
.breadcrumb a:hover { color: #e60012; }
.sep { margin: 0 6px; }

/* 主体 */
.main {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding: 40px 20px 60px;
}
.state {
  text-align: center;
  color: #999;
  padding: 80px 0;
  font-size: 16px;
}
.state.error { color: #e60012; }

/* 卡片 */
.detail-card {
  width: 720px;
  max-width: 100%;
  background: #fff;
  box-sizing: border-box;
}

/* 状态条 */
.status-bar {
  padding: 16px 40px;
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 1px;
}
.status-queuing {
  background: #e60012;
  color: #fff;
}
.status-trading {
  background: #333;
  color: #fff;
}
.status-success {
  background: #67c23a;
  color: #fff;
}
.status-invalid {
  background: #999;
  color: #fff;
}

.card-body {
  padding: 32px 40px 40px;
}
.card-title {
  font-size: 20px;
  font-weight: 500;
  color: #333;
  margin: 0 0 24px;
}
.card-title.center { text-align: center; }
.center { text-align: center; }
.tip {
  font-size: 13px;
  color: #999;
  margin: 0 0 24px;
}

/* 图标 */
.icon-trading,
.icon-invalid {
  width: 64px;
  height: 64px;
  line-height: 64px;
  margin: 0 auto 16px;
  border-radius: 50%;
  text-align: center;
  font-size: 32px;
  color: #fff;
}
.icon-trading { background: #333; }
.icon-invalid { background: #999; }

/* 信息列表 */
.info-list {
  font-size: 14px;
  border-top: 1px solid #f0f0f0;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid #f0f0f0;
}
.info-row .label { color: #999; }
.info-row .value { color: #333; }
.info-row .value.price {
  color: #e60012;
  font-weight: bold;
  font-size: 16px;
}

.notice {
  background: #fafafa;
  border-left: 4px solid #e60012;
  padding: 12px 16px;
  font-size: 13px;
  color: #333;
  margin-top: 16px;
  line-height: 1.6;
}
.notice-warning {
  background: #fafafa;
  border-left: 4px solid #333;
  padding: 12px 16px;
  font-size: 13px;
  color: #666;
  margin-top: 20px;
  line-height: 1.6;
}

/* 操作按钮 */
.action-row {
  display: flex;
  gap: 12px;
  margin-top: 28px;
  white-space: nowrap; /* 新增，防止按钮文字换行 */
}
.action-row.single { justify-content: center; }
.btn-secondary,
.btn-danger,
.btn-primary {
  flex: 1;
  height: 44px;
  border: none;
  font-size: 15px;
  letter-spacing: 1px;
  cursor: pointer;
  border-radius: 0;
  transition: all 0.2s;
  text-align: center;
  line-height: 44px;
  text-decoration: none;
  display: inline-block;
}
.btn-secondary {
  background: #f5f5f5;
  color: #333;
}
.btn-secondary:hover { background: #eee; }
.btn-danger {
  background: #e60012;
  color: #fff;
}
.btn-danger:hover:not(:disabled) { background: #c5000f; }
.btn-danger:disabled { background: #ccc; cursor: not-allowed; }
.btn-primary {
  background: #e60012;
  color: #fff;
  flex: 0 0 200px;
}
.btn-primary:hover { background: #c5000f; }

/* 弹窗 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.modal {
  background: #fff;
  width: 460px;
  max-width: 92vw;
  border-radius: 0;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}
.modal.small { width: 380px; }

.modal-header {
  position: relative;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  text-align: center;
}
.modal-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  color: #333;
}
.close-btn {
  position: absolute;
  top: 50%;
  right: 20px;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  font-size: 26px;
  line-height: 1;
  color: #999;
  cursor: pointer;
  padding: 0;
}
.close-btn:hover { color: #e60012; }

.modal-body { padding: 24px; }
.form-item { margin-bottom: 20px; }
.form-item:last-child { margin-bottom: 0; }
.form-row { display: flex; align-items: center; }
.form-label {
  flex: 0 0 90px;
  font-size: 14px;
  color: #333;
  text-align: right;
  padding-right: 12px;
  box-sizing: border-box;
}
.required { color: #e60012; }
.form-input {
  flex: 1;
  height: 40px;
  border: 1px solid #e5e5e5;
  background: #fafafa;
  padding: 0 12px;
  font-size: 14px;
  color: #333;
  outline: none;
  border-radius: 0;
  box-sizing: border-box;
  transition: border-color 0.2s, background 0.2s;
}
.form-input:focus {
  border-color: #e60012;
  background: #fff;
}
.error-tip {
  font-size: 12px;
  color: #e60012;
  margin-top: 6px;
  margin-left: 102px;
  line-height: 1.4;
}
.edit-notice {
  font-size: 12px;
  color: #999;
  margin: 16px 0 0;
  text-align: center;
}
.cancel-text {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  margin: 0;
  text-align: center;
}

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 16px 24px 24px;
  justify-content: flex-end;
  border-top: 1px solid #f0f0f0;
}
.btn-cancel {
  height: 40px;
  padding: 0 24px;
  font-size: 14px;
  border: none;
  background: #f5f5f5;
  color: #333;
  cursor: pointer;
  border-radius: 0;
}
.btn-cancel:hover { background: #eee; }
.btn-submit {
  height: 40px;
  padding: 0 24px;
  font-size: 14px;
  border: none;
  background: #e60012;
  color: #fff;
  cursor: pointer;
  border-radius: 0;
  transition: background 0.2s;
}
.btn-submit:hover:not(:disabled) { background: #c5000f; }
.btn-submit:disabled { background: #ccc; cursor: not-allowed; }

/* 底部 */
.footer { background: #1a1a1a; margin-top: auto; }
.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 30px 20px;
  display: flex;
  justify-content: center;
}
.footer-link {
  color: #ccc;
  text-decoration: none;
  font-size: 14px;
}
.footer-link:hover { color: #e60012; }

/* 过渡 */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to { opacity: 0; }

/* Toast */
.toast {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 3000;
  background: rgba(0, 0, 0, 0.88);
  color: #fff;
  padding: 16px 28px;
  font-size: 14px;
  border-left: 4px solid #e60012;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  max-width: 90vw;
  line-height: 1.5;
}
.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.92);
}
</style>