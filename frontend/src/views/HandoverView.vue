<template>
  <div class="dc-page">
    <div class="dc-toolbar">
      <el-radio-group v-model="role" size="small">
        <el-radio-button label="crew">施工班组</el-radio-button>
        <el-radio-button label="chief">地质负责人</el-radio-button>
      </el-radio-group>

      <span class="dc-label">状态</span>
      <el-select v-model="statusFilter" size="small" style="width: 130px" clearable placeholder="全部" @change="loadList">
        <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>

      <el-input
        v-model="operator"
        size="small"
        style="width: 150px"
        :placeholder="role === 'crew' ? '班组长姓名' : '负责人姓名'"
      />
      <el-button size="small" @click="loadList">刷新</el-button>
      <span style="color: #8a7761; font-size: 11px">
        {{ role === 'crew' ? '班组发起交接、按退回原因整改并重新提交' : '检查资料完整性，退回整改或确认接收；接收后可返开' }}
      </span>
    </div>

    <div class="dc-split" style="align-items: flex-start">
      <!-- 左：交接列表 -->
      <div class="dc-panel" style="width: 330px; flex: none">
        <div class="dc-panel-title">交接钻孔（{{ list.length }}）</div>

        <template v-if="role === 'crew'">
          <div v-for="h in crewHoles" :key="h.id" class="dc-round-row" :class="{ on: h.id === selectedHoleId }"
               style="cursor: pointer" @click="pickHole(h)">
            <span style="width: 86px; font-weight: 700">{{ h.holeNo }}</span>
            <span style="width: 96px">实钻 {{ num(h.actualDepth) }} m</span>
            <el-tag size="small" :type="h.status === '终孔' ? 'info' : 'warning'">{{ h.status }}</el-tag>
            <span v-if="activeMap[h.id]" style="margin-left: 6px">
              <el-tag size="small" :type="tagType(activeMap[h.id].status)">{{ statusName(activeMap[h.id].status) }}</el-tag>
            </span>
          </div>
          <div v-if="!crewHoles.length" style="font-size: 12px; color: #8a7761">暂无可交接钻孔</div>
        </template>

        <template v-else>
          <div v-for="h in list" :key="h.id" class="dc-round-row" :class="{ on: h.id === selectedId }"
               style="cursor: pointer" @click="pickHandover(h)">
            <span style="width: 90px; font-weight: 700">{{ holeNo(h.boreholeId) }}</span>
            <span style="width: 56px">v{{ h.versionNo }}</span>
            <el-tag size="small" :type="tagType(h.status)">{{ statusName(h.status) }}</el-tag>
            <span v-if="h.issueOpen > 0" style="margin-left: 6px; color: #c05621; font-size: 11px">{{ h.issueOpen }} 待处理</span>
          </div>
          <div v-if="!list.length" style="font-size: 12px; color: #8a7761">暂无交接中的钻孔</div>
        </template>
      </div>

      <!-- 右：主操作区 -->
      <div style="flex: 1; min-width: 420px; display: flex; flex-direction: column; gap: 12px">
        <!-- 未选中或钻孔无流程 -->
        <div v-if="role === 'crew' && selectedHole" class="dc-panel">
          <div class="dc-panel-title">
            {{ selectedHole.holeNo }} · 终孔交接
            <el-tag size="small" style="margin-left: 8px" :type="active ? tagType(active.status) : 'info'">
              {{ active ? statusName(active.status) : '尚未发起交接' }}
            </el-tag>
          </div>
          <div class="dc-round-row">
            <span style="width: 150px">实际孔深（发起时快照以此为准）</span>
            <span style="font-weight: 700">{{ num(selectedHole.actualDepth) }} m</span>
            <span style="color: #8a7761; margin-left: 12px">设计 {{ num(selectedHole.designDepth) }} m</span>
          </div>
          <div v-if="selectedHole.status !== '终孔'" style="margin: 8px 0; color: #c05621; font-size: 12px">
            该孔当前为「{{ selectedHole.status }}」状态，终孔后才能发起交接。
          </div>
          <div v-if="active" class="dc-round-row">
            <span style="width: 150px">当前生效版本</span>
            <span>v{{ active.versionNo }}（{{ statusName(active.status) }}）· 快照孔深 {{ num(active.snapshotDepth) }} m</span>
          </div>
          <div v-if="active && active.lastRejectReason" class="dc-round-row" style="color: #c05621">
            <span style="width: 150px">上一轮退回原因</span>
            <span>{{ active.lastRejectReason }}</span>
          </div>
          <div style="margin-top: 10px">
            <el-button v-if="!active && selectedHole.status === '终孔'" size="small" type="primary"
                       @click="openInitiate">发起交接</el-button>
            <el-button v-if="active && active.status === 'NEED_FIX'" size="small"
                       @click="openHandover(active.id)">去整改 / 重新提交</el-button>
            <el-button v-if="active && active.status === 'PENDING_CHECK'" size="small"
                       @click="openHandover(active.id)">查看提交</el-button>
            <el-button v-if="active && active.status === 'ACCEPTED'" size="small" disabled>
              已接收 · 资料已冻结
            </el-button>
          </div>
        </div>

        <!-- 版本详情 -->
        <template v-if="detail">
          <div class="dc-panel">
            <div class="dc-panel-title">
              {{ holeNo(detail.handover.boreholeId) }} · 交接版本 v{{ detail.handover.versionNo }}
              <el-tag size="small" style="margin-left: 8px" :type="tagType(detail.handover.status)">
                {{ statusName(detail.handover.status) }}
              </el-tag>
              <el-tag v-if="detail.handover.activeFlag !== 1" size="small" type="info" style="margin-left: 6px">
                历史版本（只读）
              </el-tag>
              <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">
                快照孔深 {{ num(detail.handover.snapshotDepth) }} m ·
                问题 {{ detail.handover.issueTotal }} 项 / 待处理 {{ detail.handover.issueOpen }} 项
              </span>
            </div>

            <!-- 完整性概览 -->
            <div class="dc-round-row" style="font-size: 12px">
              <el-button-group size="small">
                <el-button :type="issueTypeCount('ROUND_GAP') + issueTypeCount('ROUND_LOW_RECOVERY') ? 'danger' : 'success'">
                  回次 {{ snap.roundCount }}
                </el-button>
                <el-button :type="issueTypeCount('LAYER_GAP') ? 'danger' : 'success'">岩性层 {{ snap.layerCount }}</el-button>
                <el-button :type="issueTypeCount('PARAM_ABNORMAL') ? 'danger' : 'success'">参数时序 {{ snap.seriesCount }}</el-button>
                <el-button :type="issueTypeCount('REPORT_UNSIGNED') ? 'danger' : 'success'">班报 {{ snap.reportCount }}</el-button>
              </el-button-group>
              <span style="margin-left: 12px; color: #8a7761">
                发起 {{ detail.handover.initiatedBy || '—' }} · {{ fmt(detail.handover.initiatedAt) }}
                ｜ 检查 {{ detail.handover.checkerName || '—' }} · {{ fmt(detail.handover.checkedAt) }}
              </span>
            </div>

            <div v-if="detail.handover.rejectReason" class="dc-reject-box">
              <b>本轮退回原因：</b>{{ detail.handover.rejectReason }}
            </div>
            <div v-if="detail.handover.reopenReason && detail.handover.status === 'SUPERSEDED'" class="dc-reject-box">
              <b>返开原因：</b>{{ detail.handover.reopenReason }}（本版本结论已失效，仅留档）
            </div>
            <div v-if="detail.handover.submitNote" class="dc-round-row" style="font-size: 12px; color: #5d4037">
              <span style="width: 110px">班组提交说明</span><span>{{ detail.handover.submitNote }}</span>
            </div>

            <!-- 操作按钮 -->
            <div style="margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap">
              <!-- 班组 -->
              <template v-if="role === 'crew' && isActive">
                <template v-if="detail.handover.status === 'NEED_FIX'">
                  <el-button size="small" type="primary" @click="saveProgress">保存整改进度</el-button>
                  <el-button size="small" type="success" @click="openResubmit">整改完成 · 重新提交</el-button>
                </template>
                <template v-else-if="detail.handover.status === 'PENDING_CHECK'">
                  <el-tag size="small" type="warning">已提交，等待地质负责人检查</el-tag>
                </template>
              </template>

              <!-- 地质负责人 -->
              <template v-if="role === 'chief' && isActive">
                <template v-if="detail.handover.status === 'PENDING_CHECK'">
                  <el-button size="small" type="danger" @click="openReject">退回整改</el-button>
                  <el-button size="small" type="success" @click="doAccept">确认接收</el-button>
                  <el-button size="small" @click="loadDetail(detail.handover.id)">按实时资料重新检查</el-button>
                </template>
                <template v-else-if="detail.handover.status === 'ACCEPTED'">
                  <el-button size="small" type="warning" @click="openReopen">返开（需填原因）</el-button>
                  <span style="font-size: 12px; color: #5d4037">已接收：回次 / 岩性层 / 参数 / 班报均已冻结</span>
                </template>
              </template>

              <el-button v-if="!isActive" size="small" @click="openSnapshot">查看本版本快照</el-button>
              <el-button size="small" @click="loadVersions">历史版本（{{ versions.length }}）</el-button>
              <span style="font-size: 11px; color: #8a7761; align-self: center">乐观锁版本 #{{ detail.handover.lockVersion }}</span>
            </div>
          </div>

          <!-- 实时复查结果（负责人检查时） -->
          <div v-if="role === 'chief' && isActive" class="dc-panel">
            <div class="dc-panel-title">
              实时资料检查
              <el-button size="small" text type="primary" @click="loadLive">刷新检查结果</el-button>
              <el-tag size="small" :type="liveIssues.length ? 'danger' : 'success'" style="margin-left: 8px">
                {{ liveIssues.length ? liveIssues.length + ' 个待处理问题' : '全部合格，可接收' }}
              </el-tag>
            </div>
            <div v-if="liveDiffNote" style="font-size: 12px; color: #c05621; margin-bottom: 6px">{{ liveDiffNote }}</div>
            <div v-if="!liveIssues.length" style="font-size: 12px; color: #8a7761">
              回次与岩性层连续不重叠、低采取率均有原因、参数无异常点、班报均已签字。
            </div>
            <IssueTable :issues="liveIssues" :show-status="false" @goto="onGoto" />
          </div>

          <!-- 本版本问题项（随版本留痕） -->
          <div class="dc-panel">
            <div class="dc-panel-title">
              问题项清单（本版本快照检出 {{ detail.handover.issueTotal }} 项，已处理 {{ resolvedCount }} 项）
              <span style="float: right; font-size: 11px; color: #8a7761; font-weight: 400">点「去整改」直接回到对应资料</span>
            </div>
            <IssueTable :issues="detail.issues" :show-status="true" @goto="onGoto" />
          </div>
        </template>

        <div v-else-if="role === 'chief' && !detail" class="dc-panel" style="color: #8a7761; font-size: 12px">
          请在左侧选择待检查的钻孔。
        </div>
      </div>
    </div>

    <!-- 版本历史抽屉 -->
    <el-drawer v-model="versionDrawer" title="历史版本（只读，可还原当时看到的数据）" size="520px">
      <div v-for="v in versions" :key="v.id" class="dc-version-card"
           :class="{ on: detail && v.id === detail.handover.id }">
        <div>
          <b>v{{ v.versionNo }}</b>
          <el-tag size="small" style="margin-left: 8px" :type="tagType(v.status)">{{ statusName(v.status) }}</el-tag>
          <el-tag v-if="v.activeFlag === 1" size="small" type="success" style="margin-left: 6px">当前生效</el-tag>
        </div>
        <div style="font-size: 12px; color: #8a7761; margin: 4px 0">
          快照孔深 {{ num(v.snapshotDepth) }} m · 问题 {{ v.issueTotal }}（待处理 {{ v.issueOpen }}）·
          {{ fmt(v.checkedAt || v.initiatedAt) }}
        </div>
        <div v-if="v.rejectReason" style="font-size: 12px; color: #c05621">退回：{{ v.rejectReason }}</div>
        <div v-if="v.reopenReason" style="font-size: 12px; color: #b7791f">返开：{{ v.reopenReason }}</div>
        <el-button size="small" text type="primary" @click="restoreVersion(v)">还原该版本数据 →</el-button>
      </div>
    </el-drawer>

    <!-- 发起 / 重新提交 -->
    <el-dialog v-model="submitDialog" :title="submitMode === 'initiate' ? '发起终孔交接' : '整改后重新提交'" width="460px">
      <el-alert
        :title="submitMode === 'initiate'
          ? '发起时将以当前实际孔深 ' + num(selectedHole?.actualDepth) + ' m 对回次、岩性层、参数、班报形成资料快照。'
          : '重新提交将产生新版本，并以当前实际孔深重新快照；旧版本继续保留。'"
        type="info" :closable="false" style="margin-bottom: 12px"
      />
      <el-form label-width="92px" size="small">
        <el-form-item label="班组长"><el-input v-model="operator" placeholder="发起人姓名" /></el-form-item>
        <el-form-item label="提交说明">
          <el-input v-model="submitNote" type="textarea" :rows="3"
                    :placeholder="submitMode === 'resubmit' ? '说明本轮整改内容（对照退回原因）' : '终孔情况说明'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="submitDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="doSubmit">{{ submitMode === 'initiate' ? '发起交接' : '重新提交' }}</el-button>
      </template>
    </el-dialog>

    <!-- 退回 / 返开 -->
    <el-dialog v-model="reasonDialog" :title="reasonMode === 'reject' ? '退回整改' : '接收后返开'" width="460px">
      <el-alert
        :title="reasonMode === 'reject'
          ? '退回后状态变为「待整改」，班组只能保存整改进度，问题清零并重新提交后才能接收。'
          : '返开后原接收版本将标记为【已失效】并永久留档，钻孔可继续录入，需重新交接。'"
        :type="reasonMode === 'reject' ? 'warning' : 'error'" :closable="false" style="margin-bottom: 12px"
      />
      <el-form label-width="92px" size="small">
        <el-form-item label="负责人"><el-input v-model="operator" placeholder="地质负责人姓名" /></el-form-item>
        <el-form-item :label="reasonMode === 'reject' ? '退回原因' : '返开原因'">
          <el-input v-model="reasonText" type="textarea" :rows="3"
                    :placeholder="reasonMode === 'reject' ? '列明需整改的问题，原因会随版本保留' : '必须填写返开原因'" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="reasonDialog = false">取消</el-button>
        <el-button size="small" :type="reasonMode === 'reject' ? 'danger' : 'warning'" @click="doReason">确认</el-button>
      </template>
    </el-dialog>

    <!-- 快照只读查看抽屉 -->
    <SnapshotDrawer v-if="snapshotDrawer" v-model="snapshotDrawer" :detail="snapshotDetail" />
  </div>
