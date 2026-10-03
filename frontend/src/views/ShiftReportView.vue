<template>
  <div class="dc-page">
    <el-alert
      v-if="lockInfo"
      class="dc-lock-alert"
      type="error"
      :closable="false"
      show-icon
      :title="`钻孔 ${lockInfo.holeNo} 的终孔交接已接收（v${lockInfo.versionNo}），班报已冻结，不能新增、修改或删除；确需补录请由负责人返开。`"
    />

    <div class="dc-toolbar noprint">
      <span class="dc-label">钻机</span>
      <el-select v-model="filterRigId" size="small" style="width: 150px" clearable @change="loadReports">
        <el-option v-for="r in rigs" :key="r.id" :label="r.code" :value="r.id" />
      </el-select>
      <el-button size="small" type="primary" :disabled="anyLocked" @click="dialog = true">填写班报</el-button>
      <el-button size="small" @click="printSheet">打印 / 预览</el-button>
      <span class="dc-label">共 {{ reports.length }} 张</span>
    </div>

    <div class="dc-split noprint">
      <div class="dc-panel" style="width: 300px; flex: none">
        <div class="dc-panel-title">班报列表（点选看纸质报表）</div>
        <div
          v-for="r in reports"
          :key="r.id"
          class="dc-round-row"
          :class="{ on: r.id === selectedId, 'nav-hit': handoverNav.targetId === r.id }"
          @click="selectReport(r)"
        >
          <span style="width: 96px; font-weight: 700">{{ r.reportDate }}</span>
          <span style="width: 52px">{{ r.shiftType }}班</span>
          <span style="width: 96px">{{ holeNo(r.boreholeId) }}</span>
          <span style="width: 92px">进尺 {{ num(r.footage) }} m</span>
          <span style="color: #8a7761">{{ r.leaderSign || '未签字' }}</span>
        </div>
        <div v-if="!reports.length" style="font-size: 12px; color: #8a7761">暂无班报</div>
      </div>

      <div class="dc-panel" style="flex: 1; min-width: 340px">
        <div class="dc-panel-title">材料消耗（JSON 明细）</div>
        <div v-for="m in materials" :key="m.name + m.spec" class="dc-round-row">
          <span style="width: 92px">{{ m.category }}</span>
          <span style="width: 130px; font-weight: 700">{{ m.name }}</span>
          <span style="width: 110px">{{ m.spec || '—' }}</span>
          <span>{{ num(m.qty) }} {{ m.unit }}</span>
        </div>
        <div v-if="!materials.length" style="font-size: 12px; color: #8a7761">选中班报后显示材料明细</div>
      </div>
    </div>

    <!-- 纸质班报表 -->
    <div class="dc-sheet" v-if="selected">
      <div class="dc-sheet-title">{{ mineNameOf(selected) }} 钻探班报表</div>
      <div class="dc-sheet-sub">编号 SR-{{ String(selected.id).padStart(4, '0') }} ｜ {{ selected.reportDate }} {{ selected.shiftType }}班</div>

      <table>
        <tr>
          <th>钻机编号</th><td>{{ rigCode(selected.rigId) }}</td>
          <th>施工孔号</th><td>{{ holeNo(selected.boreholeId) }}</td>
        </tr>
        <tr>
          <th>班组长</th><td>{{ crewLeader(selected.crewId) }}</td>
          <th>本班进尺</th><td>{{ num(selected.footage) }} m</td>
        </tr>
        <tr>
          <th>纯钻时间</th><td>{{ num(selected.pureHours) }} h</td>
          <th>辅助时间</th><td>{{ num(selected.auxHours) }} h</td>
        </tr>
        <tr>
          <th>时效分析</th>
          <td colspan="3">
            纯钻占比 {{ pureRatio(selected) }}% ｜ 台班效率 {{ perShift(selected) }} m/班
          </td>
        </tr>
        <tr>
          <th>事故与停待</th><td colspan="3">{{ selected.accidentNote || '本班无事故、无停待' }}</td>
        </tr>
        <tr>
          <th>交接班备注</th><td colspan="3">{{ selected.handoverNote || '—' }}</td>
        </tr>
      </table>

      <div style="margin-top: 12px; font-weight: 700; font-size: 13px">材料消耗明细</div>
      <table style="margin-top: 6px">
        <tr>
          <th style="width: auto">类别</th>
          <th style="width: auto">名称</th>
          <th style="width: auto">规格</th>
          <th style="width: auto">数量</th>
        </tr>
        <tr v-for="m in materials" :key="m.name + m.spec">
          <td style="width: auto">{{ m.category }}</td>
          <td style="width: auto">{{ m.name }}</td>
          <td style="width: auto">{{ m.spec || '—' }}</td>
          <td style="width: auto">{{ num(m.qty) }} {{ m.unit }}</td>
        </tr>
        <tr v-if="!materials.length">
          <td colspan="4" style="text-align: center; color: #8a7761">本班无材料消耗</td>
        </tr>
      </table>

      <div class="dc-sheet-sign">
        <span>班组长签字：{{ selected.leaderSign || '____________' }}</span>
        <span>机长核对：____________</span>
        <span>日期：{{ selected.reportDate }}</span>
      </div>
    </div>

    <el-dialog v-model="dialog" title="填写班报" width="560px">
      <el-form :model="form" label-width="104px" size="small">
        <el-form-item label="施工孔号">
          <el-select v-model="form.boreholeId" style="width: 100%">
            <el-option v-for="h in holes" :key="h.id" :label="h.holeNo" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="钻机">
          <el-select v-model="form.rigId" style="width: 100%">
            <el-option v-for="r in rigs" :key="r.id" :label="r.code + ' · ' + r.model" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班组">
          <el-select v-model="form.crewId" style="width: 100%" clearable>
            <el-option v-for="c in crews" :key="c.id" :label="c.leader + '（' + c.shiftType + '班）'" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="form.reportDate" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="班别">
          <el-select v-model="form.shiftType">
            <el-option v-for="s in ['早', '中', '夜']" :key="s" :label="s + '班'" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="本班进尺 m"><el-input-number v-model="form.footage" :precision="2" /></el-form-item>
        <el-form-item label="纯钻时间 h"><el-input-number v-model="form.pureHours" :precision="2" /></el-form-item>
        <el-form-item label="辅助时间 h"><el-input-number v-model="form.auxHours" :precision="2" /></el-form-item>
        <el-form-item label="事故与停待"><el-input v-model="form.accidentNote" /></el-form-item>
        <el-form-item label="交接班备注"><el-input v-model="form.handoverNote" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="班组长签字"><el-input v-model="form.leaderSign" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreate">保存班报</el-button>
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

