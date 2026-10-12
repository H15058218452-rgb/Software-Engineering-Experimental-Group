<template>
  <!-- 弹窗 -->
  <transition name="modal-fade">
    <div v-if="visible" class="modal-mask" @click.self="handleMaskClick">
      <div class="modal">
        <!-- 表单视图 -->
        <div v-if="step === 'form'" class="modal-form">
          <div class="modal-header">
            <h3>提交购买意向</h3>
            <button class="close-btn" @click="handleClose">×</button>
          </div>

          <div class="modal-body">
            <div class="form-item">
              <div class="form-row">
                <label class="form-label">
                  买家姓名 <span class="required">*</span>
                </label>
                <input
                  v-model="form.name"
                  type="text"
                  class="form-input"
                  placeholder="请输入您的姓名（1-20 字符）"
                  maxlength="20"
                  @input="errors.name = ''"
                />
              </div>
              <div v-if="errors.name" class="error-tip">{{ errors.name }}</div>
            </div>

            <div class="form-item">
              <div class="form-row">
                <label class="form-label">
                  联系电话 <span class="required">*</span>
                </label>
                <input
                  v-model="form.phone"
                  type="text"
                  class="form-input"
                  placeholder="请输入 11 位手机号"
                  maxlength="11"
                  @input="errors.phone = ''"
                />
              </div>
              <div v-if="errors.phone" class="error-tip">{{ errors.phone }}</div>
            </div>
          </div>

          <div class="modal-footer">
            <button class="btn-cancel" @click="handleClose">取消</button>
            <button
              class="btn-submit"
              :disabled="submitting"
              @click="handleSubmit"
            >
              {{ submitting ? '提交中...' : '确认提交' }}
            </button>
          </div>
        </div>

        <!-- 成功视图（BUY-04） -->
        <div v-else class="modal-success">
          <div class="success-icon">✓</div>
          <h3 class="success-title">意向提交成功！</h3>
          <p class="success-tip">
            请妥善保存下方口令码，仅展示一次，丢失无法找回
          </p>

          <div class="code-box">
            <span class="code-text">{{ intentCode }}</span>
          </div>

          <div class="info-list">
            <div class="info-row">
              <span class="label">商品名称</span>
              <span class="value">{{ product?.name }}</span>
            </div>
            <div class="info-row">
              <span class="label">商品价格</span>
              <span class="value">{{ formatMoney(product?.price) }}</span>
            </div>
            <div class="info-row">
              <span class="label">提交时间</span>
              <span class="value">{{ formatTime(submitTime) }}</span>
            </div>
            <div class="info-row">
              <span class="label">当前状态</span>
              <span class="value">排队中</span>
            </div>
          </div>

          <button class="btn-submit full" @click="handleSuccessClose">
            我已知晓
          </button>
        </div>
      </div>
    </div>
  </transition>

  <!-- 独立 Toast：提示保管口令码，2 秒后自动消失 -->
  <Teleport to="body">
    <transition name="toast-fade">
      <div v-if="showToast" class="toast">
        <div class="toast-icon">!</div>
        <div class="toast-text">
          <p class="toast-title">请妥善保管口令码</p>
          <p class="toast-sub">仅展示一次，丢失无法找回</p>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'
import { submitIntent } from '@/api/buyer'
import { formatMoney, formatTime } from '@/utils/format'

const props = defineProps({
  visible: { type: Boolean, default: false },
  product: { type: Object, default: null }
})

const emit = defineEmits(['update:visible', 'success'])

const step = ref('form')
const submitting = ref(false)
const form = ref({ name: '', phone: '' })
const errors = ref({ name: '', phone: '' })
const intentCode = ref('')
const submitTime = ref('')
const showToast = ref(false)
let toastTimer = null

watch(() => props.visible, (val) => {
  if (val) {
    step.value = 'form'
    form.value = { name: '', phone: '' }
    errors.value = { name: '', phone: '' }
    submitting.value = false
    intentCode.value = ''
    submitTime.value = ''
    showToast.value = false
    if (toastTimer) {
      clearTimeout(toastTimer)
      toastTimer = null
    }
  }
})

function validateName(name) {
  const v = (name || '').trim()
  if (!v) return '姓名不能为空，请填写买家姓名'
  if (v.length > 20) return '姓名长度不能超过20个字符'
  return ''
}

function validatePhone(phone) {
  const v = (phone || '').trim()
  if (!v) return '联系电话不能为空，请填写联系电话'
  if (!/^1[3-9]\d{9}$/.test(v)) return '请填写正确的 11 位手机号'
  return ''
}

function validateAll() {
  errors.value.name = validateName(form.value.name)
  errors.value.phone = validatePhone(form.value.phone)
  return !errors.value.name && !errors.value.phone
}