</template>

<script>
import IssueTable from '../components/IssueTable.vue'
import SnapshotDrawer from '../components/SnapshotDrawer.vue'
export default { name: 'HandoverView', components: { IssueTable, SnapshotDrawer } }
</script>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'
import { handoverNav, gotoIssue } from '../handover-nav'

const STATUS_OPTIONS = [
  { value: 'PENDING_CHECK', label: '待检查' },
  { value: 'NEED_FIX', label: '待整改' },
  { value: 'ACCEPTED', label: '已接收' },
  { value: 'SUPERSEDED', label: '已失效' }
]

const role = ref('chief')
const operator = ref(localStorage.getItem('dc_operator') || '')
const statusFilter = ref(null)
const list = ref([])
const allHoles = ref([])
const activeMap = ref({})
const selectedHoleId = ref(null)
const selectedId = ref(null)
const detail = ref(null)
const liveIssues = ref([])
const liveLoadedFor = ref(null)
const versions = ref([])
const versionDrawer = ref(false)
const submitDialog = ref(false)
const submitMode = ref('initiate')
const submitNote = ref('')
const reasonDialog = ref(false)
const reasonMode = ref('reject')
const reasonText = ref('')
const snapshotDrawer = ref(false)
const snapshotDetail = ref(null)

const selectedHole = computed(() => allHoles.value.find((x) => x.id === selectedHoleId.value) || null)
const active = computed(() => (selectedHole.value ? activeMap.value[selectedHole.value.id] : null))
const isActive = computed(() => detail.value && detail.value.handover.activeFlag === 1)
const snap = computed(() => (detail.value && detail.value.handover.snapshot) || {})
const resolvedCount = computed(() => (detail.value?.issues || []).filter((i) => i.status === 'RESOLVED').length)
const crewHoles = computed(() => allHoles.value)
const liveDiffNote = computed(() => {
  if (!detail.value) return ''
  const openStored = (detail.value.issues || []).filter((i) => i.status === 'OPEN').length
  if (liveIssues.value.length !== openStored) {
    return `实时资料当前有 ${liveIssues.value.length} 个待处理问题，与提交快照记录的 ${openStored} 项不一致，以实时检查为准。`
  }
  return ''
})