const rigs = ref([])
const holes = ref([])
const crews = ref([])
const mines = ref([])
const reports = ref([])
const selectedId = ref(null)
const filterRigId = ref(null)
const dialog = ref(false)
const form = ref({})

/** 各钻孔生效交接（已接收则班报冻结） */
const activeMap = ref({})

const selected = computed(() => reports.value.find((r) => r.id === selectedId.value) || null)
const materials = computed(() => (selected.value && selected.value.materials) || [])

/** 当前选中班报所在孔已接收冻结 */
const lockInfo = computed(() => {
  const r = selected.value
  if (!r) return null
  const h = activeMap.value[r.boreholeId]
  if (h && h.status === 'ACCEPTED') {
    return { holeNo: holeNo(r.boreholeId), versionNo: h.versionNo }
  }
  return null
})

/** 列表中只要有已接收孔，就禁用新增入口（新增表单无法挂到冻结孔之外的判断在后端硬拦） */
const anyLocked = computed(() => Object.values(activeMap.value).some((h) => h.status === 'ACCEPTED'))

function num(v) {
  return v == null ? '—' : Number(v)
}

function rigCode(id) {
  const r = rigs.value.find((x) => x.id === id)
  return r ? r.code : '—'
}

function holeNo(id) {
  const h = holes.value.find((x) => x.id === id)
  return h ? h.holeNo : '—'
}

