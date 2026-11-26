<template>
  <div class="address-page">
    <BuyerHeader />

    <!-- 面包屑 -->
    <div class="breadcrumb">
      <router-link to="/buyer/goods">首页</router-link>
      <span class="sep">/</span>
      <span>收货地址</span>
    </div>

    <main class="main">
      <div class="address-card">
        <div class="title-row">
          <h2 class="page-title">收货地址</h2>
          <div class="title-actions">
            <span class="page-tip">已保存 {{ list.length }} / {{ MAX_COUNT }} 个</span>
            <el-button type="danger" :disabled="list.length >= MAX_COUNT" @click="openCreate">
              新增地址
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="list.length >= MAX_COUNT"
          type="warning"
          :closable="false"
          show-icon
          title="每个客户最多保存 10 个地址，如需新增请先删除不再使用的地址"
          class="limit-tip"
        />

        <div v-if="loading" class="state">加载中...</div>

        <div v-else-if="list.length === 0" class="state empty">
          <el-empty description="还没有收货地址，新增后即可下单" />
          <el-button type="danger" @click="openCreate">新增地址</el-button>
        </div>

        <div v-else class="addr-list">
          <div v-for="addr in list" :key="addr.addressId" class="addr-item">
            <div class="addr-main">
              <div class="addr-line1">
                <span class="receiver">{{ addr.receiver }}</span>
                <span class="phone">{{ addr.phone }}</span>
                <el-tag v-if="addr.isDefault" size="small" type="danger" effect="dark">默认</el-tag>
              </div>
              <div class="addr-line2">{{ addr.region }} {{ addr.detail }}</div>
            </div>
            <div class="addr-ops">
              <el-button v-if="!addr.isDefault" link type="primary" @click="handleSetDefault(addr)">
                设为默认
              </el-button>
              <el-button link type="primary" @click="openEdit(addr)">编辑</el-button>
              <el-button link type="danger" @click="handleDelete(addr)">删除</el-button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑收货地址' : '新增收货地址'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <el-form-item label="收货人" prop="receiver">
          <el-input v-model="form.receiver" maxlength="20" placeholder="请填写收货人姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="请填写 11 位手机号" />
        </el-form-item>
        <el-form-item label="所在地区" prop="region">
          <el-input v-model="form.region" maxlength="50" placeholder="如：浙江省杭州市西湖区" />
        </el-form-item>
        <el-form-item label="详细地址" prop="detail">
          <el-input
            v-model="form.detail"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="如：文一西路 100 号 3 幢 502"
          />
        </el-form-item>
        <el-form-item label="设为默认">
          <!-- 当前默认地址不允许在这里取消默认（同一客户必须有一个默认地址） -->
          <el-switch v-model="form.isDefault" :disabled="list.length === 0 || (isEdit && form.isDefault)" />
          <span class="switch-tip">同一客户同时只有一个默认地址</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="saving" @click="handleSubmit">保存</el-button>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAddressList,
  createAddress,
  updateAddress,
  deleteAddress,
  setDefaultAddress
} from '@/api/buyer'
import BuyerHeader from '@/components/BuyerHeader.vue'

// 单客户最多 10 个地址（后端 40952 同口径）
const MAX_COUNT = 10

const loading = ref(false)
const saving = ref(false)
const list = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref('')
const formRef = ref(null)

const form = reactive({
  receiver: '',
  phone: '',
  region: '',
  detail: '',
  isDefault: false
})

// 校验规则：四项必填 + 手机号 11 位数字（C.2-01）
const rules = {
  receiver: [
    { required: true, message: '请填写收货人', trigger: 'blur' },
    { max: 20, message: '收货人姓名不能超过 20 个字', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请填写手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号须为 11 位数字', trigger: 'blur' }
  ],
  region: [{ required: true, message: '请填写所在地区', trigger: 'blur' }],
  detail: [{ required: true, message: '请填写详细地址', trigger: 'blur' }]
}

async function loadList() {
  loading.value = true
  try {
    const res = await getAddressList()
    list.value = res?.list || []
  } catch (e) {
    ElMessage.error(e.message || '获取地址列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.receiver = ''
  form.phone = ''
  form.region = ''
  form.detail = ''
  form.isDefault = list.value.length === 0
  editingId.value = ''
  formRef.value?.clearValidate()
}

function openCreate() {
  if (list.value.length >= MAX_COUNT) {
    return ElMessage.warning('地址数量已达上限（10 个），请先删除不用的地址')
  }
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function openEdit(addr) {
  isEdit.value = true
  editingId.value = addr.addressId
  form.receiver = addr.receiver
  form.phone = addr.phone
  form.region = addr.region
  form.detail = addr.detail
  form.isDefault = !!addr.isDefault
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return // 校验未通过
  }

  saving.value = true
  const payload = {
    receiver: form.receiver.trim(),
    phone: form.phone.trim(),
    region: form.region.trim(),
    detail: form.detail.trim(),
    isDefault: form.isDefault
  }

  try {
    if (isEdit.value) {
      await updateAddress(editingId.value, payload)
      ElMessage.success('地址已更新')
    } else {
      await createAddress(payload)
      ElMessage.success('地址已新增')
    }
    dialogVisible.value = false
    await loadList()
  } catch (e) {
    // 40952 已达上限 / 40925 格式校验
    ElMessage.error(e.message || '保存失败，请重试')
    if (e.code === 40952) await loadList()
  } finally {
    saving.value = false
  }
}

async function handleSetDefault(addr) {
  try {
    await setDefaultAddress(addr.addressId)
    ElMessage.success('已设为默认地址')
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '设置失败，请重试')
  }
}

async function handleDelete(addr) {
  try {
    await ElMessageBox.confirm(
      addr.isDefault
        ? `确定删除默认地址「${addr.receiver} ${addr.region}」吗？删除后系统会自动把最近添加的地址设为默认。`
        : `确定删除地址「${addr.receiver} ${addr.region}」吗？`,
      '删除地址',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }

  try {
    await deleteAddress(addr.addressId)
    ElMessage.success('地址已删除')
    await loadList()
  } catch (e) {
    ElMessage.error(e.message || '删除失败，请重试')
  }
}

onMounted(loadList)
</script>

<style scoped>
.address-page {
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
  box-sizing: border-box;
}
.address-card { background: #fff; padding: 30px 40px; min-height: 420px; }

.title-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.title-actions { display: flex; align-items: center; gap: 16px; }
.page-title { font-size: 20px; margin: 0; font-weight: 500; }
.page-tip { font-size: 12px; color: #999; }

.limit-tip { margin-bottom: 16px; }

.state { text-align: center; padding: 60px 0; color: #999; }
.empty { display: flex; flex-direction: column; align-items: center; gap: 20px; }

.addr-list { display: flex; flex-direction: column; }
.addr-item {
  display: flex; align-items: center; justify-content: space-between; gap: 20px;
  padding: 20px 4px; border-bottom: 1px solid #f2f2f2;
}
.addr-item:last-child { border-bottom: none; }
.addr-main { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.addr-line1 { display: flex; align-items: center; gap: 12px; }
.receiver { font-size: 15px; font-weight: 500; }
.phone { font-size: 14px; color: #666; }
.addr-line2 { font-size: 14px; color: #666; line-height: 1.6; }
.addr-ops { display: flex; align-items: center; gap: 4px; flex: 0 0 auto; }

.switch-tip { font-size: 12px; color: #999; margin-left: 12px; }

/* 底部 */
.footer { background: #1a1a1a; color: #666; text-align: center; padding: 30px 0; font-size: 13px; margin-top: auto; }
</style>