function num(v) {
  return v == null ? '—' : Number(v)
}

function fmt(t) {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '—'
}

function statusName(code) {
  const m = { PENDING_CHECK: '待检查', NEED_FIX: '待整改', ACCEPTED: '已接收', SUPERSEDED: '已失效' }
  return m[code] || code
}

function tagType(code) {
  return { PENDING_CHECK: 'warning', NEED_FIX: 'danger', ACCEPTED: 'success', SUPERSEDED: 'info' }[code] || ''
}

function holeNo(id) {
  const x = allHoles.value.find((h) => h.id === id)
  return x ? x.holeNo : '#' + id
}

function issueTypeCount(type) {
  return (detail.value?.issues || []).filter((i) => i.issueType === type && i.status === 'OPEN').length
}

async function loadList() {
  await ensureHoles()
  const active = await api.handovers(statusFilter.value)
  list.value = active
  const map = {}
  ;(await api.handovers()).forEach((x) => { map[x.boreholeId] = x })
  activeMap.value = map
}

async function ensureHoles() {
  if (!allHoles.value.length) allHoles.value = await api.boreholes()
}

function pickHole(h) {
  selectedHoleId.value = h.id
  selectedId.value = null
  detail.value = null
  liveIssues.value = []
  const cur = activeMap.value[h.id]
  if (cur) openHandover(cur.id)
}

