export function validateName(name) {
  const value = (name || '').trim()
  if (!value) return '姓名不能为空，请填写买家姓名'
  if (value.length > 20) return '姓名长度不能超过20个字符'
  return ''
}

export function validatePhone(phone) {
  const value = (phone || '').trim()
  if (!value) return '联系电话不能为空，请填写联系电话'
  return ''
}