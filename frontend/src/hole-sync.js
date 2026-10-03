/**
 * 矿区孔位列表的加载与选中态同步（纯逻辑，无框架依赖，便于单测）。
 *
 * 背景：连续切换矿区时，慢返回的旧请求会覆盖当前矿区的数据，
 * 导致台账列表、平面图与右侧详情三处不一致。本模块保证：
 *
 * 1. 最后一次 load 唯一生效 —— 每次加载递增序号，响应或失败回来时
 *    序号已过期的旧请求一律整体忽略，绝不触碰视图状态；
 * 2. 选中态随新列表重算 —— 新列表仍含原选中孔时保留原选择
 *   （延续查看上下文，且用新列表中的对象刷新详情，不展示旧数据）；
 *    否则回退到首项（突出筛选反馈）；空列表则清空选中与详情，
 *    不得残留旧矿区的详情；
 * 3. 请求失败有明确结果 —— 仅最新一次请求的失败会清空列表/选中/详情
 *   （错误提示由接口拦截器统一弹出）；旧请求的失败直接忽略。
 */

/**
 * 决定新列表到达后的选中对象。
 * @param {Array} list 最新一次请求返回的孔位列表
 * @param {*} prevSelectedId 上一次选中的孔 id
 * @returns 选中的孔对象；列表为空时返回 null
 */
export function reconcileSelection(list, prevSelectedId) {
  if (!list || !list.length) return null
  const kept = list.find((h) => h.id === prevSelectedId)
  return kept || list[0]
}

/**
 * 创建“最后一次生效”的孔位加载同步器。
 * @param {object} deps
 * @param {(mineId: *) => Promise<Array>} deps.fetchHoles 按矿区拉取孔位
 * @param {(holes: Array) => void} deps.onList 应用最新列表（仅最新请求到达时触发）
 * @param {(hole: object) => void} deps.onSelect 选中某孔并刷新详情
 * @param {() => void} deps.onClear 清空选中与详情
 */
export function createHoleListSync({ fetchHoles, onList, onSelect, onClear }) {
  let seq = 0
  let selectedId = null

  /**
   * 加载指定矿区的孔位。返回一个永不 reject 的 Promise，
   * 结果形如 { applied, stale, holes?, error? }：
   * - applied=true  本次为最新请求且成功，已应用到视图；
   * - stale=true    本次已被更新的请求取代，结果被丢弃（成功或失败均不触碰视图）；
   * - applied=false 且 stale=false  本次为最新请求但失败，视图已清空。
   */
  async function load(mineId) {
    const token = ++seq
    let list
    try {
      list = await fetchHoles(mineId)
    } catch (err) {
      if (token !== seq) return { applied: false, stale: true, error: err }
      selectedId = null
      onList([])
      onClear()
      return { applied: false, stale: false, error: err }
    }
    if (token !== seq) return { applied: false, stale: true, holes: list }
    const holes = list || []
    onList(holes)
    const target = reconcileSelection(holes, selectedId)
    if (target) {
      selectedId = target.id
      onSelect(target)
    } else {
      selectedId = null
      onClear()
    }
    return { applied: true, stale: false, holes }
  }

  /** 用户手动点选：记录当前选中，供下一次加载时做保留判断 */
  function pick(hole) {
    selectedId = hole ? hole.id : null
  }

  return {
    load,
    pick,
    get selectedId() {
      return selectedId
    }
  }
}