function crewLeader(id) {
  const c = crews.value.find((x) => x.id === id)
  return c ? c.leader : '—'
}

function mineNameOf(r) {
  const h = holes.value.find((x) => x.id === r.boreholeId)
  if (!h) return '钻探'
  const m = mines.value.find((x) => x.id === h.mineId)
  return m ? m.name : '钻探'
}

function pureRatio(r) {
  const pure = Number(r.pureHours || 0)
  const aux = Number(r.auxHours || 0)
  if (pure + aux === 0) return '0.00'
  return ((pure / (pure + aux)) * 100).toFixed(2)
}

function perShift(r) {
  const pure = Number(r.pureHours || 0)
  if (!pure) return '0.00'
  return (Number(r.footage || 0) / (pure / 8)).toFixed(2)
}

function selectReport(r) {
  selectedId.value = r.id
  setDetail({
    title: r.reportDate + ' ' + r.shiftType + '班',
    subtitle: rigCode(r.rigId) + ' · ' + holeNo(r.boreholeId),
    tag: '班报表',
    fields: [
      { label: '班报日期', value: r.reportDate },
      { label: '班别', value: r.shiftType + '班' },
      { label: '钻机', value: rigCode(r.rigId) },
      { label: '施工孔号', value: holeNo(r.boreholeId) },
      { label: '班组长', value: crewLeader(r.crewId) },
      { label: '本班进尺 (m)', value: num(r.footage) },
      { label: '纯钻时间 (h)', value: num(r.pureHours) },
      { label: '辅助时间 (h)', value: num(r.auxHours) },
      { label: '纯钻占比 (%)', value: pureRatio(r) },
      { label: '事故与停待', value: r.accidentNote || '—' },
      { label: '交接班备注', value: r.handoverNote || '—' },
      { label: '班组长签字', value: r.leaderSign || '—' }
    ],
    json: r.materials || [],
    jsonTitle: 'JSON 材料消耗明细'
  })
}

function printSheet() {
  window.print()
}

async function submitCreate() {
  if (!form.value.boreholeId || !form.value.rigId) {
    ElMessage.warning('请选择施工孔号与钻机')
    return
  }
  await api.createReport({ ...form.value, materials: [] })
  ElMessage.success('班报已保存')
  dialog.value = false
  await loadReports()
}

async function loadReports() {
  reports.value = await api.reports(filterRigId.value ? { rigId: filterRigId.value } : {})
  if (reports.value.length && !selectedId.value) selectReport(reports.value[0])
  locateNavTarget()
}

/** 从交接台「班报缺签字」问题项跳回：选中对应班报 */
function locateNavTarget() {
  if (!handoverNav.fromHandover || handoverNav.target !== 'report') return
  const target = reports.value.find((r) => r.id === handoverNav.targetId)
  if (target) {
    selectedId.value = target.id
    selectReport(target)
  }
}

watch(
  () => handoverNav.nonce,
  async () => {
    if (handoverNav.target !== 'report') return
    // 缺签字问题可能挂在当前钻机过滤之外，先清空过滤拉全量
    filterRigId.value = null
    await loadReports()
    locateNavTarget()
  }
)

onMounted(async () => {
  rigs.value = await api.rigs()
  holes.value = await api.boreholes()
  crews.value = await api.crews()
  mines.value = await api.mines()
  try {
    const all = await api.handovers()
    const map = {}
    all.forEach((h) => { map[h.boreholeId] = h })
    activeMap.value = map
  } catch (e) { /* 忽略 */ }
  await loadReports()
})
</script>