async function pickHandover(handover) {
  await openHandover(handover.id)
}

async function openHandover(id) {
  selectedId.value = id
  await loadDetail(id)
  if (role.value === 'chief' && isActive.value) await loadLive()
}

async function loadDetail(id) {
  detail.value = await api.handoverDetail(id)
  selectedId.value = id
  const hid = detail.value.handover.boreholeId
  if (!allHoles.value.find((x) => x.id === hid)) {
    await ensureHoles()
  }
  selectedHoleId.value = hid
}

async function loadLive() {
  if (!detail.value) return
  const hid = detail.value.handover.boreholeId
  liveIssues.value = await api.liveIssues(hid)
  liveLoadedFor.value = detail.value.handover.id
}

async function loadVersions() {
  if (!detail.value) return
  versions.value = await api.handoverVersions(detail.value.handover.boreholeId)
  versionDrawer.value = true
}

async function restoreVersion(v) {
  versionDrawer.value = false
  const data = await api.handoverDetail(v.id)
  detail.value = data
  selectedId.value = v.id
  openSnapshot()
}

function openSnapshot() {
  snapshotDetail.value = detail.value
  snapshotDrawer.value = true
}

// ---------------- 发起 / 重新提交 ----------------

function openInitiate() {
  submitMode.value = 'initiate'
  submitNote.value = ''
  submitDialog.value = true
}

