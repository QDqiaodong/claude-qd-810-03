<template>
  <div class="dc-page">
    <!-- 终孔交接已接收时的冻结提示（后端为硬约束，这里同步禁用入口） -->
    <el-alert
      v-if="lockInfo"
      class="dc-lock-alert"
      type="error"
      :closable="false"
      show-icon
      :title="`钻孔 ${lockInfo.holeNo} 的终孔交接已接收（v${lockInfo.versionNo}），回次与岩性层已冻结，不能新增、修改或删除；确需补录请由负责人返开。`"
    />

    <div class="dc-toolbar">
      <span class="dc-label">钻孔</span>
      <el-select v-model="holeId" size="small" style="width: 220px" :disabled="lockInfo" @change="loadAll">
        <el-option v-for="h in holes" :key="h.id" :label="h.holeNo" :value="h.id" />
      </el-select>
      <span class="dc-label">共 {{ layers.length }} 层 / 最深 {{ num(maxDepth) }} m</span>
      <el-button size="small" type="primary" :disabled="lockInfo" @click="openCreate">补录岩性层</el-button>
    </div>

    <div class="dc-split">
      <div class="dc-panel" style="flex: none; width: 430px">
        <div class="dc-panel-title">岩芯柱状剖面（点层段看属性包）</div>
        <svg viewBox="0 0 380 660" class="dc-svg" style="width: 100%">
          <line x1="86" y1="16" x2="86" y2="640" stroke="#8d6e63" stroke-width="1" />
          <g v-for="t in ticks" :key="t">
            <line x1="78" :y1="depthY(t)" x2="86" :y2="depthY(t)" stroke="#8d6e63" stroke-width="1" />
            <text x="74" :y="depthY(t) + 4" text-anchor="end" font-size="10" fill="#8a7761">{{ t }}</text>
          </g>

          <g v-for="seg in segments" :key="seg.id"
             :style="{ cursor: lockInfo ? 'not-allowed' : 'pointer', opacity: navTargetId && seg.id === navTargetId ? 1 : undefined }"
             @click="!lockInfo && selectLayer(seg)">
            <rect
              x="92"
              :y="depthY(seg.from)"
              width="128"
              :height="Math.max(depthY(seg.to) - depthY(seg.from), 3)"
              :fill="lithoColor(seg.lithology)"
              :stroke="seg.id === selectedId || seg.id === navTargetId ? '#c05621' : '#6d5b48'"
              :stroke-width="seg.id === selectedId || seg.id === navTargetId ? 3 : 0.8"
            />
            <text x="98" :y="depthY(seg.from) + 12" font-size="10" fill="#fff">{{ seg.lithology }}</text>
            <text x="228" :y="depthY(seg.from) + 12" font-size="10" fill="#5d4037">
              RQD {{ seg.rqd == null ? '—' : num(seg.rqd) }} · 采取率 {{ seg.recovery == null ? '—' : num(seg.recovery) }}%
            </text>
            <text x="228" :y="depthY(seg.from) + 24" font-size="10" fill="#8a7761">
              {{ num(seg.from) }} ~ {{ num(seg.to) }} m
            </text>
            <line x1="92" :y1="depthY(seg.to)" x2="220" :y2="depthY(seg.to)" stroke="#6d5b48" stroke-width="0.5" />
          </g>
        </svg>

        <div class="dc-legend">
          <div v-for="(c, name) in colorMap" :key="name" class="dc-legend-item">
            <span class="dc-legend-swatch" :style="{ background: c }"></span>{{ name }}
          </div>
        </div>
      </div>

      <div style="flex: 1; min-width: 320px; display: flex; flex-direction: column; gap: 12px">
        <div class="dc-panel">
          <div class="dc-panel-title">岩性厚度占比</div>
          <div class="dc-stack">
            <div
              v-for="s in lithoStats"
              :key="s.lithology"
              class="dc-stack-seg"
              :style="{ width: num(s.ratio) + '%', background: lithoColor(s.lithology) }"
              :title="s.lithology + ' ' + num(s.ratio) + '%'"
            >
              {{ num(s.ratio) >= 6 ? s.lithology + ' ' + Math.round(num(s.ratio)) + '%' : '' }}
            </div>
          </div>
          <div style="margin-top: 8px; font-size: 11px; color: #8a7761">
            <span v-for="s in lithoStats" :key="s.lithology" style="margin-right: 12px">
              {{ s.lithology }}：{{ s.layerCount }} 层 / {{ num(s.totalThickness) }} m
            </span>
          </div>
        </div>

        <div class="dc-panel">
          <div class="dc-panel-title">钻进回次</div>
          <div v-for="r in rounds" :key="r.id" class="dc-round-row"
               :class="{ 'nav-hit': navTargetId && r.id === navTargetId }"
               :style="{ cursor: lockInfo ? 'not-allowed' : 'pointer' }"
               @click="!lockInfo && selectRound(r)">
            <span style="width: 62px; font-weight: 700">第 {{ r.roundNo }} 回次</span>
            <span style="width: 130px">{{ num(r.fromDepth) }} ~ {{ num(r.toDepth) }} m</span>
            <span style="width: 108px">岩芯 {{ num(r.coreLength) }} m</span>
            <span style="width: 108px">采取率 {{ num(r.recoveryRate) }}%</span>
            <span style="width: 78px">RQD {{ num(r.rqd) }}</span>
            <span style="color: #8a7761">{{ r.driller || '—' }} · {{ r.sampledAt || '' }}</span>
            <span v-if="r.lowRecoveryReason" style="color: #c05621; font-size: 11px">{{ r.lowRecoveryReason }}</span>
          </div>
        </div>

        <div class="dc-panel">
          <div class="dc-panel-title">低采取率预警（编录质量核查）</div>
          <div class="dc-warn-list">
            <div v-for="r in lowList" :key="r.id" class="dc-warn" @click="holeId = r.boreholeId; loadAll()">
              <span style="width: 96px; font-weight: 700">孔 {{ r.boreholeId }}</span>
              <span style="width: 60px">第 {{ r.roundNo }} 回次</span>
              <span style="width: 96px">采取率 {{ num(r.recoveryRate) }}%</span>
              <span style="color: #c05621">{{ r.lowRecoveryReason || '未填原因' }}</span>
            </div>
            <div v-if="!lowList.length" style="font-size: 12px; color: #8a7761">无低采取率回次</div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialog" title="补录岩性层" width="460px">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="层序"><el-input-number v-model="form.layerSeq" /></el-form-item>
        <el-form-item label="岩性"><el-input v-model="form.lithology" /></el-form-item>
        <el-form-item label="起始深度 m"><el-input-number v-model="form.fromDepth" :precision="2" /></el-form-item>
        <el-form-item label="终止深度 m"><el-input-number v-model="form.toDepth" :precision="2" /></el-form-item>
        <el-form-item label="颜色"><el-input v-model="form.color" /></el-form-item>
        <el-form-item label="风化程度"><el-input v-model="form.weathering" /></el-form-item>
        <el-form-item label="结构构造"><el-input v-model="form.structure" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { setDetail } from '../detail'