async function handleSubmit() {
  if (submitting.value) return
  if (!validateAll()) return

  submitting.value = true
  try {
    // ============ 真实调用（后端好了用这段） ============
    // const res = await submitIntent({
    //   productId: props.product?.id,
    //   name: form.value.name.trim(),
    //   phone: form.value.phone.trim()
    // })
    // intentCode.value = res.code

    // ============ 临时 mock（后端好了删掉） ============
    await new Promise((r) => setTimeout(r, 500))
    const phone = form.value.phone.trim()
    const submittedKey = `mock_submitted_${props.product?.id || 'default'}`
    const submittedPhones = JSON.parse(localStorage.getItem(submittedKey) || '[]')

    if (submittedPhones.includes(phone)) {
      errors.value.phone = '您已提交过该商品的购买意向，请勿重复提交'
      submitting.value = false
      return
    }

    submittedPhones.push(phone)
    localStorage.setItem(submittedKey, JSON.stringify(submittedPhones))

    intentCode.value = Math.random().toString(36).slice(2, 10).toUpperCase()
    
    // 临时记录：详情页 mock 时读取
    localStorage.setItem('mock_intent_name', form.value.name.trim())
    localStorage.setItem('mock_intent_phone', form.value.phone.trim())
    // ============ mock end ============

    submitTime.value = new Date().toISOString()
    step.value = 'success'
    emit('success')

    // 成功视图出现后，立即弹出 Toast，2 秒后消失
    showToast.value = true
    if (toastTimer) clearTimeout(toastTimer)
    toastTimer = setTimeout(() => {
      showToast.value = false
      toastTimer = null
    }, 2000)
  } catch (e) {
    if (e.code === 40904) {
      errors.value.phone = '您已提交过该商品的购买意向，请勿重复提交'
    } else {
      errors.value.name = e.message || '提交失败，请重试'
    }
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}

function handleSuccessClose() {
  emit('update:visible', false)
}

function handleMaskClick() {
  if (step.value === 'form') handleClose()
}
</script>

<style scoped>
/* 遮罩 */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 弹窗 */
.modal {
  background: #fff;
  width: 460px;
  max-width: 92vw;
  max-height: 90vh;
  overflow-y: auto;
  border-radius: 0;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.15);
}

/* 头部 */
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

/* 表单 */
.modal-body {
  padding: 24px;
}
.form-item {
  margin-bottom: 20px;
}
.form-item:last-child { margin-bottom: 0; }
.form-row {
  display: flex;
  align-items: center;
}
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
.form-input::placeholder { color: #bbb; }
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

.modal-footer {
  display: flex;
  gap: 12px;
  padding: 16px 24px 24px;
  justify-content: flex-end;
  border-top: 1px solid #f0f0f0;
}
.btn-cancel,
.btn-submit {
  height: 40px;
  padding: 0 24px;
  font-size: 14px;
  border: none;
  cursor: pointer;
  border-radius: 0;
  transition: all 0.2s;
}
.btn-cancel {
  background: #f5f5f5;
  color: #333;
}
.btn-cancel:hover { background: #eee; }
.btn-submit {
  background: #e60012;
  color: #fff;
}
.btn-submit:hover:not(:disabled) { background: #c5000f; }
.btn-submit:disabled {
  background: #ccc;
  cursor: not-allowed;
}

/* 成功视图 */
.modal-success {
  padding: 32px 28px 28px;
  text-align: center;
}
.success-icon {
  width: 56px;
  height: 56px;
  line-height: 56px;
  margin: 0 auto 16px;
  background: #e60012;
  color: #fff;
  font-size: 32px;
  border-radius: 50%;
}
.success-title {
  font-size: 20px;
  color: #333;
  margin: 0 0 8px;
  font-weight: 500;
}
.success-tip {
  font-size: 13px;
  color: #999;
  margin: 0 0 24px;
}
.code-box {
  background: #fafafa;
  padding: 16px;
  margin-bottom: 24px;
  border: 1px dashed #e60012;
}
.code-text {
  font-family: 'Courier New', monospace;
  font-size: 22px;
  font-weight: bold;
  color: #e60012;
  letter-spacing: 3px;
}
.info-list {
  text-align: left;
  margin-bottom: 24px;
  font-size: 14px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}
.info-row:last-child { border-bottom: none; }
.info-row .label { color: #999; }
.info-row .value { color: #333; }
.btn-submit.full {
  width: 100%;
  height: 44px;
  font-size: 15px;
  letter-spacing: 1px;
}

/* 过渡动画 */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.25s ease;
}
.modal-fade-enter-active .modal,
.modal-fade-leave-active .modal {
  transition: transform 0.25s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
.modal-fade-enter-from .modal,
.modal-fade-leave-to .modal {
  transform: translateY(-20px);
}

/* Toast：屏幕中央悬浮提示，位于弹窗之上 */
.toast {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 3000;
  display: flex;
  align-items: center;
  gap: 14px;
  background: rgba(0, 0, 0, 0.88);
  color: #fff;
  padding: 20px 28px;
  border-left: 4px solid #e60012;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  max-width: 90vw;
}
.toast-icon {
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  line-height: 40px;
  text-align: center;
  border-radius: 50%;
  background: #e60012;
  color: #fff;
  font-size: 26px;
  font-weight: bold;
}
.toast-text {
  display: flex;
  flex-direction: column;
}
.toast-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 4px;
  color: #fff;
}
.toast-sub {
  font-size: 13px;
  margin: 0;
  color: #ccc;
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