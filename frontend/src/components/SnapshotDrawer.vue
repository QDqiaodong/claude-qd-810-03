<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    :title="drawerTitle"
    size="640px"
  >
    <div v-if="!detail" style="font-size: 12px; color: #8a7761">无版本数据</div>
    <template v-else>
      <el-alert type="info" :closable="false" style="margin-bottom: 12px"
        :title="`以下为 v${snap.hole ? handover.versionNo : ''} 发起时的资料快照（孔深 ${num(snap.actualDepth)} m），只读，不受后续录入改写影响。`"
      />

      <!-- 孔信息 -->
      <div class="dc-snap-block">
        <div class="dc-snap-title">钻孔信息</div>
        <div class="dc-snap-grid">
          <span>孔号：{{ snap.hole?.holeNo }}</span>
          <span>设计孔深：{{ num(snap.hole?.designDepth) }} m</span>
          <span>实际孔深：{{ num(snap.hole?.actualDepth) }} m</span>
          <span>倾角：{{ num(snap.hole?.dipAngle) }}°</span>
          <span>开孔：{{ snap.hole?.spudDate || '—' }}</span>
          <span>终孔：{{ snap.hole?.finalDate || '—' }}</span>
          <span>状态：{{ snap.hole?.status }}</span>
        </div>
      </div>

      <!-- 回次 -->
      <div class="dc-snap-block">
        <div class="dc-snap-title">回次（{{ snap.rounds?.length || 0 }}）</div>
        <el-table :data="snap.rounds || []" size="small" border>
          <el-table-column prop="roundNo" label="回次" width="56" />
          <el-table-column label="深度 m" width="150">
            <template #default="{ row }">{{ num(row.fromDepth) }} ~ {{ num(row.toDepth) }}</template>
          </el-table-column>
          <el-table-column label="采取率%" width="80">
            <template #default="{ row }">
              <span :style="{ color: Number(row.recoveryRate) < 75 ? '#c05621' : '' }">{{ num(row.recoveryRate) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="rqd" label="RQD" width="64" />
          <el-table-column prop="lowRecoveryReason" label="低采取率原因">
            <template #default="{ row }">{{ row.lowRecoveryReason || '—' }}</template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 岩性层 -->
      <div class="dc-snap-block">
        <div class="dc-snap-title">岩性层（{{ snap.layers?.length || 0 }}）</div>
        <el-table :data="snap.layers || []" size="small" border max-height="260">
          <el-table-column label="回次" width="60">
            <template #default="{ row }">{{ roundNoOf(row.roundId) }}</template>
          </el-table-column>
          <el-table-column prop="layerSeq" label="层序" width="56" />
          <el-table-column prop="lithology" label="岩性" width="110" />
          <el-table-column label="深度 m" width="150">
            <template #default="{ row }">{{ num(row.fromDepth) }} ~ {{ num(row.toDepth) }}</template>
          </el-table-column>
          <el-table-column prop="weathering" label="风化" width="80" />
          <el-table-column prop="description" label="描述" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 参数时序 -->
      <div class="dc-snap-block">
        <div class="dc-snap-title">钻进参数时序（{{ snap.paramSeries?.length || 0 }} 条）</div>
        <div v-for="s in snap.paramSeries || []" :key="s.id" class="dc-snap-series">
          <div>
            <b>{{ s.recordDate }} {{ s.shiftType }}班</b> · {{ s.operator || '—' }} · {{ (s.points || []).length }} 点
            <el-tag size="small" :type="abnormalOf(s).count ? 'danger' : 'success'" style="margin-left: 8px">
              异常 {{ abnormalOf(s).count }} 点
            </el-tag>
          </div>
          <div v-if="abnormalOf(s).count" style="font-size: 11px; color: #c05621; margin-top: 2px">
            <span v-for="(p, idx) in abnormalOf(s).points" :key="idx">
              {{ p.time }} {{ p.items.join('、') }}；
            </span>
          </div>
        </div>
      </div>

      <!-- 班报 -->
      <div class="dc-snap-block">
        <div class="dc-snap-title">班报（{{ snap.reports?.length || 0 }} 张）</div>
        <el-table :data="snap.reports || []" size="small" border>
          <el-table-column prop="reportDate" label="日期" width="110" />
          <el-table-column prop="shiftType" label="班别" width="56" />
          <el-table-column label="进尺 m" width="80">
            <template #default="{ row }">{{ num(row.footage) }}</template>
          </el-table-column>
          <el-table-column label="纯钻/辅助 h" width="110">
            <template #default="{ row }">{{ num(row.pureHours) }} / {{ num(row.auxHours) }}</template>
          </el-table-column>
          <el-table-column label="班组长签字">
            <template #default="{ row }">
              <span :style="{ color: row.leaderSign ? '#5b8c5a' : '#c05621' }">
                {{ row.leaderSign || '未签字' }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  detail: { type: Object, default: null }
})

defineEmits(['update:modelValue'])

const handover = computed(() => props.detail?.handover || {})
const snap = computed(() => props.detail?.handover?.snapshot || {})

const drawerTitle = computed(() => {
  if (!handover.value.id) return '版本快照'
  const statusMap = { PENDING_CHECK: '待检查', NEED_FIX: '待整改', ACCEPTED: '已接收', SUPERSEDED: '已失效' }
  return `v${handover.value.versionNo} 资料快照（${statusMap[handover.value.status] || ''}）`
})

function num(v) {
  return v == null ? '—' : Number(v)
}

const roundMap = computed(() => {
  const m = {}
  ;(snap.value.rounds || []).forEach((r) => { m[r.id] = r.roundNo })
  return m
})

function roundNoOf(roundId) {
  return roundMap.value[roundId] ?? '—'
}

const RANGES = {
  pressure: [8, 45],
  rotation: [60, 450],
  pump: [60, 260],
  mudDensity: [1.05, 1.35]
}
const LABELS = { pressure: '钻压', rotation: '转速', pump: '泵量', mudDensity: '泥浆密度' }

function abnormalOf(series) {
  const points = []
  for (const p of series.points || []) {
    const items = []
    for (const key of Object.keys(RANGES)) {
      const v = Number(p[key])
      if (v < RANGES[key][0]) items.push(LABELS[key] + '偏低')
      else if (v > RANGES[key][1]) items.push(LABELS[key] + '偏高')
    }
    if (items.length) points.push({ time: p.time, items })
  }
  return { count: points.length, points }
}
</script>
