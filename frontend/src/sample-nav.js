import { reactive } from 'vue'

/**
 * 取样送检页与「孔位分布（孔资料）」之间的跨页导航状态。
 *
 * 与终孔交接的 handover-nav 同理：App.vue 用 v-if 切页，普通组件状态会随卸载丢失，
 * 所以把「从哪个孔资料进来」放到模块级单例；页面内部的筛选条件 / 当前批次 / 角色
 * 则由 SampleView 自行持久化到 localStorage（刷新后仍保留）。
 *
 * nav = { fromHole: true, boreholeId, nonce }
 */
export const sampleNav = reactive({
  fromHole: false,
  boreholeId: null,
  nonce: 0
})

/** 从孔资料的「取样登记」按钮进入送检链路：携带钻孔，落到现场取样视角 */
export function openSamplesForHole(boreholeId) {
  sampleNav.fromHole = true
  sampleNav.boreholeId = boreholeId
  sampleNav.nonce++
  return 'sample'
}

/** 返回孔资料时清掉导航态（筛选条件留在 localStorage，不受影响） */
export function exitToHole() {
  const ctx = { boreholeId: sampleNav.boreholeId }
  sampleNav.fromHole = false
  sampleNav.boreholeId = null
  return ctx
}

export function clearSampleNav() {
  sampleNav.fromHole = false
  sampleNav.boreholeId = null
}
