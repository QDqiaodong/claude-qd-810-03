import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { clearDetail, detail } from '../detail'
import { useHoleLoader } from '../hole-loader'

vi.mock('../api', () => ({
  api: { boreholes: vi.fn() }
}))

import { api } from '../api'

/** 可手动控制结算时机的 Promise，用于稳定复现响应乱序 */
function deferred() {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

const minesList = [
  { id: 1, name: '甲矿区', stage: '勘探' },
  { id: 2, name: '乙矿区', stage: '开采' }
]

function hole(id, mineId, holeNo, extra = {}) {
  return {
    id,
    mineId,
    holeNo,
    coordX: 10 + id,
    coordY: 20 + id,
    designDepth: 300,
    actualDepth: 120,
    collarElev: 1500,
    dipAngle: 90,
    spudDate: '2026-09-01',
    finalDate: null,
    status: '施工',
    ...extra
  }
}

const holesA = [hole(11, 1, 'ZK-A1'), hole(12, 1, 'ZK-A2')]
const holesB = [hole(21, 2, 'ZK-B1'), hole(22, 2, 'ZK-B2')]

function setup() {
  const mineId = ref(1)
  const mines = ref(minesList)
  const loader = useHoleLoader({ mineId, mines })
  return { loader, mineId, mines }
}

const ids = (list) => list.map((h) => h.id)

beforeEach(() => {
  api.boreholes.mockReset()
  clearDetail()
})

describe('孔位加载与选中态同步', () => {
  it('首次加载：自动选中首项并填充详情', async () => {
    const { loader } = setup()
    api.boreholes.mockResolvedValueOnce(holesA)

    await loader.loadHoles()

    expect(ids(loader.holes.value)).toEqual([11, 12])
    expect(loader.selectedId.value).toBe(11)
    expect(loader.selected.value.holeNo).toBe('ZK-A1')
    expect(detail.title).toBe('ZK-A1')
    expect(detail.subtitle).toContain('甲矿区')
  })

  it('响应乱序：慢返回的旧矿区请求被丢弃，列表/选中/详情保持当前矿区', async () => {
    const { loader, mineId } = setup()
    const stale = deferred()
    const fresh = deferred()
    api.boreholes.mockReturnValueOnce(stale.promise).mockReturnValueOnce(fresh.promise)

    const pStale = loader.loadHoles() // 甲矿区请求先发出
    mineId.value = 2 // 用户立即切换到乙矿区
    const pFresh = loader.loadHoles()

    fresh.resolve(holesB) // 新请求先返回 → 应用
    await pFresh
    expect(ids(loader.holes.value)).toEqual([21, 22])

    stale.resolve(holesA) // 旧请求慢返回 → 必须丢弃，不得覆盖乙矿区
    await pStale
    expect(ids(loader.holes.value)).toEqual([21, 22])
    expect(loader.selectedId.value).toBe(21)
    expect(loader.selected.value.holeNo).toBe('ZK-B1')
    expect(detail.title).toBe('ZK-B1')
    expect(detail.subtitle).toContain('乙矿区')
  })

  it('快速往返切换（甲→乙→甲）：只有最后一次选择唯一生效', async () => {
    const { loader, mineId } = setup()
    const d1 = deferred()
    const d2 = deferred()
    const d3 = deferred()
    api.boreholes
      .mockReturnValueOnce(d1.promise)
      .mockReturnValueOnce(d2.promise)
      .mockReturnValueOnce(d3.promise)

    const p1 = loader.loadHoles() // 甲 #1
    mineId.value = 2
    const p2 = loader.loadHoles() // 乙
    mineId.value = 1
    const p3 = loader.loadHoles() // 又回到甲 —— 唯一生效

    d3.resolve(holesA)
    await p3
    d2.resolve(holesB) // 过期响应 → 丢弃
    await p2
    d1.resolve(holesB) // 过期响应（故意给错误数据）→ 丢弃
    await p1

    expect(ids(loader.holes.value)).toEqual([11, 12])
    expect(loader.selectedId.value).toBe(11)
    expect(detail.title).toBe('ZK-A1')
    expect(detail.subtitle).toContain('甲矿区')
  })

  it('新结果仍含原选中孔位：保留原选择，并用最新数据刷新详情', async () => {
    const { loader } = setup()
    api.boreholes.mockResolvedValueOnce(holesA)
    await loader.loadHoles()
    loader.select(loader.holes.value[1]) // 选中 ZK-A2
    expect(detail.title).toBe('ZK-A2')

    // 同矿区刷新（如新增钻孔后），原选中孔仍在结果中
    const updated = [hole(11, 1, 'ZK-A1'), hole(12, 1, 'ZK-A2', { actualDepth: 260 }), hole(13, 1, 'ZK-A3')]
    api.boreholes.mockResolvedValueOnce(updated)
    await loader.loadHoles()

    expect(loader.selectedId.value).toBe(12)
    expect(detail.title).toBe('ZK-A2')
    expect(detail.fields.find((f) => f.label === '实际孔深 (m)').value).toBe(260)
  })

  it('新结果不含原选中孔位：自动改选首项，详情不残留旧矿区', async () => {
    const { loader, mineId } = setup()
    api.boreholes.mockResolvedValueOnce(holesA)
    await loader.loadHoles()
    loader.select(loader.holes.value[1]) // 选中甲矿区 ZK-A2

    mineId.value = 2
    api.boreholes.mockResolvedValueOnce(holesB)
    await loader.loadHoles()

    expect(loader.selectedId.value).toBe(21)
    expect(detail.title).toBe('ZK-B1')
    expect(detail.subtitle).toContain('乙矿区')
    expect(detail.fields.find((f) => f.label === '所属矿区').value).toBe('乙矿区')
  })

  it('空结果：清空选中与详情，列表/平面图/详情一致为空', async () => {
    const { loader, mineId } = setup()
    api.boreholes.mockResolvedValueOnce(holesA)
    await loader.loadHoles()

    mineId.value = 2
    api.boreholes.mockResolvedValueOnce([])
    await loader.loadHoles()

    expect(loader.holes.value).toEqual([])
    expect(loader.selectedId.value).toBeNull()
    expect(loader.selected.value).toBeNull()
    expect(detail.title).toBe('')
    expect(detail.fields).toEqual([])
  })

  it('请求失败：清空列表与详情，不残留旧矿区数据', async () => {
    const { loader, mineId } = setup()
    api.boreholes.mockResolvedValueOnce(holesA)
    await loader.loadHoles()

    mineId.value = 2
    api.boreholes.mockRejectedValueOnce(new Error('网络错误'))
    await loader.loadHoles()

    expect(loader.holes.value).toEqual([])
    expect(loader.selectedId.value).toBeNull()
    expect(loader.selected.value).toBeNull()
    expect(detail.title).toBe('')
    expect(detail.fields).toEqual([])
  })

  it('过期请求的失败同样被丢弃，不影响当前矿区状态', async () => {
    const { loader, mineId } = setup()
    const stale = deferred()
    const fresh = deferred()
    api.boreholes.mockReturnValueOnce(stale.promise).mockReturnValueOnce(fresh.promise)

    const pStale = loader.loadHoles()
    mineId.value = 2
    const pFresh = loader.loadHoles()

    fresh.resolve(holesB)
    await pFresh
    stale.reject(new Error('旧请求失败')) // 过期失败 → 丢弃，不得清空当前列表
    await pStale

    expect(ids(loader.holes.value)).toEqual([21, 22])
    expect(loader.selectedId.value).toBe(21)
    expect(detail.title).toBe('ZK-B1')
  })
})