import { handoverNav } from '../handover-nav'

const PALETTE = ['#6d4c41', '#8d6e63', '#a1887f', '#5d4037', '#795548', '#4e342e', '#bcaaa4', '#3e2723', '#c8a165', '#7b8d6e']

const holes = ref([])
const holeId = ref(null)
const layers = ref([])
const rounds = ref([])
const lithoStats = ref([])
const lowList = ref([])
const selectedId = ref(null)
const dialog = ref(false)
const form = ref({})

/** 当前钻孔的生效交接（用于已接收冻结提示） */
const activeHandover = ref(null)
const lockInfo = computed(() => {
  const h = activeHandover.value
  if (h && h.status === 'ACCEPTED') {
    const hole = holes.value.find((x) => x.id === h.boreholeId)
    return { holeNo: hole ? hole.holeNo : '#' + h.boreholeId, versionNo: h.versionNo }
  }
  return null
})

/** 交接台问题项跳转目标（层 / 回次 id），用于高亮 */
const navTargetId = ref(null)

async function refreshActiveHandover() {
  try {
    const all = await api.handovers()
    activeHandover.value = all.find((x) => x.boreholeId === holeId.value) || null
  } catch (e) {
    activeHandover.value = null
  }
}

const maxDepth = computed(() => Math.max(...layers.value.map((l) => Number(l.toDepth)), 100))
const scaleMax = computed(() => Math.ceil(maxDepth.value / 50) * 50)

const ticks = computed(() => {
  const step = scaleMax.value > 600 ? 100 : 50
  const arr = []
  for (let v = 0; v <= scaleMax.value; v += step) arr.push(v)
  return arr
})

const colorMap = computed(() => {
  const names = [...new Set(layers.value.map((l) => l.lithology))].sort()
  const map = {}
  names.forEach((n, i) => { map[n] = PALETTE[i % PALETTE.length] })
  return map
})

const segments = computed(() =>
  layers.value.map((l) => {
    const round = rounds.value.find(
      (r) => Number(r.fromDepth) <= Number(l.fromDepth) + 0.01 && Number(r.toDepth) >= Number(l.toDepth) - 0.01
    )
    return {
      ...l,
      from: Number(l.fromDepth),
      to: Number(l.toDepth),
      rqd: round ? round.rqd : null,
      recovery: round ? round.recoveryRate : null
    }
  })
)

function num(v) {
  return v == null ? '—' : Number(v)
}