function openResubmit() {
  submitMode.value = 'resubmit'
  submitNote.value = ''
  submitDialog.value = true
}

async function doSubmit() {
  saveOperator()
  const body = { operator: operator.value, note: submitNote.value }
  try {
    if (submitMode.value === 'initiate') {
      const h = await api.initiateHandover(selectedHoleId.value, body)
      ElMessage.success('交接已发起，等待检查')
      submitDialog.value = false
      await loadList()
      await openHandover(h.id)
    } else {
      const h = await api.resubmitHandover(detail.value.handover.id, {
        ...body,
        expectedVersion: detail.value.handover.lockVersion
      })
      ElMessage.success('已重新提交，产生新版本 v' + h.versionNo)
      submitDialog.value = false
      await loadList()
      await openHandover(h.id)
    }
  } catch (e) { /* 409/400 已在拦截器提示 */ }
}

async function saveProgress() {
  saveOperator()
  try {
    const res = await api.saveProgress(detail.value.handover.id, {
      operator: operator.value,
      expectedVersion: detail.value.handover.lockVersion
    })
    detail.value = { handover: res.handover, issues: res.issues }
    await loadLive()
    ElMessage.success(`整改进度已保存（${res.changed ? res.changed + ' 项问题状态变化' : '问题状态无变化'}）`)
    await loadList()
  } catch (e) { /* 已提示 */ }
}

