<script setup>
import { formatBytes, formatDate, glyphFor } from '../services/format.js'

const props = defineProps({
  node: { type: Object, required: true },
  trash: { type: Boolean, default: false }
})

const emit = defineEmits(['open', 'download', 'rename', 'remove', 'restore', 'purge'])

function onActivate() {
  if (props.trash) return
  if (props.node.folder) emit('open', props.node)
  else emit('download', props.node)
}
</script>

<template>
  <article class="card" :class="{ folder: node.folder }">
    <button class="body" @dblclick="onActivate"
            :title="trash ? '' : node.folder ? 'Abrir' : 'Descargar'">
      <span class="glyph">{{ glyphFor(node) }}</span>
      <span class="name">{{ node.name }}</span>
      <span class="meta">
        <template v-if="node.folder">Carpeta</template>
        <template v-else>{{ formatBytes(node.size) }}</template>
        · {{ trash ? 'eliminado ' + formatDate(node.trashedAt) : formatDate(node.createdAt) }}
      </span>
    </button>

    <div v-if="!trash" class="actions">
      <button v-if="!node.folder" @click="emit('download', node)" title="Descargar">↓</button>
      <button @click="emit('rename', node)" title="Renombrar">✎</button>
      <button class="danger" @click="emit('remove', node)" title="Mover a la papelera">🗑</button>
    </div>

    <div v-else class="actions">
      <button @click="emit('restore', node)" title="Restaurar">↩ Restaurar</button>
      <button class="danger" @click="emit('purge', node)" title="Eliminar definitivamente">Borrar</button>
    </div>
  </article>
</template>

<style scoped>
.card {
  position: relative;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  box-shadow: var(--shadow);
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
  overflow: hidden;
}

.card:hover {
  transform: translateY(-3px);
  border-color: var(--accent);
  box-shadow: 0 10px 30px rgba(232, 98, 61, 0.14);
}

.card.folder .glyph {
  filter: saturate(1.1);
}

.body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: 22px 18px 16px;
  text-align: left;
}

.glyph {
  font-size: 38px;
  line-height: 1;
}

.name {
  font-weight: 600;
  font-size: 15px;
  color: var(--ink);
  word-break: break-word;
  line-height: 1.25;
}

.meta {
  font-size: 12px;
  color: var(--ink-soft);
}

.actions {
  display: flex;
  gap: 2px;
  padding: 0 10px 10px;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.card:hover .actions {
  opacity: 1;
  transform: none;
}

.actions button {
  flex: 1;
  padding: 7px 0;
  border-radius: 8px;
  font-size: 13px;
  color: var(--ink-soft);
  transition: background 0.12s ease, color 0.12s ease;
}

.actions button:hover {
  background: var(--bg);
  color: var(--ink);
}

.actions button.danger:hover {
  background: var(--accent-soft);
  color: var(--accent);
}
</style>
