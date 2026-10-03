<template>
  <div class="dc-page">
    <div class="dc-toolbar">
      <span class="dc-label">矿区</span>
      <el-select v-model="mineId" size="small" style="width: 230px" @change="loadHoles">
        <el-option v-for="m in mines" :key="m.id" :label="m.name + ' · ' + m.stage" :value="m.id" />
      </el-select>
      <el-button size="small" type="primary" @click="openCreate">新增钻孔</el-button>

      <div class="dc-stats">
        <div
          v-for="s in stats"
          :key="s.mineId"
          class="dc-stat"
          :class="{ on: s.mineId === mineId }"
          @click="mineId = s.mineId; loadHoles()"
        >
          <div class="dc-stat-name">{{ s.mineName }}</div>
          <div class="dc-stat-num">{{ s.holeCount }} 孔 / 累计 {{ num(s.totalFootage) }} m</div>
        </div>
      </div>
    </div>

    <div class="dc-split">
      <div class="dc-panel" style="flex: 1; min-width: 420px">
        <div class="dc-panel-title">孔位平面分布（点径 = 实际孔深，色 = 孔状态）</div>
        <svg viewBox="0 0 620 440" class="dc-svg" style="width: 100%">
          <defs>
            <pattern id="dcGrid" width="24" height="24" patternUnits="userSpaceOnUse">
              <path d="M24 0H0V24" fill="none" stroke="#ddcfb4" stroke-width="1" />
            </pattern>
          </defs>
          <rect x="0" y="0" width="620" height="440" fill="url(#dcGrid)" />
          <line x1="30" y1="410" x2="600" y2="410" stroke="#8d6e63" stroke-width="1.5" />
          <line x1="30" y1="20" x2="30" y2="410" stroke="#8d6e63" stroke-width="1.5" />
          <text x="600" y="430" text-anchor="end" font-size="11" fill="#8d6e63">X（m）</text>
          <text x="34" y="16" font-size="11" fill="#8d6e63">Y（m）</text>

          <g v-for="p in points" :key="p.id" style="cursor: pointer" @click="select(p)">
            <line :x1="p.x - 12" :y1="p.y" :x2="p.x + 12" :y2="p.y" stroke="#8d6e63" stroke-width="0.8" />
            <line :x1="p.x" :y1="p.y - 12" :x2="p.x" :y2="p.y + 12" stroke="#8d6e63" stroke-width="0.8" />
            <circle
              :cx="p.x"
              :cy="p.y"
              :r="p.r"
              :fill="statusColor(p.status)"
              :stroke="p.id === selectedId ? '#3e2723' : '#6d5b48'"
              :stroke-width="p.id === selectedId ? 3 : 1"
            />
            <text :x="p.x + p.r + 5" :y="p.y + 4" class="dc-hole-label">{{ p.holeNo }}</text>
          </g>
        </svg>
      </div>

      <div class="dc-panel" style="width: 190px; flex: none">
        <div class="dc-panel-title">孔深标尺</div>
        <svg viewBox="0 0 150 440" class="dc-svg" style="width: 100%">
          <line x1="34" y1="20" x2="34" y2="410" stroke="#3e2723" stroke-width="2" />
          <g v-for="t in ticks" :key="t">
            <line x1="26" :y1="depthY(t)" x2="34" :y2="depthY(t)" stroke="#3e2723" stroke-width="1.2" />
            <text x="22" :y="depthY(t) + 4" text-anchor="end" font-size="10" fill="#6d5b48">{{ t }}</text>
          </g>

          <template v-if="selected">
            <line x1="20" :y1="depthY(num(selected.designDepth))" x2="140" :y2="depthY(num(selected.designDepth))"
                  stroke="#8d6e63" stroke-width="1.2" stroke-dasharray="5 4" />
            <text x="140" :y="depthY(num(selected.designDepth)) - 4" text-anchor="end" font-size="10" fill="#8d6e63">
              设计 {{ num(selected.designDepth) }}
            </text>
            <polygon
              :points="`34,${depthY(num(selected.actualDepth))} 48,${depthY(num(selected.actualDepth)) - 7} 48,${depthY(num(selected.actualDepth)) + 7}`"
              fill="#c05621"
            />
            <text x="54" :y="depthY(num(selected.actualDepth)) + 4" font-size="11" fill="#c05621" font-weight="700">
              实钻 {{ num(selected.actualDepth) }} m
            </text>
          </template>
        </svg>
        <div style="margin-top: 8px; font-size: 11px; color: #8a7761; line-height: 1.8">
          虚线 = 设计孔深<br />三角 = 实际孔深游标
        </div>
      </div>
    </div>

    <div class="dc-panel">
      <div class="dc-panel-title">钻孔台账（点行看详情）</div>
      <div v-for="h in holes" :key="h.id" class="dc-round-row" :class="{ on: h.id === selectedId }" @click="select(h)">
        <span style="width: 92px; font-weight: 700">{{ h.holeNo }}</span>
        <span style="width: 120px; color: #8a7761">X {{ num(h.coordX) }} / Y {{ num(h.coordY) }}</span>
        <span style="width: 130px">设计 {{ num(h.designDepth) }} m</span>
        <span style="width: 130px">实钻 {{ num(h.actualDepth) }} m</span>
        <span style="width: 74px" :style="{ color: statusColor(h.status) }">{{ h.status }}</span>
        <span style="color: #8a7761">{{ h.spudDate }} → {{ h.finalDate || '—' }}</span>
        <el-button
          v-if="h.status === '终孔'"
          class="dc-hole-handover-btn"
          size="small"
          type="warning"
          plain
          @click.stop="gotoHandover(h)"
        >终孔交接</el-button>
        <el-button
          class="dc-hole-handover-btn"
          size="small"
          type="primary"
          plain
          @click.stop="gotoSample(h)"
        >取样登记</el-button>
      </div>
    </div>

    <el-dialog v-model="dialog" title="新增钻孔" width="460px">
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="孔号"><el-input v-model="form.holeNo" /></el-form-item>
        <el-form-item label="所属矿区">
          <el-select v-model="form.mineId" style="width: 100%">
            <el-option v-for="m in mines" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="坐标 X"><el-input-number v-model="form.coordX" :precision="2" /></el-form-item>
        <el-form-item label="坐标 Y"><el-input-number v-model="form.coordY" :precision="2" /></el-form-item>
        <el-form-item label="设计孔深 m"><el-input-number v-model="form.designDepth" :precision="2" /></el-form-item>
        <el-form-item label="倾角 °"><el-input-number v-model="form.dipAngle" :precision="2" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option v-for="s in ['施工', '终孔', '封孔']" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { useHoleLoader } from '../hole-loader'
