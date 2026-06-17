<script setup>
import { ref, computed, onMounted } from 'vue'
import { api, session } from './services/api.js'
import { formatBytes } from './services/format.js'
import FileCard from './components/FileCard.vue'
import Breadcrumbs from './components/Breadcrumbs.vue'
import AuthView from './components/AuthView.vue'

const authed = ref(session.isAuthed())
const username = ref(session.username())
const view = ref('files') // 'files' | 'trash'

const trail = ref([{ id: 'root', name: 'Mi unidad' }])
const nodes = ref([])
const trashNodes = ref([])
const loading = ref(false)
const error = ref('')
const dragging = ref(false)
const uploads = ref([])
const fileInput = ref(null)

const currentId = computed(() => trail.value[trail.value.length - 1].id)
const totalSize = computed(() =>
  nodes.value.filter((n) => !n.folder).reduce((acc, n) => acc + n.size, 0)
)

function onAuthed() {
  authed.value = true
  username.value = session.username()
  refresh()
}

function logout() {
  session.clear()
  authed.value = false
  nodes.value = []
  trail.value = [{ id: 'root', name: 'Mi unidad' }]
}

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    if (view.value === 'trash') {
      trashNodes.value = await api.listTrash()
    } else {
      nodes.value = await api.list(currentId.value)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function openTrash() {
  view.value = 'trash'
  refresh()
}

function openFiles() {
  view.value = 'files'
  refresh()
}

function openFolder(node) {
  trail.value.push({ id: node.id, name: node.name })
  refresh()
}

function navigateTo(index) {
  trail.value = trail.value.slice(0, index + 1)
  refresh()
}

async function newFolder() {
  const name = prompt('Nombre de la carpeta')
  if (!name) return
  try {
    await api.createFolder(name.trim(), currentId.value)
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

function pickFiles() {
  fileInput.value.click()
}

async function onFilesPicked(event) {
  await uploadFiles([...event.target.files])
  event.target.value = ''
}

async function uploadFiles(files) {
  for (const file of files) {
    const entry = ref({ name: file.name, progress: 0 })
    uploads.value.push(entry.value)
    try {
      await api.upload(file, currentId.value, (p) => (entry.value.progress = p))
    } catch (e) {
      error.value = e.message
    } finally {
      uploads.value = uploads.value.filter((u) => u !== entry.value)
    }
  }
  await refresh()
}

function onDrop(event) {
  dragging.value = false
  if (view.value !== 'files') return
  const files = [...event.dataTransfer.files]
  if (files.length) uploadFiles(files)
}

async function renameNode(node) {
  const name = prompt('Nuevo nombre', node.name)
  if (!name || name === node.name) return
  try {
    await api.rename(node.id, name.trim())
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

async function trashNode(node) {
  try {
    await api.trash(node.id)
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

async function restoreNode(node) {
  try {
    await api.restore(node.id)
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

async function purgeNode(node) {
  if (!confirm(`¿Eliminar definitivamente "${node.name}"? Esto no se puede deshacer.`)) return
  try {
    await api.deleteForever(node.id)
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

async function emptyTrash() {
  if (!trashNodes.value.length) return
  if (!confirm('¿Vaciar la papelera por completo? Esto no se puede deshacer.')) return
  try {
    await api.emptyTrash()
    await refresh()
  } catch (e) {
    error.value = e.message
  }
}

function download(node) {
  api.download(node).catch((e) => (error.value = e.message))
}

onMounted(() => {
  if (authed.value) refresh()
})
</script>

<template>
  <AuthView v-if="!authed" @authed="onAuthed" />

  <div
    v-else
    class="shell"
    :class="{ dragging }"
    @dragover.prevent="dragging = true"
    @dragleave.prevent="dragging = false"
    @drop.prevent="onDrop"
  >
    <header class="topbar">
      <div class="brand">
        <span class="mark">☁</span>
        <div>
          <h1>Drive</h1>
          <p>tu nube serena</p>
        </div>
      </div>
      <div class="user">
        <span class="who">{{ username }}</span>
        <button class="link" @click="logout">Salir</button>
      </div>
    </header>

    <section class="toolbar">
      <Breadcrumbs v-if="view === 'files'" :trail="trail" @navigate="navigateTo" />
      <h2 v-else class="trash-title">🗑 Papelera</h2>

      <div class="tool-actions">
        <template v-if="view === 'files'">
          <button class="ghost" @click="openTrash">Papelera</button>
          <button class="ghost" @click="newFolder">＋ Carpeta</button>
          <button class="primary" @click="pickFiles">↑ Subir archivos</button>
          <input ref="fileInput" type="file" multiple hidden @change="onFilesPicked" />
        </template>
        <template v-else>
          <button class="ghost" @click="openFiles">← Volver</button>
          <button class="primary danger" :disabled="!trashNodes.length" @click="emptyTrash">
            Vaciar papelera
          </button>
        </template>
      </div>
    </section>

    <p v-if="view === 'files'" class="stats">
      <strong>{{ nodes.length }}</strong> elementos ·
      <strong>{{ formatBytes(totalSize) }}</strong> en esta carpeta
    </p>

    <p v-if="error" class="error">{{ error }}</p>

    <main class="content">
      <div v-if="loading" class="hint">Cargando…</div>

      <!-- Vista de archivos -->
      <template v-else-if="view === 'files'">
        <div v-if="!nodes.length" class="empty">
          <span class="empty-glyph">🪶</span>
          <p>Esta carpeta está vacía.</p>
          <p class="empty-sub">Arrastra archivos aquí o usa "Subir archivos".</p>
        </div>
        <div v-else class="grid">
          <FileCard
            v-for="node in nodes"
            :key="node.id"
            :node="node"
            @open="openFolder"
            @download="download"
            @rename="renameNode"
            @remove="trashNode"
          />
        </div>
      </template>

      <!-- Vista de papelera -->
      <template v-else>
        <div v-if="!trashNodes.length" class="empty">
          <span class="empty-glyph">✨</span>
          <p>La papelera está vacía.</p>
        </div>
        <div v-else class="grid">
          <FileCard
            v-for="node in trashNodes"
            :key="node.id"
            :node="node"
            trash
            @restore="restoreNode"
            @purge="purgeNode"
          />
        </div>
      </template>
    </main>

    <transition-group v-if="uploads.length" tag="div" name="pop" class="uploads">
      <div v-for="u in uploads" :key="u.name" class="upload">
        <span class="up-name">{{ u.name }}</span>
        <div class="bar"><div class="fill" :style="{ width: u.progress + '%' }"></div></div>
        <span class="up-pct">{{ u.progress }}%</span>
      </div>
    </transition-group>

    <div v-if="dragging && view === 'files'" class="dropzone">
      Suelta para subir a "{{ trail[trail.length - 1].name }}"
    </div>
  </div>
</template>

<style scoped>
.shell {
  max-width: 1120px;
  margin: 0 auto;
  padding: 40px 28px 80px;
  position: relative;
}

.shell.dragging::after {
  content: '';
  position: fixed;
  inset: 0;
  background: rgba(232, 98, 61, 0.06);
  pointer-events: none;
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 30px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.mark {
  font-size: 30px;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 18px;
  box-shadow: var(--shadow);
}

.brand h1 {
  font-family: 'Fraunces', serif;
  font-weight: 600;
  font-size: 30px;
  letter-spacing: -0.01em;
  line-height: 1;
}

.brand p {
  font-size: 13px;
  color: var(--ink-soft);
  font-style: italic;
}

.user {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}

.who {
  font-weight: 600;
}

.link {
  color: var(--accent);
  font-weight: 600;
}

.link:hover {
  text-decoration: underline;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 14px;
  padding: 14px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  margin-bottom: 16px;
}

.trash-title {
  font-family: 'Fraunces', serif;
  font-size: 20px;
  font-weight: 600;
}

.tool-actions {
  display: flex;
  gap: 10px;
}

.tool-actions button {
  padding: 10px 16px;
  border-radius: 10px;
  font-weight: 600;
  font-size: 14px;
  transition: transform 0.12s ease, background 0.12s ease, box-shadow 0.12s ease;
}

.tool-actions button:active {
  transform: translateY(1px);
}

.ghost {
  background: var(--surface-2);
  border: 1px solid var(--line);
  color: var(--ink);
}

.ghost:hover {
  border-color: var(--accent);
}

.primary {
  background: var(--accent);
  color: #fff;
  box-shadow: 0 6px 18px rgba(232, 98, 61, 0.28);
}

.primary:hover {
  background: #d6552f;
}

.primary.danger {
  background: #b23b1e;
}

.primary:disabled {
  opacity: 0.5;
  box-shadow: none;
}

.stats {
  font-size: 13px;
  color: var(--ink-soft);
  margin-bottom: 18px;
}

.stats strong {
  color: var(--ink);
}

.error {
  background: var(--accent-soft);
  color: #9c3a1e;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  margin-bottom: 18px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 16px;
}

.hint,
.empty {
  text-align: center;
  color: var(--ink-soft);
  padding: 60px 0;
}

.empty-glyph {
  font-size: 46px;
  display: block;
  margin-bottom: 12px;
}

.empty-sub {
  font-size: 13px;
  margin-top: 4px;
}

.uploads {
  position: fixed;
  right: 24px;
  bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 320px;
  max-width: calc(100vw - 48px);
}

.upload {
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--shadow);
  padding: 12px 14px;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 10px;
  align-items: center;
}

.up-name {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.up-pct {
  font-size: 12px;
  color: var(--ink-soft);
}

.bar {
  grid-column: 1 / -1;
  height: 6px;
  background: var(--bg);
  border-radius: 99px;
  overflow: hidden;
}

.fill {
  height: 100%;
  background: var(--accent);
  border-radius: 99px;
  transition: width 0.2s ease;
}

.dropzone {
  position: fixed;
  inset: 24px;
  border: 2px dashed var(--accent);
  border-radius: 24px;
  display: grid;
  place-items: center;
  font-family: 'Fraunces', serif;
  font-size: 24px;
  color: var(--accent);
  background: rgba(251, 227, 218, 0.55);
  backdrop-filter: blur(2px);
  pointer-events: none;
  z-index: 5;
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

.pop-enter-active,
.pop-leave-active {
  transition: all 0.2s ease;
}
</style>
