import { reactive } from 'vue'

/**
 * 右侧常驻详情面板的共享状态。
 * 各页面点选对象时调用 setDetail()，面板即时更新，不跳转、不弹窗。
 */
export const detail = reactive({
  title: '',
  subtitle: '',
  tag: '',
  fields: [],      // [{ label, value }]
  json: null,      // JSON 属性包（对象或数组）
  jsonTitle: '',
  jsonEditable: false,
  onSave: null     // (json) => Promise
})

const EMPTY = {
  title: '',
  subtitle: '',
  tag: '',
  fields: [],
  json: null,
  jsonTitle: '',
  jsonEditable: false,
  onSave: null
}

export function setDetail(payload) {
  Object.assign(detail, EMPTY, payload || {})
}

export function clearDetail() {
  Object.assign(detail, EMPTY)
}
