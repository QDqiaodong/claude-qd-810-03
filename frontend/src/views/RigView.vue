<template>
  <div class="dc-page">
    <div class="dc-panel">
      <div class="dc-panel-title">钻机（卡片上直接改状态，点卡片看技术参数包）</div>
      <div class="dc-rig-grid">
        <div
          v-for="r in rigs"
          :key="r.id"
          class="dc-rig-card"
          :class="{ on: r.id === selectedId }"
          @click="selectRig(r)"
        >
          <div style="display: flex; align-items: center; gap: 8px">
            <span class="dc-rig-code">{{ r.code }}</span>
            <span style="font-size: 11px; padding: 0 6px; border: 1px solid #8d6e63; border-radius: 2px; color: #5d4037">
              {{ r.status }}
            </span>
          </div>
          <div class="dc-rig-model">{{ r.model }} · {{ r.manufacturer || '—' }}</div>
          <div class="dc-rig-meta">
            最大钻深 {{ num(r.maxDepth) }} m ｜ 功率 {{ num(r.enginePower) }} kW<br />
            投产 {{ r.commissionedOn || '—' }}
          </div>
          <div style="margin-top: 8px" @click.stop>
            <el-select
              v-model="r.status"
              size="small"
              style="width: 110px"
              @change="changeStatus(r)"
            >
              <el-option v-for="s in ['运行', '搬迁', '维修', '停用']" :key="s" :label="s" :value="s" />
            </el-select>
          </div>
        </div>
      </div>
    </div>

    <div class="dc-panel">
      <div class="dc-panel-title">台月效率（按 30 班 × 8 h 折算）</div>
      <div v-for="e in efficiency" :key="e.rigId" class="dc-eff-row">
        <span class="dc-eff-name">{{ e.rigCode }}</span>
        <span class="dc-eff-bar">
          <span class="dc-eff-fill" :style="{ width: effWidth(e) + '%' }"></span>
        </span>
        <span class="dc-eff-num">
          {{ num(e.meterPerMonth) }} m/台月 ｜ {{ e.shiftCount }} 班 ｜ 纯钻 {{ num(e.pureRatio) }}%
        </span>
      </div>
    </div>

    <div class="dc-panel">
      <div class="dc-panel-title">班组（可新增 / 撤销）</div>
      <div class="dc-toolbar noprint" style="margin-bottom: 8px">
        <el-select v-model="crewForm.rigId" size="small" style="width: 150px" placeholder="所属钻机">
          <el-option v-for="r in rigs" :key="r.id" :label="r.code" :value="r.id" />
        </el-select>
        <el-input v-model="crewForm.leader" size="small" style="width: 120px" placeholder="班组长" />
        <el-input-number v-model="crewForm.workerCount" size="small" :min="1" style="width: 110px" />
        <el-select v-model="crewForm.shiftType" size="small" style="width: 92px">
          <el-option v-for="s in ['早', '中', '夜']" :key="s" :label="s + '班'" :value="s" />
        </el-select>
        <el-input v-model="crewForm.phone" size="small" style="width: 140px" placeholder="联系电话" />
        <el-button size="small" type="primary" @click="addCrew">新增班组</el-button>
      </div>

      <div v-for="c in crews" :key="c.id" class="dc-round-row">
        <span style="width: 110px; font-weight: 700">{{ rigCode(c.rigId) }}</span>
        <span style="width: 92px">{{ c.leader }}（{{ c.workerCount }} 人）</span>
        <span style="width: 62px">{{ c.shiftType }}班</span>
        <span style="width: 130px; color: #8a7761">{{ c.phone || '—' }}</span>
        <span style="flex: 1; color: #8a7761">{{ c.note || '—' }}</span>
        <el-button size="small" type="danger" plain @click="removeCrew(c.id)">撤销</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
import { setDetail } from '../detail'

const rigs = ref([])
const efficiency = ref([])
const crews = ref([])
const selectedId = ref(null)
const crewForm = ref({ rigId: null, leader: '', workerCount: 4, shiftType: '早', phone: '' })

function num(v) {
  return v == null ? '—' : Number(v)
}

function rigCode(id) {
  const r = rigs.value.find((x) => x.id === id)
  return r ? r.code : '—'
}

function effWidth(e) {
  const max = Math.max(...efficiency.value.map((x) => Number(x.meterPerMonth || 0)), 1)
  return Math.round((Number(e.meterPerMonth || 0) / max) * 100)
}

function selectRig(r) {
  selectedId.value = r.id
  setDetail({
    title: r.code + ' · ' + r.model,
    subtitle: r.manufacturer || '—',
    tag: r.status,
    fields: [
      { label: '钻机编号', value: r.code },
      { label: '型号', value: r.model },
      { label: '厂家', value: r.manufacturer || '—' },
      { label: '最大钻深 (m)', value: num(r.maxDepth) },
      { label: '发动机功率 (kW)', value: num(r.enginePower) },
      { label: '投产日期', value: r.commissionedOn || '—' },
      { label: '当前状态', value: r.status }
    ],
    json: r.specs || {},
    jsonTitle: 'JSON 技术参数包',
    jsonEditable: true,
    onSave: async (specs) => {
      const updated = await api.updateRig(r.id, { ...r, specs })
      const idx = rigs.value.findIndex((x) => x.id === r.id)
      if (idx >= 0) rigs.value[idx] = updated
      setDetail({ json: updated.specs || {} })
    }
  })
}

async function changeStatus(r) {
  const updated = await api.updateRig(r.id, { ...r })
  ElMessage.success('状态已更新：' + updated.status)
}

async function addCrew() {
  if (!crewForm.value.rigId || !crewForm.value.leader) {
    ElMessage.warning('请选择钻机并填写班组长')
    return
  }
  await api.createCrew({ ...crewForm.value })
  ElMessage.success('班组已创建')
  crewForm.value = { rigId: crewForm.value.rigId, leader: '', workerCount: 4, shiftType: '早', phone: '' }
  crews.value = await api.crews()
}

async function removeCrew(id) {
  await api.deleteCrew(id)
  ElMessage.success('班组已撤销')
  crews.value = await api.crews()
}

onMounted(async () => {
  rigs.value = await api.rigs()
  efficiency.value = await api.rigEfficiency()
  crews.value = await api.crews()
})
</script>
