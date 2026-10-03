<template>
  <div>
    <div v-if="!issues.length" style="font-size: 12px; color: #8a7761; padding: 4px 0">无问题项</div>

    <div v-for="g in groups" :key="g.type" class="dc-issue-group">
      <div class="dc-issue-group-head">
        <span class="dc-issue-dot"></span>
        {{ g.typeName }}
        <el-tag size="small" :type="g.openCount ? 'danger' : 'success'" style="margin-left: 8px">
          {{ g.openCount ? g.openCount + ' 待处理' : '已全部处理' }}
        </el-tag>
      </div>

      <div v-for="i in g.items" :key="(i.id || '') + i.issueKey" class="dc-issue-item" :class="{ resolved: showStatus && i.status === 'RESOLVED' }">
        <span class="dc-issue-status">
          <el-icon v-if="showStatus && i.status === 'RESOLVED'" color="#5b8c5a"><svg viewBox="0 0 1024 1024" width="14" height="14"><path fill="currentColor" d="M406.7 705.4a32 32 0 0 1-45.3 0L185.2 529.2a32 32 0 1 1 45.3-45.3l153.5 153.5 389.5-389.5a32 32 0 1 1 45.3 45.3z"/></svg></el-icon>
          <span v-else class="dc-warn-dot"></span>
        </span>
        <div class="dc-issue-body">
          <div class="dc-issue-label">{{ i.targetLabel || '—' }}</div>
          <div class="dc-issue-msg">{{ i.message }}</div>
        </div>
        <el-button
          size="small"
          :type="showStatus && i.status === 'RESOLVED' ? 'info' : 'primary'"
          plain
          @click="$emit('goto', i)"
        >
          {{ showStatus && i.status === 'RESOLVED' ? '查看资料' : '去整改' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  issues: { type: Array, default: () => [] },
  showStatus: { type: Boolean, default: true }
})

defineEmits(['goto'])

const TYPE_ORDER = ['ROUND_GAP', 'ROUND_LOW_RECOVERY', 'LAYER_GAP', 'PARAM_ABNORMAL', 'REPORT_UNSIGNED']

const groups = computed(() => {
  const map = new Map()
  for (const i of props.issues) {
    if (!map.has(i.issueType)) {
      map.set(i.issueType, { type: i.issueType, typeName: i.typeName, items: [], openCount: 0 })
    }
    const g = map.get(i.issueType)
    g.items.push(i)
    if (i.status !== 'RESOLVED') g.openCount++
  }
  return [...map.values()].sort(
    (a, b) => TYPE_ORDER.indexOf(a.type) - TYPE_ORDER.indexOf(b.type)
  )
})
</script>