function depthY(d) {
  return 16 + (d / (scaleMax.value || 1)) * 624
}

function lithoColor(name) {
  return colorMap.value[name] || '#8d6e63'
}

function selectLayer(l) {
  selectedId.value = l.id
  setDetail({
    title: '第 ' + l.layerSeq + ' 层 · ' + l.lithology,
    subtitle: num(l.fromDepth) + ' ~ ' + num(l.toDepth) + ' m · 厚度 ' + num(Number(l.toDepth) - Number(l.fromDepth)) + ' m',
    tag: '岩性分层',
    fields: [
      { label: '层序', value: l.layerSeq },
      { label: '岩性', value: l.lithology },
      { label: '起始深度 (m)', value: num(l.fromDepth) },
      { label: '终止深度 (m)', value: num(l.toDepth) },
      { label: '颜色', value: l.color || '—' },
      { label: '风化程度', value: l.weathering || '—' },
      { label: '结构构造', value: l.structure || '—' },
      { label: '描述', value: l.description || '—' }
    ],
    json: l.attrs || {},
    jsonTitle: 'JSON 属性包（含水性 / 硬度 / 裂隙 / 矿化 …）',
    jsonEditable: !lockInfo.value,
    onSave: async (attrs) => {
      if (lockInfo.value) return
      const updated = await api.updateAttrs(l.id, attrs)
      const idx = layers.value.findIndex((x) => x.id === l.id)
      if (idx >= 0) layers.value[idx] = updated
      selectedId.value = updated.id
      setDetail({ json: updated.attrs || {} })
    }
  })
}

function selectRound(r) {
  setDetail({
    title: '第 ' + r.roundNo + ' 回次',
    subtitle: num(r.fromDepth) + ' ~ ' + num(r.toDepth) + ' m',
    tag: '钻进回次',
    fields: [
      { label: '回次号', value: r.roundNo },
      { label: '起止深度 (m)', value: num(r.fromDepth) + ' ~ ' + num(r.toDepth) },
      { label: '进尺 (m)', value: num(Number(r.toDepth) - Number(r.fromDepth)) },
      { label: '岩芯长度 (m)', value: num(r.coreLength) },
      { label: '采取率 (%)', value: num(r.recoveryRate) },
      { label: 'RQD (%)', value: num(r.rqd) },
      { label: '钻工', value: r.driller || '—' },
      { label: '取样日期', value: r.sampledAt || '—' },
      { label: '低采取率原因', value: r.lowRecoveryReason || '—' },
      { label: '备注', value: r.remark || '—' }
    ]
  })
}

function openCreate() {
  const last = layers.value[layers.value.length - 1]
  form.value = {
    layerSeq: (layers.value.length || 0) + 1,
    lithology: '',
    fromDepth: last ? Number(last.toDepth) : 0,
    toDepth: last ? Number(last.toDepth) + 10 : 10,
    color: '', weathering: '', structure: '', description: ''
  }
  dialog.value = true
}

async function submitCreate() {
  try {
    await api.createLayer({ ...form.value, boreholeId: holeId.value, roundId: rounds.value[0]?.id || null })
    ElMessage.success('岩性层已补录')
    dialog.value = false
    loadAll()
    lithoStats.value = await api.lithoStats()
  } catch (e) { /* 已提示 */ }
}

async function loadAll() {
  layers.value = await api.layers({ boreholeId: holeId.value })
  rounds.value = await api.rounds(holeId.value)
  await refreshActiveHandover()
  locateNavTarget()
}

/** 从交接台问题项跳回：切到对应钻孔并高亮问题回次/岩性层 */
function locateNavTarget() {
  navTargetId.value = null
  if (!handoverNav.fromHandover || handoverNav.target !== 'core') return
  if (handoverNav.boreholeId && handoverNav.boreholeId !== holeId.value) return
  navTargetId.value = handoverNav.targetId
  const targetType = handoverNav.targetType
  const id = handoverNav.targetId
  if (targetType === 'round') {
    const r = rounds.value.find((x) => x.id === id)
    if (r) selectRound(r)
  } else if (targetType === 'layer') {
    const l = layers.value.find((x) => x.id === id)
    if (l) selectLayer(segments.value.find((s) => s.id === id) || l)
  }
}

watch(
  () => handoverNav.nonce,
  async () => {
    if (handoverNav.target !== 'core') return
    if (handoverNav.boreholeId && handoverNav.boreholeId !== holeId.value) {
      holeId.value = handoverNav.boreholeId
      await loadAll()
    } else {
      locateNavTarget()
    }
  }
)

onMounted(async () => {
  holes.value = await api.boreholes()
  if (holes.value.length) holeId.value = holes.value[0].id
  lithoStats.value = await api.lithoStats()
  lowList.value = await api.lowRecovery()
  await loadAll()
})
</script>
