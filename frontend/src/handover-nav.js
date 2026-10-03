import { reactive } from 'vue'

/**
 * 交接台与各录入页之间的跨页导航状态。
 *
 * 交接台的问题项可直接跳回对应资料整改（回次/岩性层→岩芯编录页、参数→钻进参数页、
 * 班报→班报表页）；整改后点「返回交接台」要保留【当前钻孔】和【检查进度】。
 * 因为 App.vue 用 v-if 切页，普通组件状态会随卸载丢失，所以这里把跳转上下文放到模块级单例。
 *
 * navigation = {
 *   fromHandover: true,
 *   handoverId, boreholeId, statusFilter,
 *   target: 'core' | 'param' | 'report',
 *   targetType, targetId, targetLabel,
 *   nonce: 每次跳转自增，供目标页 watch 后定位 / 高亮
 * }
 */
export const handoverNav = reactive({
  fromHandover: false,
  handoverId: null,
  boreholeId: null,
  statusFilter: null,
  target: '',
  targetType: '',
  targetId: null,
  targetLabel: '',
  nonce: 0
})

/**
 * 从交接台问题项跳到对应录入页。
 * @param issue 问题项视图：{ targetPage, targetType, targetId, targetLabel }
 * @param context 交接台上下文：{ handoverId, boreholeId, statusFilter }
 */
export function gotoIssue(issue, context) {
  handoverNav.fromHandover = true
  handoverNav.handoverId = context.handoverId
  handoverNav.boreholeId = context.boreholeId
  handoverNav.statusFilter = context.statusFilter
  handoverNav.target = issue.targetPage
  handoverNav.targetType = issue.targetType
  handoverNav.targetId = issue.targetId
  handoverNav.targetLabel = issue.targetLabel || ''
  handoverNav.nonce++
  return handoverNav.target
}

/** 返回交接台时保留的当前钻孔与检查进度 */
export function exitToHandover() {
  const ctx = {
    handoverId: handoverNav.handoverId,
    boreholeId: handoverNav.boreholeId,
    statusFilter: handoverNav.statusFilter
  }
  handoverNav.fromHandover = false
  handoverNav.target = ''
  handoverNav.targetType = ''
  handoverNav.targetId = null
  handoverNav.targetLabel = ''
  return ctx
}

/**
 * 从钻孔台账直接进入交接台（班组视角，选中该孔）。
 * 用于"现场班组从施工钻孔发起交接"的入口。
 */
export function openHandoverForHole(boreholeId) {
  handoverNav.fromHandover = false
  handoverNav.handoverId = null
  handoverNav.boreholeId = boreholeId
  handoverNav.statusFilter = null
  handoverNav.target = 'handover'
  handoverNav.targetType = ''
  handoverNav.targetId = null
  handoverNav.targetLabel = ''
  handoverNav.nonce++
  return 'handover'
}

export function clearHandoverNav() {
  handoverNav.fromHandover = false
  handoverNav.handoverId = null
  handoverNav.boreholeId = null
  handoverNav.statusFilter = null
  handoverNav.target = ''
  handoverNav.targetType = ''
  handoverNav.targetId = null
  handoverNav.targetLabel = ''
}
