<template>
  <div class="dc-page">
    <div class="dc-toolbar">
      <el-radio-group v-model="role" size="small">
        <el-radio-button label="field">现场取样</el-radio-button>
        <el-radio-button label="lab">实验室收样</el-radio-button>
      </el-radio-group>

      <template v-if="role === 'field'">
        <span class="dc-label">钻孔</span>
        <el-select v-model="holeFilter" size="small" style="width: 190px" clearable placeholder="全部钻孔"
                   @change="loadSamples">
          <el-option v-for="h in holes" :key="h.id" :label="h.holeNo" :value="h.id" />
        </el-select>
        <span class="dc-label">状态</span>
        <el-select v-model="sampleStatusFilter" size="small" style="width: 150px" clearable placeholder="全部"
                   @change="loadSamples">
          <el-option v-for="s in SAMPLE_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="operator" size="small" style="width: 130px" placeholder="取样人 / 封样人" />
        <el-button size="small" type="primary" @click="openSampleDialog()">取样登记</el-button>
        <el-button size="small" type="success" @click="openPackDialog">封样送检（{{ packable.length }} 件待送检）</el-button>
        <el-button size="small" @click="reloadField">刷新</el-button>
      </template>

      <template v-else>
        <span class="dc-label">批次状态</span>
        <el-select v-model="batchStatusFilter" size="small" style="width: 150px" clearable placeholder="全部"
                   @change="loadBatches">
          <el-option v-for="s in BATCH_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-input v-model="operator" size="small" style="width: 130px" placeholder="接收人姓名" />
        <el-button size="small" @click="loadBatches">刷新</el-button>
        <span style="color: #8a7761; font-size: 11px">逐样核对状态、数量、封签；可整批退回，部分异常时保留已接收样品</span>
      </template>
    </div>

    <!-- ===================== 现场取样视角 ===================== -->
    <template v-if="role === 'field'">
      <!-- 批次进度条：当前批次与筛选刷新后仍保留 -->
      <div v-if="activeBatches.length" class="dc-panel">
        <div class="dc-panel-title">
          与我相关的在途/接收批次
          <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">点批次查看明细与实验室回写结果</span>
        </div>
        <div class="dc-sample-batch-chips">
          <div v-for="b in activeBatches" :key="b.id" class="dc-batch-chip"
               :class="{ on: selectedBatchId === b.id }" @click="openBatch(b.id)">
            <b>{{ b.batchNo }}</b>
            <el-tag size="small" :type="batchTagType(b.status)">{{ batchStatusName(b.status) }}</el-tag>
            <span class="dc-chip-meta">{{ b.expectedCount }} 件 · 封样 {{ fmt(b.packedAt) }}</span>
          </div>
        </div>
      </div>

      <div class="dc-panel">
        <div class="dc-panel-title">
          样品登记台账（{{ samples.length }}）
          <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">
            缺编号/深度/取样人/封签的记录仅保存草稿；同孔孔段不可重叠，编号、封签唯一
          </span>
        </div>
        <el-table :data="samples" size="small" stripe @row-click="showSampleDetail"
                  :header-cell-style="{ background: '#efe6d6' }" style="cursor: pointer">
          <el-table-column label="样品编号" min-width="140">
            <template #default="{ row }">
              {{ row.sampleNo || '—' }}
              <el-tag v-if="row.status === 'DRAFT'" size="small" type="info" style="margin-left: 4px">草稿</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="孔号" width="92">
            <template #default="{ row }">{{ holeNo(row.boreholeId) }}</template>
          </el-table-column>
          <el-table-column label="孔段 (m)" width="130">
            <template #default="{ row }">
              {{ row.fromDepth == null ? '—' : num(row.fromDepth) }} ~ {{ row.toDepth == null ? '—' : num(row.toDepth) }}
            </template>
          </el-table-column>
          <el-table-column prop="sampler" label="取样人" width="90">
            <template #default="{ row }">{{ row.sampler || '—' }}</template>
          </el-table-column>
          <el-table-column prop="sealNo" label="封签号" min-width="110">
            <template #default="{ row }">{{ row.sealNo || '—' }}</template>
          </el-table-column>
          <el-table-column prop="sampleType" label="类型" width="80" />
          <el-table-column label="状态" width="118">
            <template #default="{ row }">
              <el-tag size="small" :type="sampleTagType(row.status)">{{ sampleStatusName(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="登记时间" width="128">
            <template #default="{ row }">{{ fmt(row.registeredAt || row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text type="primary"
                         :disabled="!editable(row)"
                         @click.stop="openSampleDialog(row)">补全/编辑</el-button>
              <el-button size="small" text type="danger"
                         :disabled="!editable(row)"
                         @click.stop="removeSample(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ===================== 实验室收样视角 ===================== -->
    <template v-else>
      <div class="dc-split" style="align-items: flex-start">
        <div class="dc-panel" style="width: 330px; flex: none">
          <div class="dc-panel-title">送检批次（{{ batches.length }}）</div>
          <div v-for="b in batches" :key="b.id" class="dc-round-row"
               :class="{ on: b.id === selectedBatchId }" style="cursor: pointer" @click="openBatch(b.id)">
            <span style="width: 128px; font-weight: 700">{{ b.batchNo }}</span>
            <el-tag size="small" :type="batchTagType(b.status)">{{ batchStatusName(b.status) }}</el-tag>
            <span style="margin-left: 6px; color: #8a7761; font-size: 11px">
              {{ b.expectedCount }} 件<template v-if="b.abnormalCount > 0"> / 待补 {{ b.abnormalCount }}</template>
            </span>
          </div>
          <div v-if="!batches.length" style="font-size: 12px; color: #8a7761">暂无批次</div>
        </div>

        <div style="flex: 1; min-width: 460px">
          <div v-if="!batchDetail" class="dc-panel" style="color: #8a7761; font-size: 12px">
            请在左侧选择送检批次，核对样品明细后接收或整批退回。
          </div>
          <BatchDetail v-else :detail="batchDetail" :operator="operator" :holes="holes"
                       :on-receive="onReceive" :on-return="onReturn"
                       @refresh="openBatch(batchDetail.batch.id)" />
        </div>
      </div>
    </template>

    <!-- 现场也可查看批次明细（只读 + 事件链） -->
    <el-drawer v-if="role === 'field'" v-model="fieldBatchDrawer" title="送检批次明细与交接记录" size="640px">
      <BatchDetail v-if="batchDetail" :detail="batchDetail" :operator="operator" :holes="holes"
                   :readonly="true" @refresh="openBatch(batchDetail.batch.id)" />
    </el-drawer>

    <!-- 取样登记 / 草稿补全 -->
    <el-dialog v-model="sampleDialog" :title="form.id ? '补全 / 编辑样品登记' : '岩芯样品取样登记'" width="520px">
      <el-alert
        v-if="!form.id"
        title="样品编号、起止深度、取样人、封签号是关键信息：齐全则正式登记；缺任一项仅保存为草稿，补齐后自动转正式。"
        type="info" :closable="false" style="margin-bottom: 12px"
      />
      <el-form :model="form" label-width="96px" size="small">
        <el-form-item label="钻孔孔段" required>
          <el-select v-model="form.boreholeId" style="width: 100%" placeholder="选择钻孔（指定孔段）">
            <el-option v-for="h in holes" :key="h.id"
                       :label="h.holeNo + '（当前孔深 ' + num(h.actualDepth) + ' m）'" :value="h.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="样品编号" required>
          <el-input v-model="form.sampleNo" placeholder="如 HQ-2403-H03，全局唯一" />
        </el-form-item>
        <el-form-item label="起始深度 m" required>
          <el-input-number v-model="form.fromDepth" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="终止深度 m" required>
          <el-input-number v-model="form.toDepth" :precision="2" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="取样人" required>
          <el-input v-model="form.sampler" placeholder="现场取样人姓名" />
        </el-form-item>
        <el-form-item label="封签号" required>
          <el-input v-model="form.sealNo" placeholder="如 FS-2403-03，全局唯一" />
        </el-form-item>
        <el-form-item label="样品类型">
          <el-select v-model="form.sampleType" style="width: 100%" clearable placeholder="岩芯 / 岩屑 / 水样 / 矿化样">
            <el-option v-for="t in SAMPLE_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="取样描述、岩性、异常情况" />
        </el-form-item>
      </el-form>
      <div style="font-size: 11px; color: #8a7761">
        保存时后端会再次校验：同孔正式样品孔段不重叠、编号与封签唯一。
      </div>
      <template #footer>
        <el-button size="small" @click="sampleDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submitSample">
          {{ canRegister ? '正式登记' : '保存草稿' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 封样送检：勾选待送检样品，显示明细后生成批次 -->
    <el-dialog v-model="packDialog" title="封样送检 · 生成送检批次" width="720px">
      <el-alert
        title="批次创建后即封样发运，样品明细定稿、不可增删；实验室将按明细逐样核对数量与封签。"
        type="warning" :closable="false" style="margin-bottom: 10px"
      />
      <div style="margin-bottom: 8px; display: flex; gap: 10px; align-items: center">
        <span class="dc-label">钻孔过滤</span>
        <el-select v-model="packHoleFilter" size="small" style="width: 180px" clearable placeholder="全部钻孔"
                   @change="loadPackable">
          <el-option v-for="h in holes" :key="h.id" :label="h.holeNo" :value="h.id" />
        </el-select>
        <el-button size="small" @click="loadPackable">刷新待送检</el-button>
        <span style="font-size: 11px; color: #8a7761">已选 {{ packSelected.length }} 件</span>
      </div>
      <el-table :data="packable" size="small" max-height="280" row-key="id"
                @selection-change="onPackSelection" ref="packTableRef"
                :header-cell-style="{ background: '#efe6d6' }">
        <el-table-column type="selection" width="42" reserve-selection />
        <el-table-column prop="sampleNo" label="样品编号" min-width="130" />
        <el-table-column label="孔号" width="90">
          <template #default="{ row }">{{ holeNo(row.boreholeId) }}</template>
        </el-table-column>
        <el-table-column label="孔段 (m)" width="130">
          <template #default="{ row }">{{ num(row.fromDepth) }} ~ {{ num(row.toDepth) }}</template>
        </el-table-column>
        <el-table-column prop="sampler" label="取样人" width="86" />
        <el-table-column prop="sealNo" label="封签号" min-width="105" />
      </el-table>

      <!-- 交接样品明细（勾选即显示，封样前最后确认） -->
      <div v-if="packSelected.length" class="dc-pack-summary">
        <b>本次封样明细（{{ packSelected.length }} 件）：</b>
        <el-tag v-for="s in packSelected" :key="s.id" size="small" style="margin: 2px">
          {{ s.sampleNo }} · {{ holeNo(s.boreholeId) }} · {{ num(s.fromDepth) }}~{{ num(s.toDepth) }} · 封签 {{ s.sealNo }}
        </el-tag>
      </div>

      <el-form label-width="80px" size="small" style="margin-top: 10px">
        <el-form-item label="封样人" required>
          <el-input v-model="packForm.packer" placeholder="封样责任人" style="width: 220px" />
        </el-form-item>
        <el-form-item label="送样人">
          <el-input v-model="packForm.carrier" placeholder="运输 / 送样人" style="width: 220px" />
        </el-form-item>
        <el-form-item label="运输说明">
          <el-input v-model="packForm.note" type="textarea" :rows="2" placeholder="包装方式、运输方式、随附送样单" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="packDialog = false">取消</el-button>
        <el-button size="small" type="success" :loading="packing" @click="submitPack">确认封样发运</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import BatchDetail from '../components/BatchDetail.vue'
export default { name: 'SampleView', components: { BatchDetail } }
</script>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { setDetail } from '../detail'
import { sampleNav } from '../sample-nav'
import BatchDetail from '../components/BatchDetail.vue'

// ---------------- 常量 ----------------
const SAMPLE_STATUS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'REGISTERED', label: '已登记待送检' },
  { value: 'IN_TRANSIT', label: '运输中' },
  { value: 'RECEIVED', label: '实验室已接收' },
  { value: 'ABNORMAL', label: '待补（接收异常）' }
]
const BATCH_STATUS = [
  { value: 'IN_TRANSIT', label: '运输中' },
  { value: 'RECEIVED', label: '已接收' },
  { value: 'PARTIAL_RECEIVED', label: '部分接收' },
  { value: 'RETURNED', label: '已退回' }
]
const SAMPLE_TYPES = ['岩芯', '岩屑', '水样', '矿化样']

// ---------------- 持久化状态（刷新 / 从孔资料返回后保留筛选与当前批次） ----------------
const LS_KEY = 'dc_sample_ui'

function loadUiState() {
  try {
    return JSON.parse(localStorage.getItem(LS_KEY) || '{}')
  } catch (e) {
    return {}
  }
}
function persist() {
  localStorage.setItem(LS_KEY, JSON.stringify({
    role: role.value,
    operator: operator.value,
    holeFilter: holeFilter.value,
    sampleStatusFilter: sampleStatusFilter.value,
    batchStatusFilter: batchStatusFilter.value,
    selectedBatchId: selectedBatchId.value,
    packHoleFilter: packHoleFilter.value
  }))
}

const saved = loadUiState()
const role = ref(saved.role || 'field')
const operator = ref(saved.operator || localStorage.getItem('dc_operator') || '')
const holeFilter = ref(saved.holeFilter ?? null)
const sampleStatusFilter = ref(saved.sampleStatusFilter ?? null)
const batchStatusFilter = ref(saved.batchStatusFilter ?? null)
const selectedBatchId = ref(saved.selectedBatchId ?? null)
const packHoleFilter = ref(saved.packHoleFilter ?? null)

watch([role, operator, holeFilter, sampleStatusFilter, batchStatusFilter, selectedBatchId, packHoleFilter],
  persist)

// ---------------- 数据 ----------------
const holes = ref([])
const samples = ref([])
const batches = ref([])
const batchDetail = ref(null)
const fieldBatchDrawer = ref(false)

const sampleDialog = ref(false)
const saving = ref(false)
const emptyForm = () => ({
  id: null, boreholeId: holeFilter.value || null, sampleNo: '', fromDepth: undefined,
  toDepth: undefined, sampler: '', sealNo: '', sampleType: '岩芯', remark: '', lockVersion: 0
})
const form = ref(emptyForm())

const packDialog = ref(false)
const packing = ref(false)
const packable = ref([])
const packSelected = ref([])
const packTableRef = ref(null)
const packForm = ref({ packer: operator.value || '', carrier: '', note: '' })

// ---------------- 派生 ----------------
const canRegister = computed(() =>
  form.value.boreholeId != null
  && (form.value.sampleNo || '').trim()
  && form.value.fromDepth != null
  && form.value.toDepth != null
  && (form.value.sampler || '').trim()
  && (form.value.sealNo || '').trim()
)

/** 现场视角顶部的相关批次：在途优先，其次按批次倒序，取前 5 条 */
const activeBatches = computed(() => {
  const weight = (s) => (s === 'IN_TRANSIT' ? 0 : s === 'PARTIAL_RECEIVED' ? 1 : 2)
  return [...batches.value]
    .sort((a, b) => weight(a.status) - weight(b.status) || b.id - a.id)
    .slice(0, 5)
})

// ---------------- 工具 ----------------
function num(v) { return v == null ? '—' : Number(v) }
function fmt(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '—' }
function holeNo(id) {
  const h = holes.value.find((x) => x.id === id)
  return h ? h.holeNo : '#' + id
}
function sampleStatusName(c) {
  return { DRAFT: '草稿', REGISTERED: '已登记待送检', IN_TRANSIT: '运输中', RECEIVED: '已接收', ABNORMAL: '待补（接收异常）' }[c] || c
}
function sampleTagType(c) {
  return { DRAFT: 'info', REGISTERED: 'warning', IN_TRANSIT: 'primary', RECEIVED: 'success', ABNORMAL: 'danger' }[c] || ''
}
function batchStatusName(c) {
  return { IN_TRANSIT: '运输中', RECEIVED: '已接收', PARTIAL_RECEIVED: '部分接收', RETURNED: '已退回' }[c] || c
}
function batchTagType(c) {
  return { IN_TRANSIT: 'warning', RECEIVED: 'success', PARTIAL_RECEIVED: 'danger', RETURNED: 'info' }[c] || ''
}
/** 已发运/已接收/待补的样品现场不可编辑删除 */
function editable(row) {
  return row.status === 'DRAFT' || row.status === 'REGISTERED'
}

// ---------------- 加载 ----------------
async function ensureHoles() {
  if (!holes.value.length) holes.value = await api.boreholes()
}

async function loadSamples() {
  samples.value = await api.samples({
    boreholeId: holeFilter.value || undefined,
    status: sampleStatusFilter.value || undefined
  })
}

async function loadBatches() {
  batches.value = await api.sampleBatches(batchStatusFilter.value || undefined)
}

async function reloadField() {
  await Promise.all([loadSamples(), loadBatches(), loadPackable()])
  // 若当前抽屉批次仍在，刷新其明细
  if (selectedBatchId.value && batchDetail.value) {
    try { await openBatch(selectedBatchId.value, true) } catch (e) { /* 忽略 */ }
  }
}

// ---------------- 样品详情（右侧面板） ----------------
function showSampleDetail(row) {
  setDetail({
    title: row.sampleNo || `草稿样品 #${row.id}`,
    subtitle: `${holeNo(row.boreholeId)} · ${row.sampleType || '未分类型'}`,
    tag: sampleStatusName(row.status),
    fields: [
      { label: '样品编号', value: row.sampleNo || '（未填写，草稿）' },
      { label: '所属钻孔', value: holeNo(row.boreholeId) },
      { label: '起始深度 (m)', value: num(row.fromDepth) },
      { label: '终止深度 (m)', value: num(row.toDepth) },
      { label: '取样人', value: row.sampler || '（未填写）' },
      { label: '封签号', value: row.sealNo || '（未填写）' },
      { label: '样品类型', value: row.sampleType || '—' },
      { label: '状态', value: sampleStatusName(row.status) },
      { label: '登记时间', value: fmt(row.registeredAt) },
      { label: '备注', value: row.remark || '—' }
    ]
  })
}

// ---------------- 取样登记（草稿 / 正式） ----------------
function openSampleDialog(row) {
  if (row) {
    form.value = {
      id: row.id, boreholeId: row.boreholeId, sampleNo: row.sampleNo || '',
      fromDepth: row.fromDepth == null ? undefined : Number(row.fromDepth),
      toDepth: row.toDepth == null ? undefined : Number(row.toDepth),
      sampler: row.sampler || '', sealNo: row.sealNo || '',
      sampleType: row.sampleType || '', remark: row.remark || '',
      lockVersion: row.lockVersion || 0
    }
  } else {
    form.value = emptyForm()
    form.value.sampler = operator.value || ''
  }
  sampleDialog.value = true
}

async function submitSample() {
  if (form.value.boreholeId == null) {
    ElMessage.warning('请选择钻孔（在指定孔段登记）')
    return
  }
  if (form.value.fromDepth != null && form.value.toDepth != null
      && Number(form.value.toDepth) <= Number(form.value.fromDepth)) {
    ElMessage.warning('终止深度必须大于起始深度')
    return
  }
  saving.value = true
  const body = { ...form.value, expectedVersion: form.value.lockVersion }
  const isEdit = !!form.value.id
  try {
    const saved = isEdit
      ? await api.updateSample(form.value.id, body)
      : await api.createSample(body)
    sampleDialog.value = false
    await loadSamples()
    await loadPackable()
    if ((form.value.sampler || '').trim()) {
      operator.value = form.value.sampler.trim()
      localStorage.setItem('dc_operator', operator.value)
    }
    ElMessage.success(saved.status === 'DRAFT'
      ? '关键信息不全，已保存草稿'
      : '样品已正式登记')
  } catch (e) {
    /* 409/400 已在拦截器提示 */
  } finally {
    saving.value = false
  }
}

async function removeSample(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除样品 ${row.sampleNo || ('#' + row.id)}？草稿删除后不可恢复。`,
      '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  try {
    await api.deleteSample(row.id, row.lockVersion)
    ElMessage.success('样品已删除')
    await loadSamples()
    await loadPackable()
  } catch (e) { /* 已提示 */ }
}

// ---------------- 封样送检 ----------------
async function openPackDialog() {
  packForm.value = { packer: operator.value || '', carrier: '', note: '' }
  packSelected.value = []
  packDialog.value = true
  await loadPackable()
}

async function loadPackable() {
  packable.value = await api.packableSamples(packHoleFilter.value || undefined)
}

function onPackSelection(rows) {
  packSelected.value = rows
}

async function submitPack() {
  if (!packSelected.value.length) {
    ElMessage.warning('请至少勾选一个待送检样品')
    return
  }
  if (!packForm.value.packer.trim()) {
    ElMessage.warning('封样人必须填写')
    return
  }
  packing.value = true
  try {
    const detail = await api.packBatch({
      sampleIds: packSelected.value.map((s) => s.id),
      packer: packForm.value.packer.trim(),
      carrier: packForm.value.carrier.trim(),
      note: packForm.value.note.trim()
    })
    operator.value = packForm.value.packer.trim()
    localStorage.setItem('dc_operator', operator.value)
    packDialog.value = false
    ElMessage.success(`批次 ${detail.batch.batchNo} 已封样发运`)
    selectedBatchId.value = detail.batch.id
    await Promise.all([loadSamples(), loadBatches(), loadPackable()])
    batchDetail.value = detail
    fieldBatchDrawer.value = true
  } catch (e) {
    /* 已提示：状态不对/重复封样/编号冲突 */
  } finally {
    packing.value = false
  }
}

// ---------------- 批次明细 ----------------
async function openBatch(id, silent = false) {
  selectedBatchId.value = id
  try {
    batchDetail.value = await api.sampleBatchDetail(id)
    if (role.value === 'field') fieldBatchDrawer.value = true
  } catch (e) {
    if (!silent) throw e
  }
}

/** 实验室：接收（全部 / 部分），返回 false 时保持弹窗打开（409 等冲突） */
async function onReceive(body) {
  try {
    batchDetail.value = await api.receiveBatch(selectedBatchId.value, body)
    ElMessage.success(batchDetail.value.batch.status === 'PARTIAL_RECEIVED'
      ? `部分接收：已接收 ${batchDetail.value.batch.receivedCount} 件，待补 ${batchDetail.value.batch.abnormalCount} 件`
      : '全部接收完成')
    await Promise.all([loadBatches(), loadSamples()])
    return true
  } catch (e) {
    // 冲突时重新拉取明细，让用户看到最新版本
    try { batchDetail.value = await api.sampleBatchDetail(selectedBatchId.value) } catch (ignore) { /* */ }
    return false
  }
}

/** 实验室：整批退回 */
async function onReturn(body) {
  try {
    batchDetail.value = await api.returnBatch(selectedBatchId.value, body)
    ElMessage.success('已整批退回，样品回到待送检')
    await Promise.all([loadBatches(), loadSamples()])
    return true
  } catch (e) {
    try { batchDetail.value = await api.sampleBatchDetail(selectedBatchId.value) } catch (ignore) { /* */ }
    return false
  }
}

// ---------------- 从孔资料进入 ----------------
// 页面由 App.vue 的 v-if 挂载，点「取样登记」时 nonce 自增发生在挂载之前，
// 因此入口处理统一放在 onMounted；这里保留 watch 以兼容页面已挂载时的再次跳入。
watch(
  () => sampleNav.nonce,
  async () => {
    if (!sampleNav.boreholeId) return
    await ensureHoles()
    role.value = 'field'
    holeFilter.value = sampleNav.boreholeId
    sampleNav.fromHole = true
    await loadSamples()
  }
)

// 角色切换时重新加载对应数据
watch(role, async (r) => {
  await ensureHoles()
  if (r === 'field') {
    await loadSamples()
    await loadPackable()
  } else {
    await loadBatches()
    if (selectedBatchId.value) {
      try { batchDetail.value = await api.sampleBatchDetail(selectedBatchId.value) } catch (e) { /* */ }
    }
  }
})

onMounted(async () => {
  await ensureHoles()
  await loadBatches()
  if (role.value === 'field') {
    await loadSamples()
    await loadPackable()
  }
  // 恢复当前批次
  if (selectedBatchId.value) {
    try { batchDetail.value = await api.sampleBatchDetail(selectedBatchId.value) } catch (e) { /* */ }
  }
  // 从孔资料「取样登记」跳入
  if (sampleNav.boreholeId) {
    role.value = 'field'
    holeFilter.value = sampleNav.boreholeId
    sampleNav.fromHole = true
    await loadSamples()
  }
})
</script>

<style scoped>
.dc-sample-batch-chips {
  display: flex; gap: 8px; flex-wrap: wrap;
}
.dc-batch-chip {
  display: flex; align-items: center; gap: 6px;
  border: 1px solid #d8c8ac; border-radius: 6px; padding: 6px 10px;
  background: #faf5ea; cursor: pointer; font-size: 12px;
}
.dc-batch-chip.on { border-color: #3e2723; background: #efe6d6; box-shadow: 0 0 0 2px rgba(62,39,35,.1); }
.dc-chip-meta { color: #8a7761; font-size: 11px; }
.dc-pack-summary {
  margin-top: 8px; padding: 8px; border: 1px dashed #c8b48f;
  border-radius: 6px; background: #faf5ea; font-size: 12px; max-height: 120px; overflow: auto;
}
</style>
