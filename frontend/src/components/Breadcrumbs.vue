<script setup>
defineProps({
  trail: { type: Array, required: true } // [{ id, name }]
})
const emit = defineEmits(['navigate'])
</script>

<template>
  <nav class="crumbs" aria-label="Ruta">
    <template v-for="(crumb, i) in trail" :key="crumb.id">
      <button
        class="crumb"
        :class="{ current: i === trail.length - 1 }"
        @click="emit('navigate', i)"
      >
        {{ crumb.name }}
      </button>
      <span v-if="i < trail.length - 1" class="sep">/</span>
    </template>
  </nav>
</template>

<style scoped>
.crumbs {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  font-size: 15px;
}

.crumb {
  padding: 4px 8px;
  border-radius: 8px;
  color: var(--ink-soft);
  font-weight: 500;
  transition: background 0.12s ease, color 0.12s ease;
}

.crumb:hover {
  background: var(--surface-2);
  color: var(--ink);
}

.crumb.current {
  color: var(--ink);
  font-weight: 600;
}

.sep {
  color: var(--line);
}
</style>
