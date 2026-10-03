<template>
  <div class="dc-shell">
    <header class="dc-topbar">
      <div class="dc-brand-mark">ZK</div>
      <div>
        <div class="dc-brand-title">地质钻探队</div>
        <div class="dc-brand-sub">钻机与岩芯编录管理 · {{ todayText }}</div>
      </div>

      <nav class="dc-tabs">
        <div
          v-for="(tab, index) in tabs"
          :key="tab.key"
          class="dc-tab"
          :class="{ active: tab.key === current }"
          @click="switchTab(tab.key)"
        >
          <span class="dc-tab-idx">{{ String(index + 1).padStart(2, '0') }}</span>{{ tab.title }}
        </div>
      </nav>
    </header>

    <div class="dc-body">
      <main class="dc-main">
        <!-- 交接台从问题项跳到录入页时显示的返回条：返回后保留钻孔与检查进度 -->
        <div v-if="handoverNav.fromHandover && current !== 'handover'" class="dc-return-bar">
          <el-button size="small" type="primary" plain @click="backToHandover">
            ← 返回交接台（{{ handoverNav.targetLabel || '继续整改' }}）
          </el-button>
          <span class="dc-return-hint">正在按交接问题整改，完成后返回交接台保存进度 / 重新提交</span>
        </div>

        <!-- 取样送检从孔资料进入时显示的返回条：返回孔资料，页面内筛选与批次已持久化 -->
        <div v-if="sampleNav.fromHole && current !== 'sample'" class="dc-return-bar">
          <el-button size="small" type="primary" plain @click="backToSample">
            ← 返回取样送检
          </el-button>
          <span class="dc-return-hint">正在查看孔资料，取样送检页的筛选条件与当前批次已保留</span>
        </div>

        <HolesView v-if="current === 'holes'" />
        <CoreLogView v-else-if="current === 'core'" />
        <ParamView v-else-if="current === 'param'" />
        <RigView v-else-if="current === 'rig'" />
        <ShiftReportView v-else-if="current === 'report'" />
        <SampleView v-else-if="current === 'sample'" />
        <KeepAlive>
          <HandoverView v-if="current === 'handover'" ref="handoverRef" />
        </KeepAlive>
      </main>

      <aside class="dc-detail">
        <div class="dc-detail-head">
          <div class="dc-detail-title">{{ detail.title || '对 象 详 情' }}</div>
          <div class="dc-detail-sub">{{ detail.subtitle || '在左侧点选钻孔 / 岩性层 / 钻机 / 班报' }}</div>
          <div v-if="detail.tag" class="dc-detail-tag">{{ detail.tag }}</div>
        </div>

        <div class="dc-detail-body">
          <div v-if="!detail.fields.length && !detail.json" class="dc-empty">
            尚未选中对象<br />详情随点选即时更新
          </div>

          <div v-for="f in detail.fields" :key="f.label" class="dc-field">
            <span class="dc-field-label">{{ f.label }}</span>
            <span class="dc-field-value">{{ f.value }}</span>
          </div>

          <div v-if="detail.json" class="dc-json-box">
            <div class="dc-json-title">{{ detail.jsonTitle || 'JSON 属性包' }}</div>
            <el-input
              v-if="detail.jsonEditable"
              v-model="jsonText"
              type="textarea"
              :rows="10"
              size="small"
            />
            <pre v-else class="dc-json-pre">{{ prettyJson }}</pre>
            <div v-if="detail.jsonEditable" style="margin-top: 8px">
              <el-button size="small" type="primary" @click="saveJson">保存属性包</el-button>
              <el-button size="small" @click="resetJson">还原</el-button>
            </div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { detail } from './detail'
import { handoverNav, exitToHandover } from './handover-nav'
import { sampleNav, exitToHole } from './sample-nav'
import HolesView from './views/HolesView.vue'
import CoreLogView from './views/CoreLogView.vue'
import ParamView from './views/ParamView.vue'
import RigView from './views/RigView.vue'
import ShiftReportView from './views/ShiftReportView.vue'
import SampleView from './views/SampleView.vue'
import HandoverView from './views/HandoverView.vue'

const tabs = [
  { key: 'holes', title: '孔位分布' },
  { key: 'core', title: '岩芯编录' },
  { key: 'param', title: '钻进参数' },
  { key: 'rig', title: '钻机班组' },
  { key: 'report', title: '班报表' },
  { key: 'sample', title: '取样送检' },
  { key: 'handover', title: '终孔交接' }
]

const current = ref('holes')
const handoverRef = ref(null)
const todayText = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })

const jsonText = ref('')

watch(
  () => detail.json,
  (val) => {
    jsonText.value = val == null ? '' : JSON.stringify(val, null, 2)
  },
  { immediate: true, deep: true }
)

const prettyJson = computed(() => jsonText.value)

function resetJson() {
  jsonText.value = detail.json == null ? '' : JSON.stringify(detail.json, null, 2)
}

async function saveJson() {
  if (!detail.onSave) return
  let parsed
  try {
    parsed = JSON.parse(jsonText.value)
  } catch (e) {
    ElMessage.error('JSON 格式不正确：' + e.message)
    return
  }
  try {
    await detail.onSave(parsed)
    ElMessage.success('属性包已保存')
  } catch (e) {
    /* 接口层已提示 */
  }
}

function switchTab(key) {
  current.value = key
}

/** 交接台问题项发起的跨页导航（事件由 HandoverView 派发） */
function onNavigate(e) {
  const tab = e?.detail?.tab
  if (tab) current.value = tab
}

/** 从录入页返回交接台：恢复【当前钻孔】与【检查进度】 */
async function backToHandover() {
  const ctx = exitToHandover()
  current.value = 'handover'
  await handoverRef.value?.restoreContext(ctx)
}

/** 从孔资料返回取样送检页（筛选条件 / 当前批次由页面 localStorage 自行恢复） */
function backToSample() {
  exitToHole()
  current.value = 'sample'
}

onMounted(() => {
  window.addEventListener('dc:navigate', onNavigate)
})
</script>
