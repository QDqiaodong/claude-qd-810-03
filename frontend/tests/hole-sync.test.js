import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHoleListSync, reconcileSelection } from '../src/hole-sync.js'

/** 可手动控制完成时机与顺序的 Promise，用于稳定复现响应乱序 */
function deferred() {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

function hole(id, mineId, extra = {}) {
  return { id, mineId, holeNo: `ZK-${id}`, status: '施工', actualDepth: 100 + id, ...extra }
}

/** 记录每次请求的可控桩：calls[i] 对应第 i 次 load 发起的请求 */
function createFetchStub() {
  const calls = []
  const fetchHoles = (mineId) => {
    const d = deferred()
    calls.push({ mineId, ...d })
    return d.promise
  }
  return { calls, fetchHoles }
}

/** 模拟 HolesView 的接线：台账列表 / 选中态 / 右侧详情 三处状态 */
function createView(fetchHoles) {
  const view = { holes: [], selectedId: null, detail: null, clearCount: 0 }
  const sync = createHoleListSync({
    fetchHoles,
    onList: (list) => { view.holes = list },
    onSelect: (h) => { view.selectedId = h.id; view.detail = h },
    onClear: () => { view.selectedId = null; view.detail = null; view.clearCount++ }
  })
  /** 模拟用户点选（与组件中 select() 的接线一致） */
  const click = (h) => {
    sync.pick(h)
    view.selectedId = h.id
    view.detail = h
  }
  return { view, sync, click }
}

/** 三处一致性断言：详情与选中同源，选中必在当前列表内（空列表则双双清空） */
function assertConsistent(view) {
  if (view.selectedId == null) {
    assert.equal(view.detail, null, '无选中时详情必须清空')
    return
  }
  assert.ok(
    view.holes.some((h) => h.id === view.selectedId),
    `选中孔 ${view.selectedId} 必须在当前列表 [${view.holes.map((h) => h.id)}] 中`
  )
  assert.equal(view.detail.id, view.selectedId, '详情必须与选中孔同源')
  assert.ok(view.holes.includes(view.detail), '详情对象必须来自当前列表，不得残留旧数据')
}

test('乱序返回：慢返回的旧请求被丢弃，不得覆盖当前矿区', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync } = createView(fetchHoles)

  const p1 = sync.load(1) // 切到矿区 1
  const p2 = sync.load(2) // 立即切到矿区 2

  // 新请求（矿区 2）先返回 → 应用
  calls[1].resolve([hole(21, 2), hole(22, 2)])
  const r2 = await p2
  assert.equal(r2.applied, true)
  assert.deepEqual(view.holes.map((h) => h.id), [21, 22])
  assert.equal(view.selectedId, 21)
  assertConsistent(view)

  // 旧请求（矿区 1）慢返回 → 必须整体丢弃，三处保持矿区 2 的数据
  calls[0].resolve([hole(11, 1), hole(12, 1)])
  const r1 = await p1
  assert.equal(r1.applied, false)
  assert.equal(r1.stale, true)
  assert.deepEqual(view.holes.map((h) => h.id), [21, 22])
  assert.equal(view.selectedId, 21)
  assert.equal(view.detail.holeNo, 'ZK-21')
  assertConsistent(view)
})

test('快速往返切换（A→B→A）：无论响应顺序，只有最后一次选择生效', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync } = createView(fetchHoles)

  const p1 = sync.load(1)
  const p2 = sync.load(2)
  const p3 = sync.load(1) // 又切回矿区 1

  // 乱序返回：最后一次（req3）最先到，req1 次之，req2 最后
  calls[2].resolve([hole(13, 1)])
  await p3
  calls[0].resolve([hole(11, 1)]) // 同为矿区 1 但属旧请求 → 丢弃
  const r1 = await p1
  calls[1].resolve([hole(21, 2)]) // 矿区 2 的旧请求 → 丢弃
  const r2 = await p2

  assert.equal(r1.stale, true)
  assert.equal(r2.stale, true)
  assert.deepEqual(view.holes.map((h) => h.id), [13], '列表必须来自最后一次请求')
  assert.equal(view.selectedId, 13)
  assert.equal(view.detail.holeNo, 'ZK-13')
  assertConsistent(view)
})

