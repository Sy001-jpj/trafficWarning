<script setup>
const props = defineProps({
  title: String,
  value: [String, Number],
  unit: {
    type: String,
    default: ''
  },
  desc: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <div class="card">
    <div class="card-title">{{ title }}</div>
    <div class="card-value">
      {{ value }}
      <span v-if="unit" class="unit">{{ unit }}</span>
    </div>
    <div v-if="desc" class="card-desc">{{ desc }}</div>
  </div>
</template>

<style scoped>
.card {
  background: #fff;
  padding: 16px 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: var(--shadow-sm);
}

.card-title {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.card-value {
  font-size: 22px;
  font-weight: 600;
  color: var(--text-main);
}

.unit {
  font-size: 12px;
  margin-left: 4px;
  color: var(--text-secondary);
}

.card-desc {
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
