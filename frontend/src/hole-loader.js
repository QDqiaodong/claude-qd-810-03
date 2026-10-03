import { computed, ref } from 'vue'
import { api } from './api'
import { setDetail, clearDetail } from './detail'

/**
 * 孔位列表的加载与选中态同步（孔位分布页）。
 *
 * 矿区连续切换时，慢返回的旧请求会覆盖当前矿区的数据，导致列表、平面图与
 * 右侧详情三处不一致。此处用递增序号保证“最后一次选择唯一生效”：每次加载
 * 记录矿区快照与序号，响应到达时若已有更新的请求（或筛选已变化）则直接丢弃，
 * 过期响应与过期失败都不得落地。
 *
 * 每次应用新列表后校准选中态，保证三处一致、不残留旧矿区详情：
 * - 原选中孔仍在新结果中 → 保留原选择（延续查看上下文），并用最新数据刷新详情；
 * - 否则选中首项（突出筛选反馈）；
 * - 空结果或请求失败 → 清空选中与详情，呈现明确的空态。
 */
export function useHoleLoader({ mineId, mines }) {
  const holes = ref([])
  const selectedId = ref(null)
  let reqSeq = 0

  const selected = computed(() => holes.value.find((h) => h.id === selectedId.value) || null)

  function mineName(id) {
    const m = mines.value.find((x) => x.id === id)
    return m ? m.name : '—'
  }

  function num(v) {
    return v == null ? '—' : Number(v)
  }

  /** 点选孔位：更新选中态并刷新右侧详情面板 */
  function select(h) {
    if (!h) return
    selectedId.value = h.id
    setDetail({
      title: h.holeNo,
      subtitle: mineName(h.mineId) + ' · 倾角 ' + num(h.dipAngle) + '°',
      tag: h.status,
      fields: [
        { label: '孔号', value: h.holeNo },
        { label: '所属矿区', value: mineName(h.mineId) },
        { label: '坐标 X', value: num(h.coordX) },
        { label: '坐标 Y', value: num(h.coordY) },
        { label: '设计孔深 (m)', value: num(h.designDepth) },
        { label: '实际孔深 (m)', value: num(h.actualDepth) },
        { label: '孔口标高 (m)', value: num(h.collarElev) },
        { label: '倾角 (°)', value: num(h.dipAngle) },
        { label: '开孔日期', value: h.spudDate || '—' },
        { label: '终孔日期', value: h.finalDate || '—' },
        { label: '状态', value: h.status }
      ]
    })
  }

  /** 清空选中与详情（空结果 / 请求失败时的明确空态） */
  function clearSelection() {
    selectedId.value = null
    clearDetail()
  }

  /** 新列表应用后校准选中态：保留原选择 > 改选首项 > 清空 */
  function syncSelection() {
    const kept = holes.value.find((h) => h.id === selectedId.value)
    if (kept) select(kept)
    else if (holes.value.length) select(holes.value[0])
    else clearSelection()
  }

  /**
   * 按当前矿区加载孔位。响应乱序时只应用最后一次请求；
   * 最新请求失败时清空列表与详情（错误提示由 api 拦截器统一弹出）。
   */
  async function loadHoles() {
    const reqMineId = mineId.value
    const seq = ++reqSeq
    try {
      const list = await api.boreholes(reqMineId)
      // 已有更新的筛选请求：该响应已过期，丢弃，不得覆盖当前矿区
      if (seq !== reqSeq || reqMineId !== mineId.value) return
      holes.value = list || []
      syncSelection()
    } catch (e) {
      if (seq !== reqSeq || reqMineId !== mineId.value) return
      holes.value = []
      clearSelection()
    }
  }

  return { holes, selectedId, selected, select, loadHoles }
}