// ---------------- 退回 / 接收 / 返开 ----------------

function openReject() {
  reasonMode.value = 'reject'
  reasonText.value = detail.value.handover.rejectReason || ''
  reasonDialog.value = true
}

function openReopen() {
  reasonMode.value = 'reopen'
  reasonText.value = ''
  reasonDialog.value = true
}

async function doReason() {
  if (!reasonText.value.trim()) {
    ElMessage.warning(reasonMode.value === 'reject' ? '必须填写退回原因' : '必须填写返开原因')
    return
  }
  saveOperator()
  const body = {
    operator: operator.value,
    reason: reasonText.value,
    expectedVersion: detail.value.handover.lockVersion
  }
  try {
    if (reasonMode.value === 'reject') {
      await api.rejectHandover(detail.value.handover.id, body)
      ElMessage.success('已退回整改')
    } else {
      const h = await api.reopenHandover(detail.value.handover.id, body)
      ElMessage.success('已返开，原版本已标记失效')
      reasonDialog.value = false
      await loadList()
      await openHandover(h.id)
      return
    }
    reasonDialog.value = false
    await loadList()
    await openHandover(detail.value.handover.id)
  } catch (e) { /* 已提示 */ }
}

async function doAccept() {
  saveOperator()
  try {
    await ElMessageBox.confirm(
      `确认接收 ${holeNo(detail.value.handover.boreholeId)} v${detail.value.handover.versionNo}？` +
      '接收后该孔回次、岩性层、参数记录和班报全部冻结，只能通过返开修改。',
      '确认接收', { type: 'warning', confirmButtonText: '确认接收', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  try {
    const h = await api.acceptHandover(detail.value.handover.id, {
      operator: operator.value,
      expectedVersion: detail.value.handover.lockVersion
    })
    ElMessage.success('已确认接收，资料冻结')
    await loadList()
    await openHandover(h.id)
  } catch (e) { /* 未清零 / 409 已提示 */ }
}

// ---------------- 跳转整改 ----------------

function onGoto(issue) {
  const target = gotoIssue(issue, {
    handoverId: detail.value.handover.id,
    boreholeId: detail.value.handover.boreholeId,
    statusFilter: statusFilter.value
  })
  window.dispatchEvent(new CustomEvent('dc:navigate', { detail: { tab: target } }))
}

function saveOperator() {
  if (operator.value) localStorage.setItem('dc_operator', operator.value)
}

defineExpose({
  /** 从录入页「返回交接台」时，恢复钻孔与检查进度 */
  async restoreContext(ctx) {
    role.value = ctx.role || role.value
    statusFilter.value = ctx.statusFilter || null
    await loadList()
    if (ctx.handoverId) {
      try {
        await openHandover(ctx.handoverId)
        return
      } catch (e) { /* 版本可能已不是生效版 */ }
    }
    if (ctx.boreholeId) {
      selectedHoleId.value = ctx.boreholeId
      const cur = activeMap.value[ctx.boreholeId]
      if (cur) await openHandover(cur.id)
    }
  }
})

/** 钻孔台账「终孔交接」入口：切到班组视角并选中该钻孔 */
async function applyHoleEntry() {
  if (handoverNav.target !== 'handover' || !handoverNav.boreholeId) return
  role.value = 'crew'
  statusFilter.value = null
  await ensureHoles()
  await loadList()
  selectedHoleId.value = handoverNav.boreholeId
  selectedId.value = null
  detail.value = null
  liveIssues.value = []
  const cur = activeMap.value[handoverNav.boreholeId]
  if (cur) await openHandover(cur.id)
}

watch(
  () => handoverNav.nonce,
  () => {
    if (handoverNav.target === 'handover') applyHoleEntry()
  }
)

onMounted(async () => {
  await loadList()
  await applyHoleEntry()
})
</script>
