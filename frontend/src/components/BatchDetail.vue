<template>
  <div class="dc-page">
    <!-- 批次头 -->
    <div class="dc-panel">
      <div class="dc-panel-title">
        {{ batch.batchNo }}
        <el-tag size="small" style="margin-left: 8px" :type="batchTagType(batch.status)">
          {{ batchStatusName(batch.status) }}
        </el-tag>
        <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">
          封样 {{ batch.expectedCount }} 件 · 已接收 {{ batch.receivedCount || 0 }} 件 · 待补 {{ batch.abnormalCount || 0 }} 件
        </span>
      </div>
      <div class="dc-round-row" style="font-size: 12px">
        <span style="width: 220px">封样人 {{ batch.packer || '—' }} · 送样 {{ batch.carrier || '—' }}</span>
        <span style="color: #8a7761">发运 {{ fmt(batch.packedAt) }}</span>
      </div>
      <div v-if="batch.packNote" class="dc-round-row" style="font-size: 12px; color: #5d4037">
        <span style="width: 80px">运输说明</span><span>{{ batch.packNote }}</span>
      </div>
      <div v-if="batch.receivedAt" class="dc-round-row" style="font-size: 12px">
        <span style="width: 220px">接收人 {{ batch.receiver || '—' }} · {{ fmt(batch.receivedAt) }}</span>
      </div>
      <div v-if="batch.receiveNote" class="dc-reject-box"><b>接收 / 异常说明：</b>{{ batch.receiveNote }}</div>
      <div v-if="batch.returnReason" class="dc-reject-box" style="border-color: #c05621">
        <b>整批退回原因：</b>{{ batch.returnReason }}（{{ fmt(batch.returnedAt) }}）
      </div>

      <!-- 操作按钮：仅运输中、实验室视角可操作；重复点击由 loading + 乐观锁共同防重 -->
      <div v-if="!readonly && batch.status === 'IN_TRANSIT'" style="margin-top: 10px; display: flex; gap: 8px">
        <el-button size="small" type="success" :loading="submitting" @click="openReceive">
          逐样核对并接收
        </el-button>
        <el-button size="small" type="danger" plain :loading="submitting" @click="openReturn">整批退回</el-button>
        <el-button size="small" @click="emit('refresh')">重新拉取明细</el-button>
        <span style="font-size: 11px; color: #8a7761; align-self: center">乐观锁 #{{ batch.lockVersion }}</span>
      </div>      <div v-else-if="batch.status !== 'IN_TRANSIT'" style="margin-top: 8px; font-size: 12px; color: #8a7761">
        该批次已完成交接（{{ batchStatusName(batch.status) }}），结果不可重复操作；下方为交接记录。
      </div>
    </div>

    <!-- 样品明细（交接时显示） -->
    <div class="dc-panel">
      <div class="dc-panel-title">
        样品明细（{{ items.length }} 件）
        <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">数量、封签以封样快照为准</span>
      </div>
      <el-table :data="items" size="small" stripe :header-cell-style="{ background: '#efe6d6' }">
        <el-table-column prop="sampleNo" label="样品编号" min-width="135" />
        <el-table-column label="孔号" width="90">
          <template #default="{ row }">{{ holeNo(row.boreholeId) }}</template>
        </el-table-column>
        <el-table-column label="孔段 (m)" width="130">
          <template #default="{ row }">{{ num(row.fromDepth) }} ~ {{ num(row.toDepth) }}</template>
        </el-table-column>
        <el-table-column prop="sealNo" label="封签号（核对）" min-width="115" />
        <el-table-column label="明细状态" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="itemTagType(row.itemStatus)">{{ itemStatusName(row.itemStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="abnormalNote" label="行级异常 / 待补说明" min-width="200">
          <template #default="{ row }">
            <span :style="{ color: row.abnormalNote ? '#c05621' : '#8a7761' }">
              {{ row.abnormalNote || (row.itemStatus === 'RECEIVED' ? '数量、封签一致' : '—') }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="接收时间" width="128">
          <template #default="{ row }">{{ row.receivedAt ? fmt(row.receivedAt) : '—' }}</template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 历史交接结果（事件链，只增不改） -->
    <div class="dc-panel">
      <div class="dc-panel-title">交接记录（历史结果可追溯）</div>
      <el-timeline style="margin-top: 8px">
        <el-timeline-item v-for="e in events" :key="e.id"
                          :type="eventTagType(e.eventType)" :hollow="e.eventType === 'PACK'"
                          :timestamp="fmt(e.eventAt) + ' · ' + (e.operator || '—')">
          <b>{{ eventTypeName(e.eventType) }}</b>
          <el-tag size="small" style="margin-left: 6px">{{ batchStatusName(e.eventStatus) }}</el-tag>
          <div style="font-size: 12px; color: #5d4037; margin-top: 2px">
            涉及 {{ e.sampleCount }} 件
            <template v-if="e.eventType !== 'PACK'">
              · 接收 {{ e.receivedCount }} 件<template v-if="e.abnormalCount"> · 异常 {{ e.abnormalCount }} 件</template>
            </template>
          </div>
          <div v-if="e.note" style="font-size: 12px; color: #8a7761">{{ e.note }}</div>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 接收核对对话框：逐样标记正常 / 异常 -->
    <el-dialog v-model="receiveDialog" title="实验室接收核对" width="760px" append-to-body>
      <el-alert
        :title="`封样数量 ${items.length} 件，请逐行核对样品状态与封签；全部一致才能全部接收，存在异常请勾选「异常待补」并写明说明，已接收样品会保留。`"
        type="info" :closable="false" style="margin-bottom: 10px"
      />
      <el-form label-width="80px" size="small" style="margin-bottom: 8px">
        <el-form-item label="接收人" required>
          <el-input v-model="receiver" style="width: 220px" placeholder="实验室接收人" />
        </el-form-item>
      </el-form>
      <el-table :data="receiveLines" size="small" max-height="320" :header-cell-style="{ background: '#efe6d6' }">
        <el-table-column prop="sampleNo" label="样品编号" min-width="125" />
        <el-table-column label="孔号" width="80">
          <template #default="{ row }">{{ holeNo(row.boreholeId) }}</template>
        </el-table-column>
        <el-table-column label="封签号" min-width="105">
          <template #default="{ row }">
            {{ row.sealNo }}
            <el-input v-model="row.actualSeal" size="small" placeholder="实到封签（不一致时填写）"
                      style="margin-top: 2px" />
          </template>
        </el-table-column>
        <el-table-column label="数量/状态" width="96">
          <template #default="{ row }">
            <el-checkbox v-model="row.present">实到</el-checkbox>
          </template>
        </el-table-column>
        <el-table-column label="异常待补" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.abnormal" />
          </template>
        </el-table-column>
        <el-table-column label="异常说明" min-width="180">
          <template #default="{ row }">
            <el-input v-model="row.abnormalNote" size="small" :disabled="!row.abnormal"
                      placeholder="封签破损 / 封签不符 / 数量短少" />
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="80px" size="small" style="margin-top: 10px">
        <el-form-item label="总说明">
          <el-input v-model="receiveNote" type="textarea" :rows="2" placeholder="接收总说明 / 异常概述（可选）" />
        </el-form-item>
      </el-form>
      <div style="font-size: 12px; color: #8a7761">
        当前核对：{{ receiveSummary }}
      </div>
      <template #footer>
        <el-button size="small" @click="receiveDialog = false">取消</el-button>
        <el-button size="small" :type="abnormalTotal ? 'warning' : 'success'" :loading="submitting" @click="submitReceive">
          {{ abnormalTotal ? `部分接收（接收 ${items.length - abnormalTotal} 件 / 待补 ${abnormalTotal} 件）` : `确认全部接收（${items.length} 件）` }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 整批退回 -->
    <el-dialog v-model="returnDialog" title="整批退回" width="480px" append-to-body>
      <el-alert title="整批退回后样品回到「已登记待送检」，现场可补齐手续重新组批；本批次保留并记录退回原因。"
                type="warning" :closable="false" style="margin-bottom: 10px" />
      <el-form label-width="80px" size="small">
        <el-form-item label="经办人"><el-input v-model="receiver" placeholder="实验室经办人" /></el-form-item>
        <el-form-item label="退回原因" required>
          <el-input v-model="returnReason" type="textarea" :rows="3" placeholder="如：送样单缺失、外箱无批次标签、来源无法核对" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="returnDialog = false">取消</el-button>
        <el-button size="small" type="danger" :loading="submitting" @click="submitReturn">确认整批退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  detail: { type: Object, required: true },
  operator: { type: String, default: '' },
  holes: { type: Array, default: () => [] },
  readonly: { type: Boolean, default: false },
  /** 父组件执行接收 API 并返回 Promise（resolve=true 关闭弹窗，失败返回 false） */
  onReceive: { type: Function, default: null },
  /** 父组件执行整批退回 API 并返回 Promise */
  onReturn: { type: Function, default: null }
})
const emit = defineEmits(['refresh'])

const batch = computed(() => props.detail.batch)
const items = computed(() => props.detail.items || [])
const events = computed(() => props.detail.events || [])

const receiveDialog = ref(false)
const returnDialog = ref(false)
const submitting = ref(false)
const receiver = ref(props.operator || '')
const receiveNote = ref('')
const returnReason = ref('')
const receiveLines = ref([])

watch(() => props.operator, (v) => { if (v) receiver.value = v })
watch(() => props.detail, () => {
  receiveNote.value = ''
  returnReason.value = ''
})

const abnormalTotal = computed(() => receiveLines.value.filter((l) => l.abnormal).length)
const receiveSummary = computed(() => {
  const present = receiveLines.value.filter((l) => l.present).length
  const abn = abnormalTotal.value
  return `实到 ${present} / 封样 ${items.value.length} 件，标记异常待补 ${abn} 件`
})

function num(v) {
  return v == null ? '—' : Number(v)
}
function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}
function holeNo(id) {
  const h = props.holes.find((x) => x.id === id)
  return h ? h.holeNo : '#' + id
}
function batchStatusName(c) {
  return { IN_TRANSIT: '运输中', RECEIVED: '已接收', PARTIAL_RECEIVED: '部分接收', RETURNED: '已退回' }[c] || c
}
function batchTagType(c) {
  return { IN_TRANSIT: 'warning', RECEIVED: 'success', PARTIAL_RECEIVED: 'danger', RETURNED: 'info' }[c] || ''
}
function itemStatusName(c) {
  return { PENDING: '待接收', RECEIVED: '已接收', ABNORMAL: '异常待补', RETURNED: '随批退回' }[c] || c
}
function itemTagType(c) {
  return { PENDING: 'warning', RECEIVED: 'success', ABNORMAL: 'danger', RETURNED: 'info' }[c] || ''
}
function eventTypeName(c) {
  return { PACK: '封样发运', RECEIVE: '全部接收', PARTIAL_RECEIVE: '部分接收', RETURN: '整批退回' }[c] || c
}
function eventTagType(c) {
  return { PACK: 'info', RECEIVE: 'success', PARTIAL_RECEIVE: 'warning', RETURN: 'danger' }[c] || ''
}

function openReceive() {
  receiver.value = props.operator || ''
  receiveNote.value = ''
  // 初始化逐行核对单：默认全部实到、封签一致、正常
  receiveLines.value = items.value.map((it) => ({
    sampleId: it.sampleId,
    sampleNo: it.sampleNo,
    boreholeId: it.boreholeId,
    sealNo: it.sealNo,
    actualSeal: '',
    present: true,
    abnormal: false,
    abnormalNote: ''
  }))
  receiveDialog.value = true
}

async function submitReceive() {
  if (!receiver.value.trim()) {
    ElMessage.warning('请填写实验室接收人')
    return
  }
  // 数量一致：每行都必须实到；未实到必须作为异常（短少）并说明
  for (const line of receiveLines.value) {
    if (!line.present && !line.abnormal) {
      line.abnormal = true
      if (!line.abnormalNote) line.abnormalNote = '数量短少：封样清单有此件，实到未见'
    }
    if (line.abnormal && !line.abnormalNote.trim()) {
      ElMessage.warning(`样品 ${line.sampleNo} 标记异常，必须填写异常说明（待补项）`)
      return
    }
    // 实到封签填写且与封样封签不一致：封签核对不通过，必须标记异常说明
    if (!line.abnormal && line.actualSeal.trim() && line.actualSeal.trim() !== line.sealNo) {
      ElMessage.warning(`样品 ${line.sampleNo} 实到封签 ${line.actualSeal.trim()} 与封签号 ${line.sealNo} 不一致，请标记异常并说明`)
      return
    }
  }
  submitting.value = true
  try {
    const ok = await props.onReceive?.(buildReceiveBody())
    if (ok !== false) receiveDialog.value = false
  } finally {
    submitting.value = false
  }
}

function buildReceiveBody() {
  return {
    receiver: receiver.value.trim(),
    note: receiveNote.value.trim(),
    expectedVersion: batch.value.lockVersion,
    items: receiveLines.value.map((l) => ({
      sampleId: l.sampleId,
      abnormal: l.abnormal,
      abnormalNote: l.abnormal ? l.abnormalNote.trim() : ''
    }))
  }
}

function openReturn() {
  receiver.value = props.operator || ''
  returnReason.value = ''
  returnDialog.value = true
}

async function submitReturn() {
  if (!returnReason.value.trim()) {
    ElMessage.warning('整批退回必须填写退回原因')
    return
  }
  submitting.value = true
  try {
    const ok = await props.onReturn?.({
      receiver: receiver.value.trim(),
      reason: returnReason.value.trim(),
      expectedVersion: batch.value.lockVersion
    })
    if (ok !== false) returnDialog.value = false
  } finally {
    submitting.value = false
  }
}
</script>