test('空结果：列表、选中与详情一并清空，不残留旧矿区详情', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync } = createView(fetchHoles)

  const p1 = sync.load(1)
  calls[0].resolve([hole(11, 1)])
  await p1
  assert.equal(view.selectedId, 11)

  const p2 = sync.load(2) // 切到没有孔的矿区
  calls[1].resolve([])
  const r2 = await p2

  assert.equal(r2.applied, true)
  assert.deepEqual(view.holes, [])
  assert.equal(view.selectedId, null)
  assert.equal(view.detail, null)
  assertConsistent(view)
})

test('最新请求失败：清空三处，不残留旧矿区数据', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync } = createView(fetchHoles)

  const p1 = sync.load(1)
  calls[0].resolve([hole(11, 1)])
  await p1
  assert.equal(view.selectedId, 11)

  const p2 = sync.load(2)
  calls[1].reject(new Error('网络错误'))
  const r2 = await p2

  assert.equal(r2.applied, false)
  assert.equal(r2.stale, false)
  assert.ok(r2.error instanceof Error)
  assert.deepEqual(view.holes, [])
  assert.equal(view.selectedId, null)
  assert.equal(view.detail, null)
  assertConsistent(view)
})

test('旧请求失败：完全忽略，不影响当前矿区', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync } = createView(fetchHoles)

  const p1 = sync.load(1)
  const p2 = sync.load(2)

  calls[0].reject(new Error('超时')) // 旧请求失败 → 忽略，不得触发清空
  const r1 = await p1
  assert.equal(r1.stale, true)
  assert.equal(view.clearCount, 0)

  calls[1].resolve([hole(21, 2)])
  await p2
  assert.deepEqual(view.holes.map((h) => h.id), [21])
  assert.equal(view.selectedId, 21)
  assertConsistent(view)
})

test('新列表仍含原选中孔：保留原选择，并用新列表对象刷新详情', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync, click } = createView(fetchHoles)

  const p1 = sync.load(1)
  calls[0].resolve([hole(11, 1), hole(12, 1)])
  await p1

  click(view.holes[1]) // 用户改选 ZK-12
  assert.equal(view.selectedId, 12)

  // 同矿区刷新（如新增钻孔后）：ZK-12 仍在列表中，且数据已更新
  const p2 = sync.load(1)
  calls[1].resolve([hole(11, 1), hole(12, 1, { actualDepth: 321 }), hole(13, 1)])
  await p2

  assert.equal(view.selectedId, 12, '原选中孔仍在列表中 → 保留选择')
  assert.equal(view.detail.actualDepth, 321, '详情必须用新列表中的对象刷新，不得展示旧数据')
  assertConsistent(view)
})

test('原选中孔不在新列表：回退到首项并同步详情', async () => {
  const { calls, fetchHoles } = createFetchStub()
  const { view, sync, click } = createView(fetchHoles)

  const p1 = sync.load(1)
  calls[0].resolve([hole(11, 1), hole(12, 1)])
  await p1
  click(view.holes[1]) // 选中 ZK-12

  const p2 = sync.load(2) // 切换矿区，ZK-12 不在新列表
  calls[1].resolve([hole(21, 2), hole(22, 2)])
  await p2

  assert.equal(view.selectedId, 21, '回退到新列表首项')
  assert.equal(view.detail.holeNo, 'ZK-21')
  assertConsistent(view)
})

test('reconcileSelection：保留 / 首项 / 空 三种分支', () => {
  const list = [hole(1, 1), hole(2, 1)]
  assert.equal(reconcileSelection(list, 2).id, 2, '原选中仍在 → 保留')
  assert.equal(reconcileSelection(list, 99).id, 1, '原选中不在 → 首项')
  assert.equal(reconcileSelection(list, null).id, 1, '无历史选中 → 首项')
  assert.equal(reconcileSelection([], 1), null, '空列表 → 无选中')
  assert.equal(reconcileSelection(null, 1), null, '异常入参 → 无选中')
})