import { openHandoverForHole } from '../handover-nav'
import { openSamplesForHole } from '../sample-nav'

const mines = ref([])
const stats = ref([])
const mineId = ref(null)
const dialog = ref(false)
const form = ref({})

// 孔位加载与选中态同步：矿区切换时最后一次选择唯一生效，列表/平面图/详情保持一致
const { holes, selectedId, selected, select, loadHoles } = useHoleLoader({ mineId, mines })

const points = computed(() => {
  const list = holes.value
  if (!list.length) return []
  const xs = list.map((h) => Number(h.coordX))
  const ys = list.map((h) => Number(h.coordY))
  const ds = list.map((h) => Number(h.actualDepth))
  const minX = Math.min(...xs), maxX = Math.max(...xs)
  const minY = Math.min(...ys), maxY = Math.max(...ys)
  const maxD = Math.max(...ds, 1)
  return list.map((h) => ({
    ...h,
    x: 50 + ((Number(h.coordX) - minX) / (maxX - minX || 1)) * 530,
    y: 40 + (1 - (Number(h.coordY) - minY) / (maxY - minY || 1)) * 350,
    r: 6 + (Number(h.actualDepth) / maxD) * 12
  }))
})

const maxDepth = computed(() => Math.max(...holes.value.map((h) => Number(h.designDepth), 0), ...holes.value.map((h) => Number(h.actualDepth), 0), 100))

const ticks = computed(() => {
  const max = Math.ceil(maxDepth.value / 50) * 50
  const step = max > 600 ? 100 : 50
  const arr = []
  for (let v = 0; v <= max; v += step) arr.push(v)
  return arr
})

function depthY(d) {
  return 20 + (d / (Math.ceil(maxDepth.value / 50) * 50 || 1)) * 390
}

function num(v) {
  return v == null ? '—' : Number(v)
}

function statusColor(status) {
  if (status === '施工') return '#c05621'
  if (status === '终孔') return '#5d4037'
  if (status === '封孔') return '#a1887f'
  return '#8d6e63'
}

function openCreate() {
  form.value = { mineId: mineId.value, status: '施工', coordX: 0, coordY: 0, designDepth: 300, dipAngle: 90 }
  dialog.value = true
}

/** 从施工钻孔直接进入终孔交接台（班组视角选中该孔） */
function gotoHandover(h) {
  const tab = openHandoverForHole(h.id)
  window.dispatchEvent(new CustomEvent('dc:navigate', { detail: { tab } }))
}

/** 从孔资料进入取样送检（现场取样视角选中该孔） */
function gotoSample(h) {
  const tab = openSamplesForHole(h.id)
  window.dispatchEvent(new CustomEvent('dc:navigate', { detail: { tab } }))
}

async function submitCreate() {
  try {
    await api.createBorehole({ ...form.value, actualDepth: 0 })
    ElMessage.success('钻孔已创建')
    dialog.value = false
    loadHoles()
    loadStats()
  } catch (e) { /* 已提示 */ }
}

async function loadStats() {
  try {
    stats.value = await api.mineStats()
  } catch (e) { /* 已提示：统计失败不阻塞孔位加载 */ }
}

onMounted(async () => {
  mines.value = await api.mines()
  if (mines.value.length) mineId.value = mines.value[0].id
  await loadStats()
  await loadHoles()
})
</script>
